package com.varian.mappercore.tps

import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.DispositionList
import com.quovadx.cloverleaf.upoc.Message
import com.quovadx.cloverleaf.upoc.PropertyTree
import com.varian.fhir.resources.ValueSet
import com.varian.mappercore.client.interfaceapi.dto.FilteredMessage
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.utility.BundleUtility
import com.varian.mappercore.framework.utility.ParametersUtility
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Enumerations.FHIRAllTypes

@Suppress("unused")
class AdtInbound(cloverEnv: CloverEnv, propertyTree: PropertyTree?) : UpocBase(cloverEnv, propertyTree, "ADT_IN") {
    private lateinit var coverageTypeValueSet: ValueSet
    private lateinit var allergyCategoryValueSet: ValueSet
    private lateinit var hospitalDepartmentBundle: Bundle
    init {
        this.readEventScriptParamsCallBack = ::getEventIdAndScriptParamMap
    }

    override fun handleStart(cloverEnv: CloverEnv): DispositionList {
        adtProcessResultSmatDb?.open()
        var inParams = Parameters()
        inParams.addParameter().setName("url").value = StringType("http://hl7.org/fhir/ValueSet/coverage-type")
        val coverageTypeValueSetBundle = globalInit.fhirFactory.getFhirClient()
            .operation(com.varian.fhir.resources.ValueSet(), "\$expand", "ValueSet", inParams, Bundle()) as Bundle
        coverageTypeValueSet = BundleUtility().getValueSet(coverageTypeValueSetBundle) ?: ValueSet()

        inParams = Parameters()
        inParams.addParameter().setName("url").value =
            StringType("http://varian.com/fhir/ValueSet/allergy-intolerance-category")
        val allergyCategoryValueSetBundle = globalInit.fhirFactory.getFhirClient()
            .operation(com.varian.fhir.resources.ValueSet(), "\$expand", "ValueSet", inParams, Bundle()) as Bundle
        allergyCategoryValueSet = BundleUtility().getValueSet(allergyCategoryValueSetBundle) ?: ValueSet()
        hospitalDepartmentBundle = globalInit.fhirFactory.getFhirClient().search("Organization", "type", "dept", "active", "true", "_include", "Organization:partof")
        return super.handleStart(cloverEnv)
    }

    override fun handleShutdown(): DispositionList {
        adtProcessResultSmatDb?.close()
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
        parameters[ParameterConstant.CLOVERLOGGER] = cloverLogger
        parameters[ParameterConstant.MSGMETADATA] = messageMetaData
        parameters[ParameterConstant.ATTACH_HOSPITAL_Departments] = globalInit.configuration.attachHospitalDepartments
        parameters[ParameterConstant.UPDATE_PRIMARY_DEPARTMENT] = globalInit.configuration.updatePrimaryDepartment
        parameters[ParameterConstant.SNAPSHOT_DEPARTMENTS] = globalInit.configuration.snapshotDepartments
        parameters[ParameterConstant.COVERAGE_POLICY_PLAN_TYPE] = coverageTypeValueSet
        parameters[ParameterConstant.ALLERGY_CATEGORY_CODE] = allergyCategoryValueSet
        parameters[ParameterConstant.HOSPITAL_DEPT_BUNDLE] = hospitalDepartmentBundle
        parameters[ParameterConstant.USERDATA] = message.userdata.get("hl7Message").toString()
        parameters[ParameterConstant.parser] = globalInit.parser
        cloverLogger.log(2, "parameters are ready to pass to groovy script", messageMetaData)
        return Pair(scriptContextPair, parameters)
    }

    override fun preRun(message: Message?) {
        val trxIdValue = message!!.userdata?.get("TrxId")
        cloverLogger.log(2, "Trix Id value before message processing is: $trxIdValue", messageMetaData)
        val mergePatientId = message.userdata?.get("mergePatientId")
        cloverLogger.log(2, "Decoding JSON formatted message to bundle.", messageMetaData)
        val decodedHl7Message = globalInit.hl7EncodeCharacterManager.decodeHl7Message(message.content, message.userdata)
        bundle = globalInit.parser.parseResource(decodedHl7Message) as Bundle

        if (trxIdValue != null && trxIdValue == "RELEASE" && mergePatientId != null) {
            cloverLogger.log(1, "Patient merge process is started...", messageMetaData)
            val parameters = globalInit.bundleUtility.getParameters(bundle)
            val patient = globalInit.bundleUtility.getPatient(bundle)
            val patientSearchKeys = globalInit.parametersUtility.getPatientSearchKeys(parameters!!)
            val patientIdentifiersToSearch = globalInit.patientUtility.getIdentifiers(patient!!, patientSearchKeys)
            cloverLogger.log(
                2,
                "patient id for merge patient is: ${patientIdentifiersToSearch.first().value}",
                messageMetaData
            )
            val identifierQuery = TokenClientParam("identifier").exactly()
                .systemAndCode(patientIdentifiersToSearch.first().system, mergePatientId.toString())

            val domainBundle = clientDecor.search("Patient", "identifier", identifierQuery)
            val domainPatient = globalInit.bundleUtility.getPatient(domainBundle)

            parameters.parameter.find { it.name == ParametersUtility.SUBJECT }?.value = StringType("PatientMerge")
            parameters.parameter.find { it.name == ParametersUtility.EVENT }?.value = StringType("ADT^A18")

            val uuid = java.util.UUID.randomUUID().toString()
            val lnk = Patient.PatientLinkComponent().setOther(Reference(uuid)).setType(Patient.LinkType.REPLACES)
            var lnkList = mutableListOf<Patient.PatientLinkComponent>(lnk)
            patient.link = lnkList

            val mergePatient = com.varian.fhir.resources.Patient()
            mergePatient.meta.profile.add(CanonicalType("http://varian.com/fhir/v1/StructureDefinition/Patient"))
            mergePatient.id = uuid
            mergePatient.identifier = domainPatient?.identifier

            val bundleEntry = Bundle.BundleEntryComponent()
            bundleEntry.resource = mergePatient
            bundle.addEntry(bundleEntry)
        }
    }

    override fun postRun(message: Message?, bundle: Bundle?, outcome: Outcome, dispositionList: DispositionList) {
        val userData = message?.userdata
        val trxIdValue = message?.userdata?.get("TrxId")
        cloverLogger.log(2, "Trix Id value after message processing is: $trxIdValue", messageMetaData)

        try {
            if (trxIdValue != null && trxIdValue == "RELEASE") {
                val operationOutcome = outcome.getOperationOutcome()
                val isSuccessFull = !operationOutcome.issue.any { it.severity == OperationOutcome.IssueSeverity.ERROR }
                cloverLogger.log(2, "is successful value is:$isSuccessFull", messageMetaData)
                val releaseResponse = operationOutcome.issue
                    .filter { it.severity != OperationOutcome.IssueSeverity.INFORMATION }
                    .joinToString(System.lineSeparator()) { it.severity.toString() + ": " + it.details.text }
                cloverLogger.log(2, "operation result is: $releaseResponse", messageMetaData)

                val filteredMessage = FilteredMessage()
                filteredMessage.messageRecordSer = userData?.get("messageRecordSer").toString().toLong()
                filteredMessage.patientRecordSer = userData?.get("patientRecordSer").toString().toLong()
                filteredMessage.status = if (isSuccessFull) "P" else "F"
                filteredMessage.errorString = releaseResponse

                val statusResult = globalInit.parkService.updateMessageStatus(filteredMessage)
                cloverLogger.log(2, "Message Updated status: $statusResult", messageMetaData)
            } else {
                val applicationAckValue = userData?.get("SendApplicationAck")
                cloverLogger.log(1, "application Ack Value is: $applicationAckValue", messageMetaData)
                cloverLogger.log(2, "Application Ack is being prepared.", messageMetaData)
                val ackMsgBundle = buildAckMessageFallBack(bundle, outcome)
                ackMsgBundle.entry.removeIf {  it == null || it.resource == null || (it.resource.fhirType() != FHIRAllTypes.MESSAGEHEADER.toCode() && it.resource.fhirType() != FHIRAllTypes.OPERATIONOUTCOME.toCode())  }
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
        } catch (exception: Exception) {
            if (!exception.message.isNullOrEmpty()) {
                outcome.addError(exception.message!!)
            }
            cloverLogger.log(0, "Error occurred while processing message into ARIA: ${exception.message}", messageMetaData)
            cloverLogger.log(0, "error occurred at : ${exception.stackTraceToString()}", messageMetaData)
        } finally {
            log.trace("#Performance - ADT_In process message end")
        }
    }

    private fun buildAckMessageFallBack(bundle: Bundle?, outcome: Outcome): Bundle {
        return AckResponse.getAckMessage(bundle, outcome, messageMetaData, cloverLogger)
    }
}
