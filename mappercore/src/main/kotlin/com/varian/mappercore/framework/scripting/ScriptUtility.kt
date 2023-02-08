package com.varian.mappercore.framework.scripting

import com.varian.fhir.resources.Patient
import org.hl7.fhir.r4.model.DomainResource
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Type

class ScriptUtility {

    fun find(url: String, resource: DomainResource): Type {
        return resource.getExtensionByUrl(url).value
    }

    fun getIdentifier(patient: Patient, system: String): Identifier? {
        return patient.identifier.find { it -> it.system.equals(system) }
    }
}