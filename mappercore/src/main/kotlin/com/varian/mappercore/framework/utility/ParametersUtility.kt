package com.varian.mappercore.framework.utility

import org.hl7.fhir.r4.model.Parameters

class ParametersUtility : Utility() {

    companion object {
        const val SOURCE = "Source"
        const val SUBJECT = "Subject"
        const val EVENT = "Event"
        const val DEFAULT_HOSPITAL_NAME = "DefaultHospitalName"
        const val DEFAULT_DEPARTMENT_ID = "DefaultDepartmentId"
        const val DEFAULT_ROOM_NUMBER = "DefaultRoomNumber"
        const val CURRENT_DATETIME = "CurrentDatetime"
        const val SuppressUpdateOnPrimaryCheck = "SuppressUpdateOnPrimaryCheck"
        const val AUTO_CREATE_EVENTS = "AutoCreateEvents"
        const val ADV_PATIENT_CLASS_PROCESS =
            "AdvPatientClassProcess" // AdvPatinetClassProcess spell mistake at AriaConnect sqlite. While mapping at Xlate, it is corrected
        const val PATIENT_SEARCH_KEYS = "PatientSearchKeys"
        const val PATIENT_DISALLOW_UPDATE_KEYS = "PatientDisallowUpdateKeys"
        const val MERGE_PATIENT_SEARCH_KEYS = "MergePatientSearchKeys"
        const val AUTO_CREATE_REFERRING_PHYSICIAN = "AutoCreateReferringPhysician"
        const val ALLOW_UPDATE_ON_COVERAGE_PRIMARY_CHECK = "AllowUpdateOnCoveragePrimaryCheck"
        const val DENY_SNAPSHOT_ON_DIRECTIVE_COMMENT = "DenySnapshotOnDirectiveComment"
        const val INSURANCE_UPDATE_MODE = "InsuranceUpdateMode"
        const val ALLERGY_UPDATE_MODE = "AllergyUpdateMode"
        const val POINT_OF_CONTACT_UPDATE_MODE = "PointOfContactUpdateMode"
        const val DIAGNOSIS_UPDATE_MODE = "DiagnosisUpdateMode"
        const val SCHEDULEDACTIVITYSER = "scheduledactivityser"
        const val SCHEDULED_ACTIVITY_REV_COUNT = "scheduled_activity_rev_count"
        const val EVENT_REASON = "event_reason"
        const val PATIENT_SER = "patient_ser"
        const val DEPARTMENT_SER = "department_ser"
        const val ID = "ID"
        const val HISTORY_USERNAME = "history_username"
        const val PATIENT_ID = "patient_id"
    }

    fun getEventID(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == ID }?.value)
    }

    fun getSource(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == SOURCE }?.value)
    }

    fun getPatientSer(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == PATIENT_SER }?.value)
    }

    fun getPatientId(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == PATIENT_ID }?.value)
    }

    fun getHistoryUserName(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == HISTORY_USERNAME }?.value)
    }

    fun getDepartmentSer(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == DEPARTMENT_SER }?.value)
    }

    fun getSubject(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == SUBJECT }?.value)
    }

    fun getScheduledActivitySer(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == SCHEDULEDACTIVITYSER }?.value)
    }

    fun getScheduleActivityRevCount(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == SCHEDULED_ACTIVITY_REV_COUNT }?.value)
    }

    fun getEvent(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == EVENT }?.value)
    }

    fun getDefaultHospitalName(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == DEFAULT_HOSPITAL_NAME }?.value)
    }

    fun getEventReasonForAppointment(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == EVENT_REASON }?.value)
    }

    fun getDefaultDepartmentId(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == DEFAULT_DEPARTMENT_ID }?.value)
    }

    fun getDefaultRoomNumber(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == DEFAULT_ROOM_NUMBER }?.value)
    }

    fun getCurrentDateTime(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == CURRENT_DATETIME }?.value)
    }

    fun allowPrimaryUpdateForPractitioner(parameters: Parameters): Boolean {
        return getValue(parameters.parameter.find { it.name == SuppressUpdateOnPrimaryCheck }?.value) == "0"
    }

    fun getAutoCreateEvents(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == AUTO_CREATE_EVENTS }?.value)
    }

    fun getAdvPatientClassProcess(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == ADV_PATIENT_CLASS_PROCESS }?.value)
    }

    fun getPatientSearchKeys(parameters: Parameters): List<String> {
        return parameters.parameter.filter { it.name == PATIENT_SEARCH_KEYS }.map { it.value.toString() }.toList()
    }

    fun getPatientDisallowUpdateKeys(parameters: Parameters): List<String> {
        return parameters.parameter.filter { it.name == PATIENT_DISALLOW_UPDATE_KEYS }
            .map { it.value.toString() }.toList()
    }

    fun getMergePatientSearchKeys(parameters: Parameters): List<String> {
        return parameters.parameter.filter { it.name == MERGE_PATIENT_SEARCH_KEYS }.map { it.value.toString() }.toList()
    }

    fun getAutoCreateReferringPhysician(parameters: Parameters): Boolean {
        return getValue(parameters.parameter.find { it.name == AUTO_CREATE_REFERRING_PHYSICIAN }?.value) == "1"
    }

    fun allowUpdateOnCoveragePrimaryCheck(parameters: Parameters): Boolean {
        return getValue(parameters.parameter.find { it.name == ALLOW_UPDATE_ON_COVERAGE_PRIMARY_CHECK }?.value) != "0"
    }

    fun isSnapshotAllowedOnDirectiveComment(parameters: Parameters): Boolean {
        return getValue(parameters.parameter.find { it.name == DENY_SNAPSHOT_ON_DIRECTIVE_COMMENT }?.value) != "1"
    }

    fun getInsuranceUpdateMode(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == INSURANCE_UPDATE_MODE }?.value)
    }

    fun getAllergyUpdateMode(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == ALLERGY_UPDATE_MODE }?.value)
    }

    fun getContactUpdateMode(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == POINT_OF_CONTACT_UPDATE_MODE }?.value)
    }

    fun getDiagnosisUpdateMode(parameters: Parameters): String? {
        return getValue(parameters.parameter.find { it.name == DIAGNOSIS_UPDATE_MODE }?.value)
    }

    fun isEventExists(parameters: Parameters): Boolean {
        val autoCreateEvents = getAutoCreateEvents(parameters)
        val event = getEvent(parameters)
        if (autoCreateEvents == null || event == null) {
            return false
        }
        return autoCreateEvents.contains(event)
    }
}