package com.varian.mappercore.framework.scripting

import org.hl7.fhir.instance.model.api.IBaseResource

class ClientDecorCallableReference (
    val clientDecorSearchMethod: ((resourceType: String, value: Array<out Any> ) -> IBaseResource?)? = null,
    val clientDecorRead:((resourceType: String, url: String) -> IBaseResource?) ? = null,
    val resourceType: String = "",
    val url: String = "",
    val value: Array<out Any> = arrayOf()
){

}