package com.varian.mappercore.tps

import ca.uhn.hl7v2.util.idgenerator.FileBasedHiLoGenerator
import ca.uhn.hl7v2.util.idgenerator.IDGenerator
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import org.hl7.fhir.r4.model.*

class AckResponse {
    companion object {
        private const val IS_SUCCESS_URL = "http://hl7.org/fhir/StructureDefinition/isSuccess"
        public const val MESSAGE_CONTROL_ID_EXT_URL = "http://hl7.org/fhir/StructureDefinition/messageControlId"
        private const val MESSAGE_CONTROL_ID_FOR_APPLICATION_ACK =
            "http://hl7.org/fhir/StructureDefinition/mshMessageControlId"
        private const val SUCCESS_MESSAGE = "Message processed successfully."
        private const val FAIL_MESSAGE = "Failed to process message."
        private const val PARTIAL_SUCCESS_MESSAGE = "Message processed successfully with warnings."
        private const val BUNDLE_PARSE_ERROR_MESSAGE = "Failed to parse message."

        fun getAckMessage(bundleResource: Bundle?, outcome: Outcome, messageMetaData: MessageMetaData, cloverLogger: CloverLogger): Bundle {
            val operationOutcome = outcome.getOperationOutcome()
            val ackOutcome = OperationOutcome()

            if (bundleResource == null) {
                ackOutcome.addIssue(
                    outcome.getIssue(
                        OperationOutcome.IssueType.BUSINESSRULE,
                        OperationOutcome.IssueSeverity.ERROR,
                        FAIL_MESSAGE
                    )
                )
                ackOutcome.addIssue(
                    outcome.getIssue(
                        OperationOutcome.IssueType.BUSINESSRULE,
                        OperationOutcome.IssueSeverity.ERROR,
                        BUNDLE_PARSE_ERROR_MESSAGE
                    )
                )
                outcome.getOperationOutcome().issue.forEach { ackOutcome.addIssue(it) }
                return Bundle().addEntry(Bundle.BundleEntryComponent().setResource(ackOutcome))
            } else {
                val messageHeader =
                    bundleResource.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.MESSAGEHEADER.toCode() }?.resource as MessageHeader
                val isErrorPresent = operationOutcome.issue.stream()
                    .anyMatch { i: OperationOutcome.OperationOutcomeIssueComponent -> i.severity == OperationOutcome.IssueSeverity.ERROR }
                val isWarningPresent = operationOutcome.issue.stream()
                    .anyMatch { i: OperationOutcome.OperationOutcomeIssueComponent -> i.severity == OperationOutcome.IssueSeverity.WARNING }
                val isSuccessAckMessagePresent = operationOutcome.issue.stream()
                    .anyMatch { i: OperationOutcome.OperationOutcomeIssueComponent ->
                        i.details.coding.stream().anyMatch { it.system == "SUCCESS_ACK_MESSAGE" }
                    }

                val randomIDGenerator: IDGenerator = FileBasedHiLoGenerator()
                messageHeader.addExtension(
                    Extension(
                        MESSAGE_CONTROL_ID_FOR_APPLICATION_ACK,
                        StringType(randomIDGenerator.id.toString().padStart(9, '0'))
                    )
                )

                messageHeader.addExtension(Extension(IS_SUCCESS_URL, BooleanType(!isErrorPresent)))
                val ackMessage: String
                val issueSeverity: OperationOutcome.IssueSeverity
                when {
                    isSuccessAckMessagePresent -> {
                        val message = operationOutcome.issue.flatMap { it.details.coding }
                            .first { it.system == "SUCCESS_ACK_MESSAGE" }
                        ackMessage = message.code
                        issueSeverity = OperationOutcome.IssueSeverity.INFORMATION
                        operationOutcome.issue.removeIf { it.details.coding.any { coding -> coding.system == "SUCCESS_ACK_MESSAGE" } }
                    }
                    isErrorPresent -> {
                        ackMessage = FAIL_MESSAGE
                        issueSeverity = OperationOutcome.IssueSeverity.ERROR
                    }
                    isWarningPresent -> {
                        ackMessage = PARTIAL_SUCCESS_MESSAGE
                        issueSeverity = OperationOutcome.IssueSeverity.WARNING
                    }
                    else -> {
                        ackMessage = SUCCESS_MESSAGE
                        issueSeverity = OperationOutcome.IssueSeverity.INFORMATION
                    }
                }
                cloverLogger.log(2, ackMessage, messageMetaData)
                ackOutcome.addIssue(
                    outcome.getIssue(
                        OperationOutcome.IssueType.BUSINESSRULE,
                        issueSeverity,
                        ackMessage
                    )
                )
                outcome.getOperationOutcome().issue.forEach { ackOutcome.addIssue(it) }
                bundleResource.addEntry(Bundle.BundleEntryComponent().setResource(ackOutcome))
                return bundleResource
            }
        }
    }
}