package dsl.master.inbound.interfaces.adtin.common.condition

import TestHelper
import ca.uhn.fhir.parser.IParser
import com.nhaarman.mockitokotlin2.*
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.ClientDecor
import com.varian.mappercore.framework.helper.FileOperation
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.helper.Outcome
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import com.varian.mappercore.framework.utility.ParametersUtility
import org.hl7.fhir.r4.model.*
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito

class ConditionTest {
    companion object {
        lateinit var fhirFactory: FhirFactory
        lateinit var scripts: IScripts
        lateinit var scriptInformation: ScriptInformation
        lateinit var parser: IParser

        @BeforeClass
        @JvmStatic
        fun set() {
            FileOperation.setCurrentBasePath(".")
            fhirFactory = FhirFactory()
            scripts = TestHelper.scripts
            scriptInformation = scripts.getHandlerFor("aria", "ConditionDiagnosis")!!.get()
            parser = fhirFactory.getFhirParser()
        }
    }

    lateinit var fhirClient: FhirClient
    lateinit var outcome: Outcome
    lateinit var clientDecor: ClientDecor
    lateinit var parameters: MutableMap<String, Any>

    @Before
    fun setup() {
        parameters = mutableMapOf()
        fhirClient = mock()
        outcome = Outcome(parser)
        clientDecor = ClientDecor(fhirClient, outcome)
        parameters[ParameterConstant.CLIENT_DECOR] = clientDecor
        parameters[ParameterConstant.BUNDLE_UTILITY] = fhirFactory.getBundleUtility()
        parameters[ParameterConstant.PARAMETERS_UTILITY] = fhirFactory.getParametersUtility()
        parameters[ParameterConstant.PATIENT_UTILITY] = fhirFactory.getPatientUtility()
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters["patientId"] = "Patient-1"
        parameters[ParameterConstant.USER] = TestHelper.getPractitioner("Practitioner-1014", "headlessclient")
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
    }

    @Test
    fun testSnapshotOwn_ShouldDeleteUserSpecificDiagnosis() {
        //prepare
        val json = TestHelper.readResource("/condition/condition_adtBundle.json")

        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.DIAGNOSIS_UPDATE_MODE }?.value =
                StringType("SnapshotOwn")
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val parameterCaptor = argumentCaptor<Parameters>()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient).operation(isA<Condition>(), eq("\$deleteExternalDiagnosis"), eq("Condition"), parameterCaptor.capture(), isNull())
        val parameter = parameterCaptor.firstValue
        Assert.assertNotNull(parameter)
        Assert.assertEquals(2, parameter.parameter.size)
        Assert.assertEquals("patientKey", parameter.parameter[0].name)
        Assert.assertEquals("Patient-1", parameter.parameter[0].value.toString())
        Assert.assertEquals("username", parameter.parameter[1].name)
        Assert.assertEquals("headlessclient", parameter.parameter[1].value.toString())
        verify(fhirClient, times(1)).create(isA<Condition>())
    }

    @Test
    fun testSnapshotAll_ShouldDeleteAllDiagnosis() {
        //prepare
        val json = TestHelper.readResource("/condition/condition_adtBundle.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.DIAGNOSIS_UPDATE_MODE }?.value =
                StringType("SnapshotAll")
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val parameterCaptor = argumentCaptor<Parameters>()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient).operation(isA<Condition>(), eq("\$deleteExternalDiagnosis"), eq("Condition"), parameterCaptor.capture(), isNull())
        val parameter = parameterCaptor.firstValue
        Assert.assertNotNull(parameter)
        Assert.assertEquals(1, parameter.parameter.size)
        Assert.assertEquals("patientKey", parameter.parameter[0].name)
        Assert.assertEquals("Patient-1", parameter.parameter[0].value.toString())
        verify(fhirClient, times(1)).create(isA<Condition>())
    }

    @Test
    fun testDoubleQuotes_shouldDelete_OldValues() {

        val json = TestHelper.readResource("/condition/conditionWithDoubleQuotes_adtBundle.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.DIAGNOSIS_UPDATE_MODE }?.value =
                StringType("Matching")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val bundle = parser.parseResource(json) as Bundle
        bundle.entry.removeIf { it.resource.fhirType() != "Condition" }
        //val domainCondition = bundle.entry.first() as com.varian.fhir.resources.Condition
        val updatedCondition = argumentCaptor<com.varian.fhir.resources.Condition>()
        Mockito.`when`(fhirClient.search(eq("Condition"), eq("patient"), eq("Patient-1"),
            eq("category"), eq("encounter-diagnosis"), eq("IsExternal"), any()))
            .thenReturn(bundle)
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(updatedCondition.capture())
        val condition = updatedCondition.firstValue
        Assert.assertNull(condition.clinicalStatusDate)
        Assert.assertNull(condition.clinicalStatusInARIA)
        Assert.assertNull(condition.rank)
        Assert.assertNull(condition.verificationStatus.codingFirstRep.code)
        Assert.assertEquals("236.7", condition.code.codingFirstRep.code)
        Assert.assertNull(condition.code.text)
        Assert.assertNotNull(condition.onset)
    }

    @Test
    fun testEmptyField_ShouldKeepPreviousValues() {
        val json = TestHelper.readResource("/condition/condition_adtBundle.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.DIAGNOSIS_UPDATE_MODE }?.value =
                StringType("Matching")

        val inputCondition = inputBundle.entry.find { it.resource.fhirType() == "Condition" }?.resource as com.varian.fhir.resources.Condition
        inputCondition.clinicalStatusInARIA = null
        inputCondition.clinicalStatusDate = null
        inputCondition.rank = null
        inputCondition.verificationStatus = null
        inputCondition.code.text = null
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainJson = TestHelper.readResource("/condition/condition_adtBundle.json")
        val bundle = parser.parseResource(domainJson) as Bundle
        val domainCondition = bundle.entry.find { it.resource.fhirType() == "Condition" }?.resource as com.varian.fhir.resources.Condition
        bundle.entry.removeIf { it.resource.fhirType() != "Condition" }
        val updatedCondition = argumentCaptor<com.varian.fhir.resources.Condition>()
        Mockito.`when`(fhirClient.search(eq("Condition"), eq("patient"), eq("Patient-1"),
            eq("category"), eq("encounter-diagnosis"), eq("IsExternal"), any()))
                .thenReturn(bundle)
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(updatedCondition.capture())
        val condition = updatedCondition.firstValue
        Assert.assertEquals("236.7", condition.code.codingFirstRep.code)
        Assert.assertEquals(domainCondition.clinicalStatusDate, condition.clinicalStatusDate)
        Assert.assertEquals("Active", condition.clinicalStatusInARIA.codingFirstRep.code)
        Assert.assertEquals("1", condition.rank.codingFirstRep.code)
        Assert.assertEquals("confirmed", condition.verificationStatus.codingFirstRep.code)
        Assert.assertEquals("Neoplasm of uncertain behavior of bladder clinical description", condition.code.text)
        Assert.assertEquals(domainCondition.onset, condition.onset)
    }

    @Test
    fun testUpdate_ShouldUpdateValues() {
        val json = TestHelper.readResource("/condition/condition_adtBundle.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.DIAGNOSIS_UPDATE_MODE }?.value =
                StringType("Matching")

        val inputCondition = inputBundle.entry.find { it.resource.fhirType() == "Condition" }?.resource as com.varian.fhir.resources.Condition
        inputCondition.clinicalStatusInARIA.codingFirstRep.code = "newClinicalStatus"
        inputCondition.clinicalStatusDate = DateTimeType(DateTime(2021, 11, 2, 5, 5, 5).toDate())
        inputCondition.rank.codingFirstRep.code = "newRank"
        inputCondition.verificationStatus.codingFirstRep.code = "newVerification"
        inputCondition.code.text = "newCodeText"
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainJson = TestHelper.readResource("/condition/condition_adtBundle.json")
        val bundle = parser.parseResource(domainJson) as Bundle
        val domainCondition = bundle.entry.find { it.resource.fhirType() == "Condition" }?.resource as com.varian.fhir.resources.Condition
        bundle.entry.removeIf { it.resource.fhirType() != "Condition" }
        val updatedCondition = argumentCaptor<com.varian.fhir.resources.Condition>()
        Mockito.`when`(fhirClient.search(eq("Condition"), eq("patient"), eq("Patient-1"),
            eq("category"), eq("encounter-diagnosis"), eq("IsExternal"), any()))
            .thenReturn(bundle)
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(updatedCondition.capture())
        val condition = updatedCondition.firstValue
        Assert.assertEquals("236.7", condition.code.codingFirstRep.code)
        Assert.assertEquals(10, (condition.clinicalStatusDate as DateTimeType).month)
        Assert.assertEquals(2, (condition.clinicalStatusDate as DateTimeType).day)
        Assert.assertEquals(2021, (condition.clinicalStatusDate as DateTimeType).year)
        Assert.assertEquals("newClinicalStatus", condition.clinicalStatusInARIA.codingFirstRep.code)
        Assert.assertEquals("newRank", condition.rank.codingFirstRep.code)
        Assert.assertEquals("newVerification", condition.verificationStatus.codingFirstRep.code)
        Assert.assertEquals("newCodeText", condition.code.text)
        Assert.assertEquals(domainCondition.onset, condition.onset)
    }

    @Test
    fun testShouldCreateDiagnosis_WithCurrentDateTime_IfOnsetIsNull() {
        //prepare
        val json = TestHelper.readResource("/condition/condition_adtBundle.json")

        var inputBundle = parser.parseResource(json) as Bundle
        val conditionResource =
            inputBundle.entry.find { it.resource.fhirType() == "Condition" }?.resource as com.varian.fhir.resources.Condition
        conditionResource.onset = null

        parameters[ParameterConstant.BUNDLE] = inputBundle
        val parameterCaptor = argumentCaptor<com.varian.fhir.resources.Condition>()

        //execute
        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).create(parameterCaptor.capture())
        val parameter = parameterCaptor.firstValue
        Assert.assertTrue(parameter.onset.isDateTime)
        Assert.assertEquals(2020, (parameter.onset as DateTimeType).year)
        Assert.assertEquals(11, (parameter.onset as DateTimeType).month)
        Assert.assertEquals(22, (parameter.onset as DateTimeType).day)
    }
}