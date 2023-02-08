package com.varian.mappercore.tps

import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.gclient.TokenClientParam
import ca.uhn.hl7v2.model.v251.message.ACK
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.framework.helper.ClientDecor
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Resource
import java.lang.reflect.UndeclaredThrowableException

abstract class FHIRClientUpocBase :
    TPS {
    private lateinit var scriptFactory: ScriptFactory
    private var ariaFhirClient: FhirClient? = null
    private var hl7EncodedCharMap = hashMapOf<String, String>()

    //protected lateinit var log: Logger
    protected var log: Logger = LogManager.getLogger(FHIRClientUpocBase::class.java)
    protected var cloverEnv: CloverEnv? = null
    protected lateinit var cloverLogger: CloverLogger
    protected var adtProcessResultSmatDb: SMATDB? = null
    protected lateinit var scripts: IScripts
    protected lateinit var clientDecor: ClientDecor
    protected lateinit var outcome: Outcome
    protected lateinit var globalInit: GlobalInit
    protected lateinit var readEventScriptParamsCallBack: ((Message) -> Pair<Pair<String, List<String>>, Map<String, Any>>)
    protected var messageMetaData: MessageMetaData = MessageMetaData()
    protected var bundle: Bundle = Bundle()
    protected lateinit var ackResponse: ACK

    constructor(
        cloverEnv: CloverEnv, propertyTree: PropertyTree?
    ) : super(
        cloverEnv,
        propertyTree
    ) {
        try {
            log.trace("Inside FHIRClientUpocBase: Initializing...")
            Thread.currentThread().contextClassLoader = FHIRClientUpocBase::class.java.classLoader
            //log = LogManager.getLogger(FHIRClientUpocBase::class.java)

            this.cloverEnv = cloverEnv
            this.cloverLogger = CloverLogger.initCLoverLogger(cloverEnv)
            globalInit = GlobalInit.createInstance(
                cloverEnv
            )
            adtProcessResultSmatDb = SMATDB(cloverEnv, globalInit.configuration.adtInSCInSMATDbName)
            ariaFhirClient = globalInit.fhirFactory.getFhirClient()
            log.trace("Inside FHIRClientUpocBase: Initialization completed.")
        } catch (ex: Exception) {
            cloverLogger.log(0, "Error occurred while initializing interface: ${ex.message}", messageMetaData)
            cloverLogger.log(0, "Error occurred at: ${ex.stackTraceToString()}", messageMetaData)
            log.error("FHIRClientUpocBase: Initialization failed. ${ex.message}")
            handleException(cloverEnv, ex)
        }
    }

    constructor(
        cloverEnv: CloverEnv,
        propertyTree: PropertyTree?,
        threadName: String
    ) : this(
        cloverEnv,
        propertyTree
    ) {
        try {
            log.trace("Initializing Process: $threadName")
            scriptFactory = ScriptFactory(threadName, globalInit.configuration.dslRoot, cloverEnv.siteDirName)
            scripts = scriptFactory.scripts
            log.trace("Initialization completed. Process: $threadName")
        } catch (exception: Exception) {
            cloverLogger.log(0, "Error occurred while initializing interface: ${exception.message}", messageMetaData)
            cloverLogger.log(0, "Error occurred at: ${exception.stackTraceToString()}", messageMetaData)
            log.error("Upocbase: Process initialization failed.")
            handleException(cloverEnv, exception)
        }
    }

    override fun process(cloverEnv: CloverEnv, context: String?, mode: String?, message: Message?): DispositionList {
        CloverLogger.initCLoverLogger(cloverEnv)
        return when (mode) {
            "start" -> handleStart(cloverEnv)
            "run" -> handleRun(message)
            "shutdown" -> handleShutdown()
            "time" -> handleTime(message)
            else -> DispositionList()
        }
    }

    open fun handleStart(cloverEnv: CloverEnv): DispositionList {
        try {

        } catch (exception: Exception) {
            handleException(cloverEnv, exception)
        }
        return DispositionList()
    }

    open fun handleRun(message: Message?): DispositionList {
        log.info("processing message..")
        val dispositionList = DispositionList()
        outcome = Outcome(globalInit.parser)
        clientDecor = ClientDecor(ariaFhirClient!!, outcome)
        var hl7string: String? = null
        var overAckMessage: Message?
        lateinit var opOutcome: MethodOutcome
        var responseData: String = ""
        try {
            log.info("Json Message = $message?.content")
            cloverLogger.log(1, "=============processing message..Json Message = $message?.content===============")
            val mapper = ObjectMapper()
            val json = mapper.readTree(message?.content)
            val taskdata = globalInit.parser.parseResource(json.toString()) as Bundle
            log.info("Json Message = $json")
            val requestType: String = taskdata.getMeta().getTagFirstRep().system.toString()
            cloverLogger.log(1, "=============Request Type = $requestType===============")
            try {
                when (requestType) {
                    "GET" -> {
                        val url: String = taskdata.getMeta().getTagFirstRep().code.toString()
                        val resouceType: String = taskdata.getMeta().getTagFirstRep().display.toString()
                        val resp: IBaseResource = clientDecor.read(resouceType, url)
                        responseData = globalInit.parser.encodeResourceToString(resp)

                    }

                    "GETBYID" -> {
                        val url: String = taskdata.getMeta().getTagFirstRep().code.toString()
                        val resouceType: String = taskdata.getMeta().getTagFirstRep().display.toString()
                        val resp: IBaseResource = clientDecor.readById(resouceType, url)
                        responseData = globalInit.parser.encodeResourceToString(resp)

                    }

                    "SEARCH" -> {
                        val url: String = taskdata.getMeta().getTagFirstRep().code.toString()
                        val resouceType: String = taskdata.getMeta().getTagFirstRep().display.toString()
                        val searchtype: String = taskdata.getMeta().getTagFirstRep().id.toString()
                        val searchvalue: String = taskdata.getMeta().getTagFirstRep().version.toString()

                        val token = TokenClientParam("identifier").exactly().systemAndCode(url, searchvalue)

                        val resp: IBaseResource = clientDecor.search(resouceType, searchtype, token)
                        responseData = globalInit.parser.encodeResourceToString(resp)

                    }

                    "PUT" -> {
                        val resourceData: Resource = taskdata.getEntryFirstRep().getResource()
                        taskdata.getEntryFirstRep().getResource().id
                        opOutcome = clientDecor.update(resourceData)
                        responseData = Gson().toJson(opOutcome).toString()
                    }

                    "POST" -> {
                        val resourceData: Resource = taskdata.getEntryFirstRep().getResource()
                        opOutcome = clientDecor.create(resourceData)
                        responseData = Gson().toJson(opOutcome).toString()
                    }

                    "DELETE" -> {
                        val resourceData: Resource = taskdata.getEntryFirstRep().getResource()
                        val result = clientDecor.delete(resourceData)
                        responseData = Gson().toJson(result).toString()
                    }

                    else -> {
                        responseData = "Unsupported Request Type"
                    }
                }
            } catch (exception: Exception) {
                log.error("error processing message. ${exception.message}")
                log.debug("StackTrace: ${exception.stackTraceToString()}")
                cloverLogger.log(
                    0,
                    exception.message
                        ?: (exception as UndeclaredThrowableException).undeclaredThrowable.localizedMessage,
                    messageMetaData
                )
                cloverLogger.log(
                    0,
                    "Error while processing message into ARIA ${exception.stackTraceToString()}",
                    messageMetaData
                )

                responseData = Gson().toJson(exception).toString()
            }
            log.info("Outcome Message = $responseData")

        } catch (exception: Exception) {
            log.error("error processing message. ${exception.message}")
            log.debug("StackTrace: ${exception.stackTraceToString()}")
            cloverLogger.log(
                0,
                exception.message
                    ?: (exception as UndeclaredThrowableException).undeclaredThrowable.localizedMessage,
                messageMetaData
            )
            cloverLogger.log(
                0,
                "Error while processing message into ARIA ${exception.stackTraceToString()}",
                messageMetaData
            )
            outcome.addError(exception)
        } finally {
            globalInit.reset()
            overAckMessage = getAckMessage(message, responseData, "200")
            dispositionList.add(DispositionList.OVER, overAckMessage)
            dispositionList.add(DispositionList.CONTINUE, message)
        }
        return dispositionList
    }

    private fun getAckMessage(
        message: Message?,
        returnMessage: String?,
        statusCode: String
    ): Message? {
        var overAckMessage = cloverEnv?.makeMessage(
            returnMessage,
            Message.REPLY_TYPE,
            Message.PROTOCOL_CLASS,
            false
        )
        val httpResponse = PropertyTree()
        httpResponse.put("httpResponseCode", statusCode)
        overAckMessage?.userdata = httpResponse
        overAckMessage?.metadata?.driverctl = message?.metadata?.driverctl
        return overAckMessage
    }

    open fun preRun(message: Message?) {

    }

    open fun postRun(message: Message?, bundle: Bundle?, outcome: Outcome, dispositionList: DispositionList) {
        //Do Nothing if not overridden, as of now overridden by AdtInbound & ParkPatient
    }

    open fun canRun(message: Message): Boolean {
        return true
    }

    open fun handleShutdown(): DispositionList {
        return DispositionList()
    }

    open fun handleTime(message: Message?): DispositionList {
        val dispositionList = DispositionList()
        dispositionList.add(DispositionList.CONTINUE, message)
        return dispositionList
    }

    private fun handleException(cloverEnv: CloverEnv, exception: Exception) {
        log.error("Error occurred at interface: ${globalInit.getProcessName(cloverEnv)}. Message: ${exception.message}")
        log.debug(exception.stackTraceToString())
        cloverEnv.log(2, "Error: $exception")
        cloverEnv.log(2, "Error Message: ${exception.message}")
        cloverEnv.requestProcessStop(globalInit.getProcessName(cloverEnv))
    }
}
