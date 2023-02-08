package com.varian.mappercore.client

import com.quovadx.cloverleaf.upoc.PropertyTree

interface IHL7EncodeCharacterManager {
    fun getEncodeCharacters(userdata: PropertyTree): HashMap<String, String>
    fun decodeHl7Message(message: String, userdata: PropertyTree): String
}