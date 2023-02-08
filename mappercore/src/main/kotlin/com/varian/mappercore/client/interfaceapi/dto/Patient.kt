package com.varian.mappercore.client.interfaceapi.dto

import java.sql.Timestamp

class Patient()
{
    var patientRecordSer: Long = 0
    var id1: String?=null
    var id2: String?=null
    var firstName: String?=null
    var lastName: String?=null
    var dob: Timestamp?=null
    var responseMessage: String?=null
    var middleName: String?=null
    var maidenName: String?=null
    var sex: String?=null
    var maritalStatus: String?=null
    var location: String?=null
    var interfaceId: String?=null
    var siteId: String?=null
    var groupId: String?=null
    var status: String?=null
    var hstryDateTime: Timestamp?=null
    var hstryUser: String?=null
    var patientIdentifiers:MutableList<PatientIdentifier>? = null
}