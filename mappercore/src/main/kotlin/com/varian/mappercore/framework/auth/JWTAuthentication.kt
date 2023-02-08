package com.varian.mappercore.framework.auth

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.shaded.json.JSONArray
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.varian.mappercore.configuration.Configuration
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URL

open class JWTAuthentication(
    private val configuration: Configuration,
    private val expirationClaimSetVerifier: ExpirationClaimSetVerifier?
) {
    val TOKEN_ISSUER_VALIDATION_FAILED = "Token validation failed"
    protected var log: Logger = LogManager.getLogger(JWTAuthentication::class.java)
    private val keySource: JWKSource<SecurityContext>

    init {
        this.keySource = get(configuration.jwksUrl)
    }


    private operator fun get(jwksUrl: URL?): JWKSource<SecurityContext> {
        return RemoteJWKSet(
            jwksUrl,
            DefaultResourceRetriever(configuration.jwksTimeoutInMilliseconds, configuration.jwksTimeoutInMilliseconds)
        )
    }

    fun authenticate(token: String) {
        log.info("authenicating sender..")
        val claimsSet: JWTClaimsSet
        claimsSet = try {
            val encryptedToken = SignedJWT.parse(token)
            val expectedJWSAlg = encryptedToken.header.algorithm
            val keySelector: JWSKeySelector<SecurityContext> = JWSVerificationKeySelector(expectedJWSAlg, keySource)
            val jwtProcessor: ConfigurableJWTProcessor<SecurityContext> = DefaultJWTProcessor()
            jwtProcessor.jwtClaimsSetVerifier = expirationClaimSetVerifier
            jwtProcessor.jwsKeySelector = keySelector
            jwtProcessor.process(token, null)
        } catch (je: Exception) {
            log.error("error occurred while authenticating sender. Error message: ${je.message}")
            log.debug("Exception details: $je.stackTraceToString()")
            throw throw BadJWTException(je.message.toString())
        }
        validateIssuer(claimsSet)
        validateScope(claimsSet)
        log.info("sender authenticated..")
    }

    private fun validateScope(claimsSet: JWTClaimsSet) {
        var foundRequiredScope = false
        val requiredScopes: List<String>? = configuration.necessaryScopesForARIAEvents?.split(" ")
        if (requiredScopes.isNullOrEmpty()) {
            foundRequiredScope = true
        }
        val claimedScopes = (claimsSet.getClaim("scope") as JSONArray)
        requiredScopes?.forEach {
            if (claimedScopes.contains(it)) {
                foundRequiredScope = true
            }
        }
        if (!foundRequiredScope) {
            log.info("Missing required scope.")
            throw BadJWTException("Required scope missing.")
        }
    }


    private fun validateIssuer(claimsSet: JWTClaimsSet) {
        if (!claimsSet.issuer.equals(configuration.jwtissuer, true))
            throw BadJWTException(TOKEN_ISSUER_VALIDATION_FAILED)
    }
}