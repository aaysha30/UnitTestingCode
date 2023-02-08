package master.inbound.interfaces.adtin.common.condition

def class ConditionHelper {
    static def map(domainCondition, inputCondition) {
        mapDiagnosisDate(domainCondition, inputCondition)
        mapVerificationStatus(domainCondition, inputCondition)
        mapStatus(domainCondition, inputCondition)
        mapStatusDate(domainCondition, inputCondition)
        mapClinicalDescription(domainCondition, inputCondition)
        mapRank(domainCondition, inputCondition)
        mapIsExternal(domainCondition, inputCondition)
        mapIsCancerDiagnosis(domainCondition, inputCondition)
        mapCategory(domainCondition, inputCondition)
    }

    static def mapDiagnosisDate(domainCondition, inputCondition) {
        domainCondition.onset = getValue(inputCondition.onset, domainCondition.onset)
    }

    static def mapStatus(domainCondition, inputCondition) {
        def status = getValue(inputCondition.clinicalStatusInARIA?.codingFirstRep?.code, domainCondition.clinicalStatusInARIA?.codingFirstRep?.code)
        if (status == null) {
            domainCondition.clinicalStatusInARIA = null
            domainCondition.clinicalStatus = null
        } else if (status == inputCondition.clinicalStatusInARIA?.codingFirstRep?.code) {
            domainCondition.clinicalStatusInARIA = inputCondition.clinicalStatusInARIA
        }
    }

    static def mapStatusDate(domainCondition, inputCondition) {
        domainCondition.clinicalStatusDate = getValue(inputCondition.clinicalStatusDate, domainCondition.clinicalStatusDate)
    }

    static def mapClinicalDescription(domainCondition, inputCondition) {
        domainCondition.code.text = getValue(inputCondition.code.text, domainCondition.code.text)
    }

    static def mapVerificationStatus(domainCondition, inputCondition) {
        def status = getValue(inputCondition.verificationStatus?.codingFirstRep?.code, domainCondition.verificationStatus?.codingFirstRep?.code)
        if (status == null) {
            domainCondition.verificationStatus = null
        } else if (status == inputCondition.verificationStatus?.codingFirstRep?.code) {
            domainCondition.verificationStatus = inputCondition.verificationStatus
        }
    }

    static def mapCategory(domainCondition, inputCondition) {
        if (inputCondition.hasCategory()) {
            domainCondition.category = inputCondition.category
        }
    }

    static def mapRank(domainCondition, inputCondition) {
        def value = getValue(inputCondition.rank?.codingFirstRep?.code, domainCondition.rank?.codingFirstRep?.code)

        if (value == null) {
            domainCondition.rank = null
        } else if (value == inputCondition.rank?.codingFirstRep?.code) {
            domainCondition.rank = inputCondition.rank
        }
    }

    static def mapIsExternal(domainCondition, inputCondition) {
        if (!isNullOrEmpty(inputCondition.isExternal)) {
            domainCondition.isExternal = inputCondition.isExternal
        }
    }

    static def mapIsCancerDiagnosis(domainCondition, inputCondition) {
        if (!isNullOrEmpty(inputCondition.isCancerDiagnosis)) {
            domainCondition.isCancerDiagnosis = inputCondition.isCancerDiagnosis
        }
    }
}