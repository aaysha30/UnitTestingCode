package com.varian.mappercore.framework.scripting

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.Outcome
import groovy.lang.Script
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import java.util.concurrent.ConcurrentLinkedQueue

abstract class ScriptBase : Script() {
    protected var log: Logger = LogManager.getLogger(ScriptBase::class.java)
    open fun run(source: String, subject: String, parameters: Map<String, Any>): Any? {
        val scripts = parameters[ParameterConstant.SCRIPTS] as Scripts?
        val scriptInformation = scripts!!.getHandlerFor(source, subject)
        return scripts.run(
            parameters,
            scriptInformation!!.orElseThrow { ResourceNotFoundException("Handler for source:$source,subject:$subject is not present") }!!
        )
    }

    open fun executeAsync(params: java.util.ArrayList<ScriptRunParam>) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val jobs = params.map {
                    launch {
                        try {
                            run(it.src!!, it.sub!!, it.bindMap!!)
                        } catch (ex: Exception) {
                            (it.bindMap?.get("outcome") as? Outcome?)?.addWarning(
                                ex.message ?: "error occurred.",
                                it.context
                            )
                            log.error("error occurred while executing script ${it.sub}. ${ex.message}")
                            log.debug(ex.stackTraceToString())
                        }
                    }
                }.toList()
                jobs.joinAll()
            }
        }
    }

    open fun executeClientDecorAsync(params: java.util.ArrayList<ClientDecorCallableReference>): List<IBaseResource> {
        val bundle: MutableCollection<IBaseResource> = ConcurrentLinkedQueue()
        runBlocking {
            withContext(Dispatchers.IO) {
                val jobs = params.map {
                    async {
                        if (it.clientDecorSearchMethod != null) {
                            it.clientDecorSearchMethod?.invoke(it.resourceType, it.value)
                        } else if (it.clientDecorRead != null) {
                            it.clientDecorRead?.invoke(it.resourceType, it.url)
                        } else {
                            return@async Bundle()
                        }
                    }
                }.toList()
                jobs.awaitAll().forEach {
                    if (it != null && it is IBaseResource) {
                        bundle.add(it)
                    }
                }
            }
        }
        return bundle.toMutableList()
    }
}