package com.varian.mappercore.framework.helper

import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.OperationOutcome
import org.joda.time.DateTime
import java.lang.reflect.UndeclaredThrowableException
import java.util.concurrent.ConcurrentLinkedQueue

class Outcome(private val parser: IParser, private val operationOutcome: OperationOutcome = OperationOutcome(), private val executionTimes: MutableCollection<ExecutionTime> = ConcurrentLinkedQueue()) {
    companion object {
        const val hl7ErrorSystem = "http://varian.com/fhir/hl7exceptions"
    }

    fun getExecutionTimes(): List<ExecutionTime> {
        return executionTimes.toMutableList()
    }

    fun addExecutionTime(description: String, startTime: DateTime, endTime: DateTime) {
        executionTimes.add(ExecutionTime(description, startTime, endTime, endTime.millis.minus(startTime.millis)))
    }

    fun getOperationOutcome(): OperationOutcome {
        return operationOutcome
    }

    fun getIssue(
        issueType: OperationOutcome.IssueType,
        issueSeverity: OperationOutcome.IssueSeverity,
        message: String
    ): OperationOutcome.OperationOutcomeIssueComponent {
        val issue = OperationOutcome.OperationOutcomeIssueComponent()
        issue.severity = issueSeverity
        issue.code = issueType
        issue.details = CodeableConcept().setText(message)
        return issue
    }

    fun addSuccessAckMessage(message: String) {
        val issue = OperationOutcome.OperationOutcomeIssueComponent()
        issue.severity = OperationOutcome.IssueSeverity.INFORMATION
        issue.code = OperationOutcome.IssueType.INFORMATIONAL
        issue.details.codingFirstRep.code = message
        issue.details.codingFirstRep.system = "SUCCESS_ACK_MESSAGE"
        operationOutcome.addIssue(
            issue
        )
    }

    fun addInformation(message: String) {
        operationOutcome.addIssue(
            getIssue(
                OperationOutcome.IssueType.INFORMATIONAL,
                OperationOutcome.IssueSeverity.INFORMATION,
                message
            )
        )
    }

    fun addWarning(message: String) {
        val issue = getIssue(
            OperationOutcome.IssueType.BUSINESSRULE, OperationOutcome.IssueSeverity.WARNING,
            message
        )
        operationOutcome.addIssue(issue)
    }

    fun addWarning(message: String, context: String?) {
        val issue = getIssue(
            OperationOutcome.IssueType.BUSINESSRULE, OperationOutcome.IssueSeverity.WARNING,
            message
        )
        if (!context.isNullOrEmpty()) {
            issue.addContext(context)
        }
        operationOutcome.addIssue(issue)
    }

    fun addWarning(exception: Exception) {
        addWarning(exception, null)
    }

    fun addWarning(exception: Exception, context: String?) {
        val operationOutcomeResult = getOperationOutcome(exception, OperationOutcome.IssueType.BUSINESSRULE)
        operationOutcomeResult?.issue?.forEach {
            if (!context.isNullOrEmpty()) {
                it.addContext(context)
            }
            it.severity = OperationOutcome.IssueSeverity.WARNING
            operationOutcome.addIssue(it)
        }
    }

    fun addError(message: String) {
        val issue = getIssue(
            OperationOutcome.IssueType.EXCEPTION, OperationOutcome.IssueSeverity.ERROR,
            message
        )
        operationOutcome.addIssue(issue)
    }

    fun addError(message: String, context: String?) {
        val issue = getIssue(
            OperationOutcome.IssueType.EXCEPTION, OperationOutcome.IssueSeverity.ERROR,
            message
        )
        if (!context.isNullOrEmpty()) {
            issue.addContext(context)
        }
        operationOutcome.addIssue(issue)
    }

    fun addError(exception: Exception) {
        addError(exception, null)
    }

    fun addError(exception: Exception, context: String?) {
        val operationOutcomeResult = getOperationOutcome(exception, OperationOutcome.IssueType.EXCEPTION)
        operationOutcomeResult.issue?.forEach {
            if (!context.isNullOrEmpty()) {
                it.addContext(context)
            }
            it.severity = OperationOutcome.IssueSeverity.ERROR
            operationOutcome.addIssue(it)
        }
    }

    fun hasError(): Boolean {
        return operationOutcome.hasIssue()
    }

    fun getOperationOutcome(exception: Exception, issueType: OperationOutcome.IssueType): OperationOutcome {
        return if (exception is BaseServerResponseException && exception.operationOutcome != null) {
            exception.operationOutcome as OperationOutcome
        } else {
            val msg =
                if (exception.message.isNullOrEmpty() && exception is UndeclaredThrowableException && exception.undeclaredThrowable.localizedMessage != null) {
                    exception.undeclaredThrowable.localizedMessage
                } else if (exception.message.isNullOrEmpty()) {
                    exception.toString()
                } else {
                    exception.message!!
                }
            val issue = getIssue(issueType, OperationOutcome.IssueSeverity.ERROR, msg)
            OperationOutcome().addIssue(issue)
        }
    }

    fun getErrorOperationOutcome(message: String, context: String): OperationOutcome {
        val opOutcome = OperationOutcome()
        val issue = getIssue(OperationOutcome.IssueType.EXCEPTION, OperationOutcome.IssueSeverity.ERROR, message)
        issue.addContext(context)
        opOutcome.addIssue(issue)
        return opOutcome
    }
}

fun OperationOutcome.OperationOutcomeIssueComponent.addContext(context: String) {
    this.details.addCoding().setCode(context).system = Outcome.hl7ErrorSystem
}

