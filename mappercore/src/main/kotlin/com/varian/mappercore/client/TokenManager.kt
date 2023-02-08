package com.varian.mappercore.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit


class TokenManager(
    private val authority: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scopes: String?,
) : ITokenManager {

    class TokenDetails(
        var token: String = "",
        var time: Long = 0
    )

    private var log: Logger = LogManager.getLogger(TokenManager::class.java)
    private val ariaFhirTokenCacheKey: String = "AriaFhirTokenCache"
    private val cacheLoader: Cache<String, TokenDetails> = CacheBuilder.newBuilder().build()
    private val httpClient: OkHttpClient = OkHttpClient().newBuilder()
        .connectTimeout(Int.MAX_VALUE.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(Int.MAX_VALUE.toLong(), TimeUnit.MILLISECONDS)
        .build()
    private val objectMapper: ObjectMapper = ObjectMapper()

    override fun getToken(): String {
        log.trace("Getting token for fhirclient..")
        val cachedValue = cacheLoader.getIfPresent(ariaFhirTokenCacheKey)
        if (cachedValue?.token.isNullOrBlank() || cachedValue?.time ?: 0 < System.currentTimeMillis()) {
            log.trace("Generating new token..")

            val body: RequestBody = FormBody.Builder().add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("scope", scopes!!).build()

            val request: Request = Request.Builder()
                .url(authority)
                .method("POST", body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            return if (response.isSuccessful) {
                val accessToken = objectMapper.readTree(responseBody)["access_token"].asText()
                val expiresInSeconds = objectMapper.readTree(responseBody)["expires_in"].asInt()
                val expirationTime = DateTime.now().plusSeconds(expiresInSeconds)
                cacheLoader.put(ariaFhirTokenCacheKey, TokenDetails(accessToken, expirationTime.millis))
                log.trace("token generated successfully")
                accessToken
            } else {
                log.error("Token generation failed. returning empty token")
                log.debug(responseBody)
                ""
            }
        } else {
            log.trace("returning cached token")
            return cachedValue?.token ?: ""
        }
    }
}