package com.varian.mappercore.client.interfaceapi.dto

import java.sql.Timestamp

class FilteredMessage()
{
    var messageRecordSer: Long?=null
    var msgType: String?=null
    var traceId: String?=null
    var msgEvent: String?=null
    var interfaceId: String?=null
    var status: String?=null
    var msgControlId: String?=null
    var hl7Base64: String?=null
    var patientRecordSer: Long?=null
    var hstryDateTime: Timestamp?=null
    var creationDateTime: Timestamp?=null
    var hstryUser: String?=null
    var errorString: String? = null
    var siteId: String?=null
}