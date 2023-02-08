package com.varian.mappercore.framework.utility

import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Patient

class PatientUtility {

    fun getIdentifiers(patient: com.varian.fhir.resources.Patient, patientSearchKeys: List<String>): List<Identifier> {
        return patient.identifier.filter { patientSearchKeys.contains(it.system) }
    }

    fun getIdentifier(patient: com.varian.fhir.resources.Patient, patientSearchKey: String): Identifier {
        return patient.identifier.first { patientSearchKey.contains(it.system) }
    }
}