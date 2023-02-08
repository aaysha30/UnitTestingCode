@HandlerFor(source = "Hl7", subject = "PatientCancelAdmit") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(1, "Inside the PatientCancelAdmit groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
autoCreate = parametersUtility.isEventExists(parameters)
def patient = bundleUtility.getPatient(bundle)
def account = bundleUtility.getAccount(bundle)

if (isNullOrEmpty(patient.patientClass)) {
    patient.patientClass = new CodeableConcept()
    cloverLogger.log(2, "Patient class is empty", messageMetaData)
}

patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
patient.patientClass.codingFirstRep.code = "Out Patient"
cloverLogger.log(2, "Patient class is set to out patient", messageMetaData)

if (account != null) {
    account.status = org.hl7.fhir.r4.model.Account.AccountStatus.ENTEREDINERROR
    account.inPatient = new BooleanType(false)
}

run("route", "PatientSaveRoute", map)
