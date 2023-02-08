@HandlerFor(source = "Hl7", subject = "PatientCancelDischarge") dsl

Map<String, Object> map = (Map) getBinding().getVariables()

def accountURL = "http://varian.com/fhir/identifier/Account/Id"
cloverLogger.log(1, "Inside the PatientCancelDischarge groovy", messageMetaData)
def parameters = bundleUtility.getParameters(bundle)
def patient = bundleUtility.getPatient(bundle)
def account = bundleUtility.getAccount(bundle)

cloverLogger.log(2, "Validating patient availability", messageMetaData)
def patientSearchKeys = parametersUtility.getPatientSearchKeys(parameters)
PatientValidation.isPatientIdentifierExist(patientSearchKeys)
def patientIdentifiersToSearch = patientUtility.getIdentifiers(patient, patientSearchKeys)
PatientValidation.isPatientIdentifierExist(patientIdentifiersToSearch)
def bundleDomain = clientDecor.search("Patient", "identifier", getIdentifierQuery(patientIdentifiersToSearch))
def patientDomain = bundleUtility.getPatient(bundleDomain)
cloverLogger.log(2, "Patient availability validation is passed", messageMetaData)
PatientValidation.isPatientExist(patientDomain, patientIdentifiersToSearch.first().value, outcome)

patientId = patientDomain.idElement.idPart
autoCreate = false
//Remove Account as handling of account will be event specific for PatientCancelDischarge
map?.get("bundle")?.entry?.removeIf {
    it?.getResource()?.fhirType() == "Account"
}

def accountNumber = account?.identifier?.find { identifier -> identifier.system == accountURL }?.value?.toString()

if (isNullOrEmpty(accountNumber)) {
    cloverLogger.log(0, "Account number is empty while canceling the patient discharge", messageMetaData)
    outcome.addWarning(ResponseCode.ACCOUNT_IS_NULL.value, ResponseCode.ACCOUNT_IS_NULL.toString())
    run("route", "PatientSaveRoute", map)
} else {
    cloverLogger.log(2, "Patient account number is $accountNumber", messageMetaData)
    def accountBundleDomain = clientDecor.search("Account", "patient", patientId, "identifier", getIdentifierQuery(accountURL, accountNumber))
    def accountsDomain = bundleUtility.getAccounts(accountBundleDomain)

    if (accountsDomain.size == 0) {
        cloverLogger.log(0, "Patient's account number is empty", messageMetaData)
        outcome.addWarning(ResponseCode.ACCOUNT_DOES_NOT_EXISTS.value, ResponseCode.ACCOUNT_DOES_NOT_EXISTS.toString())
        run("route", "PatientSaveRoute", map)
    } else {
        def accountDomain = accountBundleDomain.entry*.getResource().find { it.status != org.hl7.fhir.r4.model.Account.AccountStatus.ENTEREDINERROR }
        if (isNullOrEmpty(accountDomain)) {
            cloverLogger.log(0, "Patient's account number is empty", messageMetaData)
            def oo = outcome.getErrorOperationOutcome(ResponseCode.ACCOUNT_DOES_NOT_EXISTS.value, ResponseCode.ACCOUNT_DOES_NOT_EXISTS.toString())
            throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException(ResponseCode.ACCOUNT_DOES_NOT_EXISTS.toString(), oo)
        }
        account.subject = accountDomain.subject
        AccountHelper.map(account, accountDomain)
        if (account.servicePeriod.start != null) {
            accountDomain.servicePeriod.start = account.servicePeriod.start
        }

        accountDomain.servicePeriod.end = account.servicePeriod.end
        clientDecor.updateSafely(accountDomain)

        if (patient.patientClass == null) {
            patient.patientClass = new CodeableConcept()
        }
        patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
        if (!isNullOrEmpty(account.inPatient)) {
            if (account.inPatient.booleanValue()) {
                patient.patientClass.codingFirstRep.code = "In Patient"
                cloverLogger.log(2, "Patient class is set to in patient", messageMetaData)
            } else {
                patient.patientClass.codingFirstRep.code = "Out Patient"
                cloverLogger.log(2, "Patient class is set to out patient", messageMetaData)
            }
        } else if (!isNullOrEmpty(accountDomain.inPatient)) {
            if (accountDomain.inPatient.booleanValue()) {
                patient.patientClass.codingFirstRep.code = "In Patient"
                cloverLogger.log(2, "Patient class is set to in patient", messageMetaData)
            } else {
                patient.patientClass.codingFirstRep.code = "Out Patient"
                cloverLogger.log(2, "Patient class is set to out patient", messageMetaData)
            }
        }

        run("route", "PatientSaveRoute", map)
    }
}
