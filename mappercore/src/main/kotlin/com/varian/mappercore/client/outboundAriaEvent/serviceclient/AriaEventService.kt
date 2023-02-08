package com.varian.mappercore.client.outboundAriaEvent.serviceclient

import com.varian.mappercore.client.outboundAriaEvent.dto.SubscribeEventPayload

interface AriaEventService {
    fun subscribeAriaEvent(events: SubscribeEventPayload): String?
    fun getAvailableAriaEventsForSubscription(): Array<String>?
}