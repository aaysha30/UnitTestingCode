package com.varian.mappercore.helper.sqlite

import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.constant.XlateConstant.SQLITE_SAFE_EXECUTE
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.sqlite.core.CoreResultSet
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties

open class SqliteUtility {
    companion object {
        var log: Logger = LogManager.getLogger(SqliteUtility::class.java)
     fun getSqliteConnectionObject(siteDirName: String, mappedSqliteDbName: String): Connection? {
            var localConnection: Connection
            val localSqliteRelativePath = Paths.get(siteDirName, mappedSqliteDbName).toString()
            val localSqliteConnectionString = "jdbc:sqlite:$localSqliteRelativePath"
            localConnection = DriverManager.getConnection(localSqliteConnectionString)
            return localConnection
        }

        fun getValue(
            connection: Connection?,
            tableName: String?,
            outColumn: String?,
            whereCondition: Map<String, String>,
            safeExecute: Boolean
        ):String? {
            var returnValue: String? = null
            var statement: Statement? = null
            var resultSet: ResultSet? = null
            try {
                val sqlQuery = "SELECT $outColumn FROM $tableName WHERE ${getWhereClause(whereCondition)} COLLATE NOCASE"
                statement = connection!!.createStatement()
                resultSet = statement.executeQuery(sqlQuery)
                while (resultSet.next()) {
                    returnValue = resultSet.getString(outColumn)
                    break
                }
            } catch (ex: Exception) {
                if(!safeExecute) {
                    log.error("error occurred while reading data from sqllite. Message: ${ex.message}")
                    log.debug("error message: ${ex.stackTraceToString()}")
                    throw ex
                }
            } finally {
                statement?.close()
                resultSet?.close()
            }

            return returnValue
        }

       public fun getValue(
                connection: Connection?,
                tableName: String?,
                outColumn: String?,
                whereCondition: Map<String, String>
        ): String? {
            return  getValue(connection, tableName, outColumn, whereCondition, false)
        }

        fun getValues(
                connection: Connection?,
                tableName: String?,
                outColumn: String?,
                whereCondition: Map<String, String>
        ): ArrayList<String> {
            val returnValues: ArrayList<String> = ArrayList()
            val sqlQuery = "SELECT $outColumn FROM $tableName WHERE ${getWhereClause(whereCondition)}"
            var statement: Statement? = null
            var resultSet: ResultSet? = null
            try {
                statement = connection!!.createStatement()
                resultSet = statement.executeQuery(sqlQuery)
                while (resultSet.next()) {
                    returnValues.add(resultSet.getString(outColumn))
                }
            } catch (ex: Exception) {
                log.error("error occurred while reading data from sqllite. Message: ${ex.message}")
                log.debug("error message: ${ex.stackTraceToString()}")
                throw ex
            } finally {
                statement?.close()
                resultSet?.close()
            }

            return returnValues
        }

        inline fun <reified T : Any> getValues(
                connection: Connection?,
                tableName: String?,
                whereCondition: Map<String, String>?
        ): ArrayList<T> {
            val listInstance: ArrayList<T> = ArrayList()
            val sqlQuery = StringBuilder()
            sqlQuery.append("SELECT * FROM $tableName")

            whereCondition?.let {
                sqlQuery.append(" WHERE ${getWhereClause(whereCondition)}")
            }
            var statement: Statement? = null
            var resultSet: ResultSet? = null
            try {
                statement = connection!!.createStatement()
                resultSet = statement.executeQuery(sqlQuery.toString())
                while (resultSet.next()) {
                    val instance = T::class.createInstance()
                    getKMutableProperties<T>().forEach {
                        if ((resultSet as CoreResultSet).cols.contains(it.name)) {
                            it.setter.call(instance, resultSet.getString(it.name))
                        }
                    }
                    listInstance.add(instance)
                }
            } catch (ex: Exception) {
                log.error("error occurred while reading data from sqllite. Message: ${ex.message}")
                log.debug("error message: ${ex.stackTraceToString()}")
                throw ex
            } finally {
                statement?.close()
                resultSet?.close()
            }

            return listInstance
        }

        fun getLookUpValue(
                values: Map<String, String>,
                localConnection: Connection?,
                masterConnection: Connection?
        ): String? {
            var lastMatchedValue = values[XlateConstant.SQLITE_IN_VALUE]
            var connection: Connection? = null
            var inColumn = ""
            var outColumn = ""
            var ifMatched = false
            val sequenceList = Stream.of(
                    *values[XlateConstant.SQLITE_SEQUENCE]!!.split(XlateConstant.COMMA_SPLITTER)
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()).collect(Collectors.toList())
            for (site in sequenceList) {
                if (site == XlateConstant.LOCAL_SITE) {
                    connection = localConnection
                    inColumn = values[XlateConstant.SQLITE_LOCAL_IN]!!
                    outColumn = values[XlateConstant.SQLITE_LOCAL_OUT]!!
                } else if (site == XlateConstant.MASTER_SITE) {
                    connection = masterConnection
                    inColumn = values[XlateConstant.SQLITE_MASTER_IN]!!
                    outColumn = values[XlateConstant.SQLITE_MASTER_OUT]!!
                }
                var safeExecuteQuery = values.containsKey(SQLITE_SAFE_EXECUTE)
                if(safeExecuteQuery) {
                    safeExecuteQuery = values[SQLITE_SAFE_EXECUTE] == "true"
                }
                val whereCondition = HashMap<String, String>()
                whereCondition[inColumn] = lastMatchedValue!!
                val matchedValue = SqliteUtility.getValue(
                        connection,
                        values[XlateConstant.SQLITE_TABLE],
                        outColumn,
                        whereCondition,
                        safeExecuteQuery
                )

                if (matchedValue != null) {
                    lastMatchedValue = matchedValue
                    ifMatched = true
                }
            }
            if (!ifMatched && lastMatchedValue == values[XlateConstant.SQLITE_IN_VALUE] && values[XlateConstant.SQLITE_IF_NOT_MATCHED] == XlateConstant.IGNORE_ORIGINAL) {
                lastMatchedValue = null
            }
            return lastMatchedValue
        }

        fun getWhereClause(whereCondition: Map<String, String>): String {
            val whereClause = StringBuilder()
            whereCondition.forEach {
                whereClause.append(" AND ").append(it.key).append(" = '").append(it.value).append("'")
            }
            return whereClause.toString().replaceFirst(" AND ", "")
        }

        fun getOutColumns(outColumns: ArrayList<String>): String {
            return outColumns.joinToString(",")
        }

        inline fun <reified T : Any> getProperties(): ArrayList<String> {
            val properties: ArrayList<String> = ArrayList()
            getKMutableProperties<T>().forEach { properties.add(it.name) }
            return properties
        }

        inline fun <reified T : Any> getKMutableProperties(): List<KMutableProperty<*>> {
            return T::class.memberProperties
                    .filter { it.visibility == KVisibility.PUBLIC }
                    .filterIsInstance<KMutableProperty<*>>().toList()
        }
    }
}
