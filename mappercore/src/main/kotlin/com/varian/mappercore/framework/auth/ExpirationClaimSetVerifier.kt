package com.varian.mappercore.framework.auth

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier
import java.time.Clock


open class ExpirationClaimSetVerifier(private val clock: Clock) : JWTClaimsSetVerifier<SecurityContext>{

    @Throws(BadJWTException::class)
    override fun verify(claimsSet: JWTClaimsSet, context: SecurityContext?) {
        if (claimsSet.expirationTime.toInstant().toEpochMilli()  < clock.instant().toEpochMilli()) {
            throw BadJWTException("JWT expired.")
        }
    }

}