package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.XLTString
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.tps.GlobalInit

class PatientSearchKeys : XLTString() {
    override fun xlateString(cloverEnv: CloverEnv, xpm: Xpm?, parameters: String?): Any {
        return GlobalInit.createInstance(cloverEnv).getPatientSearchKeys()
    }
}
