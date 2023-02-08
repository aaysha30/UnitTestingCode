package com.varian.mappercore.tps

import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.framework.helper.ClientDecor
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory
import com.varian.mappercore.framework.utility.BundleUtility
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.OperationOutcome
import java.lang.reflect.UndeclaredThrowableException


abstract class UpocBase :
    TPS {
    private lateinit var scriptFactory: ScriptFactory
    private var ariaFhirClient: FhirClient? = null
    private var hl7EncodedCharMap = hashMapOf<String, String>()
    protected var log: Logger = LogManager.getLogger(UpocBase::class.java)
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

    constructor(
        cloverEnv: CloverEnv, propertyTree: PropertyTree?
    ) : super(
        cloverEnv,
        propertyTree
    ) {
        try {
            log.trace("Inside upocbase: Initializing...")
            Thread.currentThread().contextClassLoader = UpocBase::class.java.classLoader
            this.cloverEnv = cloverEnv
            this.cloverLogger = CloverLogger.initCLoverLogger(cloverEnv)
            globalInit = GlobalInit.createInstance(
                cloverEnv
            )
            adtProcessResultSmatDb = SMATDB(cloverEnv, globalInit.configuration.adtInSCInSMATDbName)
            ariaFhirClient = globalInit.fhirFactory.getFhirClient()
            log.trace("Inside upocbase: Initialization completed.")
        } catch (ex: Exception) {
            cloverLogger.log(0, "Error occurred while initializing interface: ${ex.message}", messageMetaData)
            cloverLogger.log(0, "Error occurred at: ${ex.stackTraceToString()}", messageMetaData)
            log.error("Upocbase: Initialization failed.")
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
        try {
            if (canRun(message!!)) {
            preRun(message)
            messageMetaData.patientId = message.userdata?.get("UniquePatientId") as String
            messageMetaData.messageCtrId = message.userdata?.get("UniqueMessageId") as String
            cloverLogger.log(1, "HL7 message is converted into JSON.", messageMetaData)
            cloverLogger.log(
                2,
                "Message is received at service client for further processing with the userData: ${message.userdata}",
                messageMetaData
            )
            try {
                val eventAndParameter = readEventScriptParamsCallBack.invoke(message)
                val event = eventAndParameter.first
                val parameters = eventAndParameter.second

                event.second.forEach {
                    val scriptInformation = scripts.getHandlerFor(event.first, it)

                    if (scriptInformation?.isPresent == true) {
                        scripts.run(parameters, scriptInformation.get())
                    }
                }
                cloverLogger.log(1, "Message processing into the ARIA is completed.", messageMetaData)
                log.info("message processing completed.")
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
                dispositionList.add(DispositionList.KILL, message)
                postRun(message, bundle, outcome, dispositionList)
                makeErrorMessage(message, bundle, outcome, dispositionList)
            }
        } else {
            dispositionList.add(DispositionList.CONTINUE, message)
        }
        }catch (exception: Exception) {
            globalInit.reset()
            cloverLogger.log(0, "Error occurred while processing message: " + exception.message, messageMetaData)
            cloverLogger.log(0, "Exception/error stack trace:" + exception.stackTraceToString(), messageMetaData)
            outcome.addError(exception)
            dispositionList.add(DispositionList.KILL, message)
            val ackMsgBundle = AckResponse.getAckMessage(bundle, outcome, messageMetaData, cloverLogger)
            ackMsgBundle.entry.removeIf {  it == null || it.resource == null || (it.resource.fhirType() != Enumerations.FHIRAllTypes.MESSAGEHEADER.toCode() && it.resource.fhirType() != Enumerations.FHIRAllTypes.OPERATIONOUTCOME.toCode())  }
            val parser = globalInit.parser
            parser.setPrettyPrint(globalInit.configuration.enableSMARTFormatting)
            val ackMsgPayload = parser.encodeResourceToString(ackMsgBundle)
            val ackMessage: Message? =
                cloverEnv?.makeMessage(ackMsgPayload, Message.DATA_TYPE, Message.PROTOCOL_CLASS, false)
            ackMessage?.userdata = message?.userdata
            ackMessage?.metadata?.driverctl = message?.metadata?.driverctl
            adtProcessResultSmatDb?.insert(ackMessage)
            dispositionList.add(DispositionList.OVER, ackMessage)
        }
        return dispositionList
    }

    private fun makeErrorMessage(
        message: Message?,
        bundle: Bundle?,
        outcome: Outcome,
        dispositionList: DispositionList
    ) {
        val errors = outcome.getOperationOutcome().issue.filter { it.severity == OperationOutcome.IssueSeverity.ERROR }
        if (errors.any()) {
            var mshControlId: String? = ""
            if (bundle != null) {
                val messageHeaderResource = BundleUtility().getMessageHeader(bundle)
                mshControlId =
                    messageHeaderResource?.getExtensionByUrl(AckResponse.MESSAGE_CONTROL_ID_EXT_URL)?.value?.toString()
            }

            val ackMessage = message?.userdata?.get("hl7Message").toString()
            val shortAckMessage = "$mshControlId - ${cloverEnv?.processName}"
            val errMsg = errors.joinToString { "${it.details.text}, ${it.details.codingFirstRep.code}" }
            val errorAckMessage: Message =
                cloverEnv!!.makeMessage(
                    ackMessage,
                    Message.DATA_TYPE,
                    Message.PROTOCOL_CLASS,
                    false
                )

            val errUserData = errorAckMessage.userdata
            errUserData.put(ErrorDbProc.ERROR_MESSAGE_KEY, errMsg)
            errUserData.put(ErrorDbProc.SHORT_ACK_MESSAGE_KEY, shortAckMessage)
            errorAckMessage.userdata = errUserData
            errorAckMessage.metadata?.driverctl = message?.metadata?.driverctl
            dispositionList.add(DispositionList.ERROR, errorAckMessage)
        }
    }

    open fun preRun(message: Message?) {

    }

    open fun postRun(message: Message?, bundle: Bundle?, outcome: Outcome, dispositionList: DispositionList) {
        //Do Nothing if not overridden, as of now overridden by AdtInbound & ParkPatient
    }

    open fun canRun(message: Message): Boolean {
        log.trace("#Performance - ADT_In process message begins")
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
