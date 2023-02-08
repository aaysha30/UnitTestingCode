/*package com.varian.mappercore.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import com.codahale.metrics.annotation.Timed
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.microsoft.applicationinsights.TelemetryClient
import com.varian.mappercore.client.FhirClientFactory
import com.varian.mappercore.configuration.Configuration
import okhttp3.OkHttpClient
import org.hl7.fhir.r4.model.OperationOutcome
import java.io.File
import java.util.concurrent.TimeUnit
import javax.ws.rs.*
import javax.ws.rs.core.Response
import com.varian.fhir.common.Stu3ContextHelper
import com.varian.mappercore.configuration.ConfigurationLoader
import com.varian.mappercore.framework.helper.FileOperation
import com.varian.mappercore.framework.scripting.Scripts

class Hl7MessageProvider() {
    private val fhirClientFactory: FhirClientFactory = FhirClientFactory()
    private val r4Context: FhirContext = FhirContext.forR4()
    private val ariaFhirContext: FhirContext = Stu3ContextHelper.forR4()
    private val telemetryClient: TelemetryClient = TelemetryClient()
    private val cacheLoader: Cache<String, FhirClientFactory.TokenDetails> = CacheBuilder.newBuilder().build()
    private val client: OkHttpClient = OkHttpClient().newBuilder()
        .connectTimeout(Int.MAX_VALUE.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Int.MAX_VALUE.toLong(), TimeUnit.MILLISECONDS)
        .build()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val configuration: Configuration
    private val scripts: Scripts

    init {
        val configurationLoader = ConfigurationLoader()
        configuration = configurationLoader.configuration

        val dslPath = FileOperation.getExecutingPath() + File.separator + configuration.dslRelativePath
        val dsl = File(dslPath)
        var dslScripts = listOf<File>()
        if (dsl.exists()) {
            dslScripts = dsl.walk().filter { i -> i.name.endsWith(".dsl.kts") }.toList()
        }
        scripts = Scripts()
        scripts.compileScripts(dslScripts)
    }

    @POST
    @Timed
    fun process(
        sender: String?,
        subject: String,
        severity: String?,
        message: String?
    ): Response {

        val ariaFhirClient =
            fhirClientFactory[
                    ariaFhirContext,
                    configuration,
                    configuration.ariaFhirServerUrl.toString(),
                    telemetryClient,
                    "ariaFhir",
                    cacheLoader,
                    client,
                    objectMapper]

        val autoCreate = configuration.autoCreate

        val scriptParameters = mapOf(
            "message" to message,
            "fhirContext" to ariaFhirContext,
            "ariaFhirClient" to ariaFhirClient,
            "scripts" to scripts,
            "references" to mutableMapOf<String, String>(),
            "autoCreate" to autoCreate,
            "scriptOutcome" to OperationOutcome(),
            "severity" to OperationOutcome.IssueSeverity.fromCode(
                if (severity.isNullOrBlank()) "information" else severity
            ),
            "path" to "resource"
        )

        val evaluatedResult = scripts.evaluate(subject, scriptParameters)

        val methodOutcome = evaluatedResult as MethodOutcome
        val result = methodOutcome.operationOutcome as OperationOutcome
        val successfulMessage = result.addIssue()
        successfulMessage.code = OperationOutcome.IssueType.BUSINESSRULE
        successfulMessage.diagnostics = "Message processed successfully"
        successfulMessage.severity = OperationOutcome.IssueSeverity.INFORMATION
        return Response.ok(r4Context.newJsonParser().encodeResourceToString(result)).build()
    }
}*/