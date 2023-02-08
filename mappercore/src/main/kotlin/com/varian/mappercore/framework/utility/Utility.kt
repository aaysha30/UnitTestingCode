package com.varian.mappercore.framework.utility

abstract class Utility {
    open fun getValue(type: org.hl7.fhir.r4.model.Type?): String? {
        return type?.toString()
    }
}