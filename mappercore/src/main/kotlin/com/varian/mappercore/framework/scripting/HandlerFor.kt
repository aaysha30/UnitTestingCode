package com.varian.mappercore.framework.scripting

import org.codehaus.groovy.transform.GroovyASTTransformationClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.TYPE)
@GroovyASTTransformationClass("com.varian.mappercore.framework.scripting.DslASTTransformation")
annotation class HandlerFor(val source: String, val subject: String)