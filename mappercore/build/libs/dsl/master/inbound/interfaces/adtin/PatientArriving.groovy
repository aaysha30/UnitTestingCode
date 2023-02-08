import java.time.LocalDate

@HandlerFor(source = "Hl7", subject = "PatientArriving") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(1, "Inside the PatientArriving groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
def locationUrl = "http://varian.com/fhir/v1/StructureDefinition/patient-location#venue"

def patientSearchKeys = parametersUtility.getPatientSearchKeys(parameters)
PatientValidation.isPatientIdentifierExist(patientSearchKeys)

def patient = bundleUtility.getPatient(bundle)
def patientIdentifiersToSearch = patientUtility.getIdentifiers(patient, patientSearchKeys)
cloverLogger.log(2, "Checking patient exist or not", messageMetaData)
PatientValidation.isPatientIdentifierExist(patientIdentifiersToSearch)

def bundleDomain = clientDecor.search("Patient", "identifier", getIdentifierQuery(patientIdentifiersToSearch))
def patientDomain = bundleUtility.getPatient(bundleDomain)

autoCreate = parametersUtility.isEventExists(parameters)
if (autoCreate && isNullOrEmpty(patientDomain)) {
    outcome.addWarning(String.format(ResponseCode.PATIENT_AUTO_CREATE.value, patientIdentifiersToSearch.first().value), ResponseCode.PATIENT_AUTO_CREATE.toString())
    return run("route", "PatientSaveRoute", map)
} else {
    PatientValidation.isPatientExist(patientDomain, patientIdentifiersToSearch.first().value, outcome)
}

def locationSystem = patient?.extension?.find { extension -> extension.url == locationUrl }?.value?.system
def venueValue = patient?.extension?.find { extension -> extension.url == locationUrl }?.value?.value
cloverLogger.log(2, "Checking patient venue exist or not", messageMetaData)
if (isNullOrEmpty(venueValue)) {
    cloverLogger.log(2, "Patient venue is empty while arriving the patient", messageMetaData)
    def oo = outcome.getErrorOperationOutcome(ResponseCode.PATIENT_ARRIVING_LOCATION_NOT_SPECIFIED.value, ResponseCode.PATIENT_ARRIVING_LOCATION_NOT_SPECIFIED.toString())
    throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException(ResponseCode.PATIENT_ARRIVING_LOCATION_NOT_SPECIFIED.value, oo)
}

def locationBundleDomain = clientDecor.search(
        "Location",
        "identifier",
        getIdentifierQuery(locationSystem, venueValue)
)
def venue = bundleUtility.getLocation(locationBundleDomain)

if (venue == null) {
    cloverLogger.log(2, "Patient venue is empty while arriving the patient", messageMetaData)
    def oo = outcome.getErrorOperationOutcome(ResponseCode.PATIENT_ARRIVING_INVALID_LOCATION.value, ResponseCode.PATIENT_ARRIVING_INVALID_LOCATION.toString())
    throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(ResponseCode.PATIENT_ARRIVING_INVALID_LOCATION.value, oo)
}
patientId = patientDomain?.idElement?.idPart

def today = new org.joda.time.LocalDate()
def formatted = today.toString('yyyy-MM-dd')

def appointmentBundleDomain = clientDecor.search(
        "Appointment",
        "patient",
        patientId,
        "date",
        formatted
)
def appointments = bundleUtility.getAppointments(appointmentBundleDomain)
def listOfAppointments = appointments.findAll {
    def appointmentStartDate = new org.joda.time.LocalDate(it.start)
    def appointmentEndDate = new org.joda.time.LocalDate(it.end)
    (appointmentStartDate >= today && appointmentEndDate < today.plusDays(1)
            && it.status?.toCode() != "entered-in-error" && (it.status?.toCode() != "cancelled"))
}

if (isNullOrEmpty(listOfAppointments)) {
    cloverLogger.log(0, "Error: Patient is arriving/check-in and no appointment for patient", messageMetaData)
    outcome.addError(String.format(ResponseCode.PATIENT_ARRIVING_NO_SCHEDULED_ACTIVITY.value, patientIdentifiersToSearch.first().value, patientDomain?.managingOrganization?.display), ResponseCode.PATIENT_ARRIVING_NO_SCHEDULED_ACTIVITY.toString())
} else {
    def appointmentIssues = new ArrayList()
    cloverLogger.log(2, "Patient is arriving/check-in and appointment is available for patient", messageMetaData)
    def inParams = new Parameters()
    inParams.addParameter().setName("locationKey").value = new StringType(venue?.idElement?.idPart)

    listOfAppointments.each() { appointmentDomain ->
        try {
            clientDecor.operation(appointmentDomain, "\$checkin", "Appointment", inParams)
        } catch (Exception exception) {
            appointmentIssues.add(exception)
        }
    }

    if (!appointmentIssues.isEmpty()) {
        cloverLogger.log(2, "No appointment issue is found", messageMetaData)
        if (listOfAppointments.size() == appointmentIssues.size()) {
            appointmentIssues.each { outcome.addError(it) }
            outcome.addError(ResponseCode.PATIENT_ARRIVING_FAILED.value, ResponseCode.PATIENT_ARRIVING_FAILED.toString())
        } else {
            appointmentIssues.each { outcome.addWarning(it) }
            def successCount = listOfAppointments.size() - appointmentIssues.size()
            outcome.addWarning(String.format(ResponseCode.PATIENT_ARRIVING_PARTIAL_SUCCESS.value, successCount, listOfAppointments.size()), ResponseCode.PATIENT_ARRIVING_PARTIAL_SUCCESS.toString())
        }
    }
}

run("route", "PatientSaveRoute", map)
