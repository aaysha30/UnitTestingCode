@HandlerFor(source = "aria", subject = "PatientDirectiveSave") dsl

def consents = bundleUtility.getConsents(bundle)
def patientDirectives = consents.findAll { it.scope.codingFirstRep.code == "adr" }

if (isNullOrEmpty(patientDirectives))
    return

def parameters = bundleUtility.getParameters(bundle)
def domainPatientDirectives = bundleUtility.getConsents(clientDecor.search("Consent", "patient", patientId, "status", "active"))
def dirgroup = patientDirectives.groupBy { it.categoryFirstRep.codingFirstRep.display }
def snapshotDirectiveComment = parametersUtility.isSnapshotAllowedOnDirectiveComment(parameters)

dirgroup.each { key, value ->
    def grpComments = []
    value.each { if (!isNullOrEmpty(it?.policyRule?.text)) { grpComments.add(it?.policyRule?.text) } }
    def comment = grpComments.join("\r\n")
    def domainDirective = findPatientDirective(key, domainPatientDirectives)
    if (!isNullOrEmpty(domainDirective)) {
        if (!isNullOrEmpty(comment)) {
            def comments
            if (isNullOrEmpty(domainDirective.policyRule?.text)) {
                domainDirective.policyRule = new CodeableConcept()
                comments = comment
            } else {
                if (snapshotDirectiveComment) {
                    comments = comment
                } else {
                    comments = String.format("%s\r\n%s", domainDirective.policyRule.text, comment)
                }
            }
            domainDirective.policyRule.text = comments
            clientDecor.updateSafely(domainDirective)
        }
    } else {
        value[0].patient.reference = patientId
        value[0].policyRule?.text = comment
        clientDecor.createSafely(value[0])
    }
}

def static findPatientDirective(patientDirectiveText, domainPatientDirectives) {
    domainPatientDirectives.find { it.categoryFirstRep.codingFirstRep.display.toLowerCase() == patientDirectiveText.toLowerCase() }
}