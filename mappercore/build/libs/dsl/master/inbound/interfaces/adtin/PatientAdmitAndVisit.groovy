@HandlerFor(source = "Hl7", subject = "PatientAdmitAndVisit") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(2, "Inside the PatientAdmitAndVisit groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
autoCreate = parametersUtility.isEventExists(parameters)
def patient = bundleUtility.getPatient(bundle)

cloverLogger.log(2, "Checking patient class is black or not", messageMetaData)
if (isNullOrEmpty(patient.patientClass?.codingFirstRep?.code)) {
    patient.patientClass = new CodeableConcept()
    cloverLogger.log(2, "Patient class is black", messageMetaData)
    patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
    patient.patientClass.codingFirstRep.code = PatientHelper.IN_PATIENT
    cloverLogger.log(2, "Patient class is set as in patient", messageMetaData)
} else if (patient.patientClass.codingFirstRep.code == PatientHelper.OUT_PATIENT) {
    cloverLogger.log(1, "Patient class is out patient for Amit and visit patient hence send warning back.", messageMetaData)
    outcome.addWarning(ResponseCode.INVALID_PATIENT_CLASS_ADT_A01.value, ResponseCode.INVALID_PATIENT_CLASS_ADT_A01.toString())
}

run("route", "PatientSaveRoute", map)

outcome