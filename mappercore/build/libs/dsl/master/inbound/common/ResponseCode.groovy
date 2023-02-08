package master.inbound.common

enum ResponseCode {
    PATIENT_INVALID_HOSPITAL("Hospital: %s, is not valid"),
    PATIENT_SINGLE_HOSPITAL("Single Hospital: %s, is used"),
    PATIENT_DEFAULT_HOSPITAL("Default Hospital: %s, is used"),
    PATIENT_INVALID_DEFAULT_HOSPITAL("Default Hospital: %s, is not valid"),
    PATIENT_HOSPITAL_NOT_CONFIGURED("Default Hospital is not configured"),
    PATIENT_INVALID_HOSPITAL_DEPARTMENT("system could not attach valid hospital and department"),
    PATIENT_DEPARTMENT_INVALID("Department: %s, is not valid"),
    PATIENT_SINGLE_DEPARTMENT("Single Department: %s, of Hospital: %s, is used"),
    PATIENT_DEFAULT_DEPARTMENT("Default Department: %s is used"),
    PATIENT_MULTIPLE_ACCOUNTS_ASSOCIATED("multiple accounts associated with %s for patient %s"),
    PATIENT_NOT_FOUND("Patient (%s) is not present"),
    PATIENT_AUTO_CREATE("Patient (%s) is not present. System will create patient as auto create is configured for this event"),
    PRACTITIONER_NOT_FOUND("A valid Doctor with Id=%s doesn't exist in database"),
    ONCOLOGIST_NOT_PROCESSED("Processing of Primary Attending Doctor failed and hence subsequent doctors were not processed."),
    PATIENT_ARRIVING_LOCATION_NOT_SPECIFIED("Location is not specified"),
    PATIENT_ARRIVING_INVALID_LOCATION("Location is not valid"),
    PATIENT_ARRIVING_NO_SCHEDULED_ACTIVITY("There is no scheduled activity for today for the given Patient (%s) and Hospital (%s)"),
    PATIENT_ARRIVING_FAILED("Scheduled activity check in failed"),
    PATIENT_ARRIVING_PARTIAL_SUCCESS("Scheduled activity check in completed partially with %s/%s activity(s)."),
    PATIENT_SWAP_LOCATION("Patient identifier and visit details must be present for both patient"),
    CURRENT_DATE_NOT_SET("Current Date is not set in Parameters resource"),
    CURRENT_DATE_FOR_ADMIT_DATE("Current Date is considered for Admit Date"),
    ROOM_NUMBER_NOT_CONFIGURED("Default Room number is not configured"),
    IGNORE_PATIENTCLASS_ROOM_NUMBER_NULL("Ignoring Patient Class because the Room Number is null or empty. Used Patient Class as OutPatient"),
    DEFAULT_ROOM_NUMBER_CONSIDERED("Default Room number: %s is used"),
    CURRENT_DATE_FOR_DISCHARGE_DATE("Current Date is considered for Discharge Date"),
    DISCHARGE_DATE_NULL_FOR_OUTPATIENT("Discharge Date should not be null for Out Patient"),
    ADMIT_DATE_NULL_FOR_INPATIENT("Admit Date should not be null for In Patient"),
    INVALID_ADMIT_DISCHARGE_DATE("Discharge date can not be before admit date"),
    IGNORE_PATIENT_CLASS("Ignoring patient class because admit/discharge date is invalid"),
    ACCOUNT_DOES_NOT_EXISTS("Account with given Account Number does not exists"),
    ACCOUNT_IS_NULL("Patient Identification. Billing Account is null"),
    ACCOUNT_PROVIDER_NOT_ONCOLOGIST("Billing Account Oncologist Id could not be processed. The doctor with ID = %s is not Oncologist"),
    ACCOUNT_PROVIDER_NOT_ACTIVE("Billing Account Oncologist Id could not be processed. The doctor with ID = %s is inactive"),
    ACCOUNT_INVALID_PROVIDER("Billing Account Oncologist Id could not be processed. Invalid Doctor with ID='%s'"),
    INVALID_ACCOUNT_STATUS("Inactive account can not be created"),
    IGNORE_PATIENT_IDENTIFIER_UPDATE("Not updating '%s' because allow update is false for this id"),
    INVALID_PATIENT_CLASS_ADT_A01("Patient class is invalid. Expected value is 'I'"),
    INVALID_POINT_OF_CONTACT("Skipping patient contact as it's last name is empty"),
    INVALID_EMPLOYER_CONTACT("Skipping employer contact as it's name is empty"),
    INVALID_TRANSPORT_CONTACT("Skipping transport contact as it's name is empty"),
    COVERAGE_INVALID_PLAN("Insurance field Plan Number and Company Name should not be null"),
    MISSING_INSURANCE_AUTHORIZED_BY("Not processing Authorization because AuthorizedBy field is not specified.")

    ResponseCode(String message) {
        this.value = message
    }
    private final String value

    String getValue() {
        value
    }
}