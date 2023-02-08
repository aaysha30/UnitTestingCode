package com.varian.mappercore.tps

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.DispositionList
import com.quovadx.cloverleaf.upoc.Message
import com.quovadx.cloverleaf.upoc.PropertyTree
import com.varian.fhir.resources.ValueSet
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.Outcome
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome

@Suppress("unused")
class FHIRClient(cloverEnv: CloverEnv, propertyTree: PropertyTree?) :
    FHIRClientUpocBase(cloverEnv, propertyTree, "FHIR_Client") {
    private lateinit var coverageTypeValueSet: ValueSet
    private lateinit var allergyCategoryValueSet: ValueSet
    protected var log1: Logger = LogManager.getLogger(FHIRClientUpocBase::class.java)

    init {
        //this.readEventScriptParamsCallBack = ::getEventIdAndScriptParamMap
    }

    override fun handleStart(cloverEnv: CloverEnv): DispositionList {
        log.trace("Inside handleStart: Initializing...")
        return super.handleStart(cloverEnv)
    }

    override fun handleShutdown(): DispositionList {
        log.trace("Inside handleShutdown: Initializing...")
        return super.handleShutdown()
    }

    fun getEventIdAndScriptParamMap(message: Message): Pair<Pair<String, List<String>>, Map<String, Any>> {
        val parametersResource = globalInit.bundleUtility.getParameters(bundle)!!
        var subjects = globalInit.parametersUtility.getSubject(parametersResource)?.split(",")
        if (subjects.isNullOrEmpty()) {
            subjects = mutableListOf()
        }
        val scriptContextPair = Pair(
            globalInit.parametersUtility.getSource(parametersResource)!!,
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
        parameters[ParameterConstant.USER] = globalInit.user
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger
        parameters[ParameterConstant.MSGMETADATA] = messageMetaData
        parameters[ParameterConstant.ATTACH_HOSPITAL_Departments] = globalInit.configuration.attachHospitalDepartments
        parameters[ParameterConstant.UPDATE_PRIMARY_DEPARTMENT] = globalInit.configuration.updatePrimaryDepartment
        parameters[ParameterConstant.SNAPSHOT_DEPARTMENTS] = globalInit.configuration.snapshotDepartments
        parameters[ParameterConstant.COVERAGE_POLICY_PLAN_TYPE] = coverageTypeValueSet
        parameters[ParameterConstant.ALLERGY_CATEGORY_CODE] = allergyCategoryValueSet
        parameters[ParameterConstant.parser] = globalInit.parser

        //globalInit.parser.encodeResourceToString()
        cloverLogger.log(2, "parameters are ready to pass to groovy script", messageMetaData)
        return Pair(scriptContextPair, parameters)
    }

    override fun preRun(message: Message?) {

    }

    override fun postRun(message: Message?, bundle: Bundle?, outcome: Outcome, dispositionList: DispositionList) {
//        val userData = message?.userdata
//        val trxIdValue = message?.userdata?.get("TrxId")
//        CloverLogger.log(2, "Trix Id value after message processing is: $trxIdValue", messageMetaData)
//        try {
//                val applicationAckValue = userData?.get("SendApplicationAck")
//                CloverLogger.log(1, "application Ack Value is: $applicationAckValue", messageMetaData)
//                CloverLogger.log(2, "Application Ack is being prepared.", messageMetaData)
//                val ackMsgBundle = buildAckMessageFallBack(bundle, outcome)
//                ackMsgBundle.entry.removeIf {  it == null || it.resource == null || (it.resource.fhirType() != FHIRAllTypes.MESSAGEHEADER.toCode() && it.resource.fhirType() != FHIRAllTypes.OPERATIONOUTCOME.toCode())  }
//                val parser = globalInit.parser
//                parser.setPrettyPrint(globalInit.configuration.enableSMARTFormatting)
//                val ackMsgPayload = parser.encodeResourceToString(ackMsgBundle)
//                val ackMessage: Message? =
//                    cloverEnv?.makeMessage(ackMsgPayload, Message.DATA_TYPE, Message.PROTOCOL_CLASS, false)
//                ackMessage?.userdata = message?.userdata
//                ackMessage?.metadata?.driverctl = message?.metadata?.driverctl
//                adtProcessResultSmatDb?.insert(ackMessage)
//                dispositionList.add(DispositionList.OVER, ackMessage)
//
//        } catch (exception: Exception) {
//            if (!exception.message.isNullOrEmpty()) {
//                outcome.addError(exception.message!!)
//            }
//            CloverLogger.log(0, "Error occurred while processing message into ARIA: ${exception.message}", messageMetaData)
//            CloverLogger.log(0, "error occurred at : ${exception.stackTraceToString()}", messageMetaData)
//        }
    }

    private fun buildAckMessageFallBack(bundle: Bundle?, outcome: Outcome): Bundle {
        return AckResponse.getAckMessage(bundle, outcome, messageMetaData, cloverLogger)
    }
}
