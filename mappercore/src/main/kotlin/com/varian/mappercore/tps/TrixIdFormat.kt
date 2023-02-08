package com.varian.mappercore.tps

import ca.uhn.hl7v2.model.v251.segment.MSH
import ca.uhn.hl7v2.parser.Parser
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.Message
import com.quovadx.cloverleaf.upoc.PropertyTree
import com.quovadx.cloverleaf.upoc.Trxid
import com.varian.mappercore.constant.AriaConnectConstant.Companion.IGNORE_ROUTE

@Suppress("unused")
class TrixIdFormat(cloverEnv: CloverEnv, propertyTree: PropertyTree?) : Trxid(cloverEnv, propertyTree) {

    private var hl7Parser: Parser

    init {
        Thread.currentThread().contextClassLoader = TrixIdFormat::class.java.classLoader
        hl7Parser = GlobalInit.createInstance(cloverEnv).hl7Parser
    }

    override fun process(cloverEnv: CloverEnv?, message: Message?, propertyTree: PropertyTree?): String {
        val userData = message!!.userdata
        val trxIdValue = userData.get("TrxId")
        return if (trxIdValue != null && trxIdValue == IGNORE_ROUTE) {
            trxIdValue.toString()
        } else {
            val hl7Message = hl7Parser.parse(message.content)
            val msh: MSH = hl7Message.get("MSH") as MSH
            msh.msh9_MessageType.msg1_MessageCode.toString() + "_" + msh.msh9_MessageType.msg2_TriggerEvent.toString()
        }
    }
}