package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.XLTStrings
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.helper.sqlite.SqliteUtility
import com.varian.mappercore.tps.GlobalInit
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

class LookUp : XLTStrings() {
    private var cloverEnv: CloverEnv? = null

    @Throws(CloverleafException::class)
    override fun xlateStrings(cloverEnv: CloverEnv, xpm: Xpm, inputValues: Vector<*>): Vector<*>? {
        val globalInit = GlobalInit.createInstance(cloverEnv)
        val localConnection = globalInit.localConnection
        val masterConnection = globalInit.masterConnection
        this.cloverEnv = cloverEnv
        var returnedValue: String? = ""

        if (inputValues.size < 2) {
            cloverEnv.log(
                2,
                "ERROR : Input values are missing. Can not perform look up operation. Search value and Table Name is mandatory."
            )
        } else if (inputValues[0] == null || inputValues[0].toString().isEmpty()) {
            returnedValue = ""
        } else {
            val values = getInputValuesInMap(inputValues)
            returnedValue = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        }
        val vector: Vector<String?> = Vector<String?>()
        vector.add(returnedValue)
        return vector
    }

    private fun getInputValuesInMap(inputValues: Vector<*>): Map<String, String> {
        var values: MutableMap<String, String> = HashMap()
        values[XlateConstant.SQLITE_IN_VALUE] = inputValues[0].toString()
        for (i in 1 until inputValues.size) {
            if (inputValues[i].toString().contains(XlateConstant.COLON_SPLITTER)) {
                val key: String =
                    inputValues[i].toString().split(XlateConstant.COLON_SPLITTER).toTypedArray().get(0).uppercase()
                val value: String = inputValues[i].toString().split(XlateConstant.COLON_SPLITTER).toTypedArray().get(1)
                values[key] = value
            } else {
                val message = "ERROR : Input values must be entered in a key value pair having a ':' in between"
                cloverEnv!!.log(2, message)
                throw IllegalArgumentException(message)
            }
        }
        values = verifyAndSetValues(values)
        return values
    }

    private fun verifyAndSetValues(values: MutableMap<String, String>): MutableMap<String, String> {
        values.putIfAbsent(XlateConstant.SQLITE_IF_NOT_MATCHED, XlateConstant.ORIGINAL)
        values.putIfAbsent(XlateConstant.SQLITE_SEQUENCE, XlateConstant.SEQUENCE_LOCAL_MASTER)
        val sequenceList = Stream.of(
            *values[XlateConstant.SQLITE_SEQUENCE]!!.split(XlateConstant.COMMA_SPLITTER).dropLastWhile { it.isEmpty() }
                .toTypedArray()).collect(Collectors.toList())
        /* TODO: if(sequenceList.size()>2){
            //throw some error
        }*/if (sequenceList.contains(XlateConstant.LOCAL_SITE)) {
            values.putIfAbsent(XlateConstant.SQLITE_LOCAL_IN, XlateConstant.IN_VALUE)
            values.putIfAbsent(XlateConstant.SQLITE_LOCAL_OUT, XlateConstant.OUT_VALUE)
        }
        if (sequenceList.contains(XlateConstant.MASTER_SITE)) {
            values.putIfAbsent(XlateConstant.SQLITE_MASTER_IN, XlateConstant.IN_VALUE)
            values.putIfAbsent(XlateConstant.SQLITE_MASTER_OUT, XlateConstant.OUT_VALUE)
        }
        return values
    }
}
