package com.varian.mappercore.client.interfaceapi.serviceclient

import com.varian.mappercore.client.interfaceapi.dto.FilteredMessage
import com.varian.mappercore.client.interfaceapi.dto.Patient

interface ParkService {

    fun getParkedPatient(patientSer: Long): Patient?

    fun createParkedPatient(patient: Patient): Long?

    fun updateParkPatient(idser: Long, patient: Patient): Long?

    fun searchParkedPatientByCriteria(map: Map<String, String>): List<Patient>?

    fun mergeParkedPatient(targetPatientIdSer: Long, mergeWithPatientId: Long): String?

    //API For Message
    fun getParkedMessage(idser: Long): FilteredMessage?

    fun createParkedMessage(message: FilteredMessage): Long?

    fun searchParkedMessageByCriteria(map: Map<String, String>): List<FilteredMessage>?

    fun updateMessageStatus(message: FilteredMessage): Boolean?
}