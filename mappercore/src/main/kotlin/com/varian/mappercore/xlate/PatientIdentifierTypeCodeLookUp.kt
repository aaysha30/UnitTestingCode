package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.XLTStrings
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.tps.GlobalInit
import java.util.*

@Suppress("unused")
class PatientIdentifierTypeCodeLookUp : XLTStrings() {

    @Throws(CloverleafException::class)
    override fun xlateStrings(cloverEnv: CloverEnv, xpm: Xpm, inputValues: Vector<*>): Vector<*>? {
        val globalInit = GlobalInit.createInstance(
            cloverEnv
        )

        if (inputValues.size == 1) {
            //when only one value is send through xlate match with the master site FHIR ARIAID and send the System value required for FHIR
            var ariaIdValue: String = inputValues[0] as String
            var returnedValue =
                globalInit.identifierTable.filter { it.InValue == ariaIdValue }.map { it.OutValue }.firstOrNull()
            val vector: Vector<String?> = Vector<String?>()
            vector.add(returnedValue)
            return vector
        }

        if (inputValues.size != 3) {
            cloverEnv.log(
                2,
                "ERROR: Input values are missing. Can not perform Patient Identifier LookUp operation. Mandatory arguments 1.IdentifierValue 2.Segment 3.Field"
            )
            return null
        }
        val identifierValue: String = inputValues[0] as String
        val segment: String = inputValues[1] as String
        val field: String = inputValues[2] as String

        val returnedValue = globalInit.getPatientMatching().filter {
            ((it.IdentifierValue.isNullOrEmpty() && identifierValue.isEmpty()) || it.IdentifierValue == identifierValue)
                    && it.Segment == segment && it.Field == field
        }.map { it.FhirAriaId }.firstOrNull()
        val vector: Vector<String?> = Vector<String?>()
        vector.add(returnedValue)
        return vector
    }
}
