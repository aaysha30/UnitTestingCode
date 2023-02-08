package com.varian.mappercore.framework.scripting

import com.varian.mappercore.framework.helper.MessageMetaData
import java.lang.Exception
import java.util.*

interface IScripts {
    @Throws(Exception::class)
    fun getHandlerFor(source: String?, subject: String?): Optional<ScriptInformation?>?

    fun getAllHandlers(): List<ScriptInformation>?

    @Throws(Exception::class)
    fun run(parameters: Map<String, Any>, sc: ScriptInformation): Any?
}