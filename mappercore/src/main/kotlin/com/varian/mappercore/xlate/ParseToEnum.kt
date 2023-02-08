package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.XLTStrings
import com.quovadx.cloverleaf.upoc.Xpm
import java.util.*

class ParseToEnum : XLTStrings() {
    override fun xlateStrings(clovEnv: CloverEnv, xpm: Xpm, inputValue: Vector<*>): Vector<*> {
        val vector: Vector<String?> = Vector<String?>()
        if (inputValue.isEmpty()) {
            vector.add(null)
        } else if (inputValue.size == 1) {
            vector.add(inputValue[0].toString())
        } else if (inputValue.size == 2) {
            val clazz = Class.forName(inputValue[1].toString())
            try {
                clazz.getDeclaredMethod("fromCode", String::class.java).invoke(null, inputValue[0].toString())
                vector.add(inputValue[0].toString())
            } catch (ex: Exception) {
                vector.add(null)
            }
        }
        return vector
    }
}