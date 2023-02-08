package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.Xpm
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.sql.SQLException
import java.util.*

class ParseToEnumTest {
    @MockK
    var cloverEnv: CloverEnv? = null

    @MockK
    var xpm: Xpm? = null

    @Before
    @Throws(SQLException::class)
    fun before() {
        MockKAnnotations.init(this)
    }

    @Test
    @Throws(CloverleafException::class)
    fun shouldReturnSameValue_ForValidAllergySeverity_Severe() {
        val parseToEnum = ParseToEnum()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("severe")
        inputValues.add("org.hl7.fhir.r4.model.AllergyIntolerance\$AllergyIntoleranceSeverity")

        val values = parseToEnum.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("severe", values[0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun shouldReturnSameValue_ForValidAllergySeverity_Mild() {
        val parseToEnum = ParseToEnum()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("mild")
        inputValues.add("org.hl7.fhir.r4.model.AllergyIntolerance\$AllergyIntoleranceSeverity")

        val values = parseToEnum.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("mild", values[0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun shouldReturnSameValue_ForValidAllergySeverity_Moderate() {
        val parseToEnum = ParseToEnum()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("moderate")
        inputValues.add("org.hl7.fhir.r4.model.AllergyIntolerance\$AllergyIntoleranceSeverity")

        val values = parseToEnum.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertEquals("moderate", values[0])
    }

    @Test
    @Throws(CloverleafException::class)
    fun shouldReturnSameValue_ForInValidAllergySeverity() {
        val parseToEnum = ParseToEnum()
        val inputValues: Vector<String?> = Vector<String?>(8)
        inputValues.add("moderateeeeeee")
        inputValues.add("org.hl7.fhir.r4.model.AllergyIntolerance\$AllergyIntoleranceSeverity")

        val values = parseToEnum.xlateStrings(cloverEnv!!, xpm!!, inputValues)
        Assert.assertNotNull(values)
        Assert.assertNull(values[0])
    }

/*    @After
    @Throws(SQLException::class)
    fun after() {
        GlobalInit.getInstance().localConnection.close()
        GlobalInit.getInstance().masterConnection.close()
    }*/
}
