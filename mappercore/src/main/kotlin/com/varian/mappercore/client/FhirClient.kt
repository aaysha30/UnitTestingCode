package com.varian.mappercore.client

import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor
import ca.uhn.fhir.rest.gclient.DateClientParam
import ca.uhn.fhir.rest.gclient.ICriterion
import ca.uhn.fhir.rest.gclient.StringClientParam
import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.tps.UpocBase
import io.mockk.impl.log.LogLevel
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.IdType
import org.hl7.fhir.r4.model.Parameters
import java.util.*

open class FhirClient(private var client: IGenericClient, private var tokenManager: ITokenManager) {
    var log: Logger = LogManager.getLogger(FhirClient::class.java)
    private fun setToken() {
        val token = tokenManager.getToken()
        client.interceptorService.allRegisteredInterceptors.forEach {
            if (it is BearerTokenAuthInterceptor) {
                it.token = token
            }
        }
    }

    open fun create(resource: IBaseResource?): MethodOutcome {
        log.trace("#Performance CALL_FHIR_CREATE - Begins - ${resource?.fhirType()}")
        return try {
            val resourceString = client.fhirContext.newJsonParser().encodeResourceToString(resource).replace(XlateConstant.ACTIVE_NULL_LITERAL, "")
            val modifiedResource = client.fhirContext.newJsonParser().parseResource(resourceString)
            setToken()
            log.trace("#Performance CALL_FHIR_CREATE - calling FHIR4Aria ${resource?.fhirType()}.create() begins")

            client.create().resource(modifiedResource).execute()
        } catch (e: Exception) {
            throw e
        } finally {
            log.trace("#Performance CALL_FHIR_CREATE - calling FHIR4Aria ${resource?.fhirType()}.create() end")
        }
    }

    open fun update(resource: IBaseResource?): MethodOutcome {
        return try {
            log.trace("#Performance CALL_FHIR_UPDATE - Begins - ${resource?.fhirType()}")
            val resourceString = client.fhirContext.newJsonParser().encodeResourceToString(resource).replace(XlateConstant.ACTIVE_NULL_LITERAL, "")
            val modifiedResource = client.fhirContext.newJsonParser().parseResource(resourceString)
            setToken()
            log.trace("#Performance CALL_FHIR_UPDATE - calling FHIR4Aria ${resource?.fhirType()}.update() begins")
            client.update().resource(modifiedResource).execute()
        } catch (e: java.lang.Exception) {
            throw e
        } finally {
            log.trace("#Performance CALL_FHIR_UPDATE - calling FHIR4Aria ${resource?.fhirType()}.update() end")
        }
    }

    open fun read(resourceType: String, url: String): IBaseResource {
        return try {
            log.trace("#Performance CALL_FHIR_READ - Begins - $resourceType")
            setToken()
            log.trace("#Performance CALL_FHIR_READ - calling FHIR4Aria $resourceType.read() begins")
            client.read().resource(resourceType).withUrl(url).execute()
        } catch (e: Exception) {
            throw e
        } finally {
            log.trace("#Performance CALL_FHIR_READ - calling FHIR4Aria $resourceType.read() end")
        }
    }

    open fun readById(resourceType: String, id: String?): IBaseResource {
        return try {
            log.trace("#Performance CALL_FHIR_READByID - Begins - $resourceType")
            setToken()
            log.trace("#Performance CALL_FHIR_READByID - calling FHIR4Aria $resourceType.readById() begins")
            client.read().resource(resourceType).withId(id).execute()
        } catch (e: Exception) {
            throw e
        } finally {
            log.trace("#Performance CALL_FHIR_READByID - calling FHIR4Aria $resourceType.readById() end")
        }
    }

    open fun search(resourceType: String, vararg values: Any?): Bundle {
        log.trace("#Performance CALL_FHIR_SEARCH - Begins - $resourceType")
        var q = client.search<IBaseBundle>().forResource(resourceType)
        for (i in 0 until values.size / 2) {
            val parameterName = values[i * 2] as String
            val parameterValue = values[i * 2 + 1]
            if (parameterValue == null || String::class.java.isAssignableFrom(parameterValue.javaClass)) {
                q = q.where(StringClientParam(parameterName).matchesExactly().value(parameterValue as String))
            } else if (Date::class.java.isAssignableFrom(parameterValue.javaClass)) {
                q = q.where(DateClientParam(parameterName).exactly().second(parameterValue as Date))
            } else if (ICriterion::class.java.isAssignableFrom(parameterValue.javaClass)) {
                q = q.where(parameterValue as ICriterion<*>)
            }
        }
        return try {
            setToken()
            log.trace("#Performance CALL_FHIR_SEARCH - calling FHIR4Aria $resourceType.search() begins")
            return q.execute() as Bundle
        } catch (e: java.lang.Exception) {
            throw e
        }finally {
            log.trace("#Performance CALL_FHIR_SEARCH - calling FHIR4Aria $resourceType.search() end")
        }
    }

    open fun delete(resource: IBaseResource?): IBaseOperationOutcome? {
        return try {
            log.trace("#Performance CALL_FHIR_DELETE - Begins - ${resource?.fhirType()}")
            setToken()
            log.trace("#Performance CALL_FHIR_DELETE- calling FHIR4Aria ${resource?.fhirType()}.delete() begins")
            client.delete().resource(resource).execute()
        } catch (e: java.lang.Exception) {
            throw e
        } finally {
            log.trace("#Performance CALL_FHIR_DELETE - calling FHIR4Aria ${resource?.fhirType()}.delete() end")
        }
    }

    open fun operation(
        resource: IBaseResource,
        operationCall: String,
        resourceType: String,
        parameters: Parameters,
        returnResourceType: IBaseResource?
    ): IBaseResource? {
        log.trace("#Performance CALL_FHIR_OPERATION - Begins - ${resource.fhirType()}")
        setToken()
        val operationName = if (resource.idElement?.idPart != null) client.operation().onInstance(
            IdType(
                resourceType,
                resource.idElement?.idPart
            )
        ) else client.operation().onType(resource::class.java)
        val r = operationName.named(operationCall)
            .withParameters(parameters)
        if (returnResourceType != null) {
            r.returnResourceType(returnResourceType::class.java)
        }
        try {
            log.trace("#Performance CALL_FHIR_OPERATION - calling FHIR4Aria ${resource.fhirType()}.operation() begins")
            return r.execute()
        }finally {
            log.trace("#Performance CALL_FHIR_OPERATION - calling FHIR4Aria ${resource.fhirType()}.operation() end")
        }
    }
}
