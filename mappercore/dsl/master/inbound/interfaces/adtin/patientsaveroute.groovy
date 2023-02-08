@HandlerFor(source = "route", subject = "PatientSaveRoute") dsl

Map<String, Object> map = getBinding().getVariables()
HospitalDepartmentHelper.attachHospitalDepartments = AttachHospitalDepartments
HospitalDepartmentHelper.snapshotDepartments = SnapshotDepartments
HospitalDepartmentHelper.updatePrimaryDepartment = UpdatePrimaryDepartment
HospitalDepartmentHelper.client = clientDecor
HospitalDepartmentHelper.bundleUtility = bundleUtility
HospitalDepartmentHelper.hospitalDeptBundle = hospitalDeptBundle
PatientHelper.client = clientDecor
PatientHelper.bundleUtility = bundleUtility
cloverLogger.log(2, "Inside the PatientSaveRoute groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)

def defaultHospitalName = parametersUtility.getDefaultHospitalName(parameters)
cloverLogger.log(2, "Default hospital considered as: $defaultHospitalName", messageMetaData)
def defaultDepartmentId = parametersUtility.getDefaultDepartmentId(parameters)
cloverLogger.log(2, "Default department considered as: $defaultDepartmentId", messageMetaData)
def defaultRoomNumber = parametersUtility.getDefaultRoomNumber(parameters)
def currentDateTime = parametersUtility.getCurrentDateTime(parameters)
def advPatientClassProcess = parametersUtility.getAdvPatientClassProcess(parameters)
cloverLogger.log(2, "AdvPatinetClassProcess config value is: $advPatientClassProcess", messageMetaData)
def patientSearchKeys = parametersUtility.getPatientSearchKeys(parameters)
def patientDisallowUpdateKeys = parametersUtility.getPatientDisallowUpdateKeys(parameters)
def contactUpdateMode = parametersUtility.getContactUpdateMode(parameters)

PatientValidation.isPatientIdentifierExist(patientSearchKeys)


def patient = bundleUtility.getPatient(bundle)
patient.identifier.each { it?.value = it?.value?.trim() }
def patientIdentifiersToSearch = patientUtility.getIdentifiers(patient, patientSearchKeys)
PatientValidation.isPatientIdentifierExist(patientIdentifiersToSearch)

bundleDomain = null
patientDomain = null
// first search patient by domain id, if not found search by identifier
if (!isNullOrEmpty(patient?.idElement?.idPart)) {
    bundleDomain = clientDecor.search("Patient", "_id", patient?.idElement?.idPart)
    patientDomain = bundleUtility.getPatient(bundleDomain)
}
if (patientDomain == null) {
    bundleDomain = clientDecor.search("Patient", "identifier", getIdentifierQuery(patientIdentifiersToSearch))
    patientDomain = bundleUtility.getPatient(bundleDomain)
}


if (!autoCreate) {
    PatientValidation.isPatientExist(patientDomain, patientIdentifiersToSearch.first().value?.trim(), outcome)
}
cloverLogger.log(2, "Patient exist validation passed.", messageMetaData)

PatientHelper.removeInvalidAddresses(patient)

def opOutcome
patientClassValues = [:]
patientCreated = false
if (patientDomain == null) {
    patientCreated = true
    HospitalDepartmentHelper.validateAndUpdateHospital_DepartmentReference(bundle, bundleDomain, defaultHospitalName, defaultDepartmentId, clientDecor, outcome)
    cloverLogger.log(2, "Hospital and department for patient validated.", messageMetaData)
    PatientHelper.removeMultiplePrimaryContacts(patient)
    cloverLogger.log(2, "Removed multiple primary contacts", messageMetaData)
    PatientHelper.filterInvalidContact(patient.contact, outcome, patient)
    cloverLogger.log(2, "Removed invalid contact", messageMetaData)
    PatientHelper.assignMultipleName(patient)
    cloverLogger.log(2, "Assigned multiple name", messageMetaData)
    PatientHelper.mapPatientClass(patient, patientDomain, outcome, defaultRoomNumber, currentDateTime, advPatientClassProcess, patientClassValues)
    cloverLogger.log(2, "Assigned patient class", messageMetaData)
    opOutcome = clientDecor.create(patient)
    bundleDomain = clientDecor.search("CareTeam", "patient", opOutcome.id.idPart)

    cloverLogger.log(1, "Patient is created/updated in ARIA", messageMetaData)
} else {
    bundleDomain = bundleUtility.addResource(bundleDomain, clientDecor.search("CareTeam", "patient", patientDomain.idElement.idPart))
    HospitalDepartmentHelper.validateAndUpdateHospital_DepartmentReference(bundle, bundleDomain, defaultHospitalName, defaultDepartmentId, clientDecor, outcome)
    cloverLogger.log(2, "Hospital and department for patient validated.", messageMetaData)

    PatientHelper.map(patient, patientDomain, contactUpdateMode, user, outcome)
    PatientHelper.mapIdentifiers(patient, patientDomain, patientDisallowUpdateKeys, outcome)
    if (advPatientClassProcess == "1") {
        cloverLogger.log(2, "Patient's class would decided by looking patient account number availability, admit date, termination date, because advPatientClassProcess config is set 1", messageMetaData)
        domainAccounts = clientDecor.search("Account", "patient", patientDomain.idElement.idPart, "status", "active")
        PatientHelper.mapAdvancePatientClass(patient, patientDomain, patientClassValues, bundleUtility.getAccounts(domainAccounts), bundleUtility.getAccount(bundle), defaultRoomNumber, outcome)
    } else {
        cloverLogger.log(2, "Patient's class would be decided irrespective of other patient info because advPatientClassProcess config is set 0", messageMetaData)
        PatientHelper.mapPatientClass(patient, patientDomain, outcome, defaultRoomNumber, currentDateTime, advPatientClassProcess, patientClassValues)
    }
    opOutcome = clientDecor.update(patientDomain)
}

patientId = opOutcome.id.idPart

def params = new ArrayList<ScriptRunParam>()
params.add(new ScriptRunParam("aria","CareTeam",map,"CareTeam"))
params.add(new ScriptRunParam("aria","AllergyIntoleranceSave",map,"AllergyIntolerance"))
params.add(new ScriptRunParam("aria","AccountSave",map,"Account"))
params.add(new ScriptRunParam("aria","CoverageSave",map,"Coverage"))
params.add(new ScriptRunParam("aria","ConditionDiagnosis",map,"Condition"))
params.add(new ScriptRunParam("aria","PatientDirectiveSave",map,"Flag"))
executeAsync(params)

outcome
