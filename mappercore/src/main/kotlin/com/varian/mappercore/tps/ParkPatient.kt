@file:Suppress("unused")

package com.varian.mappercore.tps

import ca.uhn.fhir.rest.gclient.TokenClientParam
import ca.uhn.hl7v2.model.v251.message.ADT_A39
import ca.uhn.hl7v2.model.v251.message.ADT_A43
import ca.uhn.hl7v2.model.v251.segment.PID
import ca.uhn.hl7v2.parser.Parser
import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.utility.BundleUtility
import com.varian.mappercore.framework.utility.HL7Utility
import com.varian.mappercore.framework.utility.ParametersUtility
import com.varian.mappercore.framework.utility.PatientUtility
import com.varian.mappercore.helper.sqlite.SqliteUtility
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.Identifier

class ParkPatient(cloverEnv: CloverEnv, propertyTree: PropertyTree?) :
    UpocBase(cloverEnv, propertyTree, "PARK_PATIENT") {
    private val PID = "PID"
    private val MARITAL_STATUS = "MaritalStatus"
    private val SEX = "Sex"
    private var hl7Parser: Parser
    var hl7Utility: HL7Utility
    private var adtHl7ToFhirSmatDb: SMATDB?

    init {
        log.trace("#Performance - ParkPatient Init begins")
        this.readEventScriptParamsCallBack = ::getEventIdAndScriptParamMap
        hl7Parser = globalInit.hl7Parser
        hl7Utility = HL7Utility(globalInit)
        adtHl7ToFhirSmatDb = SMATDB(cloverEnv, globalInit.configuration.adtInSCOutSMATDbName)
        log.trace("#Performance - ParkPatient Init ends")
    }

    override fun handleRun(message: Message?): DispositionList {
        log.trace("#Performance - ParkPatient process message begins")
        adtHl7ToFhirSmatDb?.insert(message)
        try {
            return super.handleRun(message)
        }finally{
            log.trace("#Performance - ParkPatient process message ends")
        }
    }

    override fun handleStart(cloverEnv: CloverEnv): DispositionList {
        adtHl7ToFhirSmatDb?.open()
        adtProcessResultSmatDb?.open()
        return super.handleStart(cloverEnv)
    }

    override fun handleShutdown(): DispositionList {
        adtHl7ToFhirSmatDb?.close()
        adtProcessResultSmatDb?.close()
        return super.handleShutdown()
    }

    override fun canRun(message: Message): Boolean {
        log.trace("#Performance - ParkPatient.isPatientPark() begins")
        val trxIdValue = message.userdata?.get("TrxId")
        if (trxIdValue != null && trxIdValue == "RELEASE") {
            return false
        }
        val userData = message.userdata
        val processingConfigList = globalInit.getProcessingConfigTable()
        var patientSelectFlag = userData.get("PatientSelectFlag")?.toString()
        if (patientSelectFlag.isNullOrEmpty()) {
            patientSelectFlag = processingConfigList.find { f -> f.Key == "PatientSelectFlag" }?.Value
        }
        if (patientSelectFlag.isNullOrEmpty()) {
            patientSelectFlag = "0"
        }
        var parkPatient = false
        when (patientSelectFlag) {
            "2" -> {
                parkPatient = true
                bundle = globalInit.parser.parseResource(Bundle::class.java, message.content)
                validatePatientId()
            }
            "1" -> {
                bundle = globalInit.parser.parseResource(Bundle::class.java, message.content)
                val patientIdentifiersToSearch = validatePatientId()
                val bundleDomain = clientDecor.search(
                    "Patient",
                    "identifier",
                    TokenClientParam("identifier").exactly().systemAndCode(
                        patientIdentifiersToSearch.first().system,
                        patientIdentifiersToSearch.first().value?.trim()
                    )
                )
                val patientDomain = BundleUtility().getPatient(bundleDomain)
                parkPatient = patientDomain == null
            }
        }
        cloverLogger.log(2, "PatientSelectFlag value is $patientSelectFlag", messageMetaData)
        log.trace("#Performance - ParkPatient.isPatientPark() ends")
        return parkPatient

    }

    private fun validatePatientId(): List<Identifier> {
        val patientSearchKeys =
            ParametersUtility().getPatientSearchKeys(BundleUtility().getParameters(bundle)!!)
        val patient = BundleUtility().getPatient(bundle)
        val patientIdentifiersToSearch = PatientUtility().getIdentifiers(patient!!, patientSearchKeys)
        if (patientIdentifiersToSearch.isNullOrEmpty()) {
            throw Exception("PatientIdentifier is empty or not configured correctly in patient matching table")
        }
        return patientIdentifiersToSearch
    }

    override fun preRun(message: Message?) {
        cloverLogger.log(3, "replacing encoding characters in message", messageMetaData)
        val decodedMessage = globalInit.hl7EncodeCharacterManager.decodeHl7Message(message!!.content, message.userdata)
        bundle = globalInit.parser.parseResource(decodedMessage) as Bundle
    }

    fun getEventIdAndScriptParamMap(message: Message): Pair<Pair<String, List<String>>, Map<String, Any>> {
        val eventid = Pair("HL7", mutableListOf("ParkPatientRefactor"))

        val parameters = mutableMapOf<String, Any>()
        val hl7 = message.userdata.get("hl7Message").toString()
        val hl7message = hl7Parser.parse(hl7)!!
        parameters[ParameterConstant.MESSAGE_CLOVERLEAF_ID] = message.metadata?.mid!!
        parameters[ParameterConstant.PROCESS_NAME] = cloverEnv?.processName!!
        parameters[ParameterConstant.CLOVERLOGGER] = cloverLogger
        parameters[ParameterConstant.HL7_MESSAGE] = hl7
        parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
        parameters[ParameterConstant.PATIENT_KEY_MAPPING_CONFIG] = globalInit.getPatientMatching()
        parameters[ParameterConstant.PARK_SERVICE] = globalInit.parkService
        parameters[ParameterConstant.TRACE_ID] = message.metadata?.mid?.joinToString(".") { it -> it.toString() }!!
        parameters[ParameterConstant.SITE_NAME] = cloverEnv?.siteName!!
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters[ParameterConstant.MSGMETADATA] = messageMetaData

        if (hl7message is ADT_A39) {
            if (hl7message.patient.pid.administrativeSex.value != null) {
                val inValue = hl7message.patient.pid.administrativeSex.value
                val sex = getLookupdata(hl7message, SEX, inValue)
                hl7message.patient.pid.administrativeSex.value = sex ?: inValue
                parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
            }
            if (hl7message.patient.pid.maritalStatus.identifier.value != null) {
                val inValue = hl7message.patient.pid.maritalStatus.identifier.value
                val maritalStatus = getLookupdata(hl7message, MARITAL_STATUS, inValue)
                hl7message.patient.pid.maritalStatus.identifier.value = maritalStatus ?: inValue
                parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
            }
        } else if (hl7message is ADT_A43) {
            if (hl7message.patient.pid.administrativeSex.value != null) {
                val inValue = hl7message.patient.pid.administrativeSex.value
                val sex = getLookupdata(hl7message, SEX, inValue)
                hl7message.patient.pid.administrativeSex.value = sex ?: inValue
                parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
            }
            if (hl7message.patient.pid.maritalStatus.identifier.value != null) {
                val inValue = hl7message.patient.pid.maritalStatus.identifier.value
                val maritalStatus = getLookupdata(hl7message, MARITAL_STATUS, inValue)
                hl7message.patient.pid.maritalStatus.identifier.value = maritalStatus ?: inValue
                parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
            }
        } else {
            if ((hl7message.get("PID") as PID).administrativeSex?.value != null) {
                val inValue = (hl7message.get("PID") as PID).administrativeSex.value
                val sex = getLookupdata(hl7message, SEX, inValue)
                (hl7message.get("PID") as PID).administrativeSex.value = sex ?: inValue
                parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
            }
            if ((hl7message.get("PID") as PID).maritalStatus?.identifier?.value != null) {
                val inValue = (hl7message.get("PID") as PID).maritalStatus.identifier.value
                val maritalStatus = getLookupdata(hl7message, MARITAL_STATUS, inValue)
                (hl7message.get("PID") as PID).maritalStatus.identifier.value = maritalStatus ?: inValue
                parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7message
            }
        }

        return Pair(eventid, parameters)
    }

    private fun getLookupdata(hl7message: ca.uhn.hl7v2.model.Message, tableName: String, inValue: String): String? {
        val values: MutableMap<String, String> = HashMap()
        values[XlateConstant.SQLITE_IN_VALUE] = inValue
        values[XlateConstant.SQLITE_LOCAL_IN] = XlateConstant.IN_VALUE
        values[XlateConstant.SQLITE_MASTER_IN] = XlateConstant.IN_VALUE
        values[XlateConstant.SQLITE_LOCAL_OUT] = XlateConstant.OUT_VALUE
        values[XlateConstant.SQLITE_MASTER_OUT] = XlateConstant.OUT_VALUE
        values[XlateConstant.SQLITE_SEQUENCE] = XlateConstant.SEQUENCE_LOCAL_MASTER
        values[XlateConstant.SQLITE_TABLE] = tableName
        return SqliteUtility.getLookUpValue(values, globalInit.localConnection, globalInit.masterConnection)
    }

    override fun postRun(message: Message?, bundle: Bundle?, outcome: Outcome, dispositionList: DispositionList) {
        val userData = message?.userdata
        try {
            outcome.addSuccessAckMessage("PatientSelectFlag attribute = 1, message sent for the parking.")
            val applicationAckValue = userData?.get("SendApplicationAck")
            cloverLogger.log(1, "application Ack Value is: $applicationAckValue", messageMetaData)
            val ackMsgBundle = buildAckMessageFallBack(bundle, outcome)
            ackMsgBundle.entry.removeIf { it.resource.fhirType() != Enumerations.FHIRAllTypes.MESSAGEHEADER.toCode() && it.resource.fhirType() != Enumerations.FHIRAllTypes.OPERATIONOUTCOME.toCode()  }
            val parser = globalInit.parser
            parser.setPrettyPrint(globalInit.configuration.enableSMARTFormatting)
            val ackMsgPayload = parser.encodeResourceToString(ackMsgBundle)
            val ackMessage: Message? =
                cloverEnv?.makeMessage(ackMsgPayload, Message.DATA_TYPE, Message.PROTOCOL_CLASS, false)
            ackMessage?.userdata = message?.userdata
            ackMessage?.metadata?.driverctl = message?.metadata?.driverctl
            adtProcessResultSmatDb?.insert(ackMessage)
            dispositionList.add(DispositionList.OVER, ackMessage)
        } catch (exception: Exception) {
            if (!exception.message.isNullOrEmpty()) {
                outcome.addError(exception.message!!)
            }
            cloverLogger.log(0, "Error occurred processing message into ARIA: ${exception.message}", messageMetaData)
            cloverLogger.log(0, "error occurred at : ${exception.stackTraceToString()}", messageMetaData)
        }finally {
            log.trace("#Performance - ParkPatient process message end")
        }
    }

    private fun buildAckMessageFallBack(bundle: Bundle?, outcome: Outcome): Bundle {
        return AckResponse.getAckMessage(bundle, outcome, messageMetaData, cloverLogger)
    }
}
