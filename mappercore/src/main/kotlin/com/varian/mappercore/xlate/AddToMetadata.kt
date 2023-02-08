package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.XLTStrings
import com.quovadx.cloverleaf.upoc.Xpm
import java.util.*

class AddToMetadata : XLTStrings() {
    @Throws(CloverleafException::class)
    override fun xlateStrings(cloverEnv: CloverEnv, xpm: Xpm, inputValues: Vector<*>?): Vector<*> {
        try {
            if (inputValues != null && !inputValues.isEmpty()) {
                val propertyTree = xpm.metadata.userdata
                for (i in 0 until inputValues.size / 2) {
                    val parameterName = inputValues[i * 2]
                    val parameterValue = inputValues[i * 2 + 1]
                    propertyTree.put(parameterName.toString(), parameterValue.toString())
                }
                xpm.metadata.userdata = propertyTree
                cloverEnv.log(1, "AddToMetadata : '" + inputValues[0] + "': " + inputValues[1])
            } else {
                cloverEnv.log(1, "AddToMetadata : unable to fetch event details. Received no values")
            }
        } catch (ex: Exception) {
            cloverEnv.log(2, "AddToMetadata : exception " + ex.message)
        }
        return Vector<Any?>()
    }
}
