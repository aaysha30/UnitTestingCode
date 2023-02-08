package com.varian.mappercore.client.outboundAriaEvent.serviceclient

import com.varian.mappercore.client.outboundAriaEvent.dto.SubscribeEventPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import java.util.concurrent.CompletableFuture

interface AriaEventRetrofit {

    @GET("api/EventSubscription/Events")
    fun getAvailableAriaEventsForSubscription(): CompletableFuture<Response<Array<String>?>>

    @PUT("api/EventSubscription")
    fun subscribeAriaEvent(@Body subscribeEventPayload: SubscribeEventPayload): CompletableFuture<Response<String?>>
}