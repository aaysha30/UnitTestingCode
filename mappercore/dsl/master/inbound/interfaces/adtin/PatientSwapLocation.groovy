@HandlerFor(source = "Hl7", subject = "PatientSwapLocation") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
def parameters = bundleUtility.getParameters(bundle)
def patientSearchKeys = parametersUtility.getPatientSearchKeys(parameters)
cloverLogger.log(1, "Inside the PatientSwapLocation groovy", messageMetaData)
PatientValidation.isPatientIdentifierExist(patientSearchKeys)

//add warning for Out patient if any.
def index = 0
def patients = bundleUtility.getPatients(bundle)

//fail if 2 pid segments are not present
cloverLogger.log(2, "Validating patient id requirement and location information before swapping the patient location", messageMetaData)
def isIdentifierAndVisitDetailsPresent = true
if (patients?.size() >= 2) {
    patients.each {
        def patientIdentifiersToSearch = patientUtility.getIdentifiers(it, patientSearchKeys)
        def location = it.patientLocationDetails?.roomNumber
        if (isNullOrEmpty(patientIdentifiersToSearch) || isNullOrEmpty(location)) {
            isIdentifierAndVisitDetailsPresent = false
        }
    }
} else {
    isIdentifierAndVisitDetailsPresent = false
}
if (!isIdentifierAndVisitDetailsPresent) {
    cloverLogger.log(0, "Error/warning: patient identifier and visit information is not available", messageMetaData)
    def oo = outcome.getErrorOperationOutcome(ResponseCode.PATIENT_SWAP_LOCATION.value, ResponseCode.PATIENT_SWAP_LOCATION.toString())
    throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(ResponseCode.PATIENT_SWAP_LOCATION.toString(), oo)
}

patients?.each { patient ->
    if (patient.patientClass?.codingFirstRep?.code != "In Patient") {
        if (index == 0) {
            cloverLogger.log(0, "Warning: Location can not be swapped for first Outpatient", messageMetaData)
            outcome.addWarning("Location can not be swapped for first Outpatient")
        } else {
            cloverLogger.log(0, "Warning: Location can not be swapped for second Outpatient", messageMetaData)
            outcome.addWarning("Location can not be swapped for second Outpatient")
        }
    }
    index++
}


autoCreate = parametersUtility.isEventExists(parameters)
def messageHeaders = bundleUtility.getMessageHeader(bundle)
def careTeams = bundleUtility.getCareTeams(bundle)
def accounts = bundleUtility.getAccounts(bundle)
def consents = bundleUtility.getConsents(bundle)

index = 0
def errors = []
patients?.each { adtPatient ->
    def careTeam = careTeams.find { it?.subject?.reference == adtPatient.idElement.idPart }
    careTeam?.subject?.reference = null
    def consentsForThisPatient = consents.findAll { it?.patient?.reference == adtPatient.idElement.idPart }
    def accountForThisPatient = accounts.find { it?.subject?.find { ref -> ref.reference == adtPatient.idElement.idPart } != null }
    adtPatient?.id = null
    try {
        savePatient(adtPatient, parameters, messageHeaders, careTeam, accountForThisPatient, consentsForThisPatient)
    }catch(Exception ex){
         errors[index] = ex
         index++
    }
}

if(patients.size == index){
    errors.each { outcome.addError(it) }
} else {
    errors.each { outcome.addWarning(it) }
}

outcome


def savePatient(patient, parameters, messageHeader, careTeam, account, consents) {
    org.hl7.fhir.r4.model.Bundle updatePatientBundle = new org.hl7.fhir.r4.model.Bundle()
    updatePatientBundle?.addEntry()?.setResource(parameters)
    updatePatientBundle?.addEntry()?.setResource(messageHeader)
    updatePatientBundle?.addEntry()?.setResource(patient)
    if (careTeam != null) {
        updatePatientBundle?.addEntry()?.setResource(careTeam)
    }
    if (!isNullOrEmpty(account)) {
        updatePatientBundle?.addEntry()?.setResource(account)
    }
    if (!isNullOrEmpty(consents)) {
        consents.each {
            updatePatientBundle?.addEntry()?.setResource(it)
        }
    }
    Map<String, Object> updatePatientMap = (Map) getBinding().getVariables().clone()
    updatePatientMap.put("bundle", updatePatientBundle)

    run("route", "PatientSaveRoute", updatePatientMap)
}

