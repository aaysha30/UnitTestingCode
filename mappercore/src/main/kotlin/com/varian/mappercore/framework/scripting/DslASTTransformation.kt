package com.varian.mappercore.framework.scripting

import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.ClassHelper.make
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import java.util.*
import kotlin.reflect.KClass

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class DslASTTransformation : ClassCodeExpressionTransformer(), ASTTransformation {
    lateinit var localsourceUnit: SourceUnit

    companion object {
        val HandlerFor_Node: ClassNode = make(HandlerFor::class.java)
    }

    override fun getSourceUnit(): SourceUnit {
        return localsourceUnit
    }

    override fun visit(nodes: Array<out ASTNode>?, source: SourceUnit?) {
        localsourceUnit = source!!
        if (nodes!!.size != 2 || nodes[0] !is AnnotationNode || nodes[1] !is AnnotatedNode) {
            throw GroovyBugError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(*nodes))
        }

        val parent = nodes[1] as AnnotatedNode
        val node = nodes[0] as AnnotationNode
        if (DslASTTransformation.HandlerFor_Node != node.classNode) return

        if (parent is DeclarationExpression) {
            val cNode = parent.declaringClass
            if (!cNode.isScript) {
                addError("Annotation " + "@HandlerFor " + " can only be used within a Script.", parent)
                return
            }
            if (node.classNode == DslASTTransformation.HandlerFor_Node) {
                cNode.addAnnotation(getAnnotation(node, HandlerFor::class))
            }
        }

    }

    fun getAnnotation(node: AnnotationNode, annotationClass: KClass<*>): AnnotationNode? {
        val an = AnnotationNode(ClassNode(annotationClass.java))
        an.setRuntimeRetention(true)
        for ((key, value) in node.members) {
            an.setMember(key, value)
        }
        return an
    }

}