@HandlerFor(source = "Hl7", subject = "PatientPreadmit") dsl

Map<String, Object> map = (Map) getBinding().getVariables()

// Get Existing Domain Patient
cloverLogger.log(1, "Inside the PatientPreadmit groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
autoCreate = parametersUtility.isEventExists(parameters)
def patientSearchKeys = parametersUtility.getPatientSearchKeys(parameters)
PatientValidation.isPatientIdentifierExist(patientSearchKeys)

def patient = bundleUtility.getPatient(bundle)
def patientIdentifiersToSearch = patientUtility.getIdentifiers(patient, patientSearchKeys)
cloverLogger.log(2, "Checking patient exist or not before pre-admitting the patient", messageMetaData)
PatientValidation.isPatientIdentifierExist(patientIdentifiersToSearch)

bundleDomain = clientDecor.search("Patient", "identifier", getIdentifierQuery(patientIdentifiersToSearch))
patientDomain = bundleUtility.getPatient(bundleDomain)

if (!isNullOrEmpty(patientDomain)) {
    patient.patientClass = patientDomain.patientClass
    patient.patientLocationDetails.admissionDate = patientDomain.patientLocationDetails.admissionDate
    patient.patientLocationDetails.dischargeDate = patientDomain.patientLocationDetails.dischargeDate
} else {
    if (isNullOrEmpty(patient.patientClass)) {
        cloverLogger.log(2, "Patient class is empty in pre-admit mode", messageMetaData)
        patient.patientClass = new CodeableConcept()
    }

    patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
    patient.patientClass.codingFirstRep.code = "Out Patient"
    cloverLogger.log(2, "Patient class is set to out patient", messageMetaData)
}

run("route", "PatientSaveRoute", map)
outcome