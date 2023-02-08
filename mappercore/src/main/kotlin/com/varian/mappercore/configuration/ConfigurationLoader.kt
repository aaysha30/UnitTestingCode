package com.varian.mappercore.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.windpapi4j.WinDPAPI
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.FileOperation
import com.varian.mappercore.framework.helper.MessageMetaData
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.charset.StandardCharsets
import java.util.*

class ConfigurationLoader {
    var configuration: Configuration
    var log: Logger = LogManager.getLogger(ConfigurationLoader::class.java)
    private val entropy = "B@r523MK!"

    init {
        try {
            log.info("reading configuration file")
            val objectMapper = ObjectMapper(YAMLFactory())
            val winDPAPI = WinDPAPI.newInstance(WinDPAPI.CryptProtectFlag.CRYPTPROTECT_LOCAL_MACHINE)
            configuration = objectMapper.readValue(FileOperation.getConfigurationFile(), Configuration::class.java)
            val encryptedBytes: ByteArray =
                Base64.getDecoder().decode(configuration.clientCredentials.clientSecret.toByteArray())
            val decryptedBytes =
                winDPAPI.unprotectData(
                    encryptedBytes, entropy.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            configuration.clientCredentials.clientSecret = String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (exception: Exception) {
            log.error("Error occurred while reading configuration. ${exception.message}")
            log.debug("Error details: ${exception.stackTraceToString()}")
            throw Exception("Error occurred while loading configuration file.", exception)
        }
    }
}
