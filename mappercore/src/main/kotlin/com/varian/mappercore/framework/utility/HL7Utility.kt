package com.varian.mappercore.framework.utility

import ca.uhn.fhir.rest.gclient.ICriterionInternal
import ca.uhn.fhir.rest.gclient.TokenClientParam
import ca.uhn.hl7v2.AcknowledgmentCode
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.Severity
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.model.v251.datatype.CX
import ca.uhn.hl7v2.model.v251.message.*
import ca.uhn.hl7v2.model.v251.segment.MRG
import ca.uhn.hl7v2.model.v251.segment.PID
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.helper.sqlite.PatientMatching
import com.varian.mappercore.tps.GlobalInit

class HL7Utility(val globalInit: GlobalInit) {

        private var hl7Parser = globalInit.hl7Parser
        private var ariaFhirClient = globalInit.fhirFactory.getFhirClient()
        private var patientMatchingTable = globalInit.getPatientMatching()

        fun isPatientExistInARIA(strHl7Message: String, messageMetaData: MessageMetaData = MessageMetaData()): Boolean {
            val hl7Message = hl7Parser.parse(strHl7Message)
            val idValueList: MutableList<String> = mutableListOf<String>()
            var mrg: MRG? = null
            var mrgSegmentFound = false
            var isPatientExist = false
            if (hl7Message is ADT_A39) {
                mrg = hl7Message.patient.mrg
                mrgSegmentFound = true
            } else if (hl7Message is ADT_A43) {
                mrg = hl7Message.patient?.mrg
                mrgSegmentFound = true
            } else if (hl7Message is ADT_A18 || hl7Message is ADT_A30 || hl7Message is ADT_A50 || hl7Message is ADT_A45) {
                mrg = hl7Message.get("MRG") as MRG
                mrgSegmentFound = true
            }
            if (mrgSegmentFound) {
                val mrgSearchingConfig =
                    patientMatchingTable.first { it.Segment == "MRG" && it.IsUsedForFinding == "1" }
                val field = mrg?.getField((mrgSearchingConfig.Field as String).toInt())
                val idValue = field?.map { m -> m as CX }
                    ?.first { f -> f.cx5_IdentifierTypeCode.value == mrgSearchingConfig.IdentifierValue }?.cx1_IDNumber?.value
                if (idValue != null) {
                    idValueList.add(idValue)
                }
            }

            val searchingConfig = patientMatchingTable.first { it.Segment == "PID" && it.IsUsedForFinding == "1" }
            if (hl7Message is ADT_A39) {
                isPatientExist =
                    checkIfPatientExistsInA39TypeMessage(hl7Message, searchingConfig, idValueList, messageMetaData)
            } else if (hl7Message is ADT_A43) {
                isPatientExist =
                    checkIfPatientExistsInA43TypeMessage(hl7Message, searchingConfig, idValueList, messageMetaData)
            } else {
                val segments = hl7Message?.getAll("PID")
                segments?.map { seg -> seg as PID }?.forEach { pid ->
                    val field = pid.getField((searchingConfig.Field as String).toInt())
                    val idValue = field.map { m -> m as CX }
                        .first { f -> f.cx5_IdentifierTypeCode.value == searchingConfig.IdentifierValue }.cx1_IDNumber.value
                    idValueList.add(idValue)
                }
                isPatientExist = isPatientExist(searchingConfig.FhirAriaId, idValueList,messageMetaData)
            }
            if (isPatientExist) return true
            return false
        }

        private fun checkIfPatientExistsInA43TypeMessage(
            hl7Message: ADT_A43,
            searchingConfig: PatientMatching,
            idValueList: MutableList<String>,
            messageMetaData: MessageMetaData  = MessageMetaData()
        ): Boolean {
            val cnt = hl7Message.patientReps
            (0 until cnt).forEach { i ->
                val field = hl7Message.getPATIENT(i).pid.getField((searchingConfig.Field as String).toInt())
                val idValue = field.map { m -> m as CX }
                    .first { f -> f.cx5_IdentifierTypeCode.value == searchingConfig.IdentifierValue }.cx1_IDNumber.value
                idValueList.add(idValue)
            }
            return isPatientExist(searchingConfig.FhirAriaId, idValueList, messageMetaData)
        }

        private fun checkIfPatientExistsInA39TypeMessage(
            hl7Message: ADT_A39,
            searchingConfig: PatientMatching,
            idValueList: MutableList<String>,
            messageMetaData: MessageMetaData = MessageMetaData()
        ): Boolean {
            val cnt = hl7Message.patientReps
            (0 until cnt).forEach { i ->
                val field = hl7Message.getPATIENT(i).pid.getField((searchingConfig.Field as String).toInt())
                val idValue = field.map { m -> m as CX }
                    .first { f -> f.cx5_IdentifierTypeCode.value == searchingConfig.IdentifierValue }.cx1_IDNumber.value
                idValueList.add(idValue)
            }
            return isPatientExist(searchingConfig.FhirAriaId, idValueList, messageMetaData)
        }

    fun getHL7Ack(hl7Message: String, severity: Severity, textMessage: String): String {
        return getHL7Ack(hl7Parser.parse(hl7Message), severity, textMessage)
    }
        fun getHL7Ack(hl7Message: Message, severity: Severity, textMessage: String): String {
            var ack: Message? = null
            if (textMessage == "CommitAckRequired") {
                ack = hl7Message.generateACK(AcknowledgmentCode.CA, null) as ACK
                setACKValues(ack)
            } else {
                ack = hl7Message.generateACK() as ACK
                setACKValues(ack)
                ack.err.severity.value = severity.code
                ack.err.userMessage.value = textMessage
            }
            return ack.toString()
        }

        fun getHL7Ack(strHl7Message: String, ackCode: AcknowledgmentCode): ACK {
            val hl7Message = hl7Parser.parse(strHl7Message)
            var ack: Message? = null
            ack = hl7Message.generateACK(ackCode, null) as ACK
            setACKValues(ack)
            return ack
        }

    fun getHL7Ack(hl7Message: Message, ackCode: AcknowledgmentCode): ACK {
        val ack = hl7Message.generateACK(ackCode, null) as ACK
        setACKValues(ack)
        return ack
    }



        private fun setACKValues(ack: ACK) {
            ack.msh.msh7_DateTimeOfMessage.time.value = ack.msh.msh7_DateTimeOfMessage.time.value.split(".")[0]
            ack.msh.msh9_MessageType.msg2_TriggerEvent.value = ""
            ack.msh.msh9_MessageType.msg3_MessageStructure.value = ""
            ack.msh.msh10_MessageControlID.value = ack.msh.msh10_MessageControlID.value.toString().padStart(9, '0')
        }

        fun getErrorAck(
            strHl7Message: String,
            acknowledgmentCode: AcknowledgmentCode,
            hl7Exception: HL7Exception
        ): String {
            val hl7Message = hl7Parser.parse(strHl7Message)
            return hl7Message.generateACK(acknowledgmentCode, hl7Exception).toString()
        }

        private fun isPatientExist(fhirAriaId: String?, idValue: List<String>?, messageMetaData: MessageMetaData = MessageMetaData()): Boolean {
            val values = mutableListOf<Any>()
            val tokenParam = idValue?.map { TokenClientParam("identifier").exactly().systemAndCode(fhirAriaId, it) }
            tokenParam?.forEach { v ->
                values.add((v as ICriterionInternal).parameterName)
                values.add(v)
                values
            }
            val bundle = ariaFhirClient.search("Patient", *values.toTypedArray())
            val resource = bundle.entry?.find { it.resource?.fhirType() == "Patient" }?.resource
            return resource != null
        }
}