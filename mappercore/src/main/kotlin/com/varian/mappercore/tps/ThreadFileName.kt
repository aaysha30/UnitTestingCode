package com.varian.mappercore.tps

import com.quovadx.cloverleaf.upoc.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ThreadFileName(cloverEnv: CloverEnv, propertyTree: PropertyTree?) :
    TPS(cloverEnv, propertyTree) {
    override fun process(cloverEnv: CloverEnv?, p1: String?, p2: String?, message: Message?): DispositionList {
        val dispositionList = DispositionList()
        if (message == null) {
            return dispositionList
        }
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        val formatted = current.format(formatter) + ".HL7"
        val obfile = PropertyTree()
        obfile.put("OBFILE", formatted)
        val fileset = PropertyTree()
        fileset.put("FILESET", obfile)
        //val output = message.metadata.set("DRIVERCTL", "{FILESET { {OBFILE $formatted} } }")
        message.metadata.setDriverctl(fileset)
        dispositionList.add(DispositionList.CONTINUE, message)
        return dispositionList
    }
}