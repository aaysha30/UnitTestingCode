package com.varian.mappercore.configuration

import com.varian.mappercore.constant.AriaConnectConstant.Companion.SMAT_DB_ADT_IN_SC_Inbound
import com.varian.mappercore.constant.AriaConnectConstant.Companion.SMAT_DB_ADT_IN_SC_Outbound
import com.varian.mappercore.constant.AriaConnectConstant.Companion.SMAT_DB_SIU_OUT_FHIR
import java.net.URL

open class Configuration {

    var ariaFhirServerUrl: URL? = null

    var interfaceApiClientUrl: URL? = null

    var ariaEventsClientUrl: URL? = null

    var masterSqliteDbName: String = "Master.sqlite"

    var connectTimeoutInMilliseconds: Int = 5001

    var socketTimeoutInMilliseconds: Int = 5002

    var interfaceServiceConnectionTimeout: Long = 30000

    var interfaceServiceReadTimeout: Long = 30000

    var interfaceServiceWriteTimeout: Long = 30000

    lateinit var clientCredentials: ClientCredentials

    var defaultHL7Version: String = "2.5.1"

    var enableSMARTFormatting: Boolean = false

    lateinit var hl7EncodeCharacters: HL7EncodeCharacters

    var dslRoot = emptyMap<String, List<String>>()

    var necessaryScopesForARIAEvents: String? = null

    var jwksUrl: URL? = null

    var jwksTimeoutInMilliseconds: Int = 5000

    var jwtissuer:String?=null

    var attachHospitalDepartments = true

    var snapshotDepartments = false

    var updatePrimaryDepartment = true

    var adtInSCOutSMATDbName = SMAT_DB_ADT_IN_SC_Outbound

    var adtInSCInSMATDbName = SMAT_DB_ADT_IN_SC_Inbound

    var siuOutSMATDbName = SMAT_DB_SIU_OUT_FHIR
}
