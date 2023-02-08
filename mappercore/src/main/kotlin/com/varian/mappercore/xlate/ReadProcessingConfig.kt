@file:Suppress("unused")

package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.XLTStrings
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.tps.GlobalInit
import java.util.*

class ReadProcessingConfig : XLTStrings() {
    @Throws(CloverleafException::class)
    override fun xlateStrings(cloverEnv: CloverEnv, xpm: Xpm, inputValues: Vector<*>): Vector<*>? {

        if (inputValues.size < 1) {
            cloverEnv.log(
                2,
                "ERROR: Input values are missing. Can not perform operation. Mandatory arguments 1.Key from ProcessingConfig"
            )
            return null
        }

        val returnedValue = GlobalInit.createInstance(
            cloverEnv
        ).getProcessingConfigTable().firstOrNull { it.Key == inputValues[0].toString() }?.Value
        val vector: Vector<String?> = Vector<String?>()
        vector.add(returnedValue)
        return vector
    }
}
