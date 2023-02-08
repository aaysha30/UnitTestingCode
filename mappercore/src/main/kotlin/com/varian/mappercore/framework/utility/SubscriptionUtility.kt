package com.varian.mappercore.framework.utility

import com.varian.mappercore.client.outboundAriaEvent.dto.SubscribeEventPayload
import com.varian.mappercore.client.outboundAriaEvent.serviceclient.AriaEventService
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.helper.sqlite.Identifier
import com.varian.mappercore.helper.sqlite.ProcessingConfig
import com.varian.mappercore.helper.sqlite.SqliteUtility
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.sql.Connection

open class SubscriptionUtility(
    private val masterConnection: Connection,
    private val localConnection: Connection,
    private val ariaEventService: AriaEventService
) {
    protected var log: Logger = LogManager.getLogger(SubscriptionUtility::class.java)

    /*
    * subscribe to events configured in Master Database EventMapping table
    * input is name of subscription
    * will return subscription id
    * */
    fun doSubscribe(subscriptionName: String): String? {

        log.info("Subscribing to Aria Events")
        val notificationUri = getProcessingConfig("NotificationUri").getOrNull(0)?.Value
            ?: throw Exception("Exception occurred while Subscription: NotificationUri should not be null")
        val interfaceType = getProcessingConfig("InterfaceType").getOrNull(0)?.Value
            ?: throw Exception("Exception occurred while Subscription: InterfaceType should not be null")

        //creating Payload for subscription
        var payload = SubscribeEventPayload()
        payload.Name = subscriptionName
        payload.TokenProviderName = "VAIS"
        payload.CallBackEndPointUrl = notificationUri
        //fetching all the events to subscribe from Master Table
        val listOfEvents: ArrayList<Identifier> =
            SqliteUtility.getValues(
                masterConnection,
                ParameterConstant.EVENTS_MAPPING_TABLE,
                mapOf("InValue" to interfaceType)
            )
        payload.Events = listOfEvents.map { it.OutValue.toString() }
        if (payload.Events?.isEmpty() == true) {
            throw Exception("Exception occurred while Subscription: Events List should not be empty")
        }
        var subscriptionId: String? = ariaEventService?.subscribeAriaEvent(payload)
        log.info("Successfully Subscribed to Aria events")
        return subscriptionId

    }

    private fun getProcessingConfig(inValue: String): ArrayList<ProcessingConfig> {
        val whereClause = mapOf("Key" to inValue)
        return SqliteUtility.getValues(
            localConnection,
            "ProcessingConfig",
            whereClause
        )
    }

}