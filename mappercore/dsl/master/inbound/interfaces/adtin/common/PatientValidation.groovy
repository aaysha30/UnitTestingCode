package master.inbound.interfaces.adtin.common

def class PatientValidation {

    def static isPatientIdentifierExist(def patientIdentifier) {
        if (isNullOrEmpty(patientIdentifier)) {
            throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException("PatientIdentifier is empty or not configured correctly in patient matching table")
        }
        return true
    }

    def static isMergePatientIdentifierExist(def mergePatientIdentifier) {
        if (isNullOrEmpty(mergePatientIdentifier)) {
            throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException("MergePatientIdentifier is empty or not configured correctly in patient matching table")
        }
        return true
    }

    def static isPatientExist(def domainPatient) {
        if (isNullOrEmpty(domainPatient)) {
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Patient is not present")
        }
        return true
    }

    def static isPatientExist(def domainPatient, def identifier, def outcome) {
        if (isNullOrEmpty(domainPatient)) {
            def oo = outcome.getErrorOperationOutcome(String.format(ResponseCode.PATIENT_NOT_FOUND.value, identifier), ResponseCode.PATIENT_NOT_FOUND.toString())
            throw new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException(ResponseCode.PATIENT_NOT_FOUND.toString(), oo)
        }
        return true
    }
}
