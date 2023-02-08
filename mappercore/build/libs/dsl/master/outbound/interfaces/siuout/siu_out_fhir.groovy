@HandlerFor(source = "Json", subject = "SiuOutFhir") dsl

Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(2, "Execution of SiuOutFhir groovy script is started...", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
def appointmentSer = Double.parseDouble(parametersUtility.getScheduledActivitySer(parameters))
def patientSer = parametersUtility.getPatientSer(parameters)
def departmentSer = parametersUtility.getDepartmentSer(parameters)
def scheduleActivitySer = appointmentSer as long
def scheduleActivityRevCount = parametersUtility.getScheduleActivityRevCount(parameters)
def appointmentParam = "Appointment-" + scheduleActivitySer + "/_history/" + scheduleActivityRevCount
def appointmentHistoryUser = parametersUtility.getHistoryUserName(parameters)

cloverLogger.log(1, "Get appointment by its Id: $appointmentParam", messageMetaData)
def appointment = clientDecor.readById("Appointment", appointmentParam)
bundle = bundleUtility.addResource(bundle, appointment)

def searchParams = new ArrayList<ClientDecorCallableReference>()

//get modified by user details
def pracUserNameIdentifier = new TokenClientParam("identifier").exactly().systemAndCode(AppointmentHelper.PRACTITIONER_USER_NAME_IDENTIFIER, appointmentHistoryUser)
searchParams.add(new ClientDecorCallableReference(clientDecor.&search, null, "Practitioner", "", "identifier", pracUserNameIdentifier))

//get department associated with appointment
def deptSearchKey = "Organization-Dept-" + departmentSer
searchParams.add(new ClientDecorCallableReference(null, clientDecor.&readById, "Organization", deptSearchKey))

//get activity definition based on department and name
def activityCode = appointment.serviceTypeFirstRep?.codingFirstRep?.code
if (activityCode) {
    searchParams.add(new ClientDecorCallableReference(clientDecor.&search, null, "ActivityDefinition", "", "name", activityCode, "context-reference", deptSearchKey))
}

if (patientSer != null) {
    def pat = "Patient-" + patientSer
    cloverLogger.log(2, "Get care team and patient bundle for patient ser: $patientSer", messageMetaData)
    searchParams.add(new ClientDecorCallableReference(clientDecor.&search, null, "Patient", "", "_id", pat))
    searchParams.add(new ClientDecorCallableReference(clientDecor.&search, null, "CareTeam", "", "patient", pat, "_include", "*"))
}

appointment.participant.each {
    def resourceType = it.actor?.reference?.split("/")[0]
    if (resourceType == "Patient") {
        //skip because patient details are already fetched
    } else {
        searchParams.add(new ClientDecorCallableReference(null, clientDecor.&readById, resourceType, it.actor?.reference))
    }
}


def allBundles = executeClientDecorAsync(searchParams)//14
allBundles?.each {
    bundle = bundleUtility.addResource(bundle, it)
}

bundle