@HandlerFor(source = "Hl7", subject = "PatientTransfer") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(2, "Inside the PatientTransfer groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
def patient = bundleUtility.getPatient(bundle)
def account = bundleUtility.getAccount(bundle)

autoCreate = parametersUtility.isEventExists(parameters)

patient.patientLocationDetails?.dischargeDate = null
account?.servicePeriod?.end = null

run("route", "PatientSaveRoute", map)

outcome