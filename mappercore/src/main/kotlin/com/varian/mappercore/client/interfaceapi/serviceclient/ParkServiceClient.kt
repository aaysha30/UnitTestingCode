package com.varian.mappercore.client.interfaceapi.serviceclient

import com.varian.mappercore.client.RetrofitProvider
import com.varian.mappercore.client.ServiceClientBase
import com.varian.mappercore.client.interfaceapi.dto.FilteredMessage
import com.varian.mappercore.client.interfaceapi.dto.Patient

class ParkServiceClient(retrofitProvider: RetrofitProvider) : ParkService, ServiceClientBase(retrofitProvider) {

    var retrofit: ParkServiceRetrofit = getRetrofit(ParkServiceRetrofit::class.java)

    override fun getParkedPatient(patientSer: Long): Patient? {
        return call(retrofit.getParkedPatient(patientSer))
    }

    override fun createParkedPatient(patient: Patient): Long? {
        return call(retrofit.createParkedPatient(patient))
    }

    override fun updateParkPatient(idser: Long, patient: Patient): Long? {
        return call(retrofit.updateParkPatient(idser, patient))
    }

    override fun searchParkedPatientByCriteria(map: Map<String, String>): List<Patient>? {
        return call(retrofit.searchParkedPatientByCriteria(map))
    }

    override fun mergeParkedPatient(targetPatientIdSer: Long, mergeWithPatientId: Long): String? {
        return call(retrofit.mergeParkedPatient(targetPatientIdSer, mergeWithPatientId))
    }

    override fun getParkedMessage(idser: Long): FilteredMessage? {
        return call(retrofit.getParkedMessage(idser))
    }

    override fun createParkedMessage(message: FilteredMessage): Long? {
        return call(retrofit.createParkedMessage(message))
    }

    override fun searchParkedMessageByCriteria(map: Map<String, String>): List<FilteredMessage>? {
        return call(retrofit.searchParkedMessageByCriteria(map))
    }

    override fun updateMessageStatus(message: FilteredMessage): Boolean? {
        return call(retrofit.updateMessageStatus(message))
    }
}