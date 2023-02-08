package com.varian.mappercore.framework.helper

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.varian.mappercore.constant.AriaConnectConstant
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class CloverLogger private constructor(private var cloverEnv: CloverEnv) {
    private var applog: Logger = LogManager.getLogger(CloverLogger::class.java)
    companion object {
        private var debugConfigValue: Int? = null
        fun initCLoverLogger(cloverEnv: CloverEnv) : CloverLogger {
            if(debugConfigValue == null){
                debugConfigValue = getDebugConfigValue(cloverEnv)
            }
            cloverEnv.setLogLevel(debugConfigValue!!)
            return CloverLogger(cloverEnv)
        }

        private fun getDebugConfigValue(cloverEnv: CloverEnv): Int {
            return when (cloverEnv.tableLookup(AriaConnectConstant.SITE_CONFIG_TBL, AriaConnectConstant.LOG_LEVEL_KEY)?.toString()) {
                "0" -> "0".toInt()
                "1" -> "2".toInt()
                "2" -> "1".toInt()
                "3" -> "3".toInt()
                else -> "1".toInt()
            }
        }
    }


    /**
     * Below ALL log func would print log for given log level.
     * To print log with message data(unique if for patient, message) for each line we would recommend to use log(logLevel, log, messageMetaData)/ log(logLevel, log, messageCtrId, patientId)
     * below value can be used for first input param 'logLevel':
     *  0 ->  should be used to print only error and exception - means debug mode is OFF.
     *  1 ->  should be used to print informative. it will also include the log level = 0 logs
     *  2 ->  should be used to print the logs which will be needed while debugging, means debug mode is ON.
     * */

    /**
     * Recommend to call below fun if patient and message unique id is present.
     * */
    fun log(logLevel: Int, log: String, messageMetaData: MessageMetaData = MessageMetaData()) {
        cloverLog(
            logLevel,
            "- UniqueID:${messageMetaData.messageCtrId} - PatientId:${messageMetaData.patientId} - $log"
        )
    }

    /**
     * Recommend to call below fun if patient and message unique id is present but can create a instance of MessageMetaData.
     * */
    fun log(logLevel: Int, log: String, messageCtrId: String = "", patientId: String = "") {
        cloverLog(logLevel, "- UniqueID:$messageCtrId - PatientId:$patientId - $log")
    }

    /**
     * we can use below log for testing purpose
     * */
    fun log(logLevel: Int, log: String) {
        cloverLog(logLevel, log)
    }

    private fun cloverLog(logLevel: Int, log: String) {
        cloverEnv.log(logLevel, log)
    }

    /**
     * we can add cloverleaf exception just like log above So that we can use it to print error in debug = 0 mode and also check if exception has log level passed as 1 and current log level for clover env is set to 0 then what would happen
     * */
}

/*
 * public class MessageMetaData constructor(patientId: String = "",messageCtrId: String = "") {
 * var patientId: String = patientId
 * var messageCtrId: String = messageCtrId
}
 */

public class MessageMetaData {
    var patientId: String? = ""
    var messageCtrId: String = ""

    constructor(patientId: String?, messageCtrId: String) {
        this.patientId = patientId
        this.messageCtrId = messageCtrId
    }

    constructor()
}