package com.varian.mappercore.tps

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.Message
import com.quovadx.cloverleaf.upoc.PropertyTree
import com.varian.mappercore.framework.helper.FileOperation
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import java.io.File

class AdtInboundTest {
/*
    @Mock
    lateinit var cloverEnv: CloverEnv
    lateinit var propertyTree: PropertyTree
    lateinit var message: Message
    lateinit var ackMessage: Message

    @Before
    fun before() {
        cloverEnv = Mockito.mock(CloverEnv::class.java)
        propertyTree = Mockito.mock(PropertyTree::class.java)
        message = Mockito.mock(Message::class.java)
        ackMessage = Mockito.mock(Message::class.java)
        var jsonMessage = File("MessageBundle.json").readText()
        Mockito.`when`(message.getContent()).thenReturn(jsonMessage)
        Mockito.`when`(
            cloverEnv.makeMessage(
                anyString(),
                eq(Message.DATA_TYPE),
                eq(Message.PROTOCOL_CLASS),
                eq(false)
            )
        )
            .thenReturn(ackMessage)
        FileOperation.setCurrentBasePath(".")
    }

    @Test
    fun testHandleRun() {
        var adtInbound = AdtInbound(cloverEnv, propertyTree)
        adtInbound.handleRun(message)
    }
 */
}