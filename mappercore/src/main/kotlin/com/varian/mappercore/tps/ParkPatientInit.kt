package com.varian.mappercore.tps

import ca.uhn.hl7v2.AcknowledgmentCode
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.Severity
import ca.uhn.hl7v2.model.v251.segment.MSH
import ca.uhn.hl7v2.parser.Parser
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.DispositionList
import com.quovadx.cloverleaf.upoc.Message
import com.quovadx.cloverleaf.upoc.PropertyTree
import com.varian.mappercore.constant.AriaConnectConstant.Companion.IGNORE_ROUTE
import com.varian.mappercore.constant.XlateConstant.ACTIVE_NULL_LITERAL
import com.varian.mappercore.framework.utility.HL7Utility
import java.lang.reflect.UndeclaredThrowableException

@Suppress("unused")
class ParkPatientInit(
    cloverEnv: CloverEnv,
    propertyTree: PropertyTree?
) : UpocBase(cloverEnv, propertyTree) {

    private var cloverEnvironment: CloverEnv = cloverEnv
    private var hl7Parser: Parser
    private var hl7Utility: HL7Utility

    init {
        Thread.currentThread().contextClassLoader = TrixIdFormat::class.java.classLoader
        hl7Parser = globalInit.hl7Parser
        hl7Utility = HL7Utility(globalInit)
    }

    override fun handleRun(message: Message?): DispositionList {
        log.trace("#Performance - ParkPatientInit Begins")
        cloverLogger.log(3, "Inside UPOC: ParkPatientInit.handleRun method..", messageMetaData)
        val dispositionList = DispositionList()
        if (message?.content.isNullOrEmpty()) {
            dispositionList.add(DispositionList.KILL, message)
            return dispositionList;
        }
        cloverLogger.log(0, "Hl7 message received from external system, processing started...", messageMetaData)

        cloverLogger.log(3, "replacing required characters in hl7 message", messageMetaData)
        message?.content = message!!.content.replace("&lt;", "<").replace("&gt;", ">")
            .replace("&apos;", "'").replace("&quot;", "\"")
            .replace("&amp;", "&").replace("\"\"", ACTIVE_NULL_LITERAL);

        cloverLogger.log(3, "reading patient id and message control id", messageMetaData)
        messageMetaData = globalInit.getPatientIdandMessageControlId(message)
        cloverLogger.log(
            0,
            "Patient Id: ${messageMetaData.patientId}, MessageControlId: ${messageMetaData.messageCtrId}",
            messageMetaData
        )
        var errorAck: String? = null
        val hl7Message = hl7Parser.parse(message.content)
        try {
            val userData = message.userdata
            userData.put("hl7Message", message.content)
            userData.put("UniquePatientId", messageMetaData.patientId)
            userData.put("UniqueMessageId", messageMetaData.messageCtrId)
            val trxIdValue = userData.get("TrxId")
            cloverLogger.log(2, "Trix id value is $trxIdValue", messageMetaData)
            cloverLogger.log(2, "Message userData is: $userData", messageMetaData)
            val msh: MSH = hl7Message.get("MSH") as MSH
            val msgEvent =
                msh.msh9_MessageType.msg1_MessageCode.toString() + "^" + msh.msh9_MessageType.msg2_TriggerEvent.toString()

            val isCommitAckRequired = msh.msh15_AcceptAcknowledgmentType?.value?.toString() == "AL"
            val ackCode = if (isCommitAckRequired) {
                cloverLogger.log(2, "Commit Ack is required by sender..", messageMetaData)
                userData.put("SendApplicationAck", "0")
                AcknowledgmentCode.CR
            } else {
                cloverLogger.log(2, "Application Ack is required by sender..", messageMetaData)
                userData.put("SendApplicationAck", "1")
                AcknowledgmentCode.AR
            }

            if (!globalInit.inboundEventList.map { x -> x.InValue }.contains(msgEvent)) {
                cloverLogger.log(0, "Unsupported Event: $msgEvent found in the message.", messageMetaData)
                val unsupportedEventTextMessage = "Unsupported Event found in the message."
                cloverLogger.log(2, "Setting Trix id: $trxIdValue", messageMetaData)
                userData.put("TrxId", IGNORE_ROUTE)
                val overAck = hl7Utility.getHL7Ack(hl7Message, ackCode)
                overAck.msa.msa3_TextMessage.value = unsupportedEventTextMessage
                val overAckMessage: Message = makeMessage(cloverEnvironment, overAck.toString())
                overAckMessage.userdata = userData
                overAckMessage.metadata.driverctl = message.metadata?.driverctl
                dispositionList.add(DispositionList.CONTINUE, overAckMessage)
                dispositionList.add(DispositionList.KILL, message)
                cloverLogger.log(0, "Unsupported Event: $msgEvent found in the message.", messageMetaData)
                cloverLogger.log(1, "Sending acknowledgement..", messageMetaData)
                cloverLogger.log(3, "Inside ParkPatient Init. Kill original message", messageMetaData)
            } else {
                if (isCommitAckRequired) {
                    val patientTempMessage = "CommitAckRequired"
                    val overAck =
                        hl7Utility.getHL7Ack(hl7Message, Severity.WARNING, patientTempMessage)
                    val overAckMessage: Message = makeMessage(cloverEnvironment, overAck)
                    overAckMessage.userdata = userData
                    overAckMessage.metadata.driverctl = message.metadata?.driverctl
                    cloverLogger.log(0, "Sending commit ack", messageMetaData)
                    dispositionList.add(DispositionList.OVER, overAckMessage)
                }
                message.userdata = userData
                dispositionList.add(DispositionList.CONTINUE, message)
            }
        } catch (exception: UndeclaredThrowableException) {
            val hl7Exception = exception.undeclaredThrowable as HL7Exception
            errorAck = hl7Utility.getErrorAck(message.content, AcknowledgmentCode.AE, hl7Exception)
            cloverLogger.log(0, "Error/Exception occur: ${hl7Exception.message}", messageMetaData)
            cloverLogger.log(2, "At : ${exception.stackTraceToString()}", messageMetaData)
        } catch (exception: Exception) {
            val exceptionMessage = if (exception.message.isNullOrEmpty()) exception.toString() else exception.message!!
            errorAck = hl7Utility.getHL7Ack(hl7Message, Severity.ERROR, exceptionMessage)
            cloverLogger.log(0, "Error/Exception occur: $exceptionMessage", messageMetaData)
            cloverLogger.log(2, "At: ${exception.stackTraceToString()}", messageMetaData)
        } finally {
            if (errorAck != null) {
                dispositionList.add(DispositionList.KILL, message)
                val overAckMessage: Message = makeMessage(cloverEnvironment, errorAck.toString())
                overAckMessage.metadata.driverctl = message.metadata?.driverctl
                dispositionList.add(DispositionList.OVER, overAckMessage)
            }
            log.trace("#Performance - ParkPatientInit end")
        }
        return dispositionList
    }

    private fun makeMessage(cloverEnvironment: CloverEnv, resourceString: String?): Message {
        return cloverEnvironment.makeMessage(
            resourceString,
            Message.DATA_TYPE,
            Message.PROTOCOL_CLASS,
            false
        )
    }
}


