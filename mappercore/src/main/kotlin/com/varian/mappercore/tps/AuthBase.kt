package com.varian.mappercore.tps

import com.nimbusds.jwt.proc.BadJWTException
import com.quovadx.cloverleaf.upoc.*
import com.varian.mappercore.configuration.ConfigurationLoader
import com.varian.mappercore.framework.auth.ExpirationClaimSetVerifier
import com.varian.mappercore.framework.auth.JWTAuthentication
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Clock

@Suppress("unused")
open class AuthBase(cloverEnv: CloverEnv, propertyTree: PropertyTree?) :
    TPS() {
    private var authLog: Logger = LogManager.getLogger(AuthBase::class.java)
    protected var cloverEnv: CloverEnv = cloverEnv

    open fun handleRun(message: Message?): DispositionList {
        val configuration = ConfigurationLoader().configuration
        val dispositionList = DispositionList()
        var overAckMessage: Message?
        try {
            authLog.info("processing message..")
            //Authenticate and Authorize Message
            authLog.info("authenticating message..")
            val bearerToken =
                (message?.userdata?.get("httpRequestHeaders") as PropertyTree).get("Authorization") as? String?
                    ?: throw BadJWTException("Authentication Required")
            var jwtAuthentication: JWTAuthentication
            if (message?.metadata?.flags?.get("is_resent") == "1" || message?.metadata?.flags?.get("recovered") == "1") {
                authLog.info("message is from recovery DB or resent hence Token expiry is not considered")
                jwtAuthentication = JWTAuthentication(configuration, null)
                jwtAuthentication.authenticate((bearerToken).split(" ")[1])
            } else {
                jwtAuthentication = JWTAuthentication(configuration, ExpirationClaimSetVerifier(Clock.systemUTC()))
                jwtAuthentication.authenticate((bearerToken).split(" ")[1])
                overAckMessage = getAckMessage(message, "Success", "200")
                dispositionList.add(DispositionList.OVER, overAckMessage)
            }
            authLog.info("authentication succeeded..")
            dispositionList.add(DispositionList.CONTINUE, message)
        } catch (exception: BadJWTException) {
            cloverEnv.log(2, "Exception occurred while Authentication: ${exception.message}")
            overAckMessage = getAckMessage(message, exception.message, "401")
            dispositionList.add(DispositionList.OVER, overAckMessage)
            authLog.debug("Exception occurred while authentication : ${exception.message}")
            authLog.error("Exception occurred while authentication : ${exception.stackTraceToString()}")
            dispositionList.add(DispositionList.KILL, message)
        } catch (exception: Exception) {
            cloverEnv.log(2, "Exception occurred while Authentication: ${exception.message}")
            overAckMessage = getAckMessage(message, exception.message, "500")
            dispositionList.add(DispositionList.OVER, overAckMessage)
            authLog.debug("Exception occurred while authentication : ${exception.message}")
            authLog.error("Exception occurred while authentication : ${exception.stackTraceToString()}")
            dispositionList.add(DispositionList.KILL, message)
        }
        return dispositionList
    }

    private fun getAckMessage(
        message: Message?,
        returnMessage: String?,
        statusCode: String
    ): Message? {
        var overAckMessage = cloverEnv.makeMessage(
            returnMessage,
            Message.REPLY_TYPE,
            Message.PROTOCOL_CLASS,
            false
        )
        val httpResponse = PropertyTree()
        httpResponse.put("httpResponseCode", statusCode)
        overAckMessage?.userdata = httpResponse
        overAckMessage?.metadata?.driverctl = message?.metadata?.driverctl
        return overAckMessage
    }

    open fun handleStart(cloverEnv: CloverEnv): DispositionList {
        return DispositionList()
    }

    override fun process(cloverEnv: CloverEnv, context: String?, mode: String?, message: Message?): DispositionList {
        return when (mode) {
            "start" -> handleStart(cloverEnv)
            "run" -> handleRun(message)
            "shutdown" -> handleShutdown()
            "time" -> handleTime(message)
            else -> DispositionList()
        }
    }

    open fun handleShutdown(): DispositionList {
        return DispositionList()
    }

    open fun handleTime(message: Message?): DispositionList {
        val dispositionList = DispositionList()
        dispositionList.add(DispositionList.CONTINUE, message)
        return dispositionList
    }
}