package master.inbound.interfaces.adtin.common.coverage

def class CoverageHelper {
    static def planTypes
    static def clientDecor
    static def insurancePlanSystem = "http://varian.com/fhir/identifier/InsurancePlan/Id"
    def static COVERAGE_PLAN_TYPE = "http://varian.com/fhir/CodeSystem/aria-coverage-policyPlanType"
    def static INSURANCE_PLAN_TYPE = "http://varian.com/fhir/CodeSystem/aria-payor-planType"
    def static INSURANCE_PLAN_START_PERIOD_EXTENSION = "ServicePeriodStart"
    def static INSURANCE_PLAN_END_PERIOD_EXTENSION = "ServicePeriodEnd"

    static def isCoverageValid(coverages, outcome) {
        def count = 0
        coverages?.each { coverage ->
            if (coverage.insurancePlan == null) {
                count++
            } else {
                def insurancePlan = coverage.insurancePlan.resource
                def insurancePlanValue = insurancePlan?.identifier?.find { it.system == insurancePlanSystem }?.value
                if (insurancePlanValue == ACTIVE_NULL_LITERAL || insurancePlan?.name == ACTIVE_NULL_LITERAL) {
                    count++
                }
            }
        }

        if (count > 0) {
            outcome.addWarning(ResponseCode.COVERAGE_INVALID_PLAN.value, ResponseCode.COVERAGE_INVALID_PLAN.toString())
        }

        //if all coverages validation failed then do not proceed.
        //if any coverage validation passes, then proceed.
        return count != coverages.size()
    }

    static def getValidPlanType(code) {
        if (!isNullOrEmpty(code) && code != ResourceUtil.ACTIVE_NULL_LITERAL) {
            def foundCode = planTypes.find { it.system == COVERAGE_PLAN_TYPE && it.code == code }
            if (foundCode == null) {
                return ""
            }
        }
        return code
    }

    static def getInsurancePlan(insurancePlan, clientDecor, outcome) {
        def insurancePlanValue = insurancePlan.identifier.find { it.system == insurancePlanSystem }?.value
        def domainInsurancePlan
        if (insurancePlanValue != null) {
            def insurancePlanDomainBundle = clientDecor.search(
                    "InsurancePlan", "name", insurancePlan.name,
                    "identifier", getIdentifierQuery(insurancePlanSystem, insurancePlanValue)
            )
            domainInsurancePlan = getResource(insurancePlanDomainBundle, "InsurancePlan")
        }

        def insurancePlanId = null
        if (domainInsurancePlan != null) {
            insurancePlanId = domainInsurancePlan?.idElement?.idPart
            mapInsurancePlanResource(insurancePlan, domainInsurancePlan)
            clientDecor.updateSafely(domainInsurancePlan)
        } else {
            def opOutcome = clientDecor.createSafely(insurancePlan)
            if (opOutcome != null) {
                insurancePlanId = opOutcome?.id?.idPart
                outcome.addInformation("insurance plan created: ${insurancePlanId}")
            }
        }
        return insurancePlanId
    }

    static def map(input_coverage, domainCoverage) {
        mapPrimary(domainCoverage, input_coverage)
        mapVerifiedOn(domainCoverage, input_coverage)
        mapInsurancePlan(domainCoverage, input_coverage)
        mapCostToBeneficiary(domainCoverage, input_coverage)
        mapPayor(domainCoverage, input_coverage)
        mapSubscriberId(domainCoverage, input_coverage)
        mapSubscriber(domainCoverage, input_coverage)
        mapType(domainCoverage, input_coverage)
        mapRelationship(domainCoverage, input_coverage)
        mapDependent(domainCoverage, input_coverage)
    }

    static def mapInsurancePlanResource(insurancePlan, domainInsurancePlan) {
        mapInsurancePlan_PlanType(insurancePlan, domainInsurancePlan)
        mapInsurancePlan_period(insurancePlan, domainInsurancePlan)
        mapInsurancePlanContact(insurancePlan, domainInsurancePlan)
    }

    static def mapCER(inputCER, domainCER) {
        mapAuthorizedBy(inputCER, domainCER)
        mapAuthorizationDate(inputCER, domainCER)
        mapAuthorizationId(inputCER, domainCER)
        mapAuthorizedPhoneAndFax(inputCER, domainCER)
        mapAuthorizationDesc(inputCER, domainCER)
    }

    static def mapAuthorizedBy(inputCER, domainCER) {
        def asserterVal = getValue(inputCER.asserter?.identifier?.value, domainCER.asserter?.identifier?.value)
        if (domainCER.asserter == null) {
            domainCER.asserter = new Reference()
        }
        domainCER.asserter.identifier.value = asserterVal
    }

    static def mapAuthorizationDate(inputCER, domainCER) {
        def inputValue = inputCER.servicedPeriod.start
        if (isNullOrEmpty(inputValue)) {
            inputValue = inputCER.servicedPeriod.getExtensionByUrl("ServicePeriod")?.value
        }

        domainCER.servicedPeriod.start = getValue(inputValue, domainCER.servicedPeriod.start)
    }

    static def mapAuthorizationId(inputCER, domainCER) {
        domainCER.preAuthRef = getValue(inputCER.preAuthRef, domainCER.preAuthRef)
        def domainAuthIdentifier = domainCER.identifier.find { it.system == inputCER.identifier[0].system }
        if (domainAuthIdentifier == null) {
            inputCER.identifier[0].value = domainCER.preAuthRef
            domainCER.identifier.add(inputCER.identifier[0])
        } else {
            domainAuthIdentifier.value = domainCER.preAuthRef
        }
    }

    static def mapAuthorizationDesc(inputCER, domainCER) {
        domainCER.disposition = getValue(inputCER.disposition, domainCER.disposition)
    }


    static def mapAuthorizedPhoneAndFax(inputCER, domainCER) {
        if (domainCER.authorTelecom == null) {
            domainCER.authorTelecom = []
        }
        inputCER.authorTelecom.each { inputTelecom ->
            def domainTelecom = domainCER.authorTelecom?.find { domainTele ->
                (inputTelecom.system == domainTele.system
                        && inputTelecom.use == domainTele.use)
            }
            def inputValue = getValue(inputTelecom.value, domainTelecom?.value)
            if (domainTelecom == null) {
                inputTelecom.value = inputValue
                domainCER.authorTelecom.add(inputTelecom)
            } else {
                domainTelecom.value = inputValue
            }
        }
    }

    static def isCoverageMatched(coverage, domainCoverages) {
        return domainCoverages.find {
            ((coverage.insurancePlan?.identifier?.value == it.insurancePlan.display)
                    && ((!isNullOrEmpty(coverage.subscriberId) && coverage.subscriberId == it.subscriberId)
                    || (isNullOrEmpty(coverage.subscriberId) && it.dependent == coverage.dependent)))
        }
    }

    static def mapPrimary(domainCoverage, inputCoverage) {
        if (inputCoverage.primary?.booleanValue()) {
            domainCoverage.primary = inputCoverage.primary
        }
    }

    static def mapVerifiedOn(domainCoverage, inputCoverage) {
        def inputValue = inputCoverage.verifiedOn
        if (isNullOrEmpty(inputValue)) {
            inputValue = inputCoverage.getExtensionByUrl("verifiedOn")?.value
        }

        domainCoverage.verifiedOn = getValue(inputValue, domainCoverage.verifiedOn)
    }

    static def mapType(domainCoverage, inputCoverage) {
        domainCoverage.type.codingFirstRep.system = inputCoverage.type.codingFirstRep.system
        domainCoverage.type.codingFirstRep.code = getValue(inputCoverage.type.codingFirstRep.code, domainCoverage.type.codingFirstRep.code)
    }

    static def mapSubscriber(domainCoverage, inputCoverage) {
        domainCoverage.subscriber?.identifier?.system = inputCoverage.subscriber?.identifier?.system
        domainCoverage.subscriber?.identifier?.value = getValue(inputCoverage.subscriber?.identifier?.value?.trim(), domainCoverage.subscriber?.identifier?.value)
    }

    static def mapInsurancePlan(domainCoverage, inputCoverage) {
        if (inputCoverage.insurancePlan != null)
            domainCoverage.insurancePlan = inputCoverage.insurancePlan
    }

    static def mapCostToBeneficiary(domainCoverage, inputCoverage) {
        if (isNullOrEmpty(domainCoverage.costToBeneficiary))
            domainCoverage.costToBeneficiary = inputCoverage.costToBeneficiary
        else {
            def element = domainCoverage.costToBeneficiary.find {
                (it.type.codingFirstRep.system == inputCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.system &&
                        it.type.codingFirstRep.code == inputCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.code)
            }
            if (element != null) {
                if (inputCoverage.costToBeneficiaryFirstRep.hasValueMoney()) {
                    def inputValue = inputCoverage.costToBeneficiaryFirstRep.valueMoney.value
                    if (isNullOrEmpty(inputValue)) {
                        inputValue = inputCoverage.costToBeneficiaryFirstRep.valueMoney.extensionFirstRep.value
                    }
                    element.valueMoney.value = getValue(inputValue, element.valueMoney.value)
                }
            } else {
                domainCoverage.costToBeneficiary.add(inputCoverage.costToBeneficiaryFirstRep)
            }
        }
    }

    static def mapPayor(domainCoverage, inputCoverage) {
        domainCoverage.payor = getValue(inputCoverage.payor, domainCoverage.payor)
    }

    static def mapSubscriberId(domainCoverage, inputCoverage) {
        domainCoverage.subscriberId = getValue(inputCoverage.subscriberId, domainCoverage.subscriberId)
    }

    static def mapRelationship(domainCoverage, inputCoverage) {
        domainCoverage.relationship.text = getValue(inputCoverage.relationship.text, domainCoverage.relationship.text)
    }

    static def mapDependent(domainCoverage, inputCoverage) {
        domainCoverage.dependent = getValue(inputCoverage.dependent, domainCoverage.dependent)
    }

    static def mapInsurancePlan_PlanType(insurancePlan, domainInsurancePlan) {
        def insurancePlanType = insurancePlan?.type?.stream()?.flatMap { it.coding.stream() }?.find { it.system == INSURANCE_PLAN_TYPE }?.code
        def domainInsurancePlanType = domainInsurancePlan?.type?.stream()?.flatMap { it.coding.stream() }?.find { it.system == INSURANCE_PLAN_TYPE }
        def updatedPlanType = getValue(insurancePlanType, domainInsurancePlanType?.code)
        if (domainInsurancePlanType == null) {
            domainInsurancePlan.typeFirstRep.addCoding(new Coding().setSystem(INSURANCE_PLAN_TYPE).setCode(updatedPlanType))
        } else {
            domainInsurancePlanType.code = updatedPlanType
        }
    }

    static def mapInsurancePlan_period(insurancePlan, domainInsurancePlan) {
        def inputValue = insurancePlan.period.start
        if (isNullOrEmpty(inputValue)) {
            inputValue = insurancePlan.period.getExtensionByUrl(INSURANCE_PLAN_START_PERIOD_EXTENSION)?.value
        }

        domainInsurancePlan.period.start = getValue(inputValue, domainInsurancePlan.period.start)

        inputValue = insurancePlan.period.end
        if (isNullOrEmpty(inputValue)) {
            inputValue = insurancePlan.period.getExtensionByUrl(INSURANCE_PLAN_END_PERIOD_EXTENSION)?.value
        }

        domainInsurancePlan.period.end = getValue(inputValue, domainInsurancePlan.period.end)
    }

    static def mapInsurancePlanContact(insurancePlan, domainInsurancePlan) {
        def insurancePlanContact = insurancePlan.contactFirstRep
        def domainInsurancePlanContact = domainInsurancePlan.contactFirstRep
        domainInsurancePlanContact.purpose = insurancePlanContact.purpose
        domainInsurancePlanContact.name.text = getValue(insurancePlanContact.name.text, domainInsurancePlanContact.name.text)
        def phoneContact = insurancePlanContact.telecom?.find { it.system?.toCode() == "phone" && it.use?.toCode() == "work" }?.value
        def domainPhoneContact = domainInsurancePlanContact.telecom?.find { it.system?.toCode() == "phone" && it.use?.toCode() == "work" }?.value
        def emailContact = insurancePlanContact.telecom?.find { it.system?.toCode() == "email" && it.use?.toCode() == "work" }?.value
        def domainEmailContact = domainInsurancePlanContact.telecom?.find { it.system?.toCode() == "email" && it.use?.toCode() == "work" }?.value
        def faxContact = insurancePlanContact.telecom?.find { it.system?.toCode() == "fax" && it.use?.toCode() == "work" }?.value
        def domainFaxContact = domainInsurancePlanContact.telecom?.find { it.system?.toCode() == "fax" && it.use?.toCode() == "work" }?.value
        def phoneValue = getValue(phoneContact, domainPhoneContact)
        def emailValue = getValue(emailContact, domainEmailContact)
        def faxValue = getValue(faxContact, domainFaxContact)
        domainInsurancePlanContact.telecom = []
        domainInsurancePlanContact.addTelecom(new ContactPoint().setSystem(ContactPoint.ContactPointSystem.PHONE).setUse(ContactPoint.ContactPointUse.WORK).setValue(phoneValue))
        domainInsurancePlanContact.addTelecom(new ContactPoint().setSystem(ContactPoint.ContactPointSystem.EMAIL).setUse(ContactPoint.ContactPointUse.WORK).setValue(emailValue))
        domainInsurancePlanContact.addTelecom(new ContactPoint().setSystem(ContactPoint.ContactPointSystem.FAX).setUse(ContactPoint.ContactPointUse.WORK).setValue(faxValue))
        domainInsurancePlanContact.address.type = insurancePlanContact.address.type
        domainInsurancePlanContact.address.line = isActiveNullList(insurancePlanContact.address.line, insurancePlanContact.address.line.find { true }, domainInsurancePlanContact.address.line)
        domainInsurancePlanContact.address.city = getValue(insurancePlanContact.address.city, domainInsurancePlanContact.address.city)
        domainInsurancePlanContact.address.state = getValue(insurancePlanContact.address.state, domainInsurancePlanContact.address.state)
        domainInsurancePlanContact.address.postalCode = getValue(insurancePlanContact.address.postalCode, domainInsurancePlanContact.address.postalCode)
        domainInsurancePlanContact.address.country = getValue(insurancePlanContact.address.country, domainInsurancePlanContact.address.country)
    }

}