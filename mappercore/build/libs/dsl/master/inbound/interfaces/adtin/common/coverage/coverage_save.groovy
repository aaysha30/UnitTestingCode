@HandlerFor(source = "aria", subject = "CoverageSave") dsl

def coverages = getAllResources(bundle, "Coverage")
if (!CoverageHelper.isCoverageValid(coverages, outcome)) {
    return
}

CoverageHelper.clientDecor = clientDecor
CoverageHelper.planTypes = policyPlanTypeValueSet.expansion?.containsFirstRep?.contains

def coverageEligibilityResponses = getAllResources(bundle, "CoverageEligibilityResponse")
if (!isNullOrEmpty(coverages)) {
    def parameters = bundleUtility.getParameters(bundle)
    def patientReference = "Patient/" + patientId
    def allowUpdateOnPrimaryCheck = parametersUtility.allowUpdateOnCoveragePrimaryCheck(parameters)
    def insuranceUpdateMode = parametersUtility.getInsuranceUpdateMode(parameters)

    def domainCoverages = clientDecor.search("Coverage", "patient", patientId, "_count", "50").entry*.getResource()

    def isCoverageSnapshot = false
    if (!isNullOrEmpty(domainCoverages)) {
        if (insuranceUpdateMode == "SnapshotAll") {
            domainCoverages.each {
                clientDecor.delete(it)
            }
            isCoverageSnapshot = true
        } else if (insuranceUpdateMode == "SnapshotOwn") {
            domainCoverages.each {
                def lastModifiedUser = it.lastModificationUser?.reference?.replace('Practitioner/', '')
                if (lastModifiedUser == user.idElement.idPart) {
                    clientDecor.delete(it)
                    isCoverageSnapshot = true
                    //what if primary gets deleted?
                }
            }
        }
    }

    if (isCoverageSnapshot) {
        domainCoverages = clientDecor.search("Coverage", "patient", patientId, "_count", "50").entry*.getResource()
    }

    def firstCoverage = coverages.first()

    def noExistingCoverageFound = isNullOrEmpty(domainCoverages)
    if (noExistingCoverageFound || allowUpdateOnPrimaryCheck) {
        //if patient does not have any coverages then mark the first coverage as primary
        //or if system is configured to update primary value of coverage, mark the first coverage as primary
        firstCoverage.primary = new BooleanType(true)
    }

    /* these variables will hold the coverage resource, whose primary is true while creating and updating
    and these coverages will be created or updated at the end.
    because fhir change the version id for all other if it updates one with primary = true */
    def inputPrimaryCoverageToCreate
    def inputPrimaryCoverageToCreate_EligibilityResponse
    def inputPrimaryCoverageToUpdate
    def inputPrimaryCoverageToUpdate_EligibilityResponse
    def domainPrimaryCoverageToUpdate
    def domainPrimaryCoverageToUpdate_EligibilityResponse

    coverages?.each { coverage ->
        def coverageEligibilityResponse = coverageEligibilityResponses.find { it.insuranceFirstRep.coverage.reference == coverage.id?.replaceFirst("Coverage/", "") }
        coverage.beneficiary = new Reference(patientReference)
        def insurancePlan = coverage.insurancePlan?.resource
        insurancePlan?.contactFirstRep?.address?.line?.each { it?.value = it?.value?.trim() }
        def planType = insurancePlan?.type?.stream()?.flatMap { it.coding.stream() }?.find { it.system == CoverageHelper.INSURANCE_PLAN_TYPE }
        planType?.code = CoverageHelper.getValidPlanType(planType?.code)

        def coveragePlanType = coverage.type?.coding?.find { it.system == CoverageHelper.COVERAGE_PLAN_TYPE }
        coveragePlanType?.code = CoverageHelper.getValidPlanType(coveragePlanType?.code)

        def insurancePlanValue = insurancePlan?.identifier?.find { it.system == CoverageHelper.insurancePlanSystem }?.value
        def insurancePlanReference
        if (insurancePlan != null && !isNullOrEmpty(insurancePlanValue) && insurancePlanValue != ACTIVE_NULL_LITERAL
                && !isNullOrEmpty(insurancePlan.name) && insurancePlan.name != ACTIVE_NULL_LITERAL) {
            def insurancePlanId = CoverageHelper.getInsurancePlan(insurancePlan, clientDecor, outcome)
            if (insurancePlanId != null) {
                insurancePlanReference = "InsurancePlan/$insurancePlanId"
                def domainCoverage = CoverageHelper.isCoverageMatched(coverage, domainCoverages)

                coverage.insurancePlan = new Reference(insurancePlanReference)
                if (domainCoverage != null) {
                    CoverageHelper.map(coverage, domainCoverage)
                    if (coverage.primary?.booleanValue()) {
                        inputPrimaryCoverageToUpdate = domainCoverage
                        inputPrimaryCoverageToUpdate_EligibilityResponse = coverageEligibilityResponse
                    } else if (domainCoverage.primary.booleanValue()) {
                        domainPrimaryCoverageToUpdate = domainCoverage
                        domainPrimaryCoverageToUpdate_EligibilityResponse = coverageEligibilityResponse
                    } else {
                        update(domainCoverage, coverageEligibilityResponse, patientReference)
                    }
                } else {
                    if (coverage.primary?.booleanValue() && !noExistingCoverageFound) {
                        inputPrimaryCoverageToCreate = coverage
                        inputPrimaryCoverageToCreate_EligibilityResponse = coverageEligibilityResponse
                    } else {
                        create(coverage, coverageEligibilityResponse, patientReference)
                    }
                }
            }
        }
    }

    if (inputPrimaryCoverageToCreate != null || inputPrimaryCoverageToUpdate != null) {
        if (domainPrimaryCoverageToUpdate != null) {
            //since input coverage has primary so previous coverage in domain should be marked primary = false
            domainPrimaryCoverageToUpdate.primary = new BooleanType(false)
            update(domainPrimaryCoverageToUpdate, null, patientId)
        }
        if (inputPrimaryCoverageToCreate != null) {
            create(inputPrimaryCoverageToCreate, inputPrimaryCoverageToCreate_EligibilityResponse, patientReference)
        } else {
            update(inputPrimaryCoverageToUpdate, inputPrimaryCoverageToUpdate_EligibilityResponse, patientReference)
        }
    } else {
        if (domainPrimaryCoverageToUpdate != null) {
            update(domainPrimaryCoverageToUpdate, domainPrimaryCoverageToUpdate_EligibilityResponse, patientReference)
        }
    }
}

def create(coverage, coverageEligibilityResponse, patientReference) {
    def opOutcome = clientDecor.createSafely(coverage)
    if (opOutcome != null) {
        createOrUpdateCoverageEligibilityResponse(coverageEligibilityResponse, patientReference, opOutcome.id?.idPart, false)
    }
}

def update(coverage, coverageEligibilityResponse, patientReference) {
    def opOutcome = clientDecor.updateSafely(coverage)
    if (opOutcome != null) {
        createOrUpdateCoverageEligibilityResponse(coverageEligibilityResponse, patientReference, opOutcome.id?.idPart, true)
    }
}

def createOrUpdateCoverageEligibilityResponse(coverageEligibilityResponse, patientReference, coverageId, isUpdate) {

    if (coverageEligibilityResponse != null) {
        if (isNullOrEmpty(coverageEligibilityResponse.asserter?.identifier?.value)) {
            outcome.addWarning(ResponseCode.MISSING_INSURANCE_AUTHORIZED_BY.value, ResponseCode.MISSING_INSURANCE_AUTHORIZED_BY.toString())
        } else {
            coverageEligibilityResponse.insuranceFirstRep.coverage = new Reference("Coverage/" + coverageId)
            coverageEligibilityResponse.patient = new Reference(patientReference)

            def coverageEligibilityResponses = null
            if (isUpdate) {
                coverageEligibilityResponses = clientDecor.search("CoverageEligibilityResponse", "coverage", coverageId)?.entry*.getResource()
            }

            def domainCER = coverageEligibilityResponses?.find { it.asserter?.identifier?.value == coverageEligibilityResponse.asserter?.identifier?.value }

            if (domainCER == null) {
                clientDecor.createSafely(coverageEligibilityResponse)
            } else {
                CoverageHelper.mapCER(coverageEligibilityResponse, domainCER)
                clientDecor.updateSafely(domainCER)
            }
        }
    }
}
