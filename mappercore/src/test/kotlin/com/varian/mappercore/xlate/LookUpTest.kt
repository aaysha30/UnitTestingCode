package com.varian.mappercore.xlate

import com.nhaarman.mockitokotlin2.anyOrNull
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.tps.GlobalInit
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

class LookUpTest {
    @MockK
    var cloverEnv: CloverEnv? = null

    @MockK
    var xpm: Xpm? = null

    @Before
    @Throws(SQLException::class)
    fun before() {
        val localSqliteConnectionString = "jdbc:sqlite:ADT_IN.sqlite"
        val masterSqliteConnectionString = "jdbc:sqlite:Master.sqlite"

        MockKAnnotations.init(this)
        every { cloverEnv!!.log(any(), any()) } returns Unit
        //every { cloverEnv!!.log(any(), any()) } returns Unit
        val globalInit: GlobalInit = mockk()
        mockkConstructor(GlobalInit::class)
        mockkObject(GlobalInit)
        mockkObject(GlobalInit.Companion)
        mockkStatic(GlobalInit::class)
        mockkStatic(GlobalInit.Companion::class)

        every { GlobalInit.createInstance(cloverEnv!!) } returns globalInit
        every { globalInit.localConnection } returns DriverManager.getConnection(localSqliteConnectionString)
        every { globalInit.masterConnection } returns DriverManager.getConnection(masterSqliteConnectionString)
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueFromBothTable() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("M")
        inputValues.add("Table:MaritalStatus")
        inputValues.add("Sequence:Local,Master")
        inputValues.add("IfNotMatched:Original")
        inputValues.add("LocalIn:InValue")
        inputValues.add("LocalOut:OutValue")
        inputValues.add("MasterIn:InValue")
        inputValues.add("MasterOut:OutValue")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("TestMarried", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueFromLocalTable() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("M")
        inputValues.add("Table:MaritalStatus")
        inputValues.add("Sequence:Local")
        inputValues.add("IfNotMatched:Original")
        inputValues.add("LocalIn:InValue")
        inputValues.add("LocalOut:OutValue")
        inputValues.add("MasterIn:InValue")
        inputValues.add("MasterOut:OutValue")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("TM", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueFromTableWithDefaultInputs() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(2)
        inputValues.add("M")
        inputValues.add("Table:MaritalStatus")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("TestMarried", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueFromBothTableForCaseSensitiveKeys() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("M")
        inputValues.add("table:MaritalStatus")
        inputValues.add("sequence:Local,Master")
        inputValues.add("IfNotMatched:Original")
        inputValues.add("LOCALin:InValue")
        inputValues.add("localOUT:OutValue")
        inputValues.add("masterIn:InValue")
        inputValues.add("masterout:OutValue")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("TestMarried", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetOriginalValueWhenNotFound() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("TestInput")
        inputValues.add("Table:MaritalStatus")
        inputValues.add("Sequence:Local,Master")
        inputValues.add("IfNotMatched:Original")
        inputValues.add("LocalIn:InValue")
        inputValues.add("LocalOut:OutValue")
        inputValues.add("MasterIn:InValue")
        inputValues.add("MasterOut:OutValue")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("TestInput", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetNullValueWhenNotFound() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("TestInput")
        inputValues.add("Table:MaritalStatus")
        inputValues.add("Sequence:Local,Master")
        inputValues.add("IfNotMatched:IgnoreOriginal")
        inputValues.add("LocalIn:InValue")
        inputValues.add("LocalOut:OutValue")
        inputValues.add("MasterIn:InValue")
        inputValues.add("MasterOut:OutValue")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals(null, values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueForInputNotHavingColon() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(2)
        inputValues.add("M")
        inputValues.add("TableMaritalStatus")
        try {
            val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        } catch (e: Exception) {
            Assert.assertTrue(e is IllegalArgumentException)
            Assert.assertEquals(
                "ERROR : Input values must be entered in a key value pair having a ':' in between",
                e.message
            )
        }
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueForNoInputValues() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(2)
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertEquals("", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueForNoTableName() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(2)
        inputValues.add("M")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertEquals("", values!![0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun GetSqliteValueFromBothTableOutBound() {
        val lookUp = LookUp()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("TestMarried")
        inputValues.add("Table:MaritalStatus")
        inputValues.add("Sequence:Master,Local")
        inputValues.add("IfNotMatched:Original")
        inputValues.add("LocalIn:OutValue")
        inputValues.add("LocalOut:InValue")
        inputValues.add("MasterIn:OutValue")
        inputValues.add("MasterOut:InValue")
        val values = lookUp.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("M", values!![0])
    }

    @After
    @Throws(SQLException::class)
    fun after() {
       /* GlobalInit.getInstance().localConnection.close()
        GlobalInit.getInstance().masterConnection.close()*/
    }
}
