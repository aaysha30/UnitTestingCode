package com.varian.mappercore.client.outboundAriaEvent.serviceclient

import com.varian.mappercore.client.RetrofitProvider
import com.varian.mappercore.client.ServiceClientBase
import com.varian.mappercore.client.outboundAriaEvent.dto.SubscribeEventPayload

class AriaEventServiceClient(retrofitProvider: RetrofitProvider) : AriaEventService,
    ServiceClientBase(retrofitProvider) {

    var retrofit: AriaEventRetrofit = getRetrofit(AriaEventRetrofit::class.java)

    override fun subscribeAriaEvent(events: SubscribeEventPayload): String? {
        return call(retrofit.subscribeAriaEvent(events))
    }

    override fun getAvailableAriaEventsForSubscription(): Array<String>? {
        return call(retrofit.getAvailableAriaEventsForSubscription())
    }

}