package com.varian.mappercore.framework.scripting

import com.fasterxml.jackson.databind.ObjectMapper
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.constant.RelativePathConstant
import com.varian.mappercore.framework.helper.FileOperation
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import groovy.lang.Binding
import groovy.lang.Script
import groovy.util.GroovyScriptEngine
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.time.StopWatch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.joda.time.DateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors

class Scripts(val threadName: String, val dslScripts: Map<String, List<String>>, val localSite: String) : IScripts {
    private var scriptEngine: GroovyScriptEngine? = null
    private val scriptInformations: MutableList<ScriptInformation> = ArrayList()

    companion object {
        var LOG: Logger = LogManager.getLogger(Scripts::class.java)
    }

    init {
        val createStarted = StopWatch.createStarted()

        setScriptEngine()
        loadScripts()
        createStarted.stop()
        //println("Time for loading scripts: ${createStarted.time} msec")
        LOG.trace("Time taken to load all groovy scripts is : ${createStarted.time} milliseconds")
        createStarted.reset()
    }

    override fun getHandlerFor(source: String?, subject: String?): Optional<ScriptInformation?>? {
        var handlers = scriptInformations.stream().filter {
            it.handlerFor.source == source && it.handlerFor.subject == subject
        }.collect(Collectors.toList())

        if (handlers.count() > 1 && handlers.stream().filter { x -> !x.isMaster }.findFirst().isPresent) {
             return handlers.stream().filter { x -> !x.isMaster }.findFirst()
        }
        return handlers.stream().findFirst()
    }

    override fun getAllHandlers(): List<ScriptInformation>? {
        return scriptInformations.stream().collect(Collectors.toList())
    }

    @Throws(Exception::class)
    override fun run(parameters: Map<String, Any>, scriptInformation: ScriptInformation): Any? {
        val binding = Binding()
        var messageMetaData = parameters[ParameterConstant.MSGMETADATA] as MessageMetaData
        LOG.trace("Binding required params to groovy script")
        parameters.forEach(binding::setVariable)
        binding.setVariable(ParameterConstant.SCRIPT_PATH, scriptInformation.scriptPath)
        binding.setVariable(ParameterConstant.LOG, LOG)
        if (!parameters.containsKey(ParameterConstant.SCRIPTS)) {
            binding.setVariable(ParameterConstant.SCRIPTS, this)
        }

        val script: Script =
            scriptEngine?.loadScriptByName(scriptInformation.scriptPath)?.getDeclaredConstructor()
                ?.newInstance() as Script
        script.binding = binding
        LOG.trace("Running Script :" + scriptInformation.handlerFor.subject)
        val executionStartTime = DateTime()
        try{
            LOG.trace("#Performance - Execute ${scriptInformation.scriptPath} begins")
            return script.run()
        }finally {
            LOG.trace("#Performance - Execute ${scriptInformation.scriptPath} ends")
            val executionEndTime = DateTime()
            if(parameters.containsKey(ParameterConstant.OUTCOME)){
                (parameters[ParameterConstant.OUTCOME] as Outcome).addExecutionTime("Script: ${scriptInformation.handlerFor.subject}", executionStartTime, executionEndTime)
            }
        }
    }

    private fun setScriptEngine() {
        try {
            val listOfGroovies = mutableListOf(
                FileOperation.getDslDirectoryPath()
            )

            val localDSLDir = Paths.get(localSite, "java_uccs\\dsl")
            if (Files.exists(localDSLDir)) {
                listOfGroovies.add(localDSLDir.toString())
            }

            scriptEngine = GroovyScriptEngine(listOfGroovies.toTypedArray())
            val compilerConfiguration = CompilerConfiguration()
            compilerConfiguration.scriptBaseClass = "com.varian.mappercore.framework.scripting.ScriptBase"
            addImports(compilerConfiguration)

            compilerConfiguration.addCompilationCustomizers(object :
                CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {
                override fun call(source: SourceUnit, context: GeneratorContext?, classNode: ClassNode?) {
                    /*if (classNode != null) {
                           val sourceFile = FileOperation.getFile(source.name)
                          val parent = sourceFile.parentFile.name
                          val grandParent = sourceFile.parentFile.parentFile.name
                          classNode.name = java.lang.String.format("%s-%s-%s", grandParent, parent, classNode.name)
                    }*/
                }
            })

            scriptEngine!!.config = compilerConfiguration
        } catch (exception: Exception) {
            //CloverLogger.log(0, "Error occurred while setting a groovy engine: $exception", MessageMetaData())
            throw RuntimeException("Error setting up Groovy Engine: ", exception)
        }
    }

    private fun loadScripts() {
        try {
            var localFileNames = mutableListOf<String>()
            LOG.trace("Loading groovy script from dsl dir...")
            val list = dslScripts.getOrDefault(threadName, emptyList())
            val localDslDirPath = Paths.get(localSite, "java_uccs\\dsl")
            if (Files.exists(localDslDirPath)) {
                val localFiles = FileUtils.listFiles(
                    File(localDslDirPath.toString()),
                    arrayOf(RelativePathConstant.SCRIPT_EXTENSION),
                    true
                )
                localFiles.stream().forEach { file -> load(file, false) }
                localFileNames = localFiles.map { file -> file.name }.toMutableList()
            }
            val filesToLoad = list.stream().map { directory ->
                FileUtils.listFiles(
                    File(FileOperation.getFullPath(directory)),
                    arrayOf(RelativePathConstant.SCRIPT_EXTENSION),
                    true
                )
            }.collect(Collectors.toList()).flatten().filter { x -> !localFileNames.contains(x.name) }
            filesToLoad.forEach { file: File ->
                load(file, true)
            }
        } catch (ex: Exception) {
            //CloverLogger.log(0, "Error occurred while loading the groovy scripts from dsl.", MessageMetaData())
            throw RuntimeException("Error loading DSL Scripts: ", ex)
        }
    }

    private fun load(file: File, isMaster: Boolean) {
        try {
            val scriptLoadingTimer = StopWatch.createStarted();

            val scriptName = file.toURI().toString()
            LOG.trace("Loading $scriptName script...")
            val loadedScriptClass: Class<*> =
                scriptEngine?.loadScriptByName(scriptName) as Class<*>
            val annotations = loadedScriptClass.annotations
            val handler = Arrays.stream(annotations)
                .filter { it.annotationClass == HandlerFor::class }
                .findFirst()
            if (handler.isPresent) {
                val handlerFor = handler.get() as HandlerFor
                scriptInformations.add(ScriptInformation(handlerFor, scriptName, isMaster))
            }
            scriptLoadingTimer.stop()
            LOG.trace("Time taken by ${file.name} to load is: ${scriptLoadingTimer.time} milliseconds")
            println(scriptName + " : " + scriptLoadingTimer.time);
        } catch (ex: Exception) {
            LOG.error("Error occurred while loading the ${file.toURI()}.")
            throw RuntimeException(String.format("Unable to load DSL Transform %s", file.toURI()), ex)
        }
    }

    private fun addImports(compilerConfiguration: CompilerConfiguration) {
        val importCustomizer = ImportCustomizer()
        importCustomizer.addImport("HandlerFor", "com.varian.mappercore.framework.scripting.HandlerFor")
        importCustomizer.addImport("ScriptRunParam", "com.varian.mappercore.framework.scripting.ScriptRunParam")
        importCustomizer.addImport("ClientDecorCallableReference", "com.varian.mappercore.framework.scripting.ClientDecorCallableReference")
        val importPackages: HashMap<String, List<String>> =
            ObjectMapper().readValue(
                FileOperation.getImportFile(),
                HashMap::class.java
            ) as HashMap<String, List<String>>

        importPackages["import_packages"]?.forEach {
            importCustomizer.addStarImports(it)
        }

        importPackages["import_static_packages"]?.forEach {
            importCustomizer.addStaticStars(it)
        }
        compilerConfiguration.addCompilationCustomizers(importCustomizer)
    }
}
