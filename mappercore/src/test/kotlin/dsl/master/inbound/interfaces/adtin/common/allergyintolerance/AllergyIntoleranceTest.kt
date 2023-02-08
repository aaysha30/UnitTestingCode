package dsl.master.inbound.interfaces.adtin.common.allergyintolerance

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.AllergyIntolerance
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.constant.XlateConstant.ACTIVE_NULL_LITERAL
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
import java.util.*

class allergyintolerance {
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
            scriptInformation = scripts.getHandlerFor("aria", "AllergyIntoleranceSave")!!.get()
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
        parameters[ParameterConstant.ALLERGY_CATEGORY_CODE] = parser.parseResource(TestHelper.readResource("/allergyintolerance/allergyCategoryValueSet.json")) as ValueSet
    }

    @Test
    fun testCreate_shouldDeleteAllAllergies_whenAllergyUpdateModeIsSnapshotAll() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockSearchAllergy()
        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()
        Mockito.`when`(fhirClient.create(allergyArgCaptor.capture()))
                .thenReturn(MethodOutcome(IdType("AllergyIntolerance/allergy")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        Mockito.verify(fhirClient, times(2)).update(allergyArgCaptor.capture())
        Mockito.verify(fhirClient, times(1)).create(any())
        val createdOrDeletedAllergies = allergyArgCaptor.allValues
        val createdAllergy = createdOrDeletedAllergies.filter { it.code.text == "Dairy product (Lactose)" }
        Assert.assertNotNull(createdAllergy)
        Assert.assertEquals(1, createdAllergy.size)
        Assert.assertEquals("Drug", createdAllergy[0].allergyCategoryInAria.text)
        val deletedAllergies =
                createdOrDeletedAllergies.filter { it.verificationStatus.codingFirstRep.code == "entered-in-error" }
        Assert.assertEquals(2, deletedAllergies.size)
        Assert.assertEquals("Dairy product (Lactose) 1", deletedAllergies[0].code.text)
        Assert.assertEquals("Dairy product (Lactose) 2", deletedAllergies[1].code.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testCreate_shouldCreateAllergy_whenAllergyUpdateModeIsSnapshotOwn() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.ALLERGY_UPDATE_MODE }?.value =
                StringType("SnapshotOwn")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockSearchAllergy()
        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()
        Mockito.`when`(fhirClient.create(allergyArgCaptor.capture()))
                .thenReturn(MethodOutcome(IdType("AllergyIntolerance/allergy")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        Mockito.verify(fhirClient, times(1)).update(allergyArgCaptor.capture())
        Mockito.verify(fhirClient, times(1)).create(any())
        val createdOrDeletedAllergies = allergyArgCaptor.allValues
        val createdAllergy = createdOrDeletedAllergies.filter { it.code.text == "Dairy product (Lactose)" }
        Assert.assertNotNull(createdAllergy)
        Assert.assertEquals(1, createdAllergy.size)
        Assert.assertEquals("Drug", createdAllergy[0].allergyCategoryInAria.text)
        val deletedAllergies =
                createdOrDeletedAllergies.filter { it.verificationStatus.codingFirstRep.code == "entered-in-error" }
        Assert.assertEquals(1, deletedAllergies.size)
        Assert.assertEquals("Dairy product (Lactose) 1", deletedAllergies[0].code.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    //region "ActiveNull scenarios"

    @Test
    fun testUpdate_shouldDeleteAllReactionsAndOnset_whenReceivedWithDoubleQuotes() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var allergyResource = inputBundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        allergyResource.reaction = mutableListOf()
        allergyResource.onset = StringType(ACTIVE_NULL_LITERAL)
        allergyResource.reactionFirstRep.addManifestation(CodeableConcept(Coding().setCode(ACTIVE_NULL_LITERAL)))
        allergyResource.noteFirstRep.text = ACTIVE_NULL_LITERAL
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.ALLERGY_UPDATE_MODE }?.value =
                StringType("Matching")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val bundle = parser.parseResource(json) as Bundle
        bundle.entry.removeIf { it.resource.fhirType() != "AllergyIntolerance" }
        val domainAllergyIntolerance = bundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        domainAllergyIntolerance.allergyCategoryInAria.codingFirstRep.code = "1"
        Mockito.`when`(fhirClient.search(eq("AllergyIntolerance"), eq("patient"), eq("Patient-1"), eq("status"), any()))
                .thenReturn(bundle)

        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        Mockito.verify(fhirClient, times(1)).update(allergyArgCaptor.capture())
        val updatedAllergies = allergyArgCaptor.firstValue
        Assert.assertTrue(updatedAllergies.reaction.isNullOrEmpty())
        Assert.assertNull(updatedAllergies.onset)
        Assert.assertNull(updatedAllergies.noteFirstRep.text)
    }

    @Test
    fun testUpdate_shouldKeepExistingReactions_whenReceivedReactionIsNullInSameAllergy() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var allergyResource = inputBundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        //bundle.addEntry(Bundle.BundleEntryComponent().setResource(allergyResource))
        allergyResource.onset = null
        allergyResource.reaction = null
        allergyResource.note = null
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.ALLERGY_UPDATE_MODE }?.value =
                StringType("Matching")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val bundle = parser.parseResource(json) as Bundle
        bundle.entry.removeIf { it.resource.fhirType() != "AllergyIntolerance" }
        val domainAllergyIntolerance = bundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        domainAllergyIntolerance.allergyCategoryInAria.codingFirstRep.code = "1"
        val domainReactions = domainAllergyIntolerance.reaction
        Mockito.`when`(fhirClient.search(eq("AllergyIntolerance"), eq("patient"), eq("Patient-1"), eq("status"), any()))
                .thenReturn(bundle)

        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        Mockito.verify(fhirClient, times(1)).update(allergyArgCaptor.capture())
        val updatedAllergies = allergyArgCaptor.firstValue
        Assert.assertFalse(updatedAllergies.reaction.isNullOrEmpty())
        Assert.assertEquals(domainReactions.size, updatedAllergies.reaction.size)
        Assert.assertEquals(domainReactions[0].manifestationFirstRep.codingFirstRep.code, updatedAllergies.reactionFirstRep.manifestationFirstRep.codingFirstRep.code)
        Assert.assertEquals(domainAllergyIntolerance.onsetDateTimeType, updatedAllergies.onsetDateTimeType)
        Assert.assertEquals(domainAllergyIntolerance.noteFirstRep.text, updatedAllergies.noteFirstRep.text)
    }

    @Test
    fun testUpdate_shouldKeepExistingReactionsAndOnset_whenReceivedReactionAndOnsetIsEmptyInSameAllergy() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var allergyResource = inputBundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        //bundle.addEntry(Bundle.BundleEntryComponent().setResource(allergyResource))
        allergyResource.onset = DateTimeType("")
        allergyResource.reaction = mutableListOf()
        allergyResource.noteFirstRep.text = ""
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.ALLERGY_UPDATE_MODE }?.value =
                StringType("Matching")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val bundle = parser.parseResource(json) as Bundle
        bundle.entry.removeIf { it.resource.fhirType() != "AllergyIntolerance" }
        val domainAllergyIntolerance = bundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        domainAllergyIntolerance.allergyCategoryInAria.codingFirstRep.code = "1"
        val domainReactions = domainAllergyIntolerance.reaction
        Mockito.`when`(fhirClient.search(eq("AllergyIntolerance"), eq("patient"), eq("Patient-1"), eq("status"), any()))
                .thenReturn(bundle)

        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        Mockito.verify(fhirClient, times(1)).update(allergyArgCaptor.capture())
        val updatedAllergies = allergyArgCaptor.firstValue
        Assert.assertFalse(updatedAllergies.reaction.isNullOrEmpty())
        Assert.assertEquals(domainReactions.size, updatedAllergies.reaction.size)
        Assert.assertEquals(domainReactions[0].manifestationFirstRep.codingFirstRep.code, updatedAllergies.reactionFirstRep.manifestationFirstRep.codingFirstRep.code)
        Assert.assertEquals(domainAllergyIntolerance.onsetDateTimeType, updatedAllergies.onsetDateTimeType)
        Assert.assertEquals(domainAllergyIntolerance.noteFirstRep.text, updatedAllergies.noteFirstRep.text)
    }

    @Test
    fun testUpdate_shouldReturnNewValues_whenReceivedReactionAndOnsetWithNewValueForSameAllergy() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var allergyResource = inputBundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        //bundle.addEntry(Bundle.BundleEntryComponent().setResource(allergyResource))
        val dt = DateTime(2020, 11, 2, 0, 0, 0).toDate()
        allergyResource.onset = DateTimeType(dt)
        allergyResource.reactionFirstRep.manifestationFirstRep.codingFirstRep.code = "new Reaction"
        allergyResource.noteFirstRep.text = "new comments"

        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.ALLERGY_UPDATE_MODE }?.value =
                StringType("Matching")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val bundle = parser.parseResource(json) as Bundle
        bundle.entry.removeIf { it.resource.fhirType() != "AllergyIntolerance" }
        val domainAllergyIntolerance = bundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        domainAllergyIntolerance.allergyCategoryInAria.codingFirstRep.code = "1"
        Mockito.`when`(fhirClient.search(eq("AllergyIntolerance"), eq("patient"), eq("Patient-1"), eq("status"), any()))
                .thenReturn(bundle)

        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        Mockito.verify(fhirClient, times(1)).update(allergyArgCaptor.capture())
        val updatedAllergies = allergyArgCaptor.firstValue
        Assert.assertFalse(updatedAllergies.reaction.isNullOrEmpty())
        Assert.assertEquals("new Reaction", updatedAllergies.reactionFirstRep.manifestationFirstRep.codingFirstRep.code)
        Assert.assertEquals(2, updatedAllergies.onsetDateTimeType.day)
        Assert.assertEquals("new comments", updatedAllergies.noteFirstRep.text)
    }

    @Test
    fun testCreate_shouldCreateAllergy_WithCurrentOnSet_WhenOnsetIsNull() {
        //prepare
        val json = TestHelper.readResource("/allergyintolerance/allergyintolerance.json")
        var inputBundle = parser.parseResource(json) as Bundle
        val parameterResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.ALLERGY_UPDATE_MODE }?.value =
                StringType("SnapshotOwn")

        val allergyIntolerance =
                inputBundle.entry.find { it.resource.fhirType() == "AllergyIntolerance" }?.resource as AllergyIntolerance
        allergyIntolerance.onset = null
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        Mockito.`when`(fhirClient.search(eq("AllergyIntolerance"), eq("patient"), eq("Patient-1"), eq("status"), any()))
                .thenReturn(Bundle())

        val allergyArgCaptor = argumentCaptor<AllergyIntolerance>()
        Mockito.`when`(fhirClient.create(allergyArgCaptor.capture()))
                .thenReturn(MethodOutcome(IdType("AllergyIntolerance/allergy")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        //Mockito.verify(fhirClient, times(0)).update(allergyArgCaptor.capture())
        Mockito.verify(fhirClient, times(1)).create(any())
        val createdOrDeletedAllergies = allergyArgCaptor.allValues
        val createdAllergy = createdOrDeletedAllergies.filter { it.code.text == "Dairy product (Lactose)" }
        Assert.assertNotNull(createdAllergy)
        Assert.assertEquals(1, createdAllergy.size)
        Assert.assertTrue(createdAllergy[0].onset is DateTimeType)
        Assert.assertTrue(createdAllergy[0].onsetDateTimeType.isToday)
        val errorOrWarning =
                outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    //endregion

    private fun mockSearchAllergy() {
        val allergyBundle = Bundle()
        val allergy = AllergyIntolerance()
        allergy.code.text = "Dairy product (Lactose) 1"
        allergy.code.id = "Drug 1"
        allergy.allergyCategoryInAria = CodeableConcept()
        allergy.allergyCategoryInAria.codingFirstRep.code = "1"
        allergy.allergyCategoryInAria.codingFirstRep.code = "Drug"
        allergy.verificationStatus.codingFirstRep.code = "confirmed"
        allergy.lastModificationUser = Reference("Practitioner-1014")
        allergyBundle.addEntry(Bundle.BundleEntryComponent().setResource(allergy))

        val allergy2 = AllergyIntolerance()
        allergy2.code.text = "Dairy product (Lactose) 2"
        allergy2.code.id = "Drug 2"
        allergy2.allergyCategoryInAria = CodeableConcept()
        allergy2.allergyCategoryInAria.codingFirstRep.code = "Drug"
        allergy2.verificationStatus.codingFirstRep.code = "confirmed"
        allergy2.lastModificationUser = Reference("Practitioner-2014")
        allergyBundle.addEntry(Bundle.BundleEntryComponent().setResource(allergy2))

        Mockito.`when`(fhirClient.search(eq("AllergyIntolerance"), eq("patient"), eq("Patient-1"), eq("status"), any()))
                .thenReturn(allergyBundle)
    }
}