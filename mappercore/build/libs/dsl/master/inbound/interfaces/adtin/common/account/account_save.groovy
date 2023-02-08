@HandlerFor(source = "aria", subject = "AccountSave") dsl

def parameters = bundleUtility.getParameters(bundle)
def event = parametersUtility.getEvent(parameters)
def account = bundleUtility.getAccount(bundle)
def accountURL = "http://varian.com/fhir/identifier/Account/Id"
def accountId = account?.identifier?.find { identifier -> identifier.system == accountURL }?.value?.toString()
HospitalDepartmentHelper.hospitalDeptBundle = hospitalDeptBundle
HospitalDepartmentHelper.bundleUtility = bundleUtility
if (event != "ADT^A13" && patientClassValues."existingPatientClass" == "In Patient" && patientClassValues."newPatientClass" == "Out Patient" && (isNullOrEmpty(account) || isNullOrEmpty(accountId))) {
    outcome.addWarning(ResponseCode.ACCOUNT_IS_NULL.value, ResponseCode.ACCOUNT_IS_NULL.toString())
    return
}
if (isNullOrEmpty(account)) {
    return
}

def patientIdRefValue = "Patient/" + patientId
def practitionerIdRefValue
if (account?.getSubject()?.size > 0) {
    def patientReference = account?.getSubject()?.find { subject -> subject.type == "Patient" }
    if (patientReference == null) {
        account.getSubject().add(new Reference(patientIdRefValue))
    } else {
        patientReference.reference = patientIdRefValue
    }
    def reference = account?.getSubject()?.find { subject -> subject.type == "Practitioner" }
    def practitionerSystem = reference?.identifier?.system
    def practitionerId = reference?.identifier?.value
    if (practitionerId != null && practitionerSystem != null && practitionerId != ResourceUtil.ACTIVE_NULL_LITERAL) {
        def practitionerBundle = clientDecor.search("Practitioner", "identifier", getIdentifierQuery(practitionerSystem, practitionerId)) as Bundle
        def practitioner = bundleUtility.getPractitioner(practitionerBundle)
        if (practitioner != null) {
            def isOncologist = practitioner.practitionerRoleInARIA?.coding?.any { it.code == CareTeamHelper.PRIMARY_ONCOLOGIST_CODE || it.code == CareTeamHelper.ONCOLOGIST_CODE }
            if (isOncologist) {
                if (!practitioner.active) {
                    outcome.addWarning(String.format(ResponseCode.ACCOUNT_PROVIDER_NOT_ACTIVE.value, practitionerId), ResponseCode.ACCOUNT_PROVIDER_NOT_ACTIVE.toString())
                } else {
                    practitionerIdRefValue = "Practitioner/" + practitioner?.idElement?.idPart
                    reference.reference = practitionerIdRefValue
                }
            } else {
                outcome.addWarning(String.format(ResponseCode.ACCOUNT_PROVIDER_NOT_ONCOLOGIST.value, practitionerId), ResponseCode.ACCOUNT_PROVIDER_NOT_ONCOLOGIST.toString())
            }
        } else {
            outcome.addWarning(String.format(ResponseCode.ACCOUNT_INVALID_PROVIDER.value, practitionerId), ResponseCode.ACCOUNT_INVALID_PROVIDER.toString())
        }
    }
}

if (!isNullOrEmpty(account?.owner?.display) || !isNullOrEmpty(account?.owner?.identifier?.value)) {
    def ownerReference
    if (!isNullOrEmpty(account?.owner?.display)) {
        def departmentDomain = HospitalDepartmentHelper.getDepartmentByIdentifier(account.owner.display, account.owner.identifier?.value, clientDecor)
        if (departmentDomain != null) {
            ownerReference = new Reference("Organization/" + departmentDomain?.idElement?.idPart)
        }
    } else {
        hospitalDomain = HospitalDepartmentHelper.getHospitalOrganizationByName(account.owner.identifier.value, clientDecor)
        if (hospitalDomain != null) {
            ownerReference = new Reference("Organization/" + hospitalDomain?.idElement?.idPart)
        }
    }
    if (ownerReference != null) {
        account.owner = ownerReference
    }
}

def accountBundleDomain = clientDecor.search("Account", "patient", patientId, "identifier", getIdentifierQuery(accountURL, accountId))

def accountsDomain = accountBundleDomain.entry*.getResource().findAll { it.status != org.hl7.fhir.r4.model.Account.AccountStatus.ENTEREDINERROR }
if (accountsDomain.size > 1) {
    outcome.addWarning(String.format(ResponseCode.PATIENT_MULTIPLE_ACCOUNTS_ASSOCIATED.value, accountId, patientId), ResponseCode.PATIENT_MULTIPLE_ACCOUNTS_ASSOCIATED.toString())
} else {
    def opOutcome
    if (accountsDomain.size == 1) {
        def domainAccount = accountsDomain[0]
        AccountHelper.map(account, domainAccount)
        AccountHelper.mapServicePeriod(domainAccount, account, patientClassValues, patientDomain?.patientLocationDetails?.dischargeDate?.value)
        opOutcome = clientDecor.updateSafely(domainAccount)
    } else {
        if ((patientClassValues."existingPatientClass" == "In Patient" && patientClassValues."newPatientClass" == "Out Patient") || (event == "ADT^A03")) {
            outcome.addWarning(ResponseCode.ACCOUNT_DOES_NOT_EXISTS.value, ResponseCode.ACCOUNT_DOES_NOT_EXISTS.toString())
        } else {
            if (account.status == org.hl7.fhir.r4.model.Account.AccountStatus.ENTEREDINERROR) {
                outcome.addWarning(String.format("%s, %s", ResponseCode.ACCOUNT_DOES_NOT_EXISTS.value, ResponseCode.INVALID_ACCOUNT_STATUS.value), ResponseCode.ACCOUNT_DOES_NOT_EXISTS.toString())
            } else {
                opOutcome = clientDecor.createSafely(account)
            }
        }
    }
}
