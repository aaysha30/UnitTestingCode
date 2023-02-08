package com.varian.mappercore.framework.utility

import com.jayway.jsonpath.JsonPath
import com.quovadx.cloverleaf.upoc.Message
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import net.minidev.json.JSONArray
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


class FilterUtility {
    protected var log: Logger = LogManager.getLogger(FilterUtility::class.java)


    fun shouldResourceBeJsonpathFiltered(
        listOfJsonPathFilterEntries: String?,
        jsonObject: Message?,
        messageMetadata: MessageMetaData,
        cloverLogger: CloverLogger
    ): Boolean {
        return try {
            if (listOfJsonPathFilterEntries.isNullOrEmpty()) {
                cloverLogger.log(2, "json path filter is not provided", messageMetadata)
                true
            } else {
                val filteredEntries: JSONArray = JsonPath.read(jsonObject?.content, listOfJsonPathFilterEntries)
                cloverLogger.log(2, "value of filteredEntries is: $filteredEntries", messageMetadata)
                !filteredEntries.isEmpty()
            }
        } catch (ex: Exception) {
            log.error("Error while filtering message. ${ex.message}")
            cloverLogger.log(0, "Error occur while json path filtering: ${ex.stackTraceToString()}", messageMetadata)
            log.debug("Stacktrace : ${ex.stackTraceToString()}")
            false
        }
    }
}