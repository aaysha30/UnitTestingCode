package com.varian.mappercore.tps

import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.constant.AriaConnectConstant
import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.helper.sqlite.SqliteUtility
import java.nio.file.Paths
import java.sql.DriverManager

class ErrorDbProc : TPS {
    private var skipErrorDbFlag: String
    private var propertyTree: PropertyTree

    companion object {
        const val SHORT_ACK_MESSAGE_KEY = "messageControlIdAndProcess"
        const val ERROR_MESSAGE_KEY = "ErrorACKMessage"
        const val USER_DEFINED_ERROR_KEY = "FILTER_ERROR"
    }

    constructor(
        cloverEnv: CloverEnv, propertyTree: PropertyTree?
    ) : super(
        cloverEnv,
        propertyTree
    ) {
        Thread.currentThread().contextClassLoader = ErrorDbProc::class.java.classLoader
        this.propertyTree = propertyTree!!
        val mappedSqliteDbName: String =
            cloverEnv.tableLookup(AriaConnectConstant.INTERFACES_TABLE, cloverEnv.processName)

        val localSqliteRelativePath = Paths.get(cloverEnv.siteDirName, mappedSqliteDbName).toString()
        val localSqliteConnectionString = "jdbc:sqlite:$localSqliteRelativePath"
        val localConnection = DriverManager.getConnection(localSqliteConnectionString)

        val values: MutableMap<String, String> = HashMap()
        values[XlateConstant.SQLITE_IF_NOT_MATCHED] = XlateConstant.ORIGINAL
        values[XlateConstant.SQLITE_SEQUENCE] = XlateConstant.SEQUENCE_LOCAL
        values[XlateConstant.SQLITE_LOCAL_IN] = AriaConnectConstant.PROCESSING_CONFIG_KEY
        values[XlateConstant.SQLITE_LOCAL_OUT] = AriaConnectConstant.PROCESSING_CONFIG_VALUE
        values[XlateConstant.SQLITE_TABLE] = AriaConnectConstant.PROCESSING_CONFIG_TABLE
        values[XlateConstant.SQLITE_IN_VALUE] = AriaConnectConstant.SKIP_ERROR_DB_CONFIG
        skipErrorDbFlag = SqliteUtility.getLookUpValue(values, localConnection, null)!!
    }

    override fun process(cloverEnv: CloverEnv, context: String?, mode: String?, message: Message?): DispositionList? {
        return when (mode) {
            "start" -> handleStart(cloverEnv)
            "run" -> handleRun(message!!)
            "shutdown" -> handleShutdown()
            "time" -> handleTime(message)
            else -> DispositionList()
        }
    }

    open fun handleRun(message: Message): DispositionList {
        val dispositionList = DispositionList()
        when (skipErrorDbFlag) {
            "0" -> {
                dispositionList.add(DispositionList.ERROR, message)
            }
            "1" -> {
                filterDefinedError(dispositionList, message)
            }
            "3" -> dispositionList.add(DispositionList.KILL, message)
            else -> modifyMessage(dispositionList, message)
        }

        return dispositionList
    }

    private fun modifyMessage(dispositionList: DispositionList, message: Message) {
        val error = message.userdata?.get(SHORT_ACK_MESSAGE_KEY)?.toString()
        if (error != null) {
            message.content = error
        }
        dispositionList.add(DispositionList.ERROR, message)
    }

    private fun filterDefinedError(dispositionList: DispositionList, message: Message) {
        //kill message only if patient not found or custom user passed error
        val messageFilter = this.propertyTree.getBranch(USER_DEFINED_ERROR_KEY)
        val errorFilterList = mutableListOf<String>()
        if (messageFilter != null) {
            for (key in messageFilter.keys()) {
                val errMessage = messageFilter.get(key.toString())?.toString()
                if (errMessage != null) {
                    errorFilterList.add(errMessage)
                }
            }
        }

        val err = message.userdata.get(ERROR_MESSAGE_KEY)?.toString()
        if (err != null && err.contains("PATIENT_NOT_FOUND")) {
            dispositionList.add(DispositionList.KILL, message)
        } else if (errorFilterList.any { err != null && err.contains(it) }) {
            dispositionList.add(DispositionList.KILL, message)
        } else {
            dispositionList.add(DispositionList.ERROR, message)
        }
    }

    open fun handleStart(cloverEnv: CloverEnv): DispositionList {
        try {

        } catch (exception: Exception) {
            return DispositionList()
        }
        return DispositionList()
    }

    open fun handleShutdown(): DispositionList {
        return DispositionList()
    }

    open fun handleTime(message: Message?): DispositionList {
        val dispositionList = DispositionList()
        dispositionList.add(DispositionList.CONTINUE, message)
        return dispositionList
    }
}