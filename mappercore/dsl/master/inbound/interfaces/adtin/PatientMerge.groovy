@HandlerFor(source = "Hl7", subject = "PatientMerge") dsl

def messageHeader = bundleUtility.getMessageHeader(bundle)
def parameters = bundleUtility.getParameters(bundle)
def careTeams = bundleUtility.getCareTeams(bundle)
def account = bundleUtility.getAccount(bundle)
def consents = bundleUtility.getConsents(bundle)
def patients = bundleUtility.getPatients(bundle)
def adtPatients = patients.findAll { it.linkFirstRep.type?.toCode() == "replaces" }
def mrgPatients = patients.findAll { it.linkFirstRep.type?.toCode() != "replaces" }

def patientSearchKeys = parametersUtility.getPatientSearchKeys(parameters)
PatientValidation.isPatientIdentifierExist(patientSearchKeys)

def mergerPatientSearchKeys = parametersUtility.getMergePatientSearchKeys(parameters)
PatientValidation.isMergePatientIdentifierExist(mergerPatientSearchKeys)

def errors = []
def errorCount = 0
adtPatients.each {
    def errorMessageForThisMerge = []
    try {
        def adtPatient = it

        def mrgPatient = mrgPatients.find { mrg -> mrg.idElement.idPart == adtPatient.linkFirstRep.other.reference }
        adtPatient.linkFirstRep.other.reference = null
        mrgPatient?.id = null

        def careTeam = careTeams.find { it?.subject?.reference == adtPatient.idElement.idPart }
        careTeam?.subject?.reference = null
        def consentsForAdtPatient = consents.findAll { it?.patient?.reference == adtPatient.idElement.idPart }

        adtPatient?.id = null

        def patientIdentifiersToSearch = patientUtility.getIdentifiers(adtPatient, patientSearchKeys)
        if (isNullOrEmpty(patientIdentifiersToSearch)) {
            errorMessageForThisMerge.add("patient identifier '${patientSearchKeys.first()}' not found for PID patient '$adtPatient.nameFirstRep.family'")
            return
        }
        def adtBundleDomain = clientDecor.search("Patient", "identifier", getIdentifierQuery(patientIdentifiersToSearch))
        def adtPatientDomain = bundleUtility.getPatient(adtBundleDomain)

        def mrgPatientIdentifiersToSearch = patientUtility.getIdentifiers(mrgPatient, mergerPatientSearchKeys)
        if (isNullOrEmpty(mrgPatientIdentifiersToSearch)) {
            errorMessageForThisMerge.add("merge patient identifier '${mergerPatientSearchKeys.first()}' not found for MRG patient '$mrgPatient.nameFirstRep.family'")
            return
        }
        def mrgBundleDomain = clientDecor.search("Patient", "identifier", getIdentifierQuery(mrgPatientIdentifiersToSearch))
        def mrgPatientDomain = bundleUtility.getPatient(mrgBundleDomain)

        if (adtPatientDomain == null && mrgPatientDomain == null) {
            outcome.addWarning("Both patients are not present for merging")
        } else if (adtPatientDomain != null && mrgPatientDomain == null) {
            // Update Patient from PID to ARIA
            def pidPatient = getResource(bundle, "Patient", patientIdentifiersToSearch.first().value)
            pidPatient.id = adtPatientDomain.id
            savePatient(pidPatient, parameters, messageHeader, careTeam, account, consentsForAdtPatient)
        } else if (adtPatientDomain == null && mrgPatientDomain != null) {
            def pidPatient = getResource(bundle, "Patient", patientIdentifiersToSearch.first().value)
            pidPatient.id = mrgPatientDomain.id
            savePatient(pidPatient, parameters, messageHeader, careTeam, account, consentsForAdtPatient)
            def flagResourceForPidPatient = getFlag("The patient ID has been updated.", pidPatient.idElement.idPart)
            clientDecor.create(flagResourceForPidPatient)
        } else if (adtPatientDomain != null && mrgPatientDomain != null) {
            // Add merge note to both the patients and Update both patients
            def flagResourceForMrgPatient = getFlag("This is a duplicate patient and the records attached shall be manually merged into patient '${adtPatientDomain.nameFirstRep.family} (${patientIdentifiersToSearch.first().value})'.", mrgPatientDomain.idElement.idPart)
            def flagResourceForPidPatient = getFlag("The records attached to the patient '${mrgPatientDomain.nameFirstRep.family} (${mrgPatientIdentifiersToSearch.first().value})' should be manually merged in.", adtPatientDomain.idElement.idPart)
            clientDecor.create(flagResourceForPidPatient)
            clientDecor.create(flagResourceForMrgPatient)
            outcome.addSuccessAckMessage("Both Patients are present for Merge. Alert is placed on both the Patients.")
        }
    } finally {
        if (errorMessageForThisMerge.size() > 0) {
            errorCount += 1
            errors.addAll(errorMessageForThisMerge)
        }
    }
}

if (errorCount == adtPatients.size()) {
    errors.each { outcome.addError(it) }
} else {
    errors.each { outcome.addWarning(it) }
}

outcome

def getFlag(code, patientId) {
    def flag = new com.varian.fhir.resources.Flag()
    flag.categoryFirstRep.codingFirstRep.system = "http://terminology.hl7.org/CodeSystem/flag-category"
    flag.categoryFirstRep.codingFirstRep.code = "clinical"
    flag.code.text = code
    flag.status = Flag.FlagStatus.ACTIVE
    flag.subject.reference = patientId
    flag.period.start = new Date()
    return flag
}

def savePatient(patient, parameters, messageHeader, careTeam, account, consents) {
    org.hl7.fhir.r4.model.Bundle updatePatientBundle = new org.hl7.fhir.r4.model.Bundle()
    updatePatientBundle?.addEntry()?.setResource(parameters)
    updatePatientBundle?.addEntry()?.setResource(messageHeader)
    updatePatientBundle?.addEntry()?.setResource(patient)
    if (careTeam != null) {
        updatePatientBundle?.addEntry()?.setResource(careTeam)
    }
    if (!isNullOrEmpty(account)) {
        updatePatientBundle?.addEntry()?.setResource(account)
    }
    if (!isNullOrEmpty(consents)) {
        consents.each {
            updatePatientBundle?.addEntry()?.setResource(it)
        }
    }
    Map<String, Object> updatePatientMap = (Map) getBinding().getVariables().clone()
    updatePatientMap.put("bundle", updatePatientBundle)
    updatePatientMap.put("autoCreate", false)
    run("route", "PatientSaveRoute", updatePatientMap)
}

outcome
