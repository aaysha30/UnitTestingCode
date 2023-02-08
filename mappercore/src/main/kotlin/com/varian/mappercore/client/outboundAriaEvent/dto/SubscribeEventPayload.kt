package com.varian.mappercore.client.outboundAriaEvent.dto

class SubscribeEventPayload {
    var CallBackEndPointUrl: String? = null
    var Events: List<String>? = null
    var Id: String? = null
    var MaxRetryLimit: Int = 20
    var Name: String? = null
    var TokenProviderName: String? = null
}