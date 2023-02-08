package dsl.master.inbound.interfaces.adtin.common.consent

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.github.windpapi4j.WinDPAPI
import com.github.windpapi4j.WinDPAPI.CryptProtectFlag
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.Consent
import com.varian.fhir.resources.Patient
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
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.`when`
import java.util.*


class PatientDirectiveTest {
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
            scriptInformation = scripts.getHandlerFor("aria", "PatientDirectiveSave")!!.get()
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
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
    }

    @Test
    fun test_shouldCreate_PatientDirective() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(Bundle())
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.create(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Flag-1")))


        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).create(any())
        val createdFlag = flagCaptor.firstValue
        Assert.assertEquals("Directive1", createdFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdFlag.policyRule.text)
        Assert.assertEquals("Patient-1", createdFlag.patient.reference)
    }

    @Test
    fun test_shouldCreate_PatientDirective_AndMergeCommentsOfSameDirective() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val consent1 = Consent()
        consent1.categoryFirstRep.codingFirstRep.system =
            "http://varian.com/fhir/CodeSystem/aria-consent-patientDirectiveType"
        consent1.categoryFirstRep.codingFirstRep.display = "MergeDirective"
        consent1.policyRule.text = "Comment1"
        consent1.scope.codingFirstRep.code = "adr"
        val consent2 = Consent()
        consent2.categoryFirstRep.codingFirstRep.system =
            "http://varian.com/fhir/CodeSystem/aria-consent-patientDirectiveType"
        consent2.categoryFirstRep.codingFirstRep.display = "MergeDirective"
        consent2.policyRule.text = "Comment2"
        consent2.scope.codingFirstRep.code = "adr"
        inputBundle.addEntry(Bundle.BundleEntryComponent().setResource(consent1))
        inputBundle.addEntry(Bundle.BundleEntryComponent().setResource(consent2))
        parameters[ParameterConstant.BUNDLE] = inputBundle
        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
            .thenReturn(Bundle())
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.create(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Flag-1")))


        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(2)).create(any())
        val createdConsent1 = flagCaptor.firstValue
        val createdConsent2 = flagCaptor.secondValue
        Assert.assertEquals("Directive1", createdConsent1.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdConsent1.policyRule.text)
        Assert.assertEquals("Patient-1", createdConsent1.patient.reference)
        Assert.assertEquals("MergeDirective", createdConsent2.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("Comment1\r\nComment2", createdConsent2.policyRule.text)
        Assert.assertEquals("Patient-1", createdConsent2.patient.reference)
    }

    @Test
    fun test_shouldCreate_PatientDirective_WithoutComment() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val consent1 = Consent()
        consent1.categoryFirstRep.codingFirstRep.system =
            "http://varian.com/fhir/CodeSystem/aria-consent-patientDirectiveType"
        consent1.categoryFirstRep.codingFirstRep.display = "MergeDirective"
        consent1.policyRule.text = null
        consent1.scope.codingFirstRep.code = "adr"
        val consent2 = Consent()
        consent2.categoryFirstRep.codingFirstRep.system =
            "http://varian.com/fhir/CodeSystem/aria-consent-patientDirectiveType"
        consent2.categoryFirstRep.codingFirstRep.display = "MergeDirective"
        consent2.policyRule.text = null
        consent2.scope.codingFirstRep.code = "adr"
        inputBundle.addEntry(Bundle.BundleEntryComponent().setResource(consent1))
        inputBundle.addEntry(Bundle.BundleEntryComponent().setResource(consent2))
        parameters[ParameterConstant.BUNDLE] = inputBundle
        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
            .thenReturn(Bundle())
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.create(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Flag-1")))


        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(2)).create(any())
        val createdConsent1 = flagCaptor.firstValue
        val createdConsent2 = flagCaptor.secondValue
        Assert.assertEquals("Directive1", createdConsent1.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdConsent1.policyRule.text)
        Assert.assertEquals("Patient-1", createdConsent1.patient.reference)
        Assert.assertEquals("MergeDirective", createdConsent2.categoryFirstRep.codingFirstRep.display)
        Assert.assertNull(createdConsent2.policyRule.text)
        Assert.assertEquals("Patient-1", createdConsent2.patient.reference)
    }

    @Test
    fun test_shouldUpdate_PatientDirective() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(
                        Bundle().addEntry(
                                Bundle.BundleEntryComponent().setResource(getFlag("directive1", "comments old"))
                        )
                )
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.update(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-1")))

        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).update(any())
        val updatedFlag = flagCaptor.firstValue
        Assert.assertEquals("directive1", updatedFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments old\r\ncomments", updatedFlag.policyRule.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldSnapshotCommentByDefault_PatientDirective() {
        //snapshot directive comment(default behaviour) when related config is not found
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //remove config for DENY_SNAPSHOT_ON_DIRECTIVE_COMMENT
        val parametersResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.removeIf { it.name == ParametersUtility.DENY_SNAPSHOT_ON_DIRECTIVE_COMMENT }

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(
                        Bundle().addEntry(
                                Bundle.BundleEntryComponent().setResource(getFlag("Directive1", "comments old"))
                        )
                )
        val flagCaptor = argumentCaptor<com.varian.fhir.resources.Consent>()
        `when`(fhirClient.update(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-1")))

        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).update(any())
        val createdFlag = flagCaptor.firstValue
        Assert.assertEquals("Directive1", createdFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdFlag.policyRule.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldSnapshotComment_PatientDirective() {
        //snapshot directive comment when related config value is 0
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //remove config for DENY_SNAPSHOT_ON_DIRECTIVE_COMMENT
        val parametersResource =
                inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == ParametersUtility.DENY_SNAPSHOT_ON_DIRECTIVE_COMMENT }?.value =
                StringType("0")

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(
                        Bundle().addEntry(
                                Bundle.BundleEntryComponent().setResource(getFlag("Directive1", "comments old"))
                        )
                )
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.update(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-1")))

        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).update(any())
        val createdFlag = flagCaptor.firstValue
        Assert.assertEquals("Directive1", createdFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdFlag.policyRule.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldCreateAndUpdate_PatientDirectives() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val flagToUpdate = getFlag("Directive2", "new comments2")
        inputBundle.addEntry(Bundle.BundleEntryComponent().setResource(flagToUpdate))

        parameters[ParameterConstant.BUNDLE] = inputBundle
        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(
                        Bundle().addEntry(
                                Bundle.BundleEntryComponent().setResource(getFlag(flagToUpdate.categoryFirstRep.codingFirstRep.display, "old comments"))
                        )
                )
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.create(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-1")))
        `when`(fhirClient.update(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-2")))

        scripts.run(parameters, scriptInformation)
        verify(fhirClient, times(1)).create(any())
        verify(fhirClient, times(1)).update(any())
        val createdFlag = flagCaptor.firstValue
        val updatedFlag = flagCaptor.secondValue
        Assert.assertEquals("Directive1", createdFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdFlag.policyRule.text)
        Assert.assertEquals("Directive2", updatedFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("old comments\r\nnew comments2", updatedFlag.policyRule.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldNotUpdate_IfNoCommentsInInput() {
        //input note is null
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle
        (inputBundle.entry.find { it.resource.fhirType() == "Consent" }?.resource as Consent).policyRule = null
        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(
                        Bundle().addEntry(
                                Bundle.BundleEntryComponent().setResource(getFlag("Directive1", "comments1"))
                        )
                )
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.update(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-1")))

        scripts.run(parameters, scriptInformation)

        verify(fhirClient, never()).create(any())
        verify(fhirClient, never()).update(any())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldUpdateComments_addNewComment() {
        //existing directive has no comments. existing note is null
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(getFlag("Directive1", null))))
        val flagCaptor = argumentCaptor<Consent>()
        `when`(fhirClient.update(flagCaptor.capture())).thenReturn(MethodOutcome(IdType("Consent-1")))

        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).update(any())
        val createdFlag = flagCaptor.firstValue
        Assert.assertEquals("Directive1", createdFlag.categoryFirstRep.codingFirstRep.display)
        Assert.assertEquals("comments", createdFlag.policyRule.text)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldDoNothing_PatientDirectivesEmpty() {
        parameters[ParameterConstant.BUNDLE] = Bundle().addEntry(Bundle.BundleEntryComponent().setResource(Patient()))
        scripts.run(parameters, scriptInformation)
        verify(fhirClient, never()).update(any())
        verify(fhirClient, never()).create(any())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_shouldDoNothing_BundleHasFlagOtherThanPatientDirectives() {
        parameters[ParameterConstant.BUNDLE] = Bundle().addEntry(Bundle.BundleEntryComponent().setResource(Consent()))
        scripts.run(parameters, scriptInformation)
        verify(fhirClient, never()).update(any())
        verify(fhirClient, never()).create(any())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_createPatientDirectives_shouldNotThrowError() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(Bundle())
        val ex = UnprocessableEntityException()
        val oo = OperationOutcome()
        oo.issueFirstRep.severity = OperationOutcome.IssueSeverity.ERROR
        oo.issueFirstRep.details.codingFirstRep.system = "http://varian.com/fhir/exceptions"
        oo.issueFirstRep.details.codingFirstRep.code = "CODE_NOT_PRESENT"
        oo.issueFirstRep.details.text = "code is mandatory"
        ex.operationOutcome = oo
        `when`(fhirClient.create(any())).thenThrow(ex)

        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).create(any())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, errorOrWarning[0].severity)
        Assert.assertEquals(2, errorOrWarning[0].details.coding.size)
        Assert.assertEquals("http://varian.com/fhir/exceptions", errorOrWarning[0].details.coding[0].system)
        Assert.assertEquals("CODE_NOT_PRESENT", errorOrWarning[0].details.coding[0].code)
        Assert.assertEquals("code is mandatory", errorOrWarning[0].details.text)
        Assert.assertEquals("http://varian.com/fhir/hl7exceptions", errorOrWarning[0].details.coding[1].system)
        Assert.assertEquals("Consent", errorOrWarning[0].details.coding[1].code)
    }

    @Test
    fun test_updatePatientDirective_shouldNotThrowError() {
        val json = TestHelper.readResource("/consent/ConsentBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        `when`(fhirClient.search("Consent", "patient", "Patient-1", "status", "active"))
                .thenReturn(
                        Bundle().addEntry(
                                Bundle.BundleEntryComponent().setResource(getFlag("Directive1", "comments old"))
                        )
                )

        val ex = UnprocessableEntityException()
        val oo = OperationOutcome()
        oo.issueFirstRep.severity = OperationOutcome.IssueSeverity.ERROR
        oo.issueFirstRep.details.codingFirstRep.system = "http://varian.com/fhir/exceptions"
        oo.issueFirstRep.details.codingFirstRep.code = "CODE_NOT_PRESENT"
        oo.issueFirstRep.details.text = "code is mandatory"
        ex.operationOutcome = oo
        `when`(fhirClient.update(any())).thenThrow(ex)
        scripts.run(parameters, scriptInformation)

        verify(fhirClient, times(1)).update(any())

        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, errorOrWarning[0].severity)
        Assert.assertEquals(2, errorOrWarning[0].details.coding.size)
        Assert.assertEquals("http://varian.com/fhir/exceptions", errorOrWarning[0].details.coding[0].system)
        Assert.assertEquals("CODE_NOT_PRESENT", errorOrWarning[0].details.coding[0].code)
        Assert.assertEquals("code is mandatory", errorOrWarning[0].details.text)
        Assert.assertEquals("http://varian.com/fhir/hl7exceptions", errorOrWarning[0].details.coding[1].system)
        Assert.assertEquals("Consent", errorOrWarning[0].details.coding[1].code)
    }

    private fun getFlag(directiveName: String?, comment: String?): Consent {
        val flag = Consent()
        flag.scope.codingFirstRep.code = "adr"
        flag.scope.codingFirstRep.system = "http://terminology.hl7.org/CodeSystem/consentscope"
        if (!comment.isNullOrEmpty()) {
            flag.policyRule = CodeableConcept().setText(comment)
        }
        if (!directiveName.isNullOrEmpty()) {
            flag.categoryFirstRep.codingFirstRep.display = directiveName
        }
        return flag
    }

    //    @Test
    fun test_encrypt_decrypt() {
        val winDPAPI = WinDPAPI.newInstance(CryptProtectFlag.CRYPTPROTECT_UI_FORBIDDEN)
        val cs = "fhir"
        val clearTextBytes = cs.toByteArray()
        val cipherTextBytes = Base64.getEncoder().encodeToString(winDPAPI.protectData(clearTextBytes))
        val decryptedBytes = winDPAPI.unprotectData(Base64.getDecoder().decode(cipherTextBytes.toByteArray()))
        val decryptedMessage = String(decryptedBytes)
        check(cs == decryptedMessage) {
            // should not happen
            "$cs != $decryptedMessage"
        }
        println("Encrypted Client Secret: $cipherTextBytes")
        println(decryptedMessage)
    }
}
