package com.varian.mappercore.client.interfaceapi.serviceclient

import com.varian.mappercore.client.interfaceapi.dto.FilteredMessage
import com.varian.mappercore.client.interfaceapi.dto.Patient
import retrofit2.Response
import retrofit2.http.*
import java.util.concurrent.CompletableFuture

interface ParkServiceRetrofit {

    @GET("parkpatient/{id}")
    fun getParkedPatient(@Path("id") patientSer: Long): CompletableFuture<Response<Patient?>>

    @POST("parkpatient")
    fun createParkedPatient(@Body patient: Patient): CompletableFuture<Response<Long?>>

    @PUT("parkpatient/{id}")
    fun updateParkPatient(@Path("id") idser: Long, @Body patient: Patient): CompletableFuture<Response<Long?>>

    @GET("parkpatient")
    fun searchParkedPatientByCriteria(@QueryMap map: Map<String, String>): CompletableFuture<Response<List<Patient>?>>

    @POST("parkpatient/merge/{targetPatientId}")
    fun mergeParkedPatient(
        @Path("targetPatientId") targetPatientId: Long,
        @Body mergeWithPatientId: Long
    ): CompletableFuture<Response<String?>>

    //API For Message
    @GET("parkmessage/{id}")
    fun getParkedMessage(@Path("id") idser: Long): CompletableFuture<Response<FilteredMessage?>>

    @POST("parkmessage")
    fun createParkedMessage(@Body message: FilteredMessage): CompletableFuture<Response<Long>>

    @GET("parkmessage")
    fun searchParkedMessageByCriteria(@QueryMap map: Map<String, String>): CompletableFuture<Response<List<FilteredMessage>?>>

    @POST("parkmessage/status")
    fun updateMessageStatus(@Body message: FilteredMessage): CompletableFuture<Response<Boolean?>>
}