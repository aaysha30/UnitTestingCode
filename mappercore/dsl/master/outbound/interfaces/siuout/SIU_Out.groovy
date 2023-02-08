@HandlerFor(source = "Json", subject = "SiuOut") dsl
//json to HL7 conversion
Map<String, Object> map = (Map) getBinding().getVariables()
cloverLogger.log(1, "Execution of SiuOut groovy script is started...", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
def appointmentType = parametersUtility.getEventReasonForAppointment(parameters)
def patientSer = parametersUtility.getPatientSer(parameters)
def departmentSer = parametersUtility.getDepartmentSer(parameters)
def eventID = parametersUtility.getEventID(parameters)
def history_username = parametersUtility.getHistoryUserName(parameters)

cloverLogger.log(2, "Collected all required parameters from bundle", messageMetaData)
//def activityDefinition = bundleUtility.getActivityDefinition(bundle)
def practitioners = bundleUtility.getPractitioners(bundle)

def modifiedByPractitioner = practitioners.find {
    it.identifier.stream().anyMatch { id -> id.system == AppointmentHelper.PRACTITIONER_USER_NAME_IDENTIFIER && id.value == history_username }
}

AppointmentHelper appointmentHelper = new AppointmentHelper()
appointmentHelper.bundle = bundle
appointmentHelper.appointmentBundle = bundleUtility.getAppointment(bundle)
appointmentHelper.patientBundle = bundleUtility.getPatient(bundle)
appointmentHelper.careTeamBundle = bundleUtility.getCareTeam(bundle)
appointmentHelper.SqliteUtility = SqliteUtility
appointmentHelper.siteDirName = siteDirName
appointmentHelper.mappedSqliteDbName =mappedSqliteDbName
appointmentHelper.appointmentType = appointmentType
appointmentHelper.localConnection = globalInit.localConnection
appointmentHelper.masterConnection = globalInit.masterConnection
appointmentHelper.bundleUtility = bundleUtility
appointmentHelper.patientSer = patientSer
appointmentHelper.departmentSer = departmentSer
appointmentHelper.eventID = eventID
appointmentHelper.messageMetaData = messageMetaData
appointmentHelper.cloverLogger = cloverLogger
//appointmentHelper.activityDefinition = activityDefinition
appointmentHelper.modifiedByPractitioner = modifiedByPractitioner

cloverLogger.log(2, "AppointmentHelper is initialized with collected params", messageMetaData)

def suppressMessage = appointmentHelper.getActivityCodeValue()

if (!suppressMessage) {
    appointmentHelper.setSegments(siu_out)
    siu_out
} else {
    //cloverLogger.log(0, "Activity Code is not matching, hence message is suppressed", messageMetaData)
    siu_out = null
}