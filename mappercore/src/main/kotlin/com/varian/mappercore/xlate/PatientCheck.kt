package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.XLTString
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.framework.utility.HL7Utility
import com.varian.mappercore.tps.GlobalInit

@Suppress("unused")
class PatientCheck : XLTString() {
    init {
        Thread.currentThread().contextClassLoader = PatientCheck::class.java.classLoader
    }

    override fun xlateString(cloverEnv: CloverEnv, xpm: Xpm, parameters: String?): Any {
        val hL7Utility = HL7Utility(GlobalInit.createInstance(cloverEnv))
        val userData = xpm.metadata?.userdata
        val msg = userData?.get("hl7Message")?.toString()
        if (msg == null) {
            val message = "Missing HL7 message in metadata"
            cloverEnv.log(2, message)
            return false
        }

        return hL7Utility.isPatientExistInARIA(msg)
    }
}
