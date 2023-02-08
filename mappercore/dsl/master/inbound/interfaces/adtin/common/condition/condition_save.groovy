@HandlerFor(source = "aria", subject = "ConditionDiagnosis") dsl

def conditionsFromBundle = getAllResources(bundle, "Condition")
if (!isNullOrEmpty(conditionsFromBundle)) {
    def parameters = bundleUtility.getParameters(bundle)
    def diagnosisUpdateMode = parametersUtility.getDiagnosisUpdateMode(parameters)
    if (diagnosisUpdateMode == "SnapshotOwn") {
        deleteDiagnosis(patientId, user, clientDecor)
    } else if (diagnosisUpdateMode == "SnapshotAll") {
        deleteDiagnosis(patientId, null, clientDecor)
    }
    def conditionsFromDomain = getAllResources(clientDecor.search(
            "Condition",
            "patient",
            patientId,
            "category",
            "encounter-diagnosis",
            "IsExternal",
            getTokenClientParam("IsExternal", "", "true")), "Condition").findAll { it.verificationStatus.codingFirstRep.code != "entered-in-error" }

    conditionsFromBundle.each { conditionFromBundle ->
        conditionFromBundle.setSubject(new Reference(patientId))
        def conditionFoundInDomain = getMatchingDiagnosis(conditionsFromDomain, conditionFromBundle)

        if (!isNullOrEmpty(conditionFoundInDomain)) {
            ConditionHelper.map(conditionFoundInDomain, conditionFromBundle)
            def opOutcome = clientDecor.updateSafely(conditionFoundInDomain)
        } else {
            if (isNullOrEmpty(conditionFromBundle.onset)) {
                conditionFromBundle.onset = new DateTimeType(parametersUtility.getCurrentDateTime(parameters))
            }
            def opOutcome = clientDecor.createSafely(conditionFromBundle)
        }
    }
}

def getMatchingDiagnosis(conditionList, conditionToFind) {
    return conditionList?.find { condition ->
        ((condition.getCode().getCoding().stream().anyMatch {
            (it.getCode() == conditionToFind.code.getCodingFirstRep().getCode()
                    && it.getSystem() == conditionToFind.code.getCodingFirstRep().getSystem())
        } || conditionToFind.getCode().getCoding().stream().anyMatch {
            (it.getCode() == condition.code.getCodingFirstRep().getCode()
                    && it.getSystem() == condition.code.getCodingFirstRep().getSystem())
        })
                && condition.isExternal.booleanValue() == conditionToFind.isExternal.booleanValue()
                && (conditionToFind.hasOnsetDateTimeType() && condition.onsetDateTimeType.value == conditionToFind.onsetDateTimeType.value)
        )
    }
}

def deleteDiagnosis(patientId, user, clientDecor) {
    def inParams = new Parameters()
    def condition = new Condition()
    inParams.addParameter().setName("patientKey").value = new StringType(patientId)
    if (user != null) {
        def userName = user.identifier.find { it.system == "http://varian.com/fhir/identifier/Practitioner/UserName" }?.value
        inParams.addParameter().setName("username").value = new StringType(userName)
    }
    clientDecor.operation(condition, "\$deleteExternalDiagnosis", "Condition", inParams)
}

outcome

