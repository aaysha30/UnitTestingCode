package dsl.master.inbound.interfaces.adtin.patientmerge

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.*
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.IdType
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class PatientMergeTest {
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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientMerge")!!.get()
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
        fhirClient = Mockito.mock(FhirClient::class.java)
        outcome = Outcome(parser)
        clientDecor = ClientDecor(fhirClient, outcome)
        parameters[ParameterConstant.CLIENT_DECOR] = clientDecor
        parameters[ParameterConstant.BUNDLE_UTILITY] = fhirFactory.getBundleUtility()
        parameters[ParameterConstant.PARAMETERS_UTILITY] = fhirFactory.getParametersUtility()
        parameters[ParameterConstant.PATIENT_UTILITY] = fhirFactory.getPatientUtility()
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters[ParameterConstant.USER] = TestHelper.getPractitioner("Practitioner-1014", "headlessclient")
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger.initCLoverLogger(mock())
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        parameters[ParameterConstant.ATTACH_HOSPITAL_Departments] = false
        parameters[ParameterConstant.UPDATE_PRIMARY_DEPARTMENT] = true
        parameters[ParameterConstant.SNAPSHOT_DEPARTMENTS] = true
        parameters[ParameterConstant.HOSPITAL_DEPT_BUNDLE] = Bundle()
        `when`(fhirClient.search(ArgumentMatchers.contains("CareTeam"), ArgumentMatchers.contains("patient"), ArgumentMatchers.any())).thenReturn(Bundle())
    }

    @Test
    fun test_pidPatient_and_mrgPatient_notPresent() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergePatient_SinglePidPatientAndMergePatient.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val noPatient = Bundle()
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        any()
                )
        ).thenReturn(noPatient, noPatient)

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val expectedErrorMessage =
                "Both patients are not present for merging"

        Assert.assertTrue(outcome.getOperationOutcome().issue.size == 1)
        Assert.assertTrue(outcome.getOperationOutcome().issueFirstRep.severity.toCode() == "warning")
        Assert.assertEquals(expectedErrorMessage, outcome.getOperationOutcome().issueFirstRep.details.text)
        verify(fhirClient, times(2))
                .search(ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"), any())
        verify(fhirClient, never()).create(any())
        verify(fhirClient, never()).update(any())
    }

    @Test
    fun test_pidPatientPresent_and_mrgPatient_notPresent() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergePatient_SinglePidPatientAndMergePatient.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val pidPatient = getPatientBundle("1", "pidPatient")
        val noPatient = Bundle()
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        any()
                )
        ).thenReturn(pidPatient, noPatient)

        val fullPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("_id"),
                        ArgumentMatchers.any()
                )
        )
                .thenReturn(fullPatient)

        `when`(
            fhirClient.search(
                ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("name"), ArgumentMatchers.any(),
                ArgumentMatchers.contains("type"), ArgumentMatchers.contains("prov"), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
            )
        )
            .thenReturn(TestHelper.getOrganizationBundle("2", "ACHospital", "prov"))

        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("type"),
                        ArgumentMatchers.contains("dept"), ArgumentMatchers.contains("partof"), ArgumentMatchers.any(), ArgumentMatchers.contains("active"),  ArgumentMatchers.contains("true")
                )
        )
                .thenReturn(TestHelper.getOrganizationBundle("1", "TEST_ID1", "dept"))

        `when`(fhirClient.update(isA<Patient>())).thenReturn(MethodOutcome(IdType("resource/id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(3, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[2].details.text)
        verify(fhirClient, times(2))
                .search(ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"), any())
        verify(fhirClient, times(2))
                .search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )
        verify(fhirClient, never()).create(any())
        verify(fhirClient, times(1)).update(ArgumentMatchers.any(Patient::class.java))
    }

    @Test
    fun test_mrgPatientPresent_and_pidPatient_notPresent() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergePatient_SinglePidPatientAndMergePatient.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val mrgPatient = getPatientBundle("1", "pidPatient")
        val pidPatient = Bundle()
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        any()
                )
        ).thenReturn(pidPatient, mrgPatient)

        val fullPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("_id"),
                        ArgumentMatchers.contains("Patient-1")
                )
        )
             .thenReturn(fullPatient)

        `when`(
            fhirClient.search(
                ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("name"), ArgumentMatchers.any(),
                ArgumentMatchers.contains("type"), ArgumentMatchers.contains("prov"), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
            )
        )
            .thenReturn(TestHelper.getOrganizationBundle("2", "ACHospital", "prov"))


        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("type"),
                        ArgumentMatchers.contains("dept"), ArgumentMatchers.contains("partof"), ArgumentMatchers.any(), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
                )
        )
                .thenReturn(TestHelper.getOrganizationBundle("1", "TEST_ID1", "dept"))

        `when`(fhirClient.update(isA<Patient>())).thenReturn(MethodOutcome(IdType("resource/id")))
        val resource1Flag = ArgumentCaptor.forClass(Flag::class.java)
        val methodOutcome1 = MethodOutcome(IdType("Flag-1"))
        `when`(fhirClient.create(resource1Flag.capture())).thenReturn(methodOutcome1)
        //execute
        scripts.run(parameters, scriptInformation)

        //assert

        val expectedWarningMessage = "The patient ID has been updated."
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "error" || it.severity.toCode() == "warning" }
        Assert.assertTrue(errorOrWarning.size == 3)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[2].details.text)

        verify(fhirClient, times(2))
                .search(ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"), any())
        verify(fhirClient, times(1))
                .search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("_id"),
                        ArgumentMatchers.contains("Patient-1")
                )

        verify(fhirClient, times(2))
                .search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )
        verify(fhirClient, times(1))
            .search(
                ArgumentMatchers.contains("Patient"),
                ArgumentMatchers.contains("_id"),
                ArgumentMatchers.any()
            )
        verify(fhirClient, times(1)).create(isA<Flag>())
        val flags = resource1Flag.allValues
        Assert.assertEquals(1, flags.size)
        val flag1 = flags[0]
        verifyFlag(flag1, "Patient-1", expectedWarningMessage)
        verify(fhirClient, times(1)).update(ArgumentMatchers.any(Patient::class.java))
    }

    @Test
    fun test_mrgPatientPresent_and_pidPatientPresent() {
        val json = TestHelper.readResource("/patientmerge/MergePatient_SinglePidPatientAndMergePatient.json")

        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val pidPatient = getPatientBundle("TestPatient_250102", "PidPatient")
        val mrgPatient = getPatientBundle("TestPatient_231201", "MergePatient")
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        any()
                )
        ).thenReturn(pidPatient, mrgPatient)

        val resource1Flag = ArgumentCaptor.forClass(Flag::class.java)
        val methodOutcome1 = MethodOutcome(IdType("Flag-1"))
        val methodOutcome2 = MethodOutcome(IdType("Flag-2"))
        `when`(fhirClient.create(resource1Flag.capture())).thenReturn(methodOutcome1, methodOutcome2)

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val informations = outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "information" }
        Assert.assertFalse(informations.isEmpty())
        for (information in informations) {
            Assert.assertTrue(information.details.coding[0].code == "Both Patients are present for Merge. Alert is placed on both the Patients.")
        }
        val  expectedFlagMessageForMrgPatient=
                "This is a duplicate patient and the records attached shall be manually merged into patient 'PidPatient (TestPatient_250102)'."
        val expectedFlagMessageForPidPatient =
                "The records attached to the patient 'MergePatient (TestPatient_231201)' should be manually merged in."
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "error" || it.severity.toCode() == "warning" }
        Assert.assertTrue(errorOrWarning.isEmpty())
        verify(fhirClient, times(2)).create(isA<Flag>())
        verify(fhirClient, times(2))
                .search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )
        verify(fhirClient, never()).create(ArgumentMatchers.any(Patient::class.java))
        verify(fhirClient, never()).update(ArgumentMatchers.any(Patient::class.java))
        val flags = resource1Flag.allValues
        Assert.assertEquals(2, flags.size)
        val flag1 = flags[0]
        val flag2 = flags[1]
        verifyFlag(flag1, "Patient-TestPatient_250102", expectedFlagMessageForPidPatient)
        verifyFlag(flag2, "Patient-TestPatient_231201", expectedFlagMessageForMrgPatient)
    }

    @Test
    fun testPatientMerge_A40Repeating_allScenarios_Success() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergePatientA40_Repeating_AllScenarios.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val noPatient = Bundle()
        val patientPid = getPatientBundle("1", "lastName1")
        val patientMrg = getPatientBundle("2", "lastName2")

        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )
        ).thenReturn(
                noPatient, noPatient,
                patientPid, noPatient,
                noPatient, patientMrg,
                patientPid, patientMrg
        )

        val fullPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(
            fhirClient.search(
                ArgumentMatchers.contains("Patient"),
                ArgumentMatchers.contains("_id"),
                ArgumentMatchers.contains("Patient-1")
            )
        )
            .thenReturn(fullPatient)

        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("_id"),
                        ArgumentMatchers.contains("Patient-2")
                )
        )
                .thenReturn(fullPatient)

        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("type"),
                        ArgumentMatchers.contains("dept"), ArgumentMatchers.contains("partof"), ArgumentMatchers.any(), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
                )
        )
                .thenReturn(TestHelper.getOrganizationBundle("1", "TEST_ID1", "dept"))
        `when`(
            fhirClient.search(
                ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("name"), ArgumentMatchers.any(),
                ArgumentMatchers.contains("type"), ArgumentMatchers.contains("prov"), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
            )
        )
            .thenReturn(TestHelper.getOrganizationBundle("2", "ACHospital1", "prov"))

        `when`(fhirClient.search(ArgumentMatchers.contains("CareTeam"), ArgumentMatchers.contains("patient"), ArgumentMatchers.any())).thenReturn(Bundle())
        `when`(fhirClient.update(any())).thenReturn(MethodOutcome(IdType("resource/id")))
        `when`(fhirClient.create(isA<Flag>())).thenReturn(MethodOutcome(IdType("consent/id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val identifierCaptor = ArgumentCaptor.forClass(TokenClientParam::class.java)
        //serach 4 pid patient and 4 mrg patients
        verify(fhirClient, times(8))
                .search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("identifier"),
                        identifierCaptor.capture()
                )

        //update patient twice. one for pid and second for mrg patient
        verify(fhirClient, times(2)).update(isA<Patient>())
        //create flag
        verify(fhirClient, times(3)).create(isA<Flag>())
    }

    @Test
    fun test_PatientMerge_PartialSuccess_forRepeatingMerge() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergePatientA40_Repeating_AllScenarios.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val noPatient = Bundle()
        val patientPid = getPatientBundle("1", "lastName1")
        val patientMrg = getPatientBundle("2", "lastName2")

        //mock
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )
        ).thenReturn(patientPid, patientMrg, noPatient)
        `when`(fhirClient.create(isA<Flag>())).thenReturn(MethodOutcome(IdType("Flag-1")))
        //run
        scripts.run(parameters, scriptInformation)

        //assert
        val errors = outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "error" }
        val warnings = outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "warning" }
        Assert.assertTrue(errors.isEmpty())
        Assert.assertFalse(warnings.isEmpty())
        for (warning in warnings) {
            Assert.assertTrue(warning.details.text.contains("Both patients"))
        }

        verify(fhirClient, times(2)).create(isA<Flag>())
        verify(fhirClient, never()).create(ArgumentMatchers.any(Patient::class.java))
        verify(fhirClient, never()).update(ArgumentMatchers.any(Patient::class.java))
    }

    @Test
    fun test_PatientMerge_Fails_forRepeatingMerge() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergePatientA40_Repeating_AllScenarios.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val noPatient = Bundle()

        //mock
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )
        ).thenReturn(noPatient)

        //run
        scripts.run(parameters, scriptInformation)

        //assert
        val errors = outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "error" }
        val warnings = outcome.getOperationOutcome().issue.filter { it.severity.toCode() == "warning" }
        Assert.assertTrue(errors.isEmpty())
        Assert.assertFalse(warnings.isEmpty())
        for (warning in warnings) {
            Assert.assertTrue(warning.details.text.contains("Both patients"))
        }

        verify(fhirClient, never()).create(isA<Flag>())
        verify(fhirClient, never()).create(ArgumentMatchers.any(Patient::class.java))
        verify(fhirClient, never()).update(ArgumentMatchers.any(Patient::class.java))
    }

    @Test
    fun test_pidPatientPresent_shouldCreateWithAccountAndDirective() {
        //prepare
        val json = TestHelper.readResource("/patientmerge/MergerPatientA18_WithAccountAndDirectiveFlag.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        val pidPatient = getPatientBundle("1", "pidPatient")
        val noPatient = Bundle()
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"),
                        any()
                )
        ).thenReturn(pidPatient, noPatient)

        val fullPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("_id"),
                        ArgumentMatchers.any()
                ))
                .thenReturn(fullPatient)
        `when`(
            fhirClient.search(
                ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("name"), ArgumentMatchers.any(),
                ArgumentMatchers.contains("type"), ArgumentMatchers.contains("prov"), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
            )
        )
            .thenReturn(TestHelper.getOrganizationBundle("2", "ACHospital", "prov"))

        `when`(
                fhirClient.search(
                        ArgumentMatchers.contains("Organization"), ArgumentMatchers.contains("type"),
                        ArgumentMatchers.contains("dept"), ArgumentMatchers.contains("partof"), ArgumentMatchers.any(), ArgumentMatchers.contains("active"), ArgumentMatchers.contains("true")
                )
        )
                .thenReturn(TestHelper.getOrganizationBundle("1", "TEST_ID1", "dept"))

        `when`(fhirClient.search("Consent", "patient", "id", "status", "active"))
                .thenReturn(Bundle())
        `when`(fhirClient.search(eq("Account"), eq("patient"), eq("id"), eq("identifier"), any()))
                .thenReturn(Bundle())

        `when`(fhirClient.update(isA<Patient>())).thenReturn(MethodOutcome(IdType("resource/id")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals("Account with given Account Number does not exists", errorOrWarning[2].details.text)
        verify(fhirClient, times(2))
                .search(ArgumentMatchers.contains("Patient"), ArgumentMatchers.contains("identifier"), any())
        verify(fhirClient, times(2))
                .search(
                        ArgumentMatchers.contains("Patient"),
                        ArgumentMatchers.contains("identifier"),
                        ArgumentMatchers.any()
                )

        verify(fhirClient, times(1)).search(eq("Account"), eq("patient"), eq("id"), eq("identifier"), any())
        verify(fhirClient, times(1)).create(isA<Consent>())
        verify(fhirClient, times(1)).update(ArgumentMatchers.any(Patient::class.java))
    }

    private fun getPatientBundle(patientId: String, lastName: String): Bundle {
        val bundle = Bundle()
        val patient = Patient()
        patient.setId("Patient/Patient-${patientId}")
        patient.identifierFirstRep.system = "http://varian.com/fhir/identifier/Patient/ARIAID1"
        patient.identifierFirstRep.value = "identifier-${patientId}"
        patient.nameFirstRep.family = lastName
        bundle.addEntry(Bundle.BundleEntryComponent().setResource(patient))
        return bundle
    }

    private fun verifyFlag(flag: Flag, patientId: String, message: String) {
        Assert.assertEquals(
                "http://terminology.hl7.org/CodeSystem/flag-category",
                flag.categoryFirstRep.codingFirstRep.system
        )
        Assert.assertEquals("clinical", flag.categoryFirstRep.codingFirstRep.code)
        Assert.assertEquals(flag.code.text, message)
        Assert.assertEquals(org.hl7.fhir.r4.model.Flag.FlagStatus.ACTIVE, flag.status)
        Assert.assertEquals(patientId, flag.subject.reference)

    }
}
