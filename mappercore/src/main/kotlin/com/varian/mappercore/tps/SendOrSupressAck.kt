package com.varian.mappercore.tps

import com.quovadx.cloverleaf.upoc.*

class SendOrSupressAck(cloverEnv: CloverEnv, propertyTree: PropertyTree?) :
    TPS(cloverEnv, propertyTree) {
    override fun process(cloverEnv: CloverEnv?, p1: String?, p2: String?, message: Message?): DispositionList {
        val dispositionList = DispositionList()
        if (message == null) {
            return dispositionList
        }
        val userData = message.userdata
        val applicationAckValue = userData?.get("SendApplicationAck")
        when (applicationAckValue) {
            "1" -> {
                dispositionList.add(DispositionList.CONTINUE, message)
                cloverEnv?.log(2, "Sent Application Ack")
            }
            "0" -> {
                dispositionList.add(DispositionList.KILL, message)
                cloverEnv?.log(2, "Sent Commit/Immediate ACK")
            }
        }
        return dispositionList;
    }
}