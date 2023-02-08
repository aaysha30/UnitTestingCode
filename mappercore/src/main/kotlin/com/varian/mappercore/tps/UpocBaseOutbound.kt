package com.varian.mappercore.tps

import ca.uhn.hl7v2.model.v251.message.SIU_S12
import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.client.CreateAMessage
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.outboundAriaEvent.serviceclient.AriaEventService
import com.varian.mappercore.constant.AriaConnectConstant
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.ClientDecor
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory
import com.varian.mappercore.framework.utility.SubscriptionUtility
import com.varian.mappercore.helper.sqlite.SqliteUtility
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome

abstract class UpocBaseOutbound :
    TPS {
    lateinit var subscriptionUtility: SubscriptionUtility

    protected var cloverEnv: CloverEnv? = null
    protected lateinit var cloverLogger: CloverLogger
    protected lateinit var scripts: IScripts
    protected lateinit var clientDecor: ClientDecor
    protected lateinit var globalInit: GlobalInit
    protected lateinit var hl7MessageUtility: CreateAMessage
    protected var log: Logger = LogManager.getLogger(UpocBaseOutbound::class.java)
    protected var siteDirName: String = ""

    var ariaFhirClient: FhirClient? = null
    var readEventScriptParamsCallBack: ((Message?) -> Pair<Pair<String, List<String>>, Map<String, Any>>?)? = null

    private lateinit var scriptFactory: ScriptFactory
    private lateinit var ariaEventService: AriaEventService
    private var mappedSqliteDbName: String = ""

    constructor(
        cloverEnv: CloverEnv, propertyTree: PropertyTree?
    ) : super(
        cloverEnv,
        propertyTree
    ) {
        try {
            log.trace("Inside upocbase outbound: Initializing...")
            /**below func must be executed once before calling cloverLogger.log() fun*/
            cloverLogger = CloverLogger.initCLoverLogger(cloverEnv)
            Thread.currentThread().contextClassLoader = UpocBase::class.java.classLoader
            this.cloverEnv = cloverEnv
            globalInit = GlobalInit.createInstance(
                cloverEnv
            )
            mappedSqliteDbName =
                cloverEnv.tableLookup(AriaConnectConstant.INTERFACES_TABLE, globalInit.getProcessName(cloverEnv))
            cloverLogger.log(2, "Mapped sqlite database to interface is: $mappedSqliteDbName", MessageMetaData())
            siteDirName = cloverEnv.siteDirName
            ariaFhirClient = globalInit.fhirFactory.getFhirClient()
            ariaEventService = globalInit.ariaEventService
            subscriptionUtility =
                SubscriptionUtility(globalInit.masterConnection, globalInit.localConnection, ariaEventService)

            log.info("Inside upocbase outbound: Initialization completed.")
        } catch (ex: Exception) {
            cloverLogger.log(0, "Error occurred while initializing: ${ex.message}", MessageMetaData())
            cloverLogger.log(0, "Error occurred at: ${ex.stackTraceToString()}", MessageMetaData())
            log.error("Upocbase outbound: Initialization failed.")
            log.debug(ex.stackTraceToString())
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
            hl7MessageUtility = CreateAMessage()
            scriptFactory = ScriptFactory(threadName, globalInit.configuration.dslRoot, cloverEnv.siteDirName)
            this.cloverEnv = cloverEnv

            siteDirName = cloverEnv.siteDirName
            cloverLogger.log(2, "Site directory is: $siteDirName", MessageMetaData())

            scripts = scriptFactory.scripts
            log.info("Initialization completed. Process: $threadName")
        } catch (exception: Exception) {
            cloverLogger.log(
                0,
                "Error occurred while initializing the process: ${exception.message}",
                MessageMetaData()
            )
            cloverLogger.log(0, "Error occurred at: ${exception.stackTraceToString()}", MessageMetaData())
            log.error("Upocbase outbound: Process initialization failed.")
            handleException(cloverEnv, exception)
        }
    }

    override fun process(cloverEnv: CloverEnv, context: String?, mode: String, message: Message?): DispositionList {
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
        return DispositionList()
    }

    open fun handleRun(message: Message?): DispositionList {
        log.info("processing message..")
        val bundleResource = globalInit.parser.parseResource(message?.content) as Bundle

        val id = globalInit.parametersUtility.getEventID(globalInit.bundleUtility.getParameters(bundleResource)!!)
        val patientSer =
            globalInit.parametersUtility.getPatientSer(globalInit.bundleUtility.getParameters(bundleResource)!!)
        val messageMetaData = MessageMetaData(patientSer, id!!)
        val dispositionList = DispositionList()
        val outcome = Outcome(globalInit.parser)
        clientDecor = ClientDecor(ariaFhirClient!!, outcome)
        val bundle: Bundle?
        var bundleout: Any? = null
        val siuS12: SIU_S12
        var hl7string: String? = null

        var errorMsg: String? = null
        try {
            siuS12 = SIU_S12()
            siuS12.initQuickstart("SIU", "S12", "P")

            var eventAndParameter = readEventScriptParamsCallBack?.invoke(message)

            if (eventAndParameter == null) {
                bundle = bundleResource
                eventAndParameter = getEventIdAndScriptParamMapFallBack(bundle, outcome, siuS12, messageMetaData)
            }

            val event = eventAndParameter.first
            val parameters = eventAndParameter.second


            event.second.forEach {
                val scriptInformation = scripts.getHandlerFor(event.first, it)

                if (scriptInformation?.isPresent == true) {
                    cloverLogger.log(2, "Groovy scripts are loaded with required passing parameters", messageMetaData)
                    bundleout = scripts.run(parameters, scriptInformation.get())
                }
            }

            if (bundleout == null) {
                cloverLogger.log(2, "Output from groovy is null", messageMetaData)
                log.info("Activity Code is not matching, hence message is suppressed")
                dispositionList.add(DispositionList.KILL, message)
                return dispositionList
            } else {
                val bundleReturn = bundleout as SIU_S12
                hl7string = hl7MessageUtility.GetHL7(bundleReturn)
                cloverLogger.log(2, "Groovy output is converted to HL7.", messageMetaData)
            }
        } catch (exception: Exception) {
            errorMsg = exception.message
            cloverLogger.log(0, "Error occurred while processing message: " + exception.message, messageMetaData)
            cloverLogger.log(0, "Exception/error stack trace:" + exception.stackTraceToString(), messageMetaData)
            outcome.addError(exception)
        } finally {
            if (bundleout != null) {
                dispositionList.add(DispositionList.KILL, message)
            }
            if (errorMsg != null) {
                val errorAckMessage: Message =
                    cloverEnv!!.makeMessage(
                        "Message Id: ${message?.metadata?.mid?.joinToString(".")} Error: $errorMsg",
                        Message.DATA_TYPE,
                        Message.PROTOCOL_CLASS,
                        false
                    )
                dispositionList.add(DispositionList.ERROR, errorAckMessage)
                cloverLogger.log(
                    0,
                    "Occurred error: " + errorAckMessage.content + ". Hence message is marked as errored.",
                    messageMetaData
                )
            } else {
                val ackMessage: Message = cloverEnv!!.makeMessage(
                    hl7string,
                    Message.DATA_TYPE,
                    Message.PROTOCOL_CLASS,
                    false
                )
                ackMessage.userdata = message?.userdata
                cloverLogger.log(1, "Hl7 message will be sent to external system.", messageMetaData)
                dispositionList.add(DispositionList.CONTINUE, ackMessage)
            }
            globalInit.reset()
        }
        return dispositionList
    }

    open fun handleShutdown(): DispositionList {
        return DispositionList()
    }

    open fun handleTime(message: Message?): DispositionList {
        val dispositionList = DispositionList()
        dispositionList.add(DispositionList.CONTINUE, message)
        return dispositionList
    }

    fun handleException(cloverEnv: CloverEnv, exception: Exception) {
        log.error("Error occurred at interface: ${globalInit.getProcessName(cloverEnv)}. Message: ${exception.message}")
        log.debug(exception.stackTraceToString())
        cloverLogger.log(0, "Error occurred at interface: ${globalInit.getProcessName(cloverEnv)}", MessageMetaData())
        cloverLogger.log(0, "Error: $exception", MessageMetaData())
        cloverLogger.log(0, "Error Message: ${exception.message}", MessageMetaData())
        cloverEnv.requestProcessStop(globalInit.getProcessName(cloverEnv))
    }

    open fun getEventIdAndScriptParamMapFallBack(
        bundle: Bundle,
        outcome: Outcome,
        siuS12: SIU_S12,
        messageMetaData: MessageMetaData
    ): Pair<Pair<String, List<String>>, Map<String, Any>> {
        clientDecor = ClientDecor(ariaFhirClient!!, outcome)
        var subjects = mutableListOf<String>()
        if (subjects.isNullOrEmpty()) {
            subjects.add("SiuOut")
        }
        val scriptContextPair = Pair(
            "Json",
            subjects
        )

        val parameters = mutableMapOf<String, Any>()
        parameters[ParameterConstant.SEVERITY] = OperationOutcome.IssueSeverity.INFORMATION
        parameters[ParameterConstant.BUNDLE] = bundle
        parameters[ParameterConstant.CLIENT_DECOR] = clientDecor
        parameters[ParameterConstant.BUNDLE_UTILITY] = globalInit.bundleUtility
        parameters[ParameterConstant.PARAMETERS_UTILITY] = globalInit.parametersUtility
        parameters[ParameterConstant.PATIENT_UTILITY] = globalInit.patientUtility
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters[ParameterConstant.SIUOUT] = siuS12
        parameters[ParameterConstant.MAPPED_SQLITE_DB_NAME] = mappedSqliteDbName
        parameters[ParameterConstant.SITE_DIR_NAME] = siteDirName
        parameters[ParameterConstant.SQLITE_UTILITY] = SqliteUtility
        parameters[ParameterConstant.GLOBAL_INIT] = globalInit
        parameters[ParameterConstant.LOG] = log
        parameters[ParameterConstant.CLOVERLOGGER] = cloverLogger
        parameters[ParameterConstant.MSGMETADATA] = messageMetaData

        return Pair(scriptContextPair, parameters)
    }
}