package dsl.master.inbound.interfaces.adtin.common.coverage

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.Coverage
import com.varian.fhir.resources.CoverageEligibilityResponse
import com.varian.fhir.resources.InsurancePlan
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
import org.hl7.fhir.r4.model.Enumerations.FHIRAllTypes
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.math.BigDecimal

class CoverageTest {
    companion object {
        lateinit var fhirFactory: FhirFactory
        lateinit var scripts: IScripts
        lateinit var scriptInformation: ScriptInformation
        lateinit var parser: IParser

        @BeforeClass
        @JvmStatic
        fun set() {
            FileOperation.setCurrentBasePath("")
            fhirFactory = FhirFactory()
            scripts = TestHelper.scripts
            scriptInformation = scripts.getHandlerFor("aria", "CoverageSave")!!.get()
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
        parameters["patientId"] = "Patient/Patient-1"
        parameters[ParameterConstant.USER] = TestHelper.getPractitioner("Practitioner-1014", "headlessclient")
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        val valueSetJson = TestHelper.readResource("/coverage/CoveragePlanTypeValueSet.json")
        val valueSet = parser.parseResource(valueSetJson) as ValueSet
        parameters[ParameterConstant.COVERAGE_POLICY_PLAN_TYPE] = valueSet
    }

    @Test
    fun testCreate_MultipleCoverage_shouldMarkFirstCoveragePrimary() {
        //allowUpdateOnPrimaryCoverageCheck is enabled

        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("1", inputBundle)

        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }

        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "1123")
        Assert.assertFalse(createdCoverages[0].primary.booleanValue())
        Assert.assertEquals("CAP", createdCoverages[0].type.codingFirstRep.code)

        Assert.assertEquals(createdCoverages[1].dependent, "2222")
        Assert.assertTrue(createdCoverages[1].primary.booleanValue())
        Assert.assertEquals("SFP", createdCoverages[1].type.codingFirstRep.code)
    }

    @Test
    fun testCreate_MultipleCoverage_shouldMarkFirstCoveragePrimaryByDefault() {
        //allowUpdateOnPrimaryCoverageCheck is not provided

        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        removePrimaryCheckForCoverageConfigParameter(inputBundle)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }

        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "1123")
        Assert.assertFalse(createdCoverages[0].primary.booleanValue())
        Assert.assertEquals(createdCoverages[1].dependent, "2222")
        Assert.assertTrue(createdCoverages[1].primary.booleanValue())
    }

    @Test
    fun testCreate_MultipleCoverage_shouldMarkFirstCoveragePrimary_whenNoExistingCoverageFound() {
        //allowUpdateOnPrimaryCoverageCheck is disabled

        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("0", inputBundle)

        //mock
        `when`(fhirClient.search(eq("Coverage"), eq("patient"), eq("Patient/Patient-1"), eq("_count"), eq("50")))
                .thenReturn(Bundle())
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }

        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "2222")
        Assert.assertTrue(createdCoverages[0].primary.booleanValue())
        Assert.assertEquals(createdCoverages[1].dependent, "1123")
        Assert.assertFalse(createdCoverages[1].primary.booleanValue())
    }

    @Test
    fun testCreate_MultipleCoverage_shouldNotMarkFirstCoveragePrimary_ConfigIsDisabled() {
        //allowUpdateOnPrimaryCoverageCheck is disabled

        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("0", inputBundle)

        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }

        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "2222")
        Assert.assertFalse(createdCoverages[0].primary.booleanValue())
        Assert.assertEquals(createdCoverages[1].dependent, "1123")
        Assert.assertFalse(createdCoverages[1].primary.booleanValue())
    }

    @Test
    fun testUpdate_shouldUpdateCoverageAndItsPrimaryCheck() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("1", inputBundle)

        //mock
        val coverageBundle = Bundle()
        val coverage = Coverage()
        coverage.id = "Coverage/Coverage-1"
        coverage.subscriberId = "12354644"
        coverage.insurancePlan = Reference()
        coverage.insurancePlan.display = "22"
        coverage.primary = BooleanType(true)
        coverageBundle.addEntry(Bundle.BundleEntryComponent().setResource(coverage))
        `when`(fhirClient.search(eq("Coverage"), eq("patient"), eq("Patient/Patient-1"), eq("_count"), eq("50")))
                .thenReturn(coverageBundle)
        mockSearchInsurancePlan()
        val coverageCreateArgCaptor = argumentCaptor<Coverage>()
        `when`(fhirClient.create(coverageCreateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdCoverage = coverageCreateArgCaptor.firstValue
        val updateCoverage = coverageUpdateArgCaptor.allValues.find { it.fhirType() == "Coverage" }!! as Coverage
        Assert.assertTrue(createdCoverage.primary.booleanValue())
        Assert.assertEquals("123546", createdCoverage.subscriberId)
        Assert.assertFalse(updateCoverage.primary.booleanValue())
        Assert.assertEquals("12354644", updateCoverage.subscriberId)
    }

    @Test
    fun testUpdate_shouldNotUpdateExistingCoveragePrimaryCheck() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("0", inputBundle)

        //mock
        mockSearchCoverageForUpdate("12354644", "1123", true, false)
        mockSearchInsurancePlan()
        val coverageCreateArgCaptor = argumentCaptor<Coverage>()
        `when`(fhirClient.create(coverageCreateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdCoverage = coverageCreateArgCaptor.firstValue
        val updateCoverage = coverageUpdateArgCaptor.allValues.find { it.fhirType() == "Coverage" }!! as Coverage
        Assert.assertFalse(createdCoverage.primary.booleanValue())
        Assert.assertEquals("123546", createdCoverage.subscriberId)
        Assert.assertTrue(updateCoverage.primary.booleanValue())
        Assert.assertEquals("12354644", updateCoverage.subscriberId)
        val valueMoney = updateCoverage.costToBeneficiaryFirstRep.valueMoney
        Assert.assertEquals(10, valueMoney.value.intValueExact())
    }

    @Test
    fun testUpdate_shouldUpdateCostToBeneficiary() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        /*setPrimaryCheckForCoverageConfigParameter("0", inputBundle)*/

        //mock
        var bundle = mockSearchCoverageForUpdate("12354644", "1123", true, false)
        var domainCoverage = bundle.entryFirstRep.resource as Coverage
        domainCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.system = "http://terminology.hl7.org/CodeSystem/coverage-copay-type"
        domainCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.code = "copay"
        domainCoverage.costToBeneficiaryFirstRep.valueMoney.value = BigDecimal("13")
        mockSearchInsurancePlan()
        val coverageCreateArgCaptor = argumentCaptor<Coverage>()
        `when`(fhirClient.create(coverageCreateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdCoverage = coverageCreateArgCaptor.firstValue
        val updateCoverage = coverageUpdateArgCaptor.allValues.find { it.fhirType() == "Coverage" }!! as Coverage
        Assert.assertEquals("123546", createdCoverage.subscriberId)
        Assert.assertEquals("12354644", updateCoverage.subscriberId)
        val valueMoney = updateCoverage.costToBeneficiaryFirstRep.valueMoney
        Assert.assertEquals(10, valueMoney.value.intValueExact())
    }

    @Test
    fun testUpdate_shouldUpdateCoverage_IfSubscriberIdNotGivenButPolicyNumberIsMatch() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, false)
        coverage.subscriberId = null
        coverage.insurancePlan.identifier.value = "22"
        mockSearchInsurancePlan()
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateCoverage = coverageUpdateArgCaptor.allValues.find { it.fhirType() == "Coverage" }!! as Coverage
        Assert.assertEquals(existingSubscriberId, updateCoverage.subscriberId)
        Assert.assertEquals(existingDependent, updateCoverage.dependent)
    }

    @Test
    fun testUpdate_shouldCreateCoverage_evenIfSubscriberIdIsNotMatched_ButPolicyNumberIsMatched() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        setPrimaryCheckForCoverageConfigParameter("0", inputBundle)
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, false)
        coverage.subscriberId = "34323434"
        coverage.insurancePlan.identifier.value = "22"
        mockSearchInsurancePlan()
        val coverageCreateArgCaptor = argumentCaptor<Coverage>()
        `when`(fhirClient.create(coverageCreateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateCoverage = coverageCreateArgCaptor.firstValue
        Assert.assertEquals("34323434", updateCoverage.subscriberId)
        Assert.assertEquals(existingDependent, updateCoverage.dependent)
    }

    @Test
    fun testCreate_shouldCreateCoverage_whenInsuranceUpdateModeIsSnapshotAll() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("1", inputBundle)

        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }
        verify(fhirClient, times(2)).delete(any())
        verify(fhirClient,times(2)).create(isA<Coverage>())
        Assert.assertNotNull(createdCoverages)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "1123")
        Assert.assertFalse(createdCoverages[0].primary.booleanValue())
        Assert.assertEquals(createdCoverages[1].dependent, "2222")
        Assert.assertTrue(createdCoverages[1].primary.booleanValue())
    }

    @Test
    fun testCreate_shouldCreateCoverage_whenInsuranceUpdateModeIsSnapshotOwn() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.INSURANCE_UPDATE_MODE }?.value =
            StringType("SnapshotOwn")
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("1", inputBundle)

        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }

        verify(fhirClient, times(1)).delete(any())
        verify(fhirClient,times(2)).create(isA<Coverage>())
        Assert.assertNotNull(createdCoverages)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "1123")
        Assert.assertFalse(createdCoverages[0].primary.booleanValue())
        Assert.assertEquals(createdCoverages[1].dependent, "2222")
        Assert.assertTrue(createdCoverages[1].primary.booleanValue())
    }

    @Test
    fun testUpdate_shouldCreateCoverage_whenInsuranceUpdateModeIsSnapshotAll() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, true)
        mockSearchInsurancePlan()
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateCoverage = coverageUpdateArgCaptor.allValues.filter { it.fhirType() == "Coverage" }.map { it as Coverage }
        verify(fhirClient,times(2)).delete(any())
        verify(fhirClient,times(2)).create(any())
        verify(fhirClient, never()).update(any<Coverage>())
        Assert.assertNotNull(updateCoverage)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(0, updateCoverage.size)
    }

    @Test
    fun testUpdate_shouldUpdateCoverage_whenInsuranceUpdateModeIsSnapshotOwn() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.INSURANCE_UPDATE_MODE }?.value =
            StringType("SnapshotOwn")
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, false)
        coverage.subscriberId = null
        coverage.insurancePlan.identifier.value = "22"
        mockSearchInsurancePlan()
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateCoverage = coverageUpdateArgCaptor.allValues.filter { it.fhirType() == "Coverage" }.map { it as Coverage }
        verify(fhirClient,times(1)).delete(isA<Coverage>())
        verify(fhirClient,times(1)).create(isA<Coverage>())
        verify(fhirClient,times(1)).update(any<Coverage>())
        Assert.assertNotNull(updateCoverage)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(1, updateCoverage.size)
        Assert.assertEquals(existingSubscriberId, updateCoverage[0].subscriberId)
        Assert.assertEquals(existingDependent, updateCoverage[0].dependent)
    }

    @Test
    fun testCreate_shouldNotSnapshotAndCreateCoverage_ValidationFailsForAll() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("1", inputBundle)
        val coverages =
            inputBundle.entry.filter { it.resource.fhirType() == "Coverage" }.map { it.resource as Coverage }
        coverages.forEach { it.insurancePlan = null }
        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).delete(any())
        verify(fhirClient, never()).create(isA<Coverage>())
        verify(fhirClient, never()).update(isA<Coverage>())
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(1, errorOrWarning?.size)
        Assert.assertEquals(
            "Insurance field Plan Number and Company Name should not be null",
            errorOrWarning?.first()?.details?.text
        )
    }

    @Test
    fun testCreate_shouldNotSnapshotAndCreateCoverage_ValidationFailsForOnlyOneInsurance() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        setPrimaryCheckForCoverageConfigParameter("1", inputBundle)
        val coverages =
            inputBundle.entry.filter { it.resource.fhirType() == "Coverage" }.map { it.resource as Coverage }
        coverages.first().insurancePlan = null
        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }
        verify(fhirClient, times(2)).delete(any())
        verify(fhirClient, times(1)).create(isA<Coverage>())
        Assert.assertNotNull(createdCoverages)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(1, errorOrWarning?.size)
        Assert.assertEquals(
            "Insurance field Plan Number and Company Name should not be null",
            errorOrWarning?.first()?.details?.text
        )
        Assert.assertEquals(1, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "1123")
        Assert.assertFalse(createdCoverages[0].primary.booleanValue())
    }

    @Test
    fun testCreate_shouldIgnoreInvalidPayorAndCoveragePlanType() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        val inputBundle = parser.parseResource(json) as Bundle
        removePrimaryCheckForCoverageConfigParameter(inputBundle)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val coverage = inputBundle.entry.find {
            it.resource.fhirType() == FHIRAllTypes.COVERAGE.toCode()
                    && (it.resource as Coverage).type.codingFirstRep.code == "SFP"
        }?.resource as Coverage
        (coverage.insurancePlan.resource as InsurancePlan).typeFirstRep.codingFirstRep.code = ""
        coverage.type.codingFirstRep.code = null
        //mock
        mockSearchCoverage()
        mockSearchInsurancePlan()
        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Test Group Plan"), eq("identifier"), any()))
            .thenReturn(Bundle())

        val coverageArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.create(coverageArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdResources = coverageArgCaptor.allValues
        val createdCoverages = createdResources.filter { it.fhirType() == "Coverage" }.map { it as Coverage }
        val createdInsurancePlan =
            createdResources.filter { it.fhirType() == "InsurancePlan" }.map { it as InsurancePlan }

        Assert.assertEquals(2, createdCoverages.size)
        Assert.assertEquals(createdCoverages[0].dependent, "1123")
        Assert.assertEquals("CAP", createdCoverages[0].type.codingFirstRep.code)
        Assert.assertTrue(createdCoverages[1].type?.codingFirstRep?.code.isNullOrEmpty())

        Assert.assertEquals(1, createdInsurancePlan.size)
        Assert.assertTrue(createdInsurancePlan[0].typeFirstRep.codingFirstRep.code.isNullOrEmpty())
    }

    @Test
    fun testUpdate_shouldSetDomainPlanTypeIfPlanTypeIsNull() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val inputCoverage = inputBundle.entry.find {
            it.resource.fhirType() == "Coverage"
                    && (it.resource as Coverage).subscriberId == "12354644"
        }?.resource as Coverage
        inputCoverage.type.codingFirstRep.code = ""
        //mock
        val bundle = mockSearchCoverageForUpdate("12354644", "1123", true, false)
        val domainCoverage = bundle.entryFirstRep.resource as Coverage
        domainCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.system =
            "http://terminology.hl7.org/CodeSystem/coverage-copay-type"
        domainCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.code = "copay"
        domainCoverage.costToBeneficiaryFirstRep.valueMoney.value = BigDecimal("13")
        domainCoverage.type.codingFirstRep.code = "SFP"
        mockSearchInsurancePlan()
        val coverageCreateArgCaptor = argumentCaptor<Coverage>()
        `when`(fhirClient.create(coverageCreateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdCoverage = coverageCreateArgCaptor.firstValue
        val updateCoverage = coverageUpdateArgCaptor.allValues.find { it.fhirType() == "Coverage" }!! as Coverage
        Assert.assertEquals("123546", createdCoverage.subscriberId)
        Assert.assertEquals("12354644", updateCoverage.subscriberId)
        Assert.assertEquals("SFP", updateCoverage.type.codingFirstRep.code)
    }

    @Test
    fun testUpdate_shouldClearDomainPlanTypeIfPlanTypeIsACTIVENull() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val inputCoverage = inputBundle.entry.find {
            it.resource.fhirType() == "Coverage"
                    && (it.resource as Coverage).subscriberId == "12354644"
        }?.resource as Coverage
        inputCoverage.type.codingFirstRep.code = "N_U_L_L"
        //mock
        var bundle = mockSearchCoverageForUpdate("12354644", "1123", true, false)
        var domainCoverage = bundle.entryFirstRep.resource as Coverage
        domainCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.system =
            "http://terminology.hl7.org/CodeSystem/coverage-copay-type"
        domainCoverage.costToBeneficiaryFirstRep.type.codingFirstRep.code = "copay"
        domainCoverage.costToBeneficiaryFirstRep.valueMoney.value = BigDecimal("13")
        domainCoverage.type.codingFirstRep.code = "SFP"
        mockSearchInsurancePlan()
        val coverageCreateArgCaptor = argumentCaptor<Coverage>()
        `when`(fhirClient.create(coverageCreateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))
        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val createdCoverage = coverageCreateArgCaptor.firstValue
        val updateCoverage = coverageUpdateArgCaptor.allValues.find { it.fhirType() == "Coverage" }!! as Coverage
        Assert.assertEquals("123546", createdCoverage.subscriberId)
        Assert.assertEquals("12354644", updateCoverage.subscriberId)
        Assert.assertTrue(updateCoverage.type?.codingFirstRep?.code.isNullOrEmpty())
    }

    @Test
    fun testInsurancePlan_shouldInsertAllDetailsInExistingInsurancePlan() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage_insurancePlan.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, true)

        val insurancePlanJson = TestHelper.readResource("/coverage/InsurancePlan_Minimal.json")
        val insurancePlanBundle = parser.parseResource(insurancePlanJson) as Bundle
        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Test Group Plan"), eq("identifier"), any()))
            .thenReturn(insurancePlanBundle)

        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateInsurancePlan =
            coverageUpdateArgCaptor.allValues.filter { it.fhirType() == "InsurancePlan" }.map { it as InsurancePlan }
        verify(fhirClient, times(1)).create(any())
        verify(fhirClient, times(1)).update(any<InsurancePlan>())
        Assert.assertNotNull(updateInsurancePlan)
        Assert.assertEquals(1, updateInsurancePlan.size)
        Assert.assertEquals("SFP", updateInsurancePlan[0].typeFirstRep.codingFirstRep.code)
        Assert.assertEquals(
            "http://varian.com/fhir/CodeSystem/aria-payor-planType",
            updateInsurancePlan[0].typeFirstRep.codingFirstRep.system
        )
        Assert.assertEquals(20, DateTime(updateInsurancePlan[0].period.start).dayOfMonth().get())
        Assert.assertEquals(5, DateTime(updateInsurancePlan[0].period.start).monthOfYear().get())
        Assert.assertEquals(2022, DateTime(updateInsurancePlan[0].period.start).year().get())
        Assert.assertEquals(7, DateTime(updateInsurancePlan[0].period.end).dayOfMonth().get())
        Assert.assertEquals(7, DateTime(updateInsurancePlan[0].period.end).monthOfYear().get())
        Assert.assertEquals(2022, DateTime(updateInsurancePlan[0].period.end).year().get())
        Assert.assertEquals("PAYOR", updateInsurancePlan[0].contactFirstRep.purpose.codingFirstRep.code)
        Assert.assertEquals("LIC CORP INDIA", updateInsurancePlan[0].contactFirstRep.name.text)
        Assert.assertEquals(
            "1",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "phone" }?.value
        )
        Assert.assertEquals(
            "lic_corpindia@corp.in",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "email" }?.value
        )
        Assert.assertEquals(
            "3",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "fax" }?.value
        )
        Assert.assertEquals("D902Updated", updateInsurancePlan[0].contactFirstRep.address.line[0].value)
        Assert.assertEquals("Level 2 Tower 4 Updated", updateInsurancePlan[0].contactFirstRep.address.line[1].value)
        Assert.assertEquals("both", updateInsurancePlan[0].contactFirstRep.address.type.toCode())
        Assert.assertEquals("PUNE Updated", updateInsurancePlan[0].contactFirstRep.address.city)
        Assert.assertEquals("MH Updated", updateInsurancePlan[0].contactFirstRep.address.state)
        Assert.assertEquals("411", updateInsurancePlan[0].contactFirstRep.address.postalCode)
        Assert.assertEquals("INDUpdated", updateInsurancePlan[0].contactFirstRep.address.country)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testInsurancePlan_shouldUpdateInsurancePlan() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage_insurancePlan.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, true)

        val insurancePlanJson = TestHelper.readResource("/coverage/insurancePlan_1.json")
        val insurancePlanBundle = parser.parseResource(insurancePlanJson) as Bundle
        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Test Group Plan"), eq("identifier"), any()))
            .thenReturn(insurancePlanBundle)

        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateInsurancePlan =
            coverageUpdateArgCaptor.allValues.filter { it.fhirType() == "InsurancePlan" }.map { it as InsurancePlan }
        verify(fhirClient, times(1)).create(any())
        verify(fhirClient, times(1)).update(any<InsurancePlan>())
        Assert.assertNotNull(updateInsurancePlan)
        Assert.assertEquals(1, updateInsurancePlan.size)
        Assert.assertEquals("SFP", updateInsurancePlan[0].typeFirstRep.codingFirstRep.code)
        Assert.assertEquals(
            "http://varian.com/fhir/CodeSystem/aria-payor-planType",
            updateInsurancePlan[0].typeFirstRep.codingFirstRep.system
        )
        Assert.assertEquals(20, DateTime(updateInsurancePlan[0].period.start).dayOfMonth().get())
        Assert.assertEquals(5, DateTime(updateInsurancePlan[0].period.start).monthOfYear().get())
        Assert.assertEquals(2022, DateTime(updateInsurancePlan[0].period.start).year().get())
        Assert.assertEquals(7, DateTime(updateInsurancePlan[0].period.end).dayOfMonth().get())
        Assert.assertEquals(7, DateTime(updateInsurancePlan[0].period.end).monthOfYear().get())
        Assert.assertEquals(2022, DateTime(updateInsurancePlan[0].period.end).year().get())
        Assert.assertEquals("LIC CORP INDIA", updateInsurancePlan[0].contactFirstRep.name.text)
        Assert.assertEquals(
            "1",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "phone" }?.value
        )
        Assert.assertEquals(
            "lic_corpindia@corp.in",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "email" }?.value
        )
        Assert.assertEquals(
            "3",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "fax" }?.value
        )
        Assert.assertEquals("D902Updated", updateInsurancePlan[0].contactFirstRep.address.line[0].value)
        Assert.assertEquals("Level 2 Tower 4 Updated", updateInsurancePlan[0].contactFirstRep.address.line[1].value)
        Assert.assertEquals("PUNE Updated", updateInsurancePlan[0].contactFirstRep.address.city)
        Assert.assertEquals("MH Updated", updateInsurancePlan[0].contactFirstRep.address.state)
        Assert.assertEquals("411", updateInsurancePlan[0].contactFirstRep.address.postalCode)
        Assert.assertEquals("INDUpdated", updateInsurancePlan[0].contactFirstRep.address.country)
        Assert.assertEquals(10, updateInsurancePlan[0].planFirstRep.generalCostFirstRep.groupSize)
        Assert.assertEquals(1000, updateInsurancePlan[0].planFirstRep.generalCostFirstRep.cost.value.intValueExact())
        Assert.assertEquals(
            "costPerDiagnosis",
            updateInsurancePlan[0].planFirstRep.generalCostFirstRep.type.codingFirstRep.code
        )
        Assert.assertEquals(10, updateInsurancePlan[0].planFirstRep.generalCost[1].groupSize)
        Assert.assertEquals(50000, updateInsurancePlan[0].planFirstRep.generalCost[1].cost.value.intValueExact())
        Assert.assertEquals(
            "monthlyCostPerMember",
            updateInsurancePlan[0].planFirstRep.generalCost[1].type.codingFirstRep.code
        )
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }


    @Test
    fun testInsurancePlan_shouldNotClearValuesOfExistingInsurancePlan() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage_insurancePlan.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage

        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, true)

        val insurancePlanJson = TestHelper.readResource("/coverage/InsurancePlan_Minimal.json")
        val insurancePlanBundle = parser.parseResource(insurancePlanJson) as Bundle
        coverage.insurancePlan.resource = insurancePlanBundle.entryFirstRep.resource
        coverage.contained[0] = insurancePlanBundle.entryFirstRep.resource

        val domainInsurancePlan = TestHelper.readResource("/coverage/insurancePlan_1.json")
        val domainInsurancePlanBundle = parser.parseResource(domainInsurancePlan) as Bundle
        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Dummy"), eq("identifier"), any()))
            .thenReturn(domainInsurancePlanBundle)

        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateInsurancePlan =
            coverageUpdateArgCaptor.allValues.filter { it.fhirType() == "InsurancePlan" }.map { it as InsurancePlan }
        verify(fhirClient, times(1)).create(any())
        verify(fhirClient, times(1)).update(any<InsurancePlan>())
        Assert.assertNotNull(updateInsurancePlan)
        Assert.assertEquals(1, updateInsurancePlan.size)
        Assert.assertEquals("CAP", updateInsurancePlan[0].typeFirstRep.codingFirstRep.code)
        Assert.assertEquals(
            "http://varian.com/fhir/CodeSystem/aria-payor-planType",
            updateInsurancePlan[0].typeFirstRep.codingFirstRep.system
        )
        Assert.assertEquals(23, DateTime(updateInsurancePlan[0].period.start).dayOfMonth().get())
        Assert.assertEquals(2, DateTime(updateInsurancePlan[0].period.start).monthOfYear().get())
        Assert.assertEquals(2022, DateTime(updateInsurancePlan[0].period.start).year().get())
        Assert.assertEquals(6, DateTime(updateInsurancePlan[0].period.end).dayOfMonth().get())
        Assert.assertEquals(7, DateTime(updateInsurancePlan[0].period.end).monthOfYear().get())
        Assert.assertEquals(2022, DateTime(updateInsurancePlan[0].period.end).year().get())
        Assert.assertEquals("LIC CORP", updateInsurancePlan[0].contactFirstRep.name.text)
        Assert.assertEquals(
            "1111",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "phone" }?.value
        )
        Assert.assertEquals(
            "lic_corp@corp.in",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "email" }?.value
        )
        Assert.assertEquals(
            "333333",
            updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "fax" }?.value
        )
        Assert.assertEquals("D902", updateInsurancePlan[0].contactFirstRep.address.line[0].value)
        Assert.assertEquals("Level 2 Tower 4", updateInsurancePlan[0].contactFirstRep.address.line[1].value)
        Assert.assertEquals("PUNE", updateInsurancePlan[0].contactFirstRep.address.city)
        Assert.assertEquals("MH", updateInsurancePlan[0].contactFirstRep.address.state)
        Assert.assertEquals("411014", updateInsurancePlan[0].contactFirstRep.address.postalCode)
        Assert.assertEquals("IND", updateInsurancePlan[0].contactFirstRep.address.country)
        Assert.assertEquals(10, updateInsurancePlan[0].planFirstRep.generalCostFirstRep.groupSize)
        Assert.assertEquals(1000, updateInsurancePlan[0].planFirstRep.generalCostFirstRep.cost.value.intValueExact())
        Assert.assertEquals(
            "costPerDiagnosis",
            updateInsurancePlan[0].planFirstRep.generalCostFirstRep.type.codingFirstRep.code
        )
        Assert.assertEquals(10, updateInsurancePlan[0].planFirstRep.generalCost[1].groupSize)
        Assert.assertEquals(50000, updateInsurancePlan[0].planFirstRep.generalCost[1].cost.value.intValueExact())
        Assert.assertEquals(
            "monthlyCostPerMember",
            updateInsurancePlan[0].planFirstRep.generalCost[1].type.codingFirstRep.code
        )
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testInsurancePlan_shouldClearValuesOfExistingInsurancePlan() {
        //prepare
        val json = TestHelper.readResource("/coverage/coverage_insurancePlan.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val coverage = inputBundle.entry.find { it.resource.fhirType() == "Coverage" }?.resource as Coverage
        val newInsurancePlan = coverage.insurancePlan.resource as InsurancePlan
        newInsurancePlan.contactFirstRep.address.line = mutableListOf()
        newInsurancePlan.contactFirstRep.address.line.add(StringType("N_U_L_L"))
        newInsurancePlan.contactFirstRep.address.city = "N_U_L_L"
        newInsurancePlan.contactFirstRep.address.state = "N_U_L_L"
        newInsurancePlan.contactFirstRep.address.country = "N_U_L_L"
        newInsurancePlan.contactFirstRep.address.postalCode = "N_U_L_L"
        newInsurancePlan.contactFirstRep.telecom.forEach { it.value = "N_U_L_L" }
        newInsurancePlan.contactFirstRep.name.text = "N_U_L_L"
        newInsurancePlan.period = null
        newInsurancePlan.period.addExtension("ServicePeriodStart", StringType("N_U_L_L"))
        newInsurancePlan.period.addExtension("ServicePeriodEnd", StringType("N_U_L_L"))
        newInsurancePlan.typeFirstRep.codingFirstRep.code = "N_U_L_L"
        //mock
        val existingSubscriberId = coverage.subscriberId
        val existingDependent = coverage.dependent
        mockSearchCoverageForUpdate(existingSubscriberId, existingDependent, false, true)

        val domainInsurancePlan = TestHelper.readResource("/coverage/insurancePlan_1.json")
        val domainInsurancePlanBundle = parser.parseResource(domainInsurancePlan) as Bundle
        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Test Group Plan"), eq("identifier"), any()))
            .thenReturn(domainInsurancePlanBundle)

        val coverageUpdateArgCaptor = argumentCaptor<DomainResource>()
        `when`(fhirClient.update(coverageUpdateArgCaptor.capture())).thenReturn(MethodOutcome(IdType("Coverage/Coverage-id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val updateInsurancePlan =
            coverageUpdateArgCaptor.allValues.filter { it.fhirType() == "InsurancePlan" }.map { it as InsurancePlan }
        verify(fhirClient, times(1)).create(any())
        verify(fhirClient, times(1)).update(any<InsurancePlan>())
        Assert.assertNotNull(updateInsurancePlan)
        Assert.assertEquals(1, updateInsurancePlan.size)
        Assert.assertNull(updateInsurancePlan[0].typeFirstRep.codingFirstRep.code)
        Assert.assertEquals(
            "http://varian.com/fhir/CodeSystem/aria-payor-planType",
            updateInsurancePlan[0].typeFirstRep.codingFirstRep.system
        )
        Assert.assertNull(updateInsurancePlan[0].period.start)
        Assert.assertNull(updateInsurancePlan[0].period.end)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.name.text)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "phone" }?.value)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "email" }?.value)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.telecom.find { it.use.toCode() == "work" && it.system.toCode() == "fax" }?.value)
        Assert.assertTrue(updateInsurancePlan[0].contactFirstRep.address.line.isNullOrEmpty())
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.address.city)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.address.state)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.address.postalCode)
        Assert.assertNull(updateInsurancePlan[0].contactFirstRep.address.country)
        Assert.assertEquals(10, updateInsurancePlan[0].planFirstRep.generalCostFirstRep.groupSize)
        Assert.assertEquals(1000, updateInsurancePlan[0].planFirstRep.generalCostFirstRep.cost.value.intValueExact())
        Assert.assertEquals(
            "costPerDiagnosis",
            updateInsurancePlan[0].planFirstRep.generalCostFirstRep.type.codingFirstRep.code
        )
        Assert.assertEquals(10, updateInsurancePlan[0].planFirstRep.generalCost[1].groupSize)
        Assert.assertEquals(50000, updateInsurancePlan[0].planFirstRep.generalCost[1].cost.value.intValueExact())
        Assert.assertEquals(
            "monthlyCostPerMember",
            updateInsurancePlan[0].planFirstRep.generalCost[1].type.codingFirstRep.code
        )
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    private fun mockSearchCoverageForUpdate(subscriberId: String, dependent: String, isPrimary: Boolean, returnNullBundle: Boolean): Bundle {
        val coverageBundle = Bundle()
        val coverage = Coverage()
        coverage.id = "Coverage/Coverage-1"
        coverage.subscriberId = subscriberId
        coverage.dependent = dependent
        coverage.insurancePlan = Reference()
        coverage.insurancePlan.display = "22"
        coverage.primary = BooleanType(isPrimary)
        coverage.lastModificationUser = Reference("Practitioner-1014")
        coverageBundle.addEntry(Bundle.BundleEntryComponent().setResource(coverage))

        val coverage2 = Coverage()
        coverage2.id = "Coverage/Coverage-2"
        coverage2.subscriberId = subscriberId
        coverage2.dependent = dependent
        coverage2.insurancePlan = Reference()
        coverage2.insurancePlan.display = "22"
        coverage2.primary = BooleanType(isPrimary)
        coverage2.lastModificationUser = Reference("Practitioner-2014")
        coverageBundle.addEntry(Bundle.BundleEntryComponent().setResource(coverage2))

        if(returnNullBundle) {
            `when`(fhirClient.search(eq("Coverage"), eq("patient"), eq("Patient/Patient-1"), eq("_count"), eq("50")))
                .thenReturn(coverageBundle, Bundle())
        } else {
            `when`(fhirClient.search(eq("Coverage"), eq("patient"), eq("Patient/Patient-1"), eq("_count"), eq("50")))
                .thenReturn(coverageBundle)
        }

        val coverageEligibilityResponseBundle = Bundle()
        coverageEligibilityResponseBundle.addEntry(Bundle.BundleEntryComponent().setResource(CoverageEligibilityResponse()))
        `when`(fhirClient.search(eq("CoverageEligibilityResponse"), eq("coverage"), any()))
                .thenReturn(coverageEligibilityResponseBundle)
        return  coverageBundle
    }

    private fun mockSearchCoverage() {
        val coverageBundle = Bundle()
        val coverage = Coverage()
        coverage.id = "Coverage/Coverage-1"
        coverage.primary = BooleanType(true)
        coverage.insurancePlan = Reference()
        coverage.insurancePlan.display = "22"
        coverage.subscriberId = "123456"
        coverage.dependent = "2222"
        coverage.lastModificationUser = Reference("Practitioner-1014")
        coverageBundle.addEntry(Bundle.BundleEntryComponent().setResource(coverage))

        val coverage2 = Coverage()
        coverage2.id = "Coverage/Coverage-2"
        coverage2.primary = BooleanType(true)
        coverage2.insurancePlan = Reference()
        coverage2.insurancePlan.display = "1"
        coverage2.subscriberId = "123456"
        coverage2.dependent = "2222"
        coverage2.lastModificationUser = Reference("Practitioner-2014")
        coverageBundle.addEntry(Bundle.BundleEntryComponent().setResource(coverage2))
        `when`(fhirClient.search(eq("Coverage"), eq("patient"), eq("Patient/Patient-1"), eq("_count"), eq("50")))
                .thenReturn(coverageBundle)
    }

    private fun mockSearchInsurancePlan() {
        val insurancePlanBundle = Bundle()
        val insurancePlan = InsurancePlan()
        insurancePlan.id = "InsurancePlan/InsurancePlan-1"
        insurancePlanBundle.addEntry(Bundle.BundleEntryComponent().setResource(insurancePlan))

        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Test Group Plan"), eq("identifier"), any()))
                .thenReturn(insurancePlanBundle)

        `when`(fhirClient.search(eq("InsurancePlan"), eq("name"), eq("Test Group Plan 30"), eq("identifier"), any()))
                .thenReturn(insurancePlanBundle)
    }

    private fun removePrimaryCheckForCoverageConfigParameter(bundle: Bundle) {
        var parameters = bundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameters.parameter.removeIf { param -> param.name == ParametersUtility.ALLOW_UPDATE_ON_COVERAGE_PRIMARY_CHECK }
    }

    private fun setPrimaryCheckForCoverageConfigParameter(isAllowed: String, bundle: Bundle) {
        var parameters = bundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        var parameter = parameters.parameter.find { param -> param.name == ParametersUtility.ALLOW_UPDATE_ON_COVERAGE_PRIMARY_CHECK }
        if (parameter == null) {
            parameter = Parameters.ParametersParameterComponent()
            parameter.name = ParametersUtility.ALLOW_UPDATE_ON_COVERAGE_PRIMARY_CHECK
        }
        parameter.value = StringType(isAllowed)
    }
}
