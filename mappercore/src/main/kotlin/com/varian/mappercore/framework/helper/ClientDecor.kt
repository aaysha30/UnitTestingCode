package com.varian.mappercore.framework.helper

import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException
import com.varian.mappercore.client.FhirClient
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Parameters
import org.joda.time.DateTime
import kotlin.jvm.internal.CallableReference

class ClientDecor(private val fhirClient: FhirClient, private val outcome: Outcome) {

    fun create(resource: IBaseResource): MethodOutcome {
        return execute(fhirClient::create, resource, false)!!
    }

    fun create(resource: IBaseResource, context: String): MethodOutcome {
        return execute(fhirClient::create, resource, false, context)!!
    }

    fun createSafely(resource: IBaseResource): MethodOutcome? {
        return execute(fhirClient::create, resource, true)
    }

    fun update(resource: IBaseResource): MethodOutcome {
        return execute(fhirClient::update, resource, false)!!
    }

    fun updateSafely(resource: IBaseResource): MethodOutcome? {
        return execute(fhirClient::update, resource, true)
    }

    fun read(resourceType: String, url: String): IBaseResource {
        val executionTimeDesc = "${resourceType}-read"
        val executionStartTime = DateTime()
        val response = fhirClient.read(resourceType, url)
        val executionEndTime = DateTime()
        outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
        return response
    }

    fun readById(resourceType: String, id: String): IBaseResource {
        val executionTimeDesc = "${resourceType}-read"
        val executionStartTime = DateTime()
        val response = fhirClient.readById(resourceType, id)
        val executionEndTime = DateTime()
        outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
        return response
    }

    fun search(resourceType: String, vararg values: Any?): Bundle {
        val executionTimeDesc = "${resourceType}-search"
        val executionStartTime = DateTime()
        val response = fhirClient.search(resourceType, *values)
        val executionEndTime = DateTime()
        outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
        return response
    }

    fun delete(resource: IBaseResource?): IBaseOperationOutcome? {
        val executionTimeDesc = "${resource?.fhirType()}-search"
        val executionStartTime = DateTime()
        val response = fhirClient.delete(resource)
        val executionEndTime = DateTime()
        outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
        return response
    }

    fun operation(
        resource: IBaseResource,
        operationCall: String,
        resourceType: String,
        parameters: Parameters
    ): IBaseResource? {
        return operation(resource, operationCall, resourceType, parameters, null)
    }

    fun operation(
        resource: IBaseResource,
        operationCall: String,
        resourceType: String,
        parameters: Parameters,
        returnResourceType: IBaseResource?
    ): IBaseResource? {
        val executionTimeDesc = "${resource.fhirType()}-search"
        val executionStartTime = DateTime()
        val response = fhirClient.operation(resource, operationCall, resourceType, parameters, returnResourceType)
        val executionEndTime = DateTime()
        outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
        return response
    }

    private fun execute(
        fhirClientMethod: (resource: IBaseResource) -> MethodOutcome,
        resource: IBaseResource,
        isSafe: Boolean
    ): MethodOutcome? {
        return execute(fhirClientMethod, resource, isSafe, null)
    }

    private fun execute(
        fhirClientMethod: (resource: IBaseResource) -> MethodOutcome?,
        resource: IBaseResource,
        isSafe: Boolean,
        context: String?
    ): MethodOutcome? {
        val executionTimeDesc = "${resource.fhirType()}-${(fhirClientMethod as CallableReference).name}"
        val executionStartTime = DateTime()
        return try {
            val methodOutcome = fhirClientMethod(resource)
            val executionEndTime = DateTime()
            outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
            if (methodOutcome?.operationOutcome != null) {
                (methodOutcome.operationOutcome as OperationOutcome).issue.forEach {
                    it.severity = OperationOutcome.IssueSeverity.WARNING
                    outcome.getOperationOutcome().addIssue(it)
                }
            }
            return methodOutcome
        } catch (exception: Exception) {
            val executionEndTime = DateTime()
            outcome.addExecutionTime(executionTimeDesc, executionStartTime, executionEndTime)
            when (isSafe) {
                true -> {
                    outcome.addWarning(exception, if (!context.isNullOrEmpty()) context else resource.fhirType())
                    return null
                }

                false -> {
                    if (exception is BaseServerResponseException) {
                        val opOutcome = outcome.getOperationOutcome(exception, OperationOutcome.IssueType.EXCEPTION)
                        opOutcome.issue.forEach { it.addContext(if (!context.isNullOrEmpty()) context else resource.fhirType()) }
                        exception.operationOutcome = opOutcome
                    }
                    throw exception
                }
            }
        }
    }
}

