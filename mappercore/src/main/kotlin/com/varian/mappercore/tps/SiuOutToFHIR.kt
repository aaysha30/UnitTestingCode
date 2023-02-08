package com.varian.mappercore.tps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.ClientDecor
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.utility.FilterUtility
import com.varian.mappercore.framework.utility.ParametersUtility
import com.varian.mappercore.helper.sqlite.SiuProcessingConfig
import com.varian.mappercore.helper.sqlite.SqliteUtility
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome

class SiuOutToFHIR(cloverEnv: CloverEnv, propertyTree: PropertyTree?) :
    UpocBaseOutbound(cloverEnv, propertyTree, "SIU_Out") {
    private lateinit var outcome: Outcome
    private lateinit var messageMetaData: MessageMetaData
    private var smatdbSIUFHIR: SMATDB?
    private var objectMapper: ObjectMapper

    init {
        this.readEventScriptParamsCallBack = ::getEventIdAndScriptParamMap
        smatdbSIUFHIR = SMATDB(cloverEnv, globalInit.configuration.siuOutSMATDbName)
        objectMapper = ObjectMapper()
    }

    override fun process(cloverEnv: CloverEnv, context: String?, mode: String, message: Message?): DispositionList {
        cloverLogger = CloverLogger.initCLoverLogger(cloverEnv)
        return when (mode) {
            "start" -> handleStart(cloverEnv)
            "run" -> handleRun(message)
            "shutdown" -> handleShutdown()
            "time" -> handleTime(message)
            else -> DispositionList()
        }
    }

    override fun handleShutdown(): DispositionList {
        smatdbSIUFHIR?.close()
        return super.handleShutdown()
    }

    override fun handleStart(cloverEnv: CloverEnv): DispositionList {
        try {
            smatdbSIUFHIR?.open()
            log.info("Subscribing to Aria Events")
            cloverLogger.log(2, "Subscribing to Aria Events...", MessageMetaData())
            val subscriptionName = "VarianConnect.${cloverEnv.siteName}.${globalInit.getProcessName(cloverEnv)}"
            cloverLogger.log(2, "subscriptionName is${subscriptionName}", MessageMetaData())
            this.subscriptionUtility.doSubscribe(subscriptionName)
            cloverLogger.log(2, "Interface is Subscribed to Aria events", MessageMetaData())
        } catch (exception: Exception) {
            cloverLogger.log(
                0,
                "Error occurred while Subscribing to Aria events : ${exception.message}",
                MessageMetaData()
            )
            cloverLogger.log(
                2,
                "Error occurred while Subscribing to Aria events : ${exception.stackTraceToString()}",
                MessageMetaData()
            )
            handleException(cloverEnv, exception)
        }
        return DispositionList()
    }

    override fun handleRun(message: Message?): DispositionList {
        log.trace("processing message.. appointment event to fhir bundle")
        val dispositionList = DispositionList()
        outcome = Outcome(globalInit.parser)
        clientDecor = ClientDecor(this.ariaFhirClient!!, outcome)

        val id = objectMapper.readTree(message?.content).findValue("id").toString()
        val patientId = objectMapper.readTree(message?.content).findValue("patient_id").toString()
        messageMetaData = MessageMetaData(patientId, id)
        cloverLogger.log(0, "Message processing started...", messageMetaData)

        //val suppressMessageValue = globalInit.getProcessingConfigTable().find { it.Key == "SuppressUnmapped" }?.Value
        //if Event is triggered by Interface it is suppressed
        if (isIgnoreUser(message, messageMetaData)) {
            cloverLogger.log(1, "Message trigger by interface update is suppressed.", messageMetaData)
            dispositionList.add(DispositionList.KILL, message)
            return dispositionList
        }

        //if Filtered value is evaluates to true process the message or else suppress it

        var filterUtility = FilterUtility()
        val filters: ArrayList<SiuProcessingConfig> = SqliteUtility.getValues(
            globalInit.localConnection,
            "ProcessingConfig",
            mapOf("Key" to "Filters")
        )
        if (!filterUtility.shouldResourceBeJsonpathFiltered(filters[0].Value, message, messageMetaData, cloverLogger)) {
            cloverLogger.log(1, "Message is filtered and suppressed for given json path", messageMetaData)
            log.info("Message is filtered and suppressed")
            dispositionList.add(DispositionList.KILL, message)
            return dispositionList
        }
        cloverLogger.log(2, "Json path filtration is completed", messageMetaData)

        val eventAndParameter = readEventScriptParamsCallBack?.invoke(message)!!

        val event = eventAndParameter.first
        val parameters = eventAndParameter.second


        event.second.forEach {
            val scriptInformation = scripts.getHandlerFor(event.first, it)

            if (scriptInformation?.isPresent == true) {
                cloverLogger.log(2, "Groovy scripts are loaded with required passing parameters", messageMetaData)
                val bundle = scripts.run(parameters, scriptInformation.get()) as Bundle
                val parser = globalInit.parser
                parser.setPrettyPrint(globalInit.configuration.enableSMARTFormatting)

                val ackMessage: Message = cloverEnv!!.makeMessage(
                    parser.encodeResourceToString(bundle),
                    Message.DATA_TYPE,
                    Message.PROTOCOL_CLASS,
                    false
                )

                //log.trace("testtool: ${ackMessage.content}")
                ackMessage.userdata = message?.userdata
                smatdbSIUFHIR?.insert(ackMessage)
                cloverLogger.log(1, "Fhir bundle will be sent", messageMetaData)
                dispositionList.add(DispositionList.CONTINUE, ackMessage)
                dispositionList.add(DispositionList.KILL, message)
            }
        }
        return dispositionList
    }

    private fun isIgnoreUser(message: Message?, messageMetaData: MessageMetaData): Boolean {
        val mapper = objectMapper
        val historyUsername = mapper.readTree(message?.content).get("data")?.get("history_username")?.textValue()
            ?: return false
        cloverLogger.log(2, "history username is :$historyUsername", messageMetaData)
        val whereClause = mapOf("Key" to "IgnoreUsers")
        val ignoreUser =
            SqliteUtility.getValues<SiuProcessingConfig>(globalInit.localConnection, "ProcessingConfig", whereClause)
        val ignoreUsers: SiuProcessingConfig = ignoreUser[0]
        when (historyUsername.lowercase()) {
            ignoreUsers.Value?.lowercase(),
            ignoreUsers.Value2?.lowercase(),
            ignoreUsers.Value3?.lowercase(),
            ignoreUsers.Value4?.lowercase(),
            ignoreUsers.Value5?.lowercase(),
            -> return true
        }
        cloverLogger.log(2, "history username and ignore username is not same", messageMetaData)
        return false
    }

    fun getEventIdAndScriptParamMap(message: Message?): Pair<Pair<String, List<String>>, Map<String, Any>>? {
        val eventid = Pair("Json", mutableListOf("SiuOutFhir"))

        val mapper = objectMapper
        val json = mapper.readTree(message?.content)
        val bundleJson: ObjectNode = mapper.createObjectNode()
        bundleJson.put("resourceType", "Bundle")

        val entry: ArrayNode = mapper.createArrayNode()
        val emptyJson: ObjectNode = mapper.createObjectNode()
        val resource: ObjectNode = mapper.createObjectNode()
        resource.put("resourceType", "Parameters")
        val parameter: ArrayNode = mapper.createArrayNode()

        val jsonArray7: ObjectNode = mapper.createObjectNode()
        jsonArray7.put("name", "ID")
        val id = json.findValue("id")
        jsonArray7.replace("valueString", id)
        parameter.add(jsonArray7)

        val jsonArray5: ObjectNode = mapper.createObjectNode()
        jsonArray5.put("name", "patient_ser")
        val patientSer = (json["data"] as ObjectNode)["patient_ser"]
        jsonArray5.replace("valueString", patientSer)
        parameter.add(jsonArray5)

        val jsonArray1: ObjectNode = mapper.createObjectNode()
        jsonArray1.put("name", "Source")
        jsonArray1.put("valueString", "Json")
        parameter.add(jsonArray1)

        val jsonArray2: ObjectNode = mapper.createObjectNode()
        jsonArray2.put("name", "Subject")
        jsonArray2.put("valueString", "SiuOutFhir")
        parameter.add(jsonArray2)

        val jsonArray3: ObjectNode = mapper.createObjectNode()
        jsonArray3.put("name", ParametersUtility.SCHEDULEDACTIVITYSER)
        jsonArray3.replace("valueString", (json["data"] as ObjectNode)["scheduled_activity_ser"])
        parameter.add(jsonArray3)

        val jsonArray4: ObjectNode = mapper.createObjectNode()
        jsonArray4.put("name", "event_reason")
        jsonArray4.replace("valueString", (json["data"] as ObjectNode)["event_reason"])
        parameter.add(jsonArray4)

        val jsonArray6: ObjectNode = mapper.createObjectNode()
        jsonArray6.put("name", "department_ser")
        jsonArray6.replace("valueString", (json["data"] as ObjectNode)["department_ser"])
        parameter.add(jsonArray6)

        val jsonArray8: ObjectNode = mapper.createObjectNode()
        jsonArray8.put("name", ParametersUtility.SCHEDULED_ACTIVITY_REV_COUNT)
        jsonArray8.replace("valueString", (json["data"] as ObjectNode)["scheduled_activity_rev_count"])
        parameter.add(jsonArray8)

        val jsonArray9: ObjectNode = mapper.createObjectNode()
        jsonArray9.put("name", ParametersUtility.HISTORY_USERNAME)
        val historyUser = (json["data"] as ObjectNode)["history_username"]
        jsonArray9.replace("valueString", historyUser)
        parameter.add(jsonArray9)

        val jsonArray10: ObjectNode = mapper.createObjectNode()
        jsonArray10.put("name", "patient_id")
        val patientId = (json["data"] as ObjectNode)["patient_id"]
        jsonArray10.replace("valueString", patientId)
        parameter.add(jsonArray10)


        resource.replace("parameter", parameter)
        emptyJson.replace("resource", resource)
        entry.add(emptyJson)
        bundleJson.replace("entry", entry)



        val parameters = mutableMapOf<String, Any>()
        parameters[ParameterConstant.SEVERITY] = OperationOutcome.IssueSeverity.INFORMATION
        parameters[ParameterConstant.BUNDLE] = globalInit.parser.parseResource(bundleJson.toString()) as Bundle
        parameters[ParameterConstant.CLIENT_DECOR] = clientDecor
        parameters[ParameterConstant.BUNDLE_UTILITY] = globalInit.bundleUtility
        parameters[ParameterConstant.PARAMETERS_UTILITY] = globalInit.parametersUtility
        parameters[ParameterConstant.PATIENT_UTILITY] = globalInit.patientUtility
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters[ParameterConstant.SITE_DIR_NAME] = siteDirName
        parameters[ParameterConstant.SQLITE_UTILITY] = SqliteUtility
        parameters[ParameterConstant.GLOBAL_INIT] = globalInit
        parameters[ParameterConstant.LOG] = log
        parameters[ParameterConstant.CLOVERLOGGER] = cloverLogger
        parameters[ParameterConstant.MSGMETADATA] = messageMetaData
        parameters[ParameterConstant.parser] = globalInit.parser

        return Pair(eventid, parameters)
    }
}