package master.inbound.interfaces.adtin.common.account

def class AccountHelper {
    def static accountURL = "http://varian.com/fhir/identifier/Account/Id"

    static def map(input_account, domainAccount) {
        mapIdentifiers(domainAccount.identifier, input_account.identifier)
        mapOwner(domainAccount, input_account)
        mapSubject(domainAccount, input_account)
        mapAccountClass(domainAccount, input_account)
        mapName(domainAccount, input_account)
        mapStatus(domainAccount, input_account)
    }

    static def mapServicePeriod(domainAccount, inputAccount, patientClassValues, patientDischargeDate) {
        if (inputAccount.servicePeriod.start != null) {
            domainAccount.servicePeriod.start = inputAccount.servicePeriod.start
        }

        if (patientClassValues."existingPatientClass" == "In Patient" && patientClassValues."newPatientClass" == "Out Patient") {
            if (inputAccount.servicePeriod.end != null) {
                domainAccount.servicePeriod.end = inputAccount.servicePeriod.end
            } else {
                domainAccount.servicePeriod.end = patientDischargeDate
            }
        } else if (patientClassValues."existingPatientClass" == "Out Patient" && patientClassValues."newPatientClass" == "Out Patient") {
            if (domainAccount.servicePeriod.end == null) {
                domainAccount.servicePeriod.end = inputAccount.servicePeriod.end
            }
        } else if (!(patientClassValues."existingPatientClass" == "Out Patient" && patientClassValues."newPatientClass" == "Out Patient")) {
            def inputValue = inputAccount.servicePeriod.end
            if (isNullOrEmpty(inputValue)) {
                inputValue = inputAccount.servicePeriod.getExtensionByUrl("ServicePeriodEnd")?.value
            }
            domainAccount.servicePeriod.end = getValue(inputValue, domainAccount.servicePeriod.end)
        }
    }

    static def mapSubject(domainAccount, inputAccount) {
        if (inputAccount.subject != null) {
            def domainPractitioner = domainAccount?.getSubject()?.find { subject -> subject.reference.contains("Practitioner") }
            def inputPractitioner = inputAccount?.getSubject()?.find { subject -> subject.reference?.contains("Practitioner") }?.reference
            def inputPractitionerDisplay = inputAccount?.getSubject()?.find { subject -> subject.type?.contains("Practitioner") }?.identifier?.value
            if (inputPractitionerDisplay == ResourceUtil.ACTIVE_NULL_LITERAL) {
                domainAccount.getSubject().removeIf { subject -> subject.reference.contains("Practitioner") }
            } else if (inputPractitioner != null) {
                if (domainPractitioner == null) {
                    domainAccount.getSubject().add(new Reference(inputPractitioner))
                } else {
                    domainPractitioner.reference = inputPractitioner
                }
            }
        }
    }

    static def mapOwner(domainAccount, inputAccount) {
        if (inputAccount.owner?.display == ResourceUtil.ACTIVE_NULL_LITERAL) {
            domainAccount.owner = null
        } else if (inputAccount.owner != null) {
            domainAccount.owner = inputAccount.owner
        }
    }

    static def mapName(domainAccount, inputAccount) {
        domainAccount.name = getValue(inputAccount.name, domainAccount.name)
    }

    static def mapIdentifiers(domainIdentifiers, input_identifiers) {
        for (identifier in input_identifiers) {
            def domainIdentifier = domainIdentifiers.find { it.system == identifier.system }
            if (domainIdentifier == null) {
                domainIdentifiers.add(identifier)
            } else if (identifier.value != null) {
                domainIdentifier.value = identifier.value
            }
        }
    }

    static def mapStatus(domainAccount, inputAccount) {
        if (inputAccount.status != null) {
            domainAccount.status = inputAccount.status
        }
    }

    static def mapAccountClass(domainAccount, input_account) {
        if (input_account.inPatient != null) {
            domainAccount.inPatient = input_account.inPatient
        }
    }

    static def getFutureAccountPatientClass(activeDomainAccounts, inputAccount) {
        def inputAccountCode = inputAccount?.identifier?.find { identifier -> identifier.system == accountURL }?.value?.toString()
        def activeDomainAccountsExceptInputAccount = activeDomainAccounts.findAll {
            def domainAccountCode = it.identifier?.find { identifier -> identifier.system == accountURL }?.value?.toString()
            domainAccountCode != inputAccountCode
        }
        if (!isNullOrEmpty(inputAccount)) {
            activeDomainAccountsExceptInputAccount.add(inputAccount)
        }

        def inPatientAccounts = activeDomainAccountsExceptInputAccount.findAll { it.inPatient != null && it.inPatient.booleanValue() }
        def accountGroupByBillingCode = inPatientAccounts.groupBy({ account ->
            account.identifier?.find { identifier -> identifier.system == accountURL }?.value?.toString()
        })

        def today = new Date()
        def foundValidInAccountClass = accountGroupByBillingCode.find { key, value ->
            if (value.size >= 1) {
                if ((!isNullOrEmpty(value[0].servicePeriod.start) && value[0].servicePeriod.start.before(today))
                        && (isNullOrEmpty(value[0].servicePeriod.end) || value[0].servicePeriod.end.after(today))
                        && value[0].status == com.varian.fhir.resources.Account.AccountStatus.ACTIVE) {
                    return true
                }
            }
        }
        return foundValidInAccountClass ? PatientHelper.IN_PATIENT : PatientHelper.OUT_PATIENT
    }
}