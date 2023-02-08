@HandlerFor(source = "Hl7", subject = "PatientDischarge") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(1, "Inside the PatientDischarge groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
autoCreate = parametersUtility.isEventExists(parameters)
def patient = bundleUtility.getPatient(bundle)

if (isNullOrEmpty(patient.patientClass)) {
    cloverLogger.log(2, "Patient class is blank at this stage", messageMetaData)
    patient.patientClass = new CodeableConcept()
}
cloverLogger.log(2, "validating admission date and discharge date before discharging the patient", messageMetaData)
if (!isNullOrEmpty(patient.patientLocationDetails.admissionDate) && !isNullOrEmpty(patient.patientLocationDetails.dischargeDate)
        && patient.patientLocationDetails.admissionDate.value.after(patient.patientLocationDetails.dischargeDate.value)) {
    outcome.addError(ResponseCode.IGNORE_PATIENT_CLASS.value.toString(), ResponseCode.IGNORE_PATIENT_CLASS.toString())
    outcome.addError(ResponseCode.INVALID_ADMIT_DISCHARGE_DATE.value.toString(), ResponseCode.INVALID_ADMIT_DISCHARGE_DATE.toString())
} else {
    patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
    patient.patientClass.codingFirstRep.code = "Out Patient"
    cloverLogger.log(2, "Patient class is set to out patient", messageMetaData)
    run("route", "PatientSaveRoute", map)
}



outcome
