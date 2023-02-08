package com.varian.mappercore.configuration

class ClientCredentials {

    lateinit var clientName: String
    lateinit var clientId: String

    lateinit var clientSecret: String

    lateinit var authority: String

    var scopes: String? = null
}
