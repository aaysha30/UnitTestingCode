@HandlerFor(source = "Hl7", subject = "PatientOut") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(1, "Inside the PatientOut groovy", messageMetaData)
def patient = bundleUtility.getPatient(bundle)
autoCreate = true
if (isNullOrEmpty(patient.patientClass)) {
    cloverLogger.log(2, "Patient class is empty in patient out mode", messageMetaData)
    patient.patientClass = new CodeableConcept()
}

patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
patient.patientClass.codingFirstRep.code = "Out Patient"
cloverLogger.log(2, "Patient class is set to out patient", messageMetaData)

run("route", "PatientSaveRoute", map)
outcome