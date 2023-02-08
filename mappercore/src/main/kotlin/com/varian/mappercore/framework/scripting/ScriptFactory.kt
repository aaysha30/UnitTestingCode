package com.varian.mappercore.framework.scripting

class ScriptFactory(val threadName: String, dslScripts: Map<String, List<String>>, val localSite: String) {

    val scripts: IScripts

    init {
        scripts = Scripts(threadName, dslScripts, localSite)
    }
}
