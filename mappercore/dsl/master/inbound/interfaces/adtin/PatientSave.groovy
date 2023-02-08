@HandlerFor(source = "Hl7", subject = "PatientSave") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
def parameters = bundleUtility.getParameters(bundle)

cloverLogger.log(1, "Inside the patientsave groovy", messageMetaData)

autoCreate = parametersUtility.isEventExists(parameters)
run("route", "PatientSaveRoute", map)
