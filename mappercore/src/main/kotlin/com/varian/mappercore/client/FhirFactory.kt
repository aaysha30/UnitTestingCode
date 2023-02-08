package com.varian.mappercore.client

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor
import com.varian.fhir.common.Stu3ContextHelper
import com.varian.mappercore.configuration.Configuration
import com.varian.mappercore.framework.utility.BundleUtility
import com.varian.mappercore.framework.utility.ParametersUtility
import com.varian.mappercore.framework.utility.PatientUtility
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

open class FhirFactory {

    private var fhirClient: FhirClient? = null
    private var fhirContext: FhirContext? = null
    private var parser: IParser? = null
    private var bundleUtility: BundleUtility? = null
    private var parametersUtility: ParametersUtility? = null
    private var patientUtility: PatientUtility? = null
    private var log: Logger = LogManager.getLogger(FhirFactory::class.java)
    private lateinit var tokenManager: TokenManager

    open fun getFhirClient(): FhirClient {
        return fhirClient!!
    }

    open fun setFhirClient(fhirServerUrl: String, configuration: Configuration): FhirClient {
        if (fhirClient == null) {
            fhirClient = getClient(fhirServerUrl, configuration)
        }
        return fhirClient!!
    }

    open fun getClient(fhirServerUrl: String, configuration: Configuration): FhirClient {
        log.info("Initializing fhir client")
        val ctx: FhirContext = getFhirContext()
        ctx.restfulClientFactory.connectTimeout = configuration.connectTimeoutInMilliseconds
        ctx.restfulClientFactory.socketTimeout = configuration.socketTimeoutInMilliseconds
        ctx.restfulClientFactory.serverValidationMode = ServerValidationModeEnum.NEVER
        val client = ctx.newRestfulGenericClient(fhirServerUrl)
        client.encoding = EncodingEnum.JSON

        tokenManager = TokenManager(
                configuration.clientCredentials.authority,
                configuration.clientCredentials.clientId,
                configuration.clientCredentials.clientSecret,
                configuration.clientCredentials.scopes
        )

        client.registerInterceptor(
                BearerTokenAuthInterceptor(
                        tokenManager.getToken()
                )
        )
        log.info("Fhir client initialized successfully")
        return FhirClient(client, tokenManager)
    }

    open fun getFhirContext(): FhirContext {
        if (fhirContext == null) {
            fhirContext = Stu3ContextHelper.forR4()
        }
        return fhirContext!!
    }

    open fun getFhirParser(): IParser {
        if (parser == null) {
            parser = getFhirContext().newJsonParser()
        }
        return parser!!
    }

    open fun getBundleUtility(): BundleUtility {
        if (bundleUtility == null) {
            bundleUtility = BundleUtility()
        }
        return bundleUtility!!
    }

    open fun getParametersUtility(): ParametersUtility {
        if (parametersUtility == null) {
            parametersUtility = ParametersUtility()
        }
        return parametersUtility!!
    }

    open fun getPatientUtility(): PatientUtility {
        if (patientUtility == null) {
            patientUtility = PatientUtility()
        }
        return patientUtility!!
    }

    open fun getTokenManager(): TokenManager {
        return tokenManager
    }
}
