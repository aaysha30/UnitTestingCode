@HandlerFor(source = "aria", subject = "AllergyIntoleranceSave") dsl

def inputAllergies = getAllResources(bundle, "AllergyIntolerance")
if (!isNullOrEmpty(inputAllergies)) {
    def NoKnownAllergyCode = "716186003"

    def parameters = bundleUtility.getParameters(bundle)
    def allergyUpdateMode = parametersUtility.getAllergyUpdateMode(parameters)
    def domainAllergies = clientDecor.search("AllergyIntolerance", "patient", patientId, "status",
            getTokenClientParam("verification-status:not", "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification", "entered-in-error")).entry*.getResource()

    if (!isNullOrEmpty(domainAllergies)) {
        if (allergyUpdateMode == "SnapshotAll") {
            domainAllergies.each {
                deleteAllergies(it, NoKnownAllergyCode, inputAllergies)
            }
            isAllergySnapshot = true
        } else if (allergyUpdateMode == "SnapshotOwn") {
            domainAllergies.each {
                def lastModifiedUser = it.lastModificationUser?.reference?.replace('Practitioner/', '')
                if (lastModifiedUser == user.idElement.idPart) {
                    deleteAllergies(it, NoKnownAllergyCode, inputAllergies)
                    isAllergySnapshot = true
                }
            }
        }
    }

//create or update
    inputAllergies?.each { resource ->
        resource.patient.reference = patientId
        resource.allergyCategoryInAria.codingFirstRep.code = allergyCategoryValueSet.expansion?.containsFirstRep?.contains?.find { vs ->
            vs.display == resource.allergyCategoryInAria.codingFirstRep.code
        }?.code
        def domainAllergy = domainAllergies.find { ele ->
            (ele.getCode().getText() == resource.code.text
                    && ele.allergyCategoryInAria != null && resource.allergyCategoryInAria != null
                    && ele.allergyCategoryInAria.codingFirstRep.code == resource.allergyCategoryInAria.codingFirstRep.code
                    && ele.verificationStatus.codingFirstRep.code != "entered-in-error")
        }
        if (domainAllergy != null) {
            resource.id = domainAllergy.idElement.idPart
            resource.meta = domainAllergy.meta
            resource.onset = getValue(resource.onset, domainAllergy.onset)
            resource.reaction = isActiveNullList(resource.reaction, resource.reaction?.find { true }?.manifestationFirstRep?.codingFirstRep?.code, domainAllergy.reaction)
            resource.noteFirstRep.text = getValue(resource.noteFirstRep.text, domainAllergy.noteFirstRep.text)
            def opOutcome = clientDecor.updateSafely(resource)
            if (opOutcome != null) {
                outcome.addInformation("allergy updated: ${opOutcome.id.idPart}")
            }
        } else {
            if(isNullOrEmpty(resource.onset)) {
                resource.onset = new DateTimeType(new Date())
            }
            def opOutcome = clientDecor.createSafely(resource)
            if (opOutcome != null) {
                outcome.addInformation("allergy created: ${opOutcome.id.idPart}")
            }
        }
    }
}

private void deleteAllergies(it, String NoKnownAllergyCode, inputAllergies) {
    def resource = it
    if (resource.verificationStatus.codingFirstRep.code != "entered-in-error" && resource.code.text != NoKnownAllergyCode) {
        def foundAllergy = inputAllergies?.find {
            (it.code.text == resource.code.text
                    && it.allergyCategoryInAria != null && resource.allergyCategoryInAria != null
                    && it.allergyCategoryInAria.codingFirstRep.code == resource.allergyCategoryInAria.codingFirstRep.code)
        }
        if (foundAllergy == null) {
            resource.verificationStatus.codingFirstRep.code = "entered-in-error"
            def opOutcome = clientDecor.updateSafely(resource)
            if (opOutcome != null) {
                outcome.addInformation("allergy deleted: ${opOutcome.id.idPart}")
            }
        }
    }
}
