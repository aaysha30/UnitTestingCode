package dsl.master.inbound.interfaces.adtin.patientsave

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.CareTeam
import com.varian.fhir.resources.Patient
import com.varian.fhir.resources.Organization
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import com.varian.mappercore.framework.utility.BundleUtility
import com.varian.mappercore.framework.utility.ParametersUtility
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.hl7.fhir.r4.model.CareTeam.CareTeamParticipantComponent
import org.hl7.fhir.r4.model.Enumerations.FHIRAllTypes
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import java.text.SimpleDateFormat
import java.util.*


class PatientSaveTest {
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
            scriptInformation = scripts.getHandlerFor("Json", "SiuOutFhir")!!.get()
            parser = fhirFactory.getFhirParser()
        }
    }//end of companion obj

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
        parameters[ParameterConstant.USER] = TestHelper.getPractitioner("Practitioner-1014", "headlessclient")
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger.initCLoverLogger(mock())
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        parameters[ParameterConstant.ATTACH_HOSPITAL_Departments] = false
        parameters[ParameterConstant.UPDATE_PRIMARY_DEPARTMENT] = true
        parameters[ParameterConstant.SNAPSHOT_DEPARTMENTS] = true
        parameters[ParameterConstant.HOSPITAL_DEPT_BUNDLE] = Bundle()

        val json = TestHelper.readResource("/patient/CareTeamDomainBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val ct = inputBundle.entry.find { it.resource.fhirType() == FHIRAllTypes.CARETEAM.toCode() }?.resource
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(ct)))
        CareTeamParticipantComponent().member.id
    }

    @Test
    fun test_patientCreated_with_specifiedHospitalAndDepartment() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        val careTeamBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(careTeamBundle)
        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        val careTeam = careTeamResource.firstValue
        val patient = patientResource.firstValue
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-1")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientCreated_with_singleDepartment_belongsToSpecifiedHospital() {
        //prepare
        val inputHospital = "validHospital"
        val inputDepartment = "invalidDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", "validSingleDepartment", "dept"))

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
       // println("Not Null")
        val careTeam = careTeamResource.firstValue
        val patient = patientResource.firstValue
        Assert.assertEquals("Organization/Organization-dept-1", careTeam.participantFirstRep.member.reference)
        Assert.assertEquals("Organization/Organization-prov-1", patient.managingOrganization.reference)
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        val expectedWarningMessage1 = "Department: ${inputDepartment}, is not valid"
        val expectedWarningMessage2 =
            "Single Department: validSingleDepartment, of Hospital: Organization-prov-1, is used"

        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
        Assert.assertEquals("PATIENT_DEPARTMENT_INVALID", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(expectedWarningMessage2, errorOrWarning[1].details.text)
        Assert.assertEquals("PATIENT_SINGLE_DEPARTMENT", errorOrWarning[1].details.codingFirstRep.code)

    }

    @Test
    fun test_patientCreated_with_defaultConfiguredDepartment_belongsToSpecifiedHospital() {
        //prepare
        val inputHospital = "validHospital"
        val inputDepartment = "invalidDepartment"
        val defaultHospital = ""
        val defaultDepartment = "validDepartment1"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        val organizationBundle = getOrganizationBundle("Organization/Organization-dept-1", "validSingleDepartment", "dept")
        val secondDept = Organization()
        secondDept.identifierFirstRep.value = defaultDepartment
        secondDept.id = "Organization/Organization-dept-2"

        val bundleEntryComponent = Bundle.BundleEntryComponent()
        bundleEntryComponent.resource = secondDept
        organizationBundle.addEntry(bundleEntryComponent)

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(organizationBundle)

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
        val careTeam = careTeamResource.firstValue
        val patient = patientResource.firstValue
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-2")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        val expectedWarningMessage1 = "Department: ${inputDepartment}, is not valid"
        val expectedWarningMessage2 = "Default Department: $defaultDepartment is used"
        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
        Assert.assertEquals("PATIENT_DEPARTMENT_INVALID", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(expectedWarningMessage2, errorOrWarning[1].details.text)
        Assert.assertEquals("PATIENT_DEFAULT_DEPARTMENT", errorOrWarning[1].details.codingFirstRep.code)
    }

    @Test
    fun test_patientCreated_with_singleHospitalAssignedToOrg_AndSpecifiedDepartment() {
        //prepare
        val inputHospital = "inValidHospital"
        val inputDepartment = "validDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(Bundle())
        `when`(fhirClient.search("Organization", "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", "validHospital", "dept"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", "validDepartment", "dept"))

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
        val careTeam = careTeamResource.firstValue
        val patient = bundleValue
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-1")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        val expectedWarningMessage1 = "Hospital: inValidHospital, is not valid"
        val expectedWarningMessage2 = "Single Hospital: validHospital, is used"
        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
        Assert.assertEquals("PATIENT_INVALID_HOSPITAL", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(expectedWarningMessage2, errorOrWarning[1].details.text)
        Assert.assertEquals("PATIENT_SINGLE_HOSPITAL", errorOrWarning[1].details.codingFirstRep.code)
    }

    @Test
    fun test_patientCreated_with_defaultConfiguredHospitalAndDefaultConfigureDepartment() {
        //prepare
        val inputHospital = "invalidHospital"
        val inputDepartment = "invalidDepartment"
        val defaultHospital = "validHospital2"
        val defaultDepartment = "validDepartment2"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(noPatient)

        val organizationHospBundle = getOrganizationBundle("Organization/Organization-prov-1", "validFirstHospital", "prov")
        val secondOrg = Organization()
        secondOrg.identifierFirstRep.value = defaultDepartment
        secondOrg.id = "Organization/Organization-prov-2"
        val bundleEntryComponent1 = Bundle.BundleEntryComponent()
        bundleEntryComponent1.resource = secondOrg
        organizationHospBundle.addEntry(bundleEntryComponent1)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(Bundle())
        `when`(fhirClient.search("Organization", "type", "prov", "active", "true"))
            .thenReturn(organizationHospBundle)

        val organizationDeptBundle = getOrganizationBundle("Organization/Organization-dept-1", "validSingleDepartment", "dept")
        val secondDept = Organization()
        secondDept.identifierFirstRep.value = defaultDepartment
        secondDept.id = "Organization/Organization-dept-2"
        val bundleEntryComponent = Bundle.BundleEntryComponent()
        bundleEntryComponent.resource = secondDept
        organizationDeptBundle.addEntry(bundleEntryComponent)

        `when`(fhirClient.search("Organization", "name", defaultHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-2", defaultHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-2", "active", "true"))
            .thenReturn(organizationDeptBundle)

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
        val careTeam = careTeamResource.firstValue
        val patient = bundleValue
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-2")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-2")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-2")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(4, errorOrWarning.size)
        val expectedWarningMessage1 = "Hospital: $inputHospital, is not valid"
        val expectedWarningMessage2 = "Default Hospital: $defaultHospital, is used"
        val expectedWarningMessage3 = "Department: $inputDepartment, is not valid"
        val expectedWarningMessage4 = "Default Department: $defaultDepartment is used"
        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
        Assert.assertEquals("PATIENT_INVALID_HOSPITAL", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(expectedWarningMessage2, errorOrWarning[1].details.text)
        Assert.assertEquals("PATIENT_DEFAULT_HOSPITAL", errorOrWarning[1].details.codingFirstRep.code)
        Assert.assertEquals(expectedWarningMessage3, errorOrWarning[2].details.text)
        Assert.assertEquals("PATIENT_DEPARTMENT_INVALID", errorOrWarning[2].details.codingFirstRep.code)
        Assert.assertEquals(expectedWarningMessage4, errorOrWarning[3].details.text)
        Assert.assertEquals("PATIENT_DEFAULT_DEPARTMENT", errorOrWarning[3].details.codingFirstRep.code)
    }

    @Test
    fun test_patient_created_with_single_hospital_and_department_if_HospitalAndDepartmentIsNotSpecified_InInputBundle() {
        val inputHospital = ""
        val inputDepartment = ""
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle
        (inputBundle.entry[2].resource as Patient).managingOrganization = null
        inputBundle.entry.removeAt(3)
        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", "validHospital", "dept"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", "validDepartment", "dept"))

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val careTeam = careTeamResource.firstValue
        val patient = bundleValue
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-1")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }

        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        val expectedWarningMessage1 = "Single Hospital: validHospital, is used"
        val expectedWarningMessage2 = "Single Department: validDepartment, of Hospital: Organization-prov-1, is used"
        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
        Assert.assertEquals(expectedWarningMessage2, errorOrWarning[1].details.text)

    }

    @Test
    fun test_patient_updated_with_specifiedHospitalAndDepartment() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val careTeam = bundleValue[1] as CareTeam
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(careTeam.participantFirstRep.member.id, "Organization/Organization-dept-inputdept")
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-inputdept")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-inputhospital")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-inputhospital")
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun test_patient_update_use_same_hospital_and_department_ifGivenHospitalIsNotValid() {
        //prepare
        val inputHospital = "invalidHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatientBundle =
            parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatientBundle)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(Bundle())

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val careTeam = bundleValue[1] as CareTeam
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(careTeam.participantFirstRep.member.id, "Organization/Organization-dept-1")
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-1")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-Prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-Prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        val expectedWarningMessage1 = "Hospital: $inputHospital, is not valid"
        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
    }

    @Test
    fun test_patient_update_use_same_hospital_and_department_ifGivenDepartmentIsNotValid() {
        //prepare
        val inputHospital = "validHospital"
        val inputDepartment = "invalidDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatientBundle =
            parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatientBundle)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", "validSingleDepartment", "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val careTeam = bundleValue[1] as CareTeam
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(careTeam.participantFirstRep.member.id, "Organization/Organization-dept-1")
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-1")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-Prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-Prov-1")
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        val expectedWarningMessage1 = "Department: $inputDepartment, is not valid"
        Assert.assertEquals(expectedWarningMessage1, errorOrWarning[0].details.text)
    }

    @Test
    fun test_Update_Patient_IfHospitalAndDepartmentIsNotGivenInInputBundle() {
        val inputHospital = "invalidHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        //remove careTeam
        inputBundle.entry.removeAt(3)

        //remove patient's managing organization
        (inputBundle.entry[2].resource as Patient).managingOrganization = null

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatientBundle =
            parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatientBundle)

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val careTeam = bundleValue[1] as CareTeam
        val patient = bundleValue[0] as Patient
        Assert.assertEquals("Organization/Organization-Prov-1", patient.managingOrganization.reference)
        Assert.assertEquals("Organization/Organization-dept-1", careTeam.participantFirstRep.member.reference)
    }

    @Test
    fun test_NoValidHospitalFound() {
        val inputHospital = "validHospital"
        val inputDepartment = "invalidDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)
        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(Bundle())
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: Exception) {
            Assert.assertTrue(ex is UnprocessableEntityException)
            val unprocessableEx = ex as UnprocessableEntityException
            Assert.assertEquals(
                "PATIENT_INVALID_HOSPITAL_DEPARTMENT",
                (unprocessableEx.operationOutcome as OperationOutcome).issueFirstRep.details.codingFirstRep.code
            )
            Assert.assertEquals(
                "system could not attach valid hospital and department",
                (unprocessableEx.operationOutcome as OperationOutcome).issueFirstRep.details.text
            )
        }
    }

    @Test(expected = UnprocessableEntityException::class)
    fun test_NoValidDepartmentFound() {
        val inputHospital = "validHospital"
        val inputDepartment = "invalidDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", "validHospital", "dept"))

        `when`(fhirClient.search("Organization", "name", defaultHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", defaultHospital, "prov"))
        scripts.run(parameters, scriptInformation)
    }

    @Test
    fun test_patientCreate_Fails() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenThrow(UnprocessableEntityException("Patient create failed"))

        //execute
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: UnprocessableEntityException) {
            outcome.addError(ex)
        }

        //assert
        verify(fhirClient, never()).update(any())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient create failed", errorOrWarning[0].details.text)
        Assert.assertEquals("Patient", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(Outcome.hl7ErrorSystem, errorOrWarning[0].details.codingFirstRep.system)
    }

    @Test
    fun testPatientCreate_shouldCreateReferringPhysician_ifAutoCreateIsEnabled() {
        //prepare
        val referringPhysicianIdentifier = "RP1"
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "1")
        inputBundle = addReferringPhysician(inputBundle, referringPhysicianIdentifier)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()

        val pracResource = argumentCaptor<Practitioner>()
        `when`(fhirClient.create(pracResource.capture())).thenReturn(MethodOutcome(IdType("Practitioner-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(isA<Practitioner>())
        val practitioner = pracResource.secondValue
        Assert.assertNotNull(practitioner)
        Assert.assertEquals(referringPhysicianIdentifier, practitioner.identifierFirstRep.value)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testPatientCreate_shouldNotCreateReferringPhysician_ifAutoCreateIsDisabled() {
        //prepare
        val referringPhysicianIdentifier = "RP1"
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        inputBundle = addReferringPhysician(inputBundle, referringPhysicianIdentifier)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(isA<Practitioner>())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals("A valid Doctor with Id=RP1 doesn't exist in database", errorOrWarning[0].details.text)
        Assert.assertEquals("PRACTITIONER_NOT_FOUND", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(Outcome.hl7ErrorSystem, errorOrWarning[0].details.codingFirstRep.system)
    }

    @Test
    fun testPatientCreate_shouldNotCreateReferringPhysician_ifAutoCreateConfigIsMissing() {
        //prepare
        val referringPhysicianIdentifier = "RP1"
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        inputBundle = addReferringPhysician(inputBundle, referringPhysicianIdentifier)
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.removeIf { it.name == ParametersUtility.AUTO_CREATE_REFERRING_PHYSICIAN }
        parameters[ParameterConstant.BUNDLE] = inputBundle
        //mock
        mockPatientCreateHappyFlow()
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(isA<Practitioner>())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }

        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals("A valid Doctor with Id=RP1 doesn't exist in database", errorOrWarning[0].details.text)
        Assert.assertEquals("PRACTITIONER_NOT_FOUND", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(Outcome.hl7ErrorSystem, errorOrWarning[0].details.codingFirstRep.system)
    }

    @Test
    fun testPatientUpdate_shouldAddPrimaryPhysician_ifPrimaryUpdateIsAllowed() {
        //add new primary physician and uncheck existing primary physician
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-5", "primary-oncologist", careTeam)
        addPhysicianParticipant("Practitioner-6", "oncologist", careTeam)
        addPhysicianParticipant("Practitioner-7", "primary-referring-physician", careTeam)
        addPhysicianParticipant("Practitioner-8", "referring-physician", careTeam)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-1", "primary-oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner-2", "oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner-3", "primary-referring-physician", domainCareTeam)
        addPhysicianParticipant("Practitioner-4", "referring-physician", domainCareTeam)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainCTBundle)


        val pracBundle5 = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-5"))).setActive(true).setId(IdType("Practitioner-5")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-6"))).setActive(true).setId(IdType("Practitioner-6")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-7"))).setActive(true).setId(IdType("Practitioner-7")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-8"))).setActive(true).setId(IdType("Practitioner-8")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle5)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(isA<Patient>())
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        val updatedResource = updatedResourceCaptor.allValues
        Assert.assertNotNull(updatedResource)
        Assert.assertEquals(2, updatedResource.size)
        val updatedCareTeam = updatedResource[1] as CareTeam
        val oncologist = updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "oncologist" }
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }

        Assert.assertEquals(3, oncologist.size)
        Assert.assertEquals(3, referringPhysician.size)
        Assert.assertEquals(1, primaryOncologist.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals("Practitioner/Practitioner-5", primaryOncologist[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-7", primaryReferringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner-1", oncologist[0].member.reference)
        Assert.assertEquals("Practitioner-2", oncologist[1].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-6", oncologist[2].member.reference)
        Assert.assertEquals("Practitioner-3", referringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner-4", referringPhysician[1].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-8", referringPhysician[2].member.reference)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testPatientUpdate_shouldNotUpdatePrimaryPhysician_primaryUpdateIsAllowed() {
        //input primary physician and existing primary physician are same for both oncologist and referring physician
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-1", "primary-oncologist", careTeam)
        addPhysicianParticipant("Practitioner-5", "oncologist", careTeam)
        addPhysicianParticipant("Practitioner-3", "primary-referring-physician", careTeam)
        addPhysicianParticipant("Practitioner-6", "referring-physician", careTeam)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-1", "primary-oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner/Practitioner-2", "oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner/Practitioner-3", "primary-referring-physician", domainCareTeam)
        addPhysicianParticipant("Practitioner/Practitioner-4", "referring-physician", domainCareTeam)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        val pracBundle1 = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setActive(true).setIdentifier(mutableListOf(Identifier().setValue("Practitioner-1"))).setId(IdType("Practitioner-1")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-5"))).setActive(true).setId(IdType("Practitioner-5")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-3"))).setActive(true).setId(IdType("Practitioner-3")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-6"))).setActive(true).setId(IdType("Practitioner-6")) as Practitioner)
        )

        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainCTBundle)
        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle1)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(isA<Patient>())
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        val updatedResource = updatedResourceCaptor.allValues
        Assert.assertNotNull(updatedResource)
        Assert.assertEquals(2, updatedResource.size)
        val updatedCareTeam = updatedResource[1] as CareTeam
        val oncologist = updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "oncologist" }
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }

        Assert.assertEquals(2, oncologist.size)
        Assert.assertEquals(2, referringPhysician.size)
        Assert.assertEquals(1, primaryOncologist.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals("Practitioner/Practitioner-1", primaryOncologist[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-3", primaryReferringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-2", oncologist[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-5", oncologist[1].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-4", referringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-6", referringPhysician[1].member.reference)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testPatientUpdate_shouldNotUpdatePrimaryCheckForPhysician_ifPrimaryUpdateIsNotAllowed() {
        //add new physician but keep existing physician as primary for both oncologist and referring physician
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("1")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-5", "oncologist", careTeam)
        addPhysicianParticipant("Practitioner-6", "referring-physician", careTeam)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainBundleCT = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainBundleCT.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-1", "primary-oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner-2", "oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner-3", "primary-referring-physician", domainCareTeam)
        addPhysicianParticipant("Practitioner-4", "referring-physician", domainCareTeam)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainBundleCT)
        val pracBundle5 = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setActive(true).setIdentifier(mutableListOf(Identifier().setValue("Practitioner-5"))).setId(IdType("Practitioner-5")) as Practitioner)
        ).addEntry(Bundle.BundleEntryComponent()
            .setResource(Practitioner().setActive(true).setIdentifier(mutableListOf(Identifier().setValue("Practitioner-6"))).setId(IdType("Practitioner-6")) as Practitioner))


        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle5)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(isA<Patient>())
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        val updatedResource = updatedResourceCaptor.allValues
        Assert.assertNotNull(updatedResource)
        Assert.assertEquals(2, updatedResource.size)
        val updatedCareTeam = updatedResource[1] as CareTeam
        val oncologist = updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "oncologist" }
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }

        Assert.assertEquals(2, oncologist.size)
        Assert.assertEquals(2, referringPhysician.size)
        Assert.assertEquals(1, primaryOncologist.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals("Practitioner-1", primaryOncologist[0].member.reference)
        Assert.assertEquals("Practitioner-3", primaryReferringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner-2", oncologist[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-5", oncologist[1].member.reference)
        Assert.assertEquals("Practitioner-4", referringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-6", referringPhysician[1].member.reference)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientCreate_AllowUpdate() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        addPatientIdentifier(inputBundle, "http://varian.com/fhir/identifier/Patient/ARIAID3", "ARIAIDVALUE3")
        addPatientIdentifier(inputBundle, "http://varian.com/fhir/identifier/Patient/ARIAID4", "ARIAIDVALUE4")
        addParameter(inputBundle, "PatientDisallowUpdateKeys", "http://varian.com/fhir/identifier/Patient/ARIAID3")
        addParameter(inputBundle, "PatientDisallowUpdateKeys", "http://varian.com/fhir/identifier/Patient/ARIAID4")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val patientReturned = patientResource.firstValue
        Assert.assertTrue(patientReturned.identifier.any { it.system == "http://varian.com/fhir/identifier/Patient/ARIAID3" })
        Assert.assertTrue(patientReturned.identifier.any { it.value == "ARIAIDVALUE3" })
        Assert.assertTrue(patientReturned.identifier.any { it.system == "http://varian.com/fhir/identifier/Patient/ARIAID4" })
        Assert.assertTrue(patientReturned.identifier.any { it.value == "ARIAIDVALUE4" })
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_InPatient() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "In Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val bundleValue = patientResource.firstValue
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertEquals(roomNumber, patientReturned.patientLocationDetails.roomNumber.value)
        Assert.assertEquals(admissionDate, patientReturned.patientLocationDetails.admissionDate.value)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_InPatient_AdmissionDateNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "In Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = null
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val bundleValue = patientResource.firstValue
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertEquals(roomNumber, patientReturned.patientLocationDetails.roomNumber.value)
        Assert.assertEquals(currentDatetime, patientReturned.patientLocationDetails.admissionDate.value)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Current Date is considered for Admit Date", errorOrWarning[0].details.text)
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_InPatient_AdmissionDateNull_CurrentDateNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val currentDatetime = null
        val patientClass = "In Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = null
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: Exception) {
            Assert.assertTrue(ex is UnprocessableEntityException)
            val unprocessableEx = ex as UnprocessableEntityException
            Assert.assertEquals("ADMIT_DATE_NULL_FOR_INPATIENT", unprocessableEx.message)
            Assert.assertEquals(
                "Current Date is not set in Parameters resource",
                outcome.getOperationOutcome().issueFirstRep.details.text
            )
        }
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_InPatient_RoomNumberNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "In Patient"
        val roomNumber = null
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val bundleValue = patientResource.firstValue
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertEquals(defaultRoomNumber, patientReturned.patientLocationDetails.roomNumber.value)
        Assert.assertEquals(admissionDate, patientReturned.patientLocationDetails.admissionDate.value)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Default Room number: 1111 is used", errorOrWarning[0].details.text)
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_InPatient_RoomNumberNull_DefaultRoomNumberNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "In Patient"
        val roomNumber = null
        val defaultRoomNumber = null
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val bundleValue = patientResource.firstValue
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue
        Assert.assertEquals("Out Patient", patientReturned.patientClass.codingFirstRep.code)
        Assert.assertNull(patientReturned.patientLocationDetails.roomNumber)
        Assert.assertNull(patientReturned.patientLocationDetails.admissionDate)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        Assert.assertEquals("Default Room number is not configured", errorOrWarning[0].details.text)
        Assert.assertEquals(
            "Ignoring Patient Class because the Room Number is null or empty. Used Patient Class as OutPatient",
            errorOrWarning[1].details.text
        )
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_OutPatient() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "Out Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val bundleValue = patientResource.firstValue
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertNull(patientReturned.patientLocationDetails.roomNumber)
        Assert.assertNull(patientReturned.patientLocationDetails.admissionDate)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientCreate_AdvPatientClassProcess_0_PatientClassNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = null
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(Bundle())

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()
        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val bundleValue = patientResource.firstValue
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue
        Assert.assertEquals("Out Patient", patientReturned.patientClass.codingFirstRep.code)
        Assert.assertNull(patientReturned.patientLocationDetails.roomNumber)
        Assert.assertNull(patientReturned.patientLocationDetails.admissionDate)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientUpdate_AdvPatientClassProcess_0_InToOutPatient() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "Out Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val existingPatientClass = "In Patient"
        val existingRoomNumber = "2222"
        val existingAdmissionDate = DateTime.now().minusDays(10).toDate()
        val existingDischargeDate = DateTime.now().minusDays(8).toDate()
        modifyForPatientClass(
            existingPatient,
            existingPatientClass,
            existingRoomNumber,
            defaultRoomNumber,
            currentDatetime,
            existingAdmissionDate,
            existingDischargeDate
        )

        //mock
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        ).thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(any())
        val bundleValue = patientResource.allValues
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue[0] as Patient
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertNull(patientReturned.patientLocationDetails.roomNumber)
        Assert.assertNull(patientReturned.patientLocationDetails.admissionDate)
        Assert.assertEquals(dischargeDate, patientReturned.patientLocationDetails.dischargeDate.value)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
    }

    @Test
    fun test_patientUpdate_AdvPatientClassProcess_0_OutToInPatient() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "In Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val existingPatientClass = "Out Patient"
        val existingRoomNumber = "2222"
        val existingAdmissionDate = DateTime.now().minusDays(10).toDate()
        val existingDischargeDate = DateTime.now().minusDays(8).toDate()
        modifyForPatientClass(
            existingPatient,
            existingPatientClass,
            existingRoomNumber,
            defaultRoomNumber,
            currentDatetime,
            existingAdmissionDate,
            existingDischargeDate
        )

        //mock
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        ).thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(any())
        val bundleValue = patientResource.allValues
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue[0] as Patient
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertEquals(roomNumber, patientReturned.patientLocationDetails.roomNumber.value)
        Assert.assertEquals(admissionDate, patientReturned.patientLocationDetails.admissionDate.value)
        Assert.assertNull(patientReturned.patientLocationDetails.dischargeDate)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientUpdate_AdvPatientClassProcess_0_OutToOutPatient() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "Out Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = DateTime.now().minusDays(1).toDate()
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val existingPatientClass = "Out Patient"
        val existingRoomNumber = "2222"
        val existingAdmissionDate = DateTime.now().minusDays(10).toDate()
        val existingDischargeDate = DateTime.now().minusDays(8).toDate()
        modifyForPatientClass(
            existingPatient,
            existingPatientClass,
            existingRoomNumber,
            defaultRoomNumber,
            currentDatetime,
            existingAdmissionDate,
            existingDischargeDate
        )

        //mock
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        ).thenReturn(existingPatient)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(any())
        val bundleValue = patientResource.allValues
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue[0] as Patient
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertNull(roomNumber, patientReturned.patientLocationDetails.roomNumber)
        Assert.assertNull(patientReturned.patientLocationDetails.admissionDate)
        Assert.assertEquals(existingDischargeDate, patientReturned.patientLocationDetails.dischargeDate.value)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_patientUpdate_AdvPatientClassProcess_0_InToOutPatient_DischargeDateNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val currentDatetime = formatter.parse(formatter.format(Date()))
        val patientClass = "Out Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = null
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val existingPatientClass = "In Patient"
        val existingRoomNumber = "2222"
        val existingAdmissionDate = DateTime.now().minusDays(10).toDate()
        val existingDischargeDate = DateTime.now().minusDays(8).toDate()
        modifyForPatientClass(
            existingPatient,
            existingPatientClass,
            existingRoomNumber,
            defaultRoomNumber,
            currentDatetime,
            existingAdmissionDate,
            existingDischargeDate
        )

        //mock
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        ).thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(any())
        val bundleValue = patientResource.allValues
        Assert.assertNotNull(bundleValue)
        val patientReturned = bundleValue[0] as Patient
        Assert.assertEquals(patientClass, patientReturned.patientClass.codingFirstRep.code)
        Assert.assertNull(patientReturned.patientLocationDetails.roomNumber)
        Assert.assertNull(patientReturned.patientLocationDetails.admissionDate)
        Assert.assertEquals(currentDatetime, patientReturned.patientLocationDetails.dischargeDate.value)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        Assert.assertEquals("Current Date is considered for Discharge Date", errorOrWarning[0].details.text)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[1].details.text)
    }

    @Test
    fun test_patientUpdate_AdvPatientClassProcess_0_OutPatient_DischargeDateNull_CurrentDateNull() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        val currentDatetime = null
        val patientClass = "Out Patient"
        val roomNumber = "1234"
        val defaultRoomNumber = "1111"
        val admissionDate = DateTime.now().minusDays(5).toDate()
        val dischargeDate = null
        modifyForPatientClass(
            inputBundle,
            patientClass,
            roomNumber,
            defaultRoomNumber,
            currentDatetime,
            admissionDate,
            dischargeDate
        )

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val existingPatientClass = "In Patient"
        val existingRoomNumber = "2222"
        val existingAdmissionDate = DateTime.now().minusDays(10).toDate()
        val existingDischargeDate = DateTime.now().minusDays(8).toDate()
        modifyForPatientClass(
            existingPatient,
            existingPatientClass,
            existingRoomNumber,
            defaultRoomNumber,
            currentDatetime,
            existingAdmissionDate,
            existingDischargeDate
        )

        //mock
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        ).thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: Exception) {
            //assert
            Assert.assertTrue(ex is UnprocessableEntityException)
            Assert.assertEquals(
                "Current Date is not set in Parameters resource",
                outcome.getOperationOutcome().issueFirstRep.details.text
            )
        }
    }

    @Test
    fun test_patientUpdate_AllowUpdate() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = "defaultHospital"
        val defaultDepartment = "defaultDepartment"
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        addPatientIdentifier(inputBundle, "http://varian.com/fhir/identifier/Patient/ARIAID3", "ARIAIDVALUE3")
        addPatientIdentifier(inputBundle, "http://varian.com/fhir/identifier/Patient/ARIAID4", "ARIAIDVALUE4")
        addParameter(inputBundle, "PatientDisallowUpdateKeys", "http://varian.com/fhir/identifier/Patient/ARIAID3")
        addParameter(inputBundle, "PatientDisallowUpdateKeys", "http://varian.com/fhir/identifier/Patient/ARIAID4")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        addPatientIdentifier(
            existingPatient,
            "http://varian.com/fhir/identifier/Patient/ARIAID3",
            "OriginalARIAIDVALUE3"
        )
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val updateResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(any())
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient
        Assert.assertNotNull(updatedPatient)
        Assert.assertTrue(updatedPatient.identifier.any { it.system == "http://varian.com/fhir/identifier/Patient/ARIAID3" })
        Assert.assertTrue(updatedPatient.identifier.any { it.value == "OriginalARIAIDVALUE3" })
        Assert.assertTrue(updatedPatient.identifier.any { it.system == "http://varian.com/fhir/identifier/Patient/ARIAID4" })
        Assert.assertTrue(updatedPatient.identifier.any { it.value == "ARIAIDVALUE4" })
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }

        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals(
            "Not updating 'http://varian.com/fhir/identifier/Patient/ARIAID3' because allow update is false for this id",
            errorOrWarning[0].details.text
        )
    }

    @Test
    fun testPrimaryCareProvider_shouldBeAttached_whenCreatingPatient() {
        val json = TestHelper.readResource("/patient/PatientBundleWithPrimaryCareProvider.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        val docOnco = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("docOnco"))).setActive(true).setId(IdType("Practitioner-1")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("docRp1"))).setActive(true).setId(IdType("Practitioner-3")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("docPD1"))).setActive(true).setId(IdType("Practitioner-2")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("docPD2"))).setActive(true).setId(IdType("Practitioner-4")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(docOnco)

        val createResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdResource = createResourceCaptor.allValues
        Assert.assertNotNull(createdResource)
        Assert.assertEquals(1, createdResource.size)
        val bundle = createdResource[0] as CareTeam
        val createdCareTeam = bundle
        Assert.assertEquals(5, createdCareTeam.participant.size)
        val oncologist = createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "oncologist" }
        val primaryOncologist =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }

        Assert.assertEquals(1, primaryOncologist.size)
        Assert.assertEquals(0, oncologist.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals(2, referringPhysician.size)

        //persist existing primary oncologist
        Assert.assertEquals("Practitioner/Practitioner-1", primaryOncologist[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-2", primaryReferringPhysician[0].member.reference)
        Assert.assertEquals("56802-2", primaryReferringPhysician[0].role[1].codingFirstRep.code)
        Assert.assertEquals("Practitioner/Practitioner-3", referringPhysician[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-4", referringPhysician[1].member.reference)
        Assert.assertEquals("56802-2", referringPhysician[1].role[1].codingFirstRep.code)

        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testPrimaryCareProvider_shouldNotBeCreated_whenPractionerIsN_U_L_L() {
        val json = TestHelper.readResource("/patient/PatientBundleWithNullPrimaryCareProvider.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "1")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(Bundle())

        val createResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdResource = createResourceCaptor.allValues
        Assert.assertNotNull(createdResource)
        Assert.assertEquals(1, createdResource.size)
        val bundle = createdResource[0] as CareTeam
        val createdCareTeam = bundle
        Assert.assertEquals(1, createdCareTeam.participant.size)
        val org = createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "default-service-organization" }

        Assert.assertEquals(1, org.size)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testPrimaryCareProvider_shouldNotBeAttached_IfPhysicianIsInActive() {
        val json = TestHelper.readResource("/patient/PatientBundleWithPrimaryCareProvider.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        val docOnco = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier((mutableListOf(Identifier().setValue("docOnco")))).setActive(false).setId(IdType("Practitioner-1")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier((mutableListOf(Identifier().setValue("docRp1")))).setActive(false).setId(IdType("Practitioner-2")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier((mutableListOf(Identifier().setValue("docPD1")))).setActive(false).setId(IdType("Practitioner-3")) as Practitioner)
        ).addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier((mutableListOf(Identifier().setValue("docPD2")))).setActive(false).setId(IdType("Practitioner-4")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(docOnco)

        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdResource = updateResourceCaptor.allValues
        Assert.assertNotNull(createdResource)
        Assert.assertEquals(1, createdResource.size)
        val bundle = createdResource[0] as CareTeam
        val createdCareTeam = bundle
        Assert.assertEquals(1, createdCareTeam.participant.size)
        val oncologist = createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "oncologist" }
        val primaryOncologist =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }

        Assert.assertEquals(0, primaryOncologist.size)
        Assert.assertEquals(0, oncologist.size)
        Assert.assertEquals(0, primaryReferringPhysician.size)
        Assert.assertEquals(0, referringPhysician.size)
        //A valid Doctor with Id=docOnco doesn't exist in database
        //Processing of Primary Attending Doctor failed and hence subsequent doctors were not processed.
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(2, errorOrWarning.size)
        Assert.assertEquals("A valid Doctor with Id=docOnco doesn't exist in database", errorOrWarning[0].details.text)
        Assert.assertEquals(
            "Processing of Primary Attending Doctor failed and hence subsequent doctors were not processed.",
            errorOrWarning[1].details.text
        )
    }

    @Test
    fun testPrimaryCareProvider_shouldNotBeAttached_IfPhysicianIsNotPresent() {
        val json = TestHelper.readResource("/patient/PatientBundleWithPrimaryCareProvider.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(Bundle())

        val careTeamResourceCaptor = argumentCaptor<CareTeam>()
        `when`(fhirClient.update(careTeamResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdResource = careTeamResourceCaptor.allValues
        Assert.assertNotNull(createdResource)
        Assert.assertEquals(1, createdResource.size)
        val bundle = createdResource[0]
        val createdCareTeam = bundle
        Assert.assertEquals(1, createdCareTeam.participant.size)
        val oncologist = createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "oncologist" }
        val primaryOncologist =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            createdCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }

        Assert.assertEquals(0, primaryOncologist.size)
        Assert.assertEquals(0, oncologist.size)
        Assert.assertEquals(0, primaryReferringPhysician.size)
        Assert.assertEquals(0, referringPhysician.size)

        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(5, errorOrWarning.size)
        Assert.assertEquals("A valid Doctor with Id=docOnco doesn't exist in database", errorOrWarning[0].details.text)
        Assert.assertEquals(
            "Processing of Primary Attending Doctor failed and hence subsequent doctors were not processed.",
            errorOrWarning[1].details.text
        )
        Assert.assertEquals("A valid Doctor with Id=docPD1 doesn't exist in database", errorOrWarning[2].details.text)
        Assert.assertEquals("A valid Doctor with Id=docRp1 doesn't exist in database", errorOrWarning[3].details.text)
        Assert.assertEquals("A valid Doctor with Id=docPD2 doesn't exist in database", errorOrWarning[4].details.text)
    }

    @Test
    fun testInActivePhysician_shouldBeFiltered_whenUpdatingPatient() {
        //input contains one primary oncologist which is inactive in domain
        //input contains one referring physician which is inactive in domain
        //verify interface should not unmark him as primary care provider
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-2", "primary-referring-physician", careTeam)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        /*val domainCareTeam = domainBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-1", "primary-oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner/Practitioner-2", "primary-referring-physician", domainCareTeam, true)
*/
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)

        val pracBundle = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setActive(false).setId(IdType("Practitioner-2")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)
        val updatedCareTeam = updatedResourceCaptor.allValues[1] as CareTeam
        Assert.assertEquals(1, updatedCareTeam.participant.size)
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }
        Assert.assertEquals(0, primaryOncologist.size)
        Assert.assertEquals(0, referringPhysician.size)
        Assert.assertEquals(0, primaryReferringPhysician.size)
    }

    @Test
    fun testPrimaryCareProvider_shouldNotClearPrimaryCareProvider_whenUpdatingPatient() {
        //one primary care provider exist
        //input contains same physician but without primary care provider code.
        //verify interface should not unmark him as primary care provider
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-2", "primary-referring-physician", careTeam)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-1", "primary-oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner/Practitioner-2", "primary-referring-physician", domainCareTeam, true)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainCTBundle)

        val pracBundle = Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(Practitioner().setId(IdType("Practitioner-2")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)
        val updatedCareTeam = updatedResourceCaptor.allValues[1] as CareTeam
        Assert.assertEquals(3, updatedCareTeam.participant.size)
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }
        Assert.assertEquals(1, primaryOncologist.size)
        Assert.assertEquals(0, referringPhysician.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals("56802-2", primaryReferringPhysician[0].role[1].codingFirstRep.code)
        Assert.assertEquals("Practitioner/Practitioner-1", primaryOncologist[0].member.reference)
        Assert.assertEquals("Practitioner/Practitioner-2", primaryReferringPhysician[0].member.reference)
    }

    @Test
    fun testPrimaryCareProvider_shouldClearPrimaryCareProviderForExistingCareTeam_IfNULLInMessage() {
        //one primary care provider exist
        //input contains NULL value with primary care provider role.
        //verify interface should not unmark him as primary care provider
        val json = TestHelper.readResource("/patient/PatientBundleWithNullPrimaryCareProvider.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "1")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")


        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-1", "primary-oncologist", domainCareTeam)
        addPhysicianParticipant("Practitioner/Practitioner-2", "primary-referring-physician", domainCareTeam, true)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainCTBundle)

        val pracBundle = Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(Practitioner().setId(IdType("Practitioner-2")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)
        val updatedCareTeam = updatedResourceCaptor.allValues[1] as CareTeam
        Assert.assertEquals(3, updatedCareTeam.participant.size)
        Assert.assertNull(updatedCareTeam.participant.find { it.role.any { role -> role.codingFirstRep.code == "56802-2" } })
    }

    @Test
    fun testPrimaryCareProvider_shouldUpdatePrimaryCareProvider_whenUpdatingPatient() {
        //one physician exist but not as primary care provider
        //input contains same physician, with primary care provider code.
        //verify interface should mark him as primary care provider
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("1")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam

        //same is added twice to mock xlate behaviour. xlate transform will add two times. once from PV1/Role and second from PD1
        addPhysicianParticipant("Practitioner-2", "referring-physician", careTeam)
        addPhysicianParticipant("Practitioner-2", "referring-physician", careTeam, true)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-2", "referring-physician", domainCareTeam)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()

        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)


        val pracBundle = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-2"))).setActive(true).setId(IdType("Practitioner-2")) as Practitioner)
        )

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)
        val updatedCareTeam = updatedResourceCaptor.allValues[1] as CareTeam
        Assert.assertEquals(2, updatedCareTeam.participant.size)
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }
        Assert.assertEquals(0, primaryOncologist.size)
        Assert.assertEquals(1, referringPhysician.size)
        Assert.assertEquals(0, primaryReferringPhysician.size)
        Assert.assertEquals("56802-2", referringPhysician[0].role[1].codingFirstRep.code)
        Assert.assertEquals("Practitioner/Practitioner-2", referringPhysician[0].member.reference)
    }

    @Test
    fun testPrimaryCareProvider_shouldAddPrimaryCareProvider_whenUpdatingPatient() {
        //one physician exist as primary care provider
        //input contains other physician, with primary care provider code.
        //verify interface should add second physician mark him as primary care provider and existing physician should be primary as well
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam

        //same is added twice to mock xlate behaviour. xlate transform will add two times. once from PV1/Role and second from PD1
        addPhysicianParticipant("Practitioner-2", "primary-referring-physician", careTeam, false)
        addPhysicianParticipant("Practitioner-2", "referring-physician", careTeam, true)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-1", "primary-referring-physician", domainCareTeam, true)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()

        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainCTBundle)

        val pracBundle = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setIdentifier(mutableListOf(Identifier().setValue("Practitioner-2"))).setActive(true).setId(IdType("Practitioner-2")) as Practitioner)
        )
        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)
        val updatedCareTeam = updatedResourceCaptor.allValues[1] as CareTeam
        Assert.assertEquals(3, updatedCareTeam.participant.size)
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }
        Assert.assertEquals(0, primaryOncologist.size)
        Assert.assertEquals(1, referringPhysician.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals("56802-2", primaryReferringPhysician[0].role[1].codingFirstRep.code)
        Assert.assertEquals("Practitioner/Practitioner-2", primaryReferringPhysician[0].member.reference)
        Assert.assertEquals("56802-2", referringPhysician[0].role[1].codingFirstRep.code)
        Assert.assertEquals("Practitioner/Practitioner-1", referringPhysician[0].member.reference)
    }

    @Test
    fun testPrimaryCareProvider_shouldUpdatePrimaryCareProvider_AndPrimaryReferringPhysician() {
        //one referring physician exist
        //input contains same physician as primary referring physician and with primary care provider code.
        //verify interface should update physician. mark him as primary care provider and primary-referring-physician
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val parameterResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameterResource.parameter.find { it.name == ParametersUtility.SuppressUpdateOnPrimaryCheck }?.value =
            StringType("0")

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner-1", "primary-referring-physician", careTeam)
        addPhysicianParticipant("Practitioner-1", "referring-physician", careTeam, true)

        val domainBundle = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle
        val domainCareTeam = domainCTBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        addPhysicianParticipant("Practitioner/Practitioner-1", "referring-physician", domainCareTeam)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        mockPatientCreateHappyFlow()

        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(domainBundle)
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(domainCTBundle)

        val pracBundle = Bundle().addEntry(
            Bundle.BundleEntryComponent()
                .setResource(Practitioner().setActive(true).setIdentifier(mutableListOf(Identifier().setValue("Practitioner-1"))).setId(IdType("Practitioner-1")) as Practitioner)
        )
        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(pracBundle)

        val updatedResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updatedResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)
        val updatedCareTeam = updatedResourceCaptor.allValues[1] as CareTeam
        Assert.assertEquals(2, updatedCareTeam.participant.size)
        val primaryOncologist =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-oncologist" }
        val referringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "referring-physician" }
        val primaryReferringPhysician =
            updatedCareTeam.participant.filter { it.roleFirstRep.codingFirstRep.code == "primary-referring-physician" }
        Assert.assertEquals(0, primaryOncologist.size)
        Assert.assertEquals(0, referringPhysician.size)
        Assert.assertEquals(1, primaryReferringPhysician.size)
        Assert.assertEquals("56802-2", primaryReferringPhysician[0].role[1].codingFirstRep.code)
        Assert.assertEquals("Practitioner/Practitioner-1", primaryReferringPhysician[0].member.reference)
    }

    @Test
    fun test_UpdatePatient_ShouldNotMapCustomAttribute_IfInputHasNoCustomAttribute() {
        //input patient has no attributes and domain patient has attributes
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.customAttributes = mutableListOf()
        domainPatient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", "Attribute value"))
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        Assert.assertNotNull(updatedPatient.customAttributes)
        Assert.assertEquals(1, updatedPatient.customAttributes.size)
        Assert.assertEquals("UserAttrib01", updatedPatient.customAttributes[0].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel1", updatedPatient.customAttributes[0].code.codingFirstRep.display)
        Assert.assertEquals("Attribute value", updatedPatient.customAttributes[0].value.codingFirstRep.code)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_UpdatePatient_ShouldAddFirstCustomAttributes() {
        //input patient has attribute and domain does not have any attributes
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.customAttributes = mutableListOf()
        patient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", "Attribute value"))

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        Assert.assertNotNull(updatedPatient.customAttributes)
        Assert.assertEquals(1, updatedPatient.customAttributes.size)
        Assert.assertEquals("UserAttrib01", updatedPatient.customAttributes[0].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel1", updatedPatient.customAttributes[0].code.codingFirstRep.display)
        Assert.assertEquals("Attribute value", updatedPatient.customAttributes[0].value.codingFirstRep.code)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_UpdatePatient_ShouldModifyCustomAttribute_IfItsValueIsEmpty() {
        //input patient has attribute but value is empty and domain  has same attribute with valid value
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.customAttributes = mutableListOf()
        patient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", ""))

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.customAttributes = mutableListOf()
        domainPatient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", "Attribute Value"))
        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        Assert.assertNotNull(updatedPatient.customAttributes)
        Assert.assertEquals(1, updatedPatient.customAttributes.size)
        Assert.assertEquals("UserAttrib01", updatedPatient.customAttributes[0].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel1", updatedPatient.customAttributes[0].code.codingFirstRep.display)
        Assert.assertNull(updatedPatient.customAttributes[0].value.codingFirstRep.code)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_UpdatePatient_ShouldUpdateCustomAttributesValue() {
        //input patient has attribute but value is empty and domain  has same attribute with valid value
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.customAttributes = mutableListOf()
        patient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", "New Attribute Value"))

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.customAttributes = mutableListOf()
        domainPatient.customAttributes.add(
            Patient.CustomAttributes(
                "UserAttrib01",
                "UserLabel1",
                "Old Attribute Value"
            )
        )
        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        Assert.assertNotNull(updatedPatient.customAttributes)
        Assert.assertEquals(1, updatedPatient.customAttributes.size)
        Assert.assertEquals("UserAttrib01", updatedPatient.customAttributes[0].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel1", updatedPatient.customAttributes[0].code.codingFirstRep.display)
        Assert.assertEquals("New Attribute Value", updatedPatient.customAttributes[0].value.codingFirstRep.code)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_UpdatePatient_ShouldAddNewCustomAttributesToExistingAttributes() {
        //input patient has attribute but value is empty and domain  has same attribute with valid value
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.customAttributes = mutableListOf()
        patient.customAttributes.add(Patient.CustomAttributes("UserAttrib02", "UserLabel2", "Attribute Value 2"))

        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.customAttributes = mutableListOf()
        domainPatient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", "Attribute Value 1"))
        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        Assert.assertNotNull(updatedPatient.customAttributes)
        Assert.assertEquals(2, updatedPatient.customAttributes.size)
        Assert.assertEquals("UserAttrib01", updatedPatient.customAttributes[0].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel1", updatedPatient.customAttributes[0].code.codingFirstRep.display)
        Assert.assertEquals("Attribute Value 1", updatedPatient.customAttributes[0].value.codingFirstRep.code)
        Assert.assertEquals("UserAttrib02", updatedPatient.customAttributes[1].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel2", updatedPatient.customAttributes[1].code.codingFirstRep.display)
        Assert.assertEquals("Attribute Value 2", updatedPatient.customAttributes[1].value.codingFirstRep.code)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_PatientCreate_ShouldCreateCustomAttributes() {
        //prepare
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        parameters[ParameterConstant.BUNDLE] = inputBundle
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.customAttributes = mutableListOf()
        patient.customAttributes.add(Patient.CustomAttributes("UserAttrib01", "UserLabel1", "Attribute value"))

        //mock
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle())
        val createResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.create(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val createdPatient = createResourceCaptor.firstValue
        Assert.assertNotNull(createdPatient.customAttributes)
        Assert.assertEquals(1, createdPatient.customAttributes.size)
        Assert.assertEquals("UserAttrib01", createdPatient.customAttributes[0].code.codingFirstRep.code)
        Assert.assertEquals("UserLabel1", createdPatient.customAttributes[0].code.codingFirstRep.display)
        Assert.assertEquals("Attribute value", createdPatient.customAttributes[0].value.codingFirstRep.code)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_ShouldUpdateDeathDateAndCauseAndAutopsyIndicatorAndZPI() {
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.autopsyDetails = Patient.AutopsyDetailsComponent()
        patient.autopsyDetails.status = CodeableConcept().setText("Updated Autopsy Indicator")
        patient.autopsyDetails.outcome = CodeableConcept().setText("Updated Autopsy Outcome")
        patient.patientDeathReason = CodeableConcept(Coding().setCode("Accident"))
        val newDate = Date()
        patient.deceased = DateTimeType(newDate)
        patient.patientClinicalTrial = BooleanType(false)
        patient.sexOrientation = CodeableConcept(Coding().setCode("Updated Straight or heterosexual"))
        patient.genderIdentity = CodeableConcept(Coding().setCode("Updated Female"))
        patient.mobilePhoneProvider = StringType("updated Airtel")
        patient.retirementDetails = Patient.RetirementDetailsComponent()
        patient.retirementDetails.retirementNote = StringType("updated retired")
        patient.retirementDetails.retirementReason = StringType("updated Age")
        patient.retirementDetails.retirementDate = DateType(Date())
        patient.occupation = StringType("updated Engineer")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.autopsyDetails = Patient.AutopsyDetailsComponent()
        domainPatient.autopsyDetails.status = CodeableConcept().setText("OutdatedAutopsyIndicator")
        domainPatient.autopsyDetails.outcome = CodeableConcept().setText("OutdatedAutopsyOutcome")
        domainPatient.patientDeathReason = CodeableConcept(Coding().setCode("Invalid"))
        domainPatient.deceased = BooleanType(true)
        domainPatient.patientClinicalTrial = BooleanType(true)
        domainPatient.sexOrientation = CodeableConcept(Coding().setCode("Old Straight or heterosexual"))
        domainPatient.genderIdentity = CodeableConcept(Coding().setCode("Old Female"))
        domainPatient.mobilePhoneProvider = StringType("Airtel")
        domainPatient.retirementDetails = Patient.RetirementDetailsComponent()
        domainPatient.retirementDetails.retirementNote = StringType("retired")
        domainPatient.retirementDetails.retirementReason = StringType("Age")
        val oldDate = DateTime.now().minusDays(10).toDate()
        domainPatient.retirementDetails.retirementDate = DateType(oldDate)
        domainPatient.occupation = StringType("Engineer")
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        //Assert.assertNotNull(updatedPatient.autopsyDetails)
        Assert.assertEquals("Accident", updatedPatient.patientDeathReason.codingFirstRep.code)
        Assert.assertEquals("Updated Autopsy Indicator", updatedPatient.autopsyDetails.status.text)
        Assert.assertEquals("Updated Autopsy Outcome", updatedPatient.autopsyDetails.outcome.text)
        Assert.assertNotNull(updatedPatient.deceased)
        Assert.assertEquals("Updated Straight or heterosexual", updatedPatient.sexOrientation.codingFirstRep.code)
        Assert.assertEquals("Updated Female", updatedPatient.genderIdentity.codingFirstRep.code)
        Assert.assertFalse(updatedPatient.patientClinicalTrial.booleanValue())
        Assert.assertTrue(updatedPatient.deceased is DateTimeType)

        Assert.assertEquals("updated retired", updatedPatient.retirementDetails.retirementNote.value)
        Assert.assertEquals("updated Age", updatedPatient.retirementDetails.retirementReason.value)
        Assert.assertEquals(newDate, updatedPatient.retirementDetails.retirementDate.value)
        Assert.assertEquals("updated Airtel", updatedPatient.mobilePhoneProvider.value)
        Assert.assertEquals("updated Engineer", updatedPatient.occupation.value)
    }

    @Test
    fun test_ShouldNotUpdateDeathDateANdCauseAndAutopsyIndicator_IfBlankInMessage() {
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.deceased = null
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.autopsyDetails = Patient.AutopsyDetailsComponent()
        domainPatient.autopsyDetails.status = CodeableConcept().setText("OutdatedAutopsy")
        domainPatient.autopsyDetails.outcome = CodeableConcept().setText("OutdatedOutcome")
        domainPatient.patientDeathReason = CodeableConcept(Coding().setCode("Invalid"))
        domainPatient.deceased = DateTimeType(Date())
        domainPatient.patientClinicalTrial = BooleanType(true)
        domainPatient.sexOrientation = CodeableConcept(
            Coding().setSystem("http://varian.com/fhir/CodeSystem/AriaSystem").setCode("old Straight or heterosexual")
        )
        domainPatient.genderIdentity =
            CodeableConcept(Coding().setSystem("http://varian.com/fhir/CodeSystem/AriaSystem").setCode("old Female"))
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))

        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        //Assert.assertNotNull(updatedPatient.autopsyDetails)
        Assert.assertEquals("Invalid", updatedPatient.patientDeathReason.codingFirstRep.code)
        Assert.assertEquals("OutdatedAutopsy", updatedPatient.autopsyDetails.status.text)
        Assert.assertEquals("OutdatedOutcome", updatedPatient.autopsyDetails.outcome.text)
        Assert.assertNotNull(updatedPatient.deceased)
        Assert.assertEquals("old Straight or heterosexual", updatedPatient.sexOrientation.codingFirstRep.code)
        Assert.assertEquals("old Female", updatedPatient.genderIdentity.codingFirstRep.code)
        Assert.assertTrue(updatedPatient.patientClinicalTrial.booleanValue())
    }

    @Test
    fun test_ShouldClearDeathDateAndBirthDateIfActiveNullAndZPI() {
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.deceased = null
        patient.retirementDetails = Patient.RetirementDetailsComponent()
        val dateType = DateType()
        dateType.extensionFirstRep.setValue(StringType("N_U_L_L"))
        patient.retirementDetails.retirementDate = dateType
        patient.retirementDetails.retirementNote = StringType("N_U_L_L")
        patient.retirementDetails.retirementReason = StringType("N_U_L_L")
        patient.mobilePhoneProvider = StringType("N_U_L_L")
        patient.occupation = StringType("N_U_L_L")
        patient.addExtension(Extension("ActiveNullBirthDate", StringType("N_U_L_L")))
        patient.addExtension(Extension("ActiveNullDeceasedDate", StringType("N_U_L_L")))
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.deceased = DateTimeType(Date())
        domainPatient.birthDate = Date()
        domainPatient.mobilePhoneProvider = StringType("Airtel")
        domainPatient.retirementDetails = Patient.RetirementDetailsComponent()
        domainPatient.retirementDetails.retirementNote = StringType("retired")
        domainPatient.retirementDetails.retirementReason = StringType("Age")
        domainPatient.retirementDetails.retirementDate = DateType(Date())
        domainPatient.occupation = StringType("Engineer")

        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), anyString())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient
        Assert.assertNull(updatedPatient.deceased)
        Assert.assertNull(updatedPatient.birthDate)
        Assert.assertNull(updatedPatient.retirementDetails.retirementDate)
        Assert.assertNull(updatedPatient.retirementDetails.retirementNote)
        Assert.assertNull(updatedPatient.retirementDetails.retirementReason)
        Assert.assertNull(updatedPatient.mobilePhoneProvider)
        Assert.assertNull(updatedPatient.occupation)
    }

    @Test
    fun test_ShouldNotClearDeathDateAndBirthDateIfEmpty() {
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.deceased = null
        patient.birthDate = null
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        domainPatient.deceased = DateTimeType(Date())
        domainPatient.birthDate = Date()
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), anyString())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient
        Assert.assertNotNull(updatedPatient.deceased)
        Assert.assertNotNull(updatedPatient.birthDate)
    }

    @Test
    fun test_ShouldSetDeathDateAndCauseAndZPI_IfNotExist() {
        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, "inputHospital", "inputDepartment", "", "", "0")
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.autopsyDetails = Patient.AutopsyDetailsComponent()
        patient.autopsyDetails.status = CodeableConcept().setText("Autopsy")
        patient.autopsyDetails.outcome = CodeableConcept().setText("Outcome")
        patient.patientDeathReason = CodeableConcept(Coding().setCode("Accident"))
        patient.deceased = DateTimeType(Date())
        patient.patientClinicalTrial = BooleanType(true)
        patient.sexOrientation = CodeableConcept(Coding().setCode("Straight or heterosexual"))
        patient.genderIdentity = CodeableConcept(Coding().setCode("Female"))
        patient.mobilePhoneProvider = StringType("Airtel")
        patient.retirementDetails = Patient.RetirementDetailsComponent()
        patient.retirementDetails.retirementNote = StringType("retired")
        patient.retirementDetails.retirementReason = StringType("Age")
        patient.retirementDetails.retirementDate = DateType(Date())
        patient.occupation = StringType("Engineer")
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val domainPatient = Patient()
        domainPatient.setId("Patient-1")
        domainPatient.patientClass = CodeableConcept().addCoding(Coding().setCode("Out Patient"))
        mockPatientCreateHappyFlow()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(domainPatient)))
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), anyString())).thenReturn(Bundle().addEntry(Bundle.BundleEntryComponent().setResource(CareTeam())))
        val updateResourceCaptor = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //verify
        val updatedPatient = updateResourceCaptor.allValues[0] as Patient

        //Assert.assertNotNull(updatedPatient.autopsyDetails)
        Assert.assertEquals("Accident", updatedPatient.patientDeathReason.codingFirstRep.code)
        Assert.assertEquals("Autopsy", updatedPatient.autopsyDetails.status.text)
        Assert.assertEquals("Outcome", updatedPatient.autopsyDetails.outcome.text)
        Assert.assertEquals("Straight or heterosexual", updatedPatient.sexOrientation.codingFirstRep.code)
        Assert.assertEquals("Female", updatedPatient.genderIdentity.codingFirstRep.code)
        Assert.assertTrue(updatedPatient.patientClinicalTrial.booleanValue())
        Assert.assertNotNull(updatedPatient.deceased)
        Assert.assertTrue(updatedPatient.deceased is DateTimeType)
        Assert.assertEquals("retired", updatedPatient.retirementDetails.retirementNote.value)
        Assert.assertEquals("Age", updatedPatient.retirementDetails.retirementReason.value)
        Assert.assertNotNull(updatedPatient.retirementDetails.retirementDate)
        Assert.assertEquals("Airtel", updatedPatient.mobilePhoneProvider.value)
        Assert.assertEquals("Engineer", updatedPatient.occupation.value)
    }

    @Test
    fun testPatientUpdate_forContactUpdateModeSnapshotAll_ShouldSnapshotAllExceptTransport() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("SnapshotAll")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(3, patient.contact.size)
        Assert.assertEquals("Ambulance", patient.contactFirstRep.name.givenAsSingleString)
        Assert.assertEquals("New Employer C Name", patient.contact[1].name.givenAsSingleString)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testPatientUpdate_forContactUpdateModeSnapshotOwn() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("SnapshotOwn")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(6, patient.contact.size)
        Assert.assertNotNull(patient.contact.find { it.name.givenAsSingleString == "New Employer C Name" })
        val ownContactOtherThanTransport = patient.contact.find {
            it.relationshipFirstRep.codingFirstRep.code != "O"
                    && it.extension.any { ext ->
                ext.url == "http://varian.com/fhir/v1/StructureDefinition/lastModificationUser" && (ext.value as? Reference)?.reference?.contains(
                    "Practitioner-1014"
                ) == true
            }
        }
        Assert.assertNull(ownContactOtherThanTransport)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testPatientUpdate_ShouldFilterInvalidContacts() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("SnapshotAll")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inputPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        inputPatient.contact.forEach {
            it.name.family = null; it.name.given = null
        }

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient

        //only domain transport contact will remain same as it is not snapshot
        Assert.assertEquals(1, patient.contact.size)

        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(3, errorOrWarning?.size)
    }

    @Test
    fun test_patientCreated_with_filteredInvalidContacts() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val inputPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        inputPatient.contact.forEach {
            if (it.relationshipFirstRep.codingFirstRep.code == "O") {
                it.name.given[0] = StringType("Cab")
                it.telecomFirstRep.value = ""
            } else {
                it.name = null
            }

        }
        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val transportationValueSetJson = TestHelper.readResource("/patient/ValueSet_Transportation.json")
        val transportationValueSetBundle = parser.parseResource(transportationValueSetJson) as Bundle
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
            fhirClient.operation(
                isA<ValueSet>(),
                eq("\$expand"),
                eq("ValueSet"),
                parameterCaptor.capture(),
                isA<Bundle>()
            )
        ).thenReturn(transportationValueSetBundle)

        val patientResource = argumentCaptor<Patient>()
        val careTeamResource = argumentCaptor<CareTeam>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.update(careTeamResource.capture())).thenReturn(MethodOutcome(IdType("CareTeam-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val careTeam = careTeamResource.firstValue
        val patient = bundleValue
        val parameters = parameterCaptor.firstValue
        Assert.assertEquals("http://varian.com/fhir/ValueSet/patient-transportation-contact", parameters.getParameter("url").primitiveValue())
        Assert.assertEquals("Organization-prov-1", parameters.getParameter("publisher").primitiveValue())
        Assert.assertEquals(careTeam.participantFirstRep.member.reference, "Organization/Organization-dept-1")
        Assert.assertEquals(patient.managingOrganization.reference, "Organization/Organization-prov-1")
        Assert.assertEquals(patient.managingOrganization.id, "Organization/Organization-prov-1")
        Assert.assertEquals(1, patient.contact.size)
        val transportContact =
            patient.contact.find { it.relationshipFirstRep.codingFirstRep.code == "O" && it.name.given[0].value == "Cab" }
        Assert.assertEquals("Cab", transportContact?.name?.given?.get(0)?.value ?: "")
        Assert.assertEquals("5556664545", transportContact?.telecomFirstRep?.value)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(2, errorOrWarning.size)
        Assert.assertEquals(2, errorOrWarning.filter { OperationOutcome.IssueSeverity.WARNING == it.severity }.size)
        Assert.assertEquals(
            1,
            errorOrWarning.filter { it.details.text == "Skipping patient contact as it's last name is empty" }.size
        )
        Assert.assertEquals(
            1,
            errorOrWarning.filter { it.details.text == "Skipping employer contact as it's name is empty" }.size
        )
    }

    @Test
    fun testPatientUpdate_ShouldGetTransportContactNumberFromValueSet() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("SnapshotAll")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inputPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        inputPatient.contact.forEach {
            if (it.relationshipFirstRep.codingFirstRep.code == "O") {
                it.name.given[0] = StringType("Cab")
                it.telecomFirstRep.value = ""
            }
        }

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        val transportationValueSetJson = TestHelper.readResource("/patient/ValueSet_Transportation.json")
        val transportationValueSetBundle = parser.parseResource(transportationValueSetJson) as Bundle
        `when`(
            fhirClient.operation(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(transportationValueSetBundle)

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient

        //will add transport contact Cab in existing list as it is not snapshoy
        Assert.assertEquals(4, patient.contact.size)
        val transportContact =
            patient.contact.find { it.relationshipFirstRep.codingFirstRep.code == "O" && it.name.given[0].value == "Cab" }
        Assert.assertEquals("Cab", transportContact?.name?.given?.get(0)?.value ?: "")
        Assert.assertEquals("5556664545", transportContact?.telecomFirstRep?.value)
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testPatientUpdate_ShouldOverrideExistingTransportContactNumberFromValueSet() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("SnapshotAll")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inputPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        inputPatient.contact.forEach {
            if (it.relationshipFirstRep.codingFirstRep.code == "O") {
                it.name.given[0] = StringType("Cab")
                it.telecomFirstRep.value = ""
            }
        }

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        val domainPatient = existingPatient.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        domainPatient.contact.forEach {
            if (it.relationshipFirstRep.codingFirstRep.code == "O") {
                it.name.given[0] = StringType("Cab")
                it.telecomFirstRep.value = "12345"
            }
        }
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        val transportationValueSetJson = TestHelper.readResource("/patient/ValueSet_Transportation.json")
        val transportationValueSetBundle = parser.parseResource(transportationValueSetJson) as Bundle
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
            fhirClient.operation(
                isA<ValueSet>(),
                eq("\$expand"),
                eq("ValueSet"),
                parameterCaptor.capture(),
                isA<Bundle>()
            )
        ).thenReturn(transportationValueSetBundle)

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient

        //will update transport contact Cab in existing list as it is not snapshot
        Assert.assertEquals(3, patient.contact.size)
        val transportContact =
            patient.contact.find { it.relationshipFirstRep.codingFirstRep.code == "O" && it.name.given[0].value == "Cab" }
        Assert.assertEquals("Cab", transportContact?.name?.given?.get(0)?.value ?: "")
        Assert.assertEquals("5556664545", transportContact?.telecomFirstRep?.value)
        val parameters = parameterCaptor.firstValue
        Assert.assertEquals("http://varian.com/fhir/ValueSet/patient-transportation-contact", parameters.getParameter("url").primitiveValue())
        Assert.assertEquals("Organization-prov-inputhospital", parameters.getParameter("publisher").primitiveValue())
        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testPatientUpdate_forContactUpdate_ShouldPreserveDomainValues_WhenReceivedWithEmptyValues() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("Matching")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inputPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        inputPatient.contact.map {
            it.address = null; it.telecom = null;
            val c = CodeableConcept()
            c.text = ""
            it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival")?.setValue(c)
        }
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient =
            parser.parseResource(TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")) as Bundle
        existingPatient.entry.removeIf { it.resource.fhirType() != FHIRAllTypes.PATIENT.toCode()}
        val domainPatient = existingPatient.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        domainPatient.id = "Patient-1"
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)
        val existingCareTeam =
            parser.parseResource(TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")) as Bundle
        existingCareTeam.entry.removeIf { it.resource.fhirType() != FHIRAllTypes.CARETEAM.toCode()}
        `when`(fhirClient.search(eq("CareTeam"), eq("patient"), any())).thenReturn(existingCareTeam)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        val transportationValueSetJson = TestHelper.readResource("/patient/ValueSet_Transportation.json")
        val transportationValueSetBundle = parser.parseResource(transportationValueSetJson) as Bundle
        `when`(fhirClient.search("ValueSet", "name", "aria-patient-transportation-contact", "publisher", "Organization-prov-inputhospital"))
            .thenReturn(transportationValueSetBundle)

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(3, patient.contact.size)
        Assert.assertEquals(
            domainPatient.contactFirstRep.telecomFirstRep.value,
            patient.contactFirstRep.telecomFirstRep.value
        )
        Assert.assertEquals(
            domainPatient.contactFirstRep.telecomFirstRep.value,
            patient.contactFirstRep.telecomFirstRep.value
        )
        Assert.assertEquals(
            domainPatient.contactFirstRep.address.line[0].value,
            patient.contactFirstRep.address.line[0].value
        )
        Assert.assertEquals(
            domainPatient.contactFirstRep.address.line[1].value,
            patient.contactFirstRep.address.line[1].value
        )
        Assert.assertEquals(domainPatient.contactFirstRep.address.city, patient.contactFirstRep.address.city)
        Assert.assertEquals(domainPatient.contactFirstRep.address.district, patient.contactFirstRep.address.district)
        Assert.assertEquals(domainPatient.contactFirstRep.address.state, patient.contactFirstRep.address.state)
        Assert.assertEquals(domainPatient.contactFirstRep.address.country, patient.contactFirstRep.address.country)
        Assert.assertEquals(
            domainPatient.contactFirstRep.address.postalCode,
            patient.contactFirstRep.address.postalCode
        )
        Assert.assertEquals(
            (domainPatient.contact[1].getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival").value as CodeableConcept).text,
            (patient.contact[1].getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival").value as CodeableConcept).text
        )
        Assert.assertEquals(domainPatient.contact[1].telecomFirstRep.value, patient.contact[1].telecomFirstRep.value)
        Assert.assertEquals(6, patient.contact[2].telecom.size)
        for (i in 0..5) {
            Assert.assertEquals(domainPatient.contact[2].telecom[i].value, patient.contact[2].telecom[i].value)
        }
        Assert.assertEquals(domainPatient.contact[2].address.city, patient.contact[2].address.city)
        Assert.assertEquals(domainPatient.contact[2].address.district, patient.contact[2].address.district)
        Assert.assertEquals(domainPatient.contact[2].address.state, patient.contact[2].address.state)
        Assert.assertEquals(domainPatient.contact[2].address.country, patient.contact[2].address.country)
        Assert.assertEquals(domainPatient.contact[2].address.postalCode, patient.contact[2].address.postalCode)

        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testPatientUpdate_forContactUpdate_ShouldDeleteValues_WhenReceivedWithDoubleQuotes() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("Matching")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inputPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        inputPatient.patientMothersMaidenName = StringType("N_U_L_L")
        inputPatient.personGender = CodeableConcept(Coding().setCode("N_U_L_L"))

        inputPatient.contact.map {
            it.address?.city = "N_U_L_L";
            it.address?.line = mutableListOf(StringType(" N_U_L_L"), StringType("N_U_L_L"))
            it.address?.district = "N_U_L_L";
            it.address?.state = "N_U_L_L";
            it.address?.country = "N_U_L_L";
            it.address?.postalCode = "N_U_L_L";
            it.telecom.forEach { t -> t.value = "N_U_L_L" }
            val c = CodeableConcept()
            c.text = "N_U_L_L"
            it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival")?.setValue(c)
        }


        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient =
            parser.parseResource(TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")) as Bundle
        BundleUtility().getPatient(existingPatient)?.setId("Patient-1")
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val transportationValueSetJson = TestHelper.readResource("/patient/ValueSet_Transportation.json")
        val transportationValueSetBundle = parser.parseResource(transportationValueSetJson) as Bundle
        `when`(fhirClient.search("ValueSet", "name", "aria-patient-transportation-contact", "publisher", "Organization-prov-inputhospital"))
            .thenReturn(transportationValueSetBundle)

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient

        Assert.assertEquals(3, patient.contact.size)
        Assert.assertNull(patient.contactFirstRep.telecomFirstRep.value)
        Assert.assertNull(patient.contactFirstRep.address.line[0].value)
        Assert.assertNull(patient.contactFirstRep.address.line[1].value)
        Assert.assertNull(patient.contactFirstRep.address.city)
        Assert.assertNull(patient.contactFirstRep.address.district)
        Assert.assertNull(patient.contactFirstRep.address.state)
        Assert.assertNull(patient.contactFirstRep.address.country)
        Assert.assertNull(patient.contactFirstRep.address.postalCode)
        Assert.assertNull((patient.contact[1].getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival").value as CodeableConcept).text)
        Assert.assertEquals(6, patient.contact[2].telecom.size)
        for (i in 0..5) {
            Assert.assertNull(patient.contact[2].telecom[i].value)
        }
        Assert.assertNull(patient.contact[2].address.line[0].value)
        Assert.assertNull(patient.contact[2].address.line[1].value)
        Assert.assertNull(patient.contact[2].address.city)
        Assert.assertNull(patient.contact[2].address.district)
        Assert.assertNull(patient.contact[2].address.state)
        Assert.assertNull(patient.contact[2].address.country)
        Assert.assertNull(patient.contact[2].address.postalCode)

        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun testPatientUpdate_forContactUpdate_ShouldStoreNewValues_WhenDomainDoesNotContainAnyValue() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var parametersResource =
            inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "PointOfContactUpdateMode" }?.value = StringType("Matching")
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient =
            parser.parseResource(TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")) as Bundle
        val domainPatient = existingPatient.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        domainPatient.setId("Patient-1")
        domainPatient.contact.map {
            it.address = null; it.telecom = null;
            val c = CodeableConcept()
            c.text = ""
            it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival")?.setValue(c)
        }
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(3, patient.contact.size)
        Assert.assertEquals("(020)020020020", patient.contactFirstRep.telecomFirstRep.value)
        Assert.assertEquals("Employer NAGAR2 Employer Street2", patient.contactFirstRep.address.line[0].value)
        Assert.assertEquals("Thergaon", patient.contactFirstRep.address.line[1].value)
        Assert.assertEquals("Palo Alto", patient.contactFirstRep.address.city)
        Assert.assertTrue(patient.contactFirstRep.address.district.isNullOrEmpty())
        Assert.assertEquals("CA", patient.contactFirstRep.address.state)
        Assert.assertTrue(patient.contactFirstRep.address.country.isNullOrEmpty())
        Assert.assertEquals("90878", patient.contactFirstRep.address.postalCode)
        Assert.assertEquals(
            "comments",
            (patient.contact[1].getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival").value as CodeableConcept).text
        )
        Assert.assertEquals("02532333", patient.contact[1].telecomFirstRep.value)
        Assert.assertEquals(6, patient.contact[2].telecom.size)
        for (i in 0..5) {
            Assert.assertNotNull(patient.contact[2].telecom[i].value)
        }
        Assert.assertEquals("homephone", patient.contact[2].telecom[1].value)
        Assert.assertEquals("mobilephone", patient.contact[2].telecom[2].value)
        Assert.assertEquals("emailaddess@abc.com", patient.contact[2].telecom[3].value)
        Assert.assertEquals("fax", patient.contact[2].telecom[4].value)
        Assert.assertEquals("otherphone", patient.contact[2].telecom[5].value)
        Assert.assertEquals("Street address", patient.contact[2].address.line[0].value)
        Assert.assertEquals("street address 2", patient.contact[2].address.line[1].value)
        Assert.assertEquals("city", patient.contact[2].address.city)
        Assert.assertEquals("county", patient.contact[2].address.district)
        Assert.assertEquals("state", patient.contact[2].address.state)
        Assert.assertEquals("IND", patient.contact[2].address.country)
        Assert.assertEquals("postalcode", patient.contact[2].address.postalCode)

        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }


    @Test
    fun testPatientUpdate_forNameUpdate_ShouldStoreNewValues_WhenDomainDoesNotContainAnyValue() {
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        var patientIn =
            inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient

        patientIn.nameFirstRep.family = "displayNameUpdated"
        val hName = HumanName()
        hName.use = HumanName.NameUse.TEMP
        hName.family = "hname"
        hName.extensionFirstRep.url = "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation"
        hName.extensionFirstRep.setValue(Coding().setCode("IDE"))
        patientIn.name.add(hName)

        val hName1 = HumanName()
        hName1.use = HumanName.NameUse.TEMP
        hName1.family = "hname1"
        hName1.extensionFirstRep.url = "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation"
        hName1.extensionFirstRep.setValue(Coding().setCode("ABC"))
        patientIn.name.add(hName1)

        val hName2 = HumanName()
        hName2.use = HumanName.NameUse.MAIDEN
        hName2.family = "maidenName"
        patientIn.name.add(hName2)

        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val existingPatient =
            parser.parseResource(TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")) as Bundle
        val domainPatient = existingPatient.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        val dhName = HumanName()
        dhName.use = HumanName.NameUse.TEMP
        dhName.addGiven("hnm")
        dhName.extensionFirstRep.url = "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation"
        dhName.extensionFirstRep.setValue(Coding().setCode("IDE"))
        domainPatient.name.add(dhName)
        domainPatient.setId("Patient-1")
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any()))
            .thenReturn(existingPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-inputhospital", inputHospital, "prov"))

        `when`(
            fhirClient.search(
                "Organization",
                "type",
                "dept",
                "partof",
                "Organization-prov-inputhospital",
                "active",
                "true"
            )
        )
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-inputdept", inputDepartment, "dept"))

        val patientResource = argumentCaptor<BaseResource>()
        `when`(fhirClient.update(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.allValues
        verify(fhirClient, never()).create(any())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue[0] as Patient
        Assert.assertEquals(4, patient.name.size)
        val displayName = patient.name.filter { it.use == HumanName.NameUse.OFFICIAL }
        val maidenName = patient.name.filter { it.use == HumanName.NameUse.MAIDEN }
        val legalName = patient.name.filter { it.use == HumanName.NameUse.TEMP }

        Assert.assertEquals(1, displayName.size)
        Assert.assertEquals(1, maidenName.size)
        Assert.assertEquals(2, legalName.size)

        Assert.assertEquals(displayName[0].family, "displayNameUpdated")
        Assert.assertEquals(maidenName[0].family, "maidenName")
        Assert.assertEquals(legalName[0].family, "hname")
        Assert.assertEquals(legalName[1].family, "hname1")

        val errorOrWarning =
            outcome.getOperationOutcome().issue?.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isNullOrEmpty())
    }

    @Test
    fun test_multiplePatientName_create_singleNameWithNoTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "dFName"
        inPatient.name.add(hName)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(1, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertTrue(patient.name[0].givenAsSingleString.isNullOrEmpty())
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())
    }

    @Test
    fun test_multiplePatientName_create_singleNameWithDTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "dFName"
        hName.use = HumanName.NameUse.OFFICIAL
        inPatient.name.add(hName)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(1, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertTrue(patient.name[0].givenAsSingleString.isNullOrEmpty())
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())
    }

    @Test
    fun test_multiplePatientName_create_singleNameWithLTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "dFName"
        hName.use = HumanName.NameUse.TEMP
        hName.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", StringType("IDE"))
        inPatient.name.add(hName)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(
            fhirClient.search(
                eq("Patient"),
                eq("identifier"),
                any()
            )
        ).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(1, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertTrue(patient.name[0].givenAsSingleString.isNullOrEmpty())
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())
    }

    @Test
    fun test_multiplePatientName_create_singleNameWithNTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "dFName"
        hName.use = HumanName.NameUse.USUAL
        inPatient.name.add(hName)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(2, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertTrue(patient.name[0].givenAsSingleString.isNullOrEmpty())
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())

        Assert.assertEquals(HumanName.NameUse.USUAL, patient.name[1].use)
        Assert.assertEquals("dFName", patient.name[1].family)
        Assert.assertTrue(patient.name[1].givenAsSingleString.isNullOrEmpty())
        Assert.assertTrue(patient.name[1].extension.isNullOrEmpty())
    }

    @Test
    fun test_multiplePatientName_create_singleNameWithBTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "dFName"
        hName.given.add(0, StringType("firstName"))
        hName.given.add(1, StringType("middleName"))
        hName.use = HumanName.NameUse.OLD
        inPatient.name.add(hName)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(2, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertEquals(2, patient.name[0].given.size)
        Assert.assertEquals("firstName", patient.name[0].given[0].value)
        Assert.assertEquals("middleName", patient.name[0].given[1].value)
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())

        Assert.assertEquals(HumanName.NameUse.OLD, patient.name[1].use)
        Assert.assertEquals("dFName", patient.name[1].family)
        Assert.assertEquals(2, patient.name[0].given.size)
        Assert.assertEquals("firstName", patient.name[0].given[0].value)
        Assert.assertEquals("middleName", patient.name[0].given[1].value)
        Assert.assertTrue(patient.name[1].extension.isNullOrEmpty())
    }

    @Test
    fun test_multiplePatientName_create_TwoNames_BothWithLBTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "mFName"
        hName.use = HumanName.NameUse.TEMP
        var codeableConcept = Coding()
        codeableConcept.setCode("SYL")
        hName.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", codeableConcept)
        inPatient.name.add(hName)

        val hName1 = HumanName()
        hName1.family = "dFName"
        hName1.use = HumanName.NameUse.TEMP
        codeableConcept = Coding()
        codeableConcept.setCode("IDE")
        hName1.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", codeableConcept)
        inPatient.name.add(hName1)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, never()).update(isA<Patient>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(3, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())

        Assert.assertEquals(HumanName.NameUse.MAIDEN, patient.name[1].use)
        Assert.assertEquals("mFName", patient.name[1].family)
        Assert.assertTrue(patient.name[1].extension.isNullOrEmpty())

        Assert.assertEquals(HumanName.NameUse.TEMP, patient.name[2].use)
        Assert.assertEquals("mFName", patient.name[2].family)
        Assert.assertFalse(patient.name[2].extension.isNullOrEmpty())
        Assert.assertEquals(
            "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation",
            patient.name[2].extensionFirstRep.url
        )
        Assert.assertEquals("SYL", (patient.name[2].extensionFirstRep.value as Coding).code)
    }

    @Test
    fun test_multiplePatientName_create_TwoNames_WithDandLTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName = HumanName()
        hName.family = "mFName"
        hName.use = HumanName.NameUse.TEMP
        var codeableConcept = Coding()
        codeableConcept.setCode("IDE")
        hName.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", codeableConcept)
        inPatient.name.add(hName)

        val hName1 = HumanName()
        hName1.family = "dFName"
        hName1.use = HumanName.NameUse.OFFICIAL
        inPatient.name.add(hName1)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(3, patient.name.count())
        Assert.assertEquals(HumanName.NameUse.OFFICIAL, patient.name[0].use)
        Assert.assertEquals("dFName", patient.name[0].family)
        Assert.assertTrue(patient.name[0].extension.isNullOrEmpty())

        Assert.assertEquals(HumanName.NameUse.MAIDEN, patient.name[1].use)
        Assert.assertEquals("mFName", patient.name[1].family)
        Assert.assertTrue(patient.name[1].extension.isNullOrEmpty())

        Assert.assertEquals(HumanName.NameUse.TEMP, patient.name[2].use)
        Assert.assertEquals("mFName", patient.name[2].family)
        Assert.assertFalse(patient.name[2].extension.isNullOrEmpty())
        Assert.assertEquals(
            "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation",
            patient.name[2].extensionFirstRep.url
        )
        Assert.assertEquals("IDE", (patient.name[2].extensionFirstRep.value as Coding).code)
    }

    @Test
    fun test_multiplePatientName_create_MultipleNames_AllTypeCode() {
        //prepare
        val inputHospital = "inputHospital"
        val inputDepartment = "inputDepartment"
        val defaultHospital = ""
        val defaultDepartment = ""

        val json = TestHelper.readResource("/patient/BundleForHospitalAndDeptValidation.json")
        var inputBundle = parser.parseResource(json) as Bundle
        inputBundle = modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment)
        val inPatient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }!!.resource as Patient
        inPatient.name = mutableListOf()

        val hName1 = HumanName()
        hName1.family = "legalName2"
        hName1.use = HumanName.NameUse.TEMP
        var codeableConcept = Coding()
        codeableConcept.setCode("SYL")
        hName1.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", codeableConcept)
        inPatient.name.add(hName1)

        val hName = HumanName()
        hName.family = "legalName1"
        hName.given.add(StringType("firstLegalName1"))
        hName.given.add(StringType("secondLegalName1"))
        hName.use = HumanName.NameUse.TEMP
        codeableConcept = Coding()
        codeableConcept.setCode("IDE")
        hName.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", codeableConcept)
        inPatient.name.add(hName)

        val hName2 = HumanName()
        hName2.family = "legalName3"
        hName2.use = HumanName.NameUse.TEMP
        codeableConcept = Coding()
        codeableConcept.setCode("ABC")
        hName2.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation", codeableConcept)
        inPatient.name.add(hName2)

        val hName3 = HumanName()
        hName3.family = "displayLastName"
        hName3.given.add(StringType("displayFirstName"))
        hName3.use = HumanName.NameUse.OFFICIAL
        inPatient.name.add(hName3)

        val hName4 = HumanName()
        hName4.family = "preferredLastName"
        hName4.given.add(StringType("preferredFirstName"))
        hName4.use = HumanName.NameUse.USUAL
        inPatient.name.add(hName4)

        val hName5 = HumanName()
        hName5.family = "oldLastName"
        hName5.given.add(StringType("oldFirstName"))
        hName5.use = HumanName.NameUse.OLD
        inPatient.name.add(hName5)

        val hName6 = HumanName()
        hName6.family = "maidenLastName"
        hName6.given.add(StringType("maidenFirstName"))
        hName6.use = HumanName.NameUse.MAIDEN
        inPatient.name.add(hName6)

        parameters[ParameterConstant.BUNDLE] = inputBundle

        //mock
        val noPatient = Bundle()
        `when`(
            fhirClient.search(
                eq("Patient"),
                eq("identifier"),
                any()
            )
        ).thenReturn(noPatient)

        `when`(fhirClient.search("Organization", "name", inputHospital, "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", inputHospital, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", inputDepartment, "dept"))

        val patientResource = argumentCaptor<Patient>()

        `when`(fhirClient.create(patientResource.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        val bundleValue = patientResource.firstValue
        verify(fhirClient, times(1)).update(isA<CareTeam>())
        Assert.assertNotNull(bundleValue)
        val patient = bundleValue
        Assert.assertEquals(7, patient.name.count())
        val displayName = patient.name.find { it.use == HumanName.NameUse.OFFICIAL }
        val maidenName = patient.name.find { it.use == HumanName.NameUse.MAIDEN }
        val preferredName = patient.name.find { it.use == HumanName.NameUse.USUAL }
        val oldName = patient.name.find { it.use == HumanName.NameUse.OLD }
        val tempName = patient.name.filter { it.use == HumanName.NameUse.TEMP }
        Assert.assertEquals(3, tempName.size)

        Assert.assertEquals("displayLastName", displayName?.family)
        Assert.assertEquals("displayFirstName", displayName?.givenAsSingleString)
        Assert.assertTrue(displayName?.extension.isNullOrEmpty())

        Assert.assertEquals("maidenLastName", maidenName?.family)
        Assert.assertEquals("maidenFirstName", maidenName?.givenAsSingleString)
        Assert.assertTrue(maidenName?.extension.isNullOrEmpty())

        Assert.assertEquals("preferredLastName", preferredName?.family)
        Assert.assertEquals("preferredFirstName", preferredName?.givenAsSingleString)
        Assert.assertTrue(preferredName?.extension.isNullOrEmpty())

        Assert.assertEquals("oldLastName", oldName?.family)
        Assert.assertEquals("oldFirstName", oldName?.givenAsSingleString)
        Assert.assertTrue(oldName?.extension.isNullOrEmpty())

        Assert.assertEquals("legalName2", tempName[0].family)
        Assert.assertFalse(tempName[0].extension.isNullOrEmpty())
        Assert.assertEquals(
            "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation",
            patient.name[2].extensionFirstRep.url
        )
        Assert.assertEquals("SYL", (tempName[0].extensionFirstRep.value as Coding).code)

        Assert.assertEquals("legalName1", tempName[1].family)
        Assert.assertEquals("firstLegalName1", tempName[1].given[0].value)
        Assert.assertEquals("secondLegalName1", tempName[1].given[1].value)
        Assert.assertFalse(tempName[1].extension.isNullOrEmpty())
        Assert.assertEquals(
            "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation",
            patient.name[2].extensionFirstRep.url
        )
        Assert.assertEquals("IDE", (tempName[1].extensionFirstRep.value as Coding).code)

        Assert.assertEquals("legalName3", tempName[2].family)
        Assert.assertFalse(tempName[2].extension.isNullOrEmpty())
        Assert.assertEquals(
            "http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation",
            patient.name[2].extensionFirstRep.url
        )
        Assert.assertEquals("ABC", (tempName[2].extensionFirstRep.value as Coding).code)
    }

    private fun mockPatientCreateHappyFlow() {
        val noResource = Bundle()
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(noResource)

        `when`(
            fhirClient.search(
                eq("Practitioner"),
                eq("identifier"),
                any()
            )
        ).thenReturn(noResource)

        `when`(fhirClient.search("Organization", "name", "inputHospital", "type", "prov", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", "inputHospital", "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", "inputDepartment", "dept"))

        `when`(fhirClient.create(any())).thenReturn(MethodOutcome(IdType("Patient-1")))
    }

    private fun modifyForPatientClass(
        inputBundle: Bundle,
        patientClass: String?,
        roomNumber: String?,
        defaultRoomNumber: String?,
        currentDatetime: Date?,
        admissionDate: Date?,
        dischargeDate: Date?
    ) {
        val parameters = inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameters.parameter.find { it.name == "CurrentDatetime" }?.value =
            if (currentDatetime != null) StringType(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(currentDatetime)) else null
        parameters.parameter.find { it.name == "DefaultRoomNumber" }?.value =
            if (defaultRoomNumber != null && defaultRoomNumber.isNotEmpty()) StringType(defaultRoomNumber) else null

        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.patientClass.codingFirstRep.code = patientClass
        patient.patientLocationDetails.roomNumber =
            if (roomNumber != null && roomNumber.isNotEmpty()) StringType(roomNumber) else null
        patient.patientLocationDetails.admissionDate = if (admissionDate != null) DateType(admissionDate) else null
        patient.patientLocationDetails.dischargeDate = if (dischargeDate != null) DateType(dischargeDate) else null
    }

    private fun modifyInputBundle(
        inputBundle: Bundle,
        inputHospital: String,
        inputDepartment: String,
        defaultHospital: String,
        defaultDepartment: String
    ): Bundle {
        return modifyInputBundle(inputBundle, inputHospital, inputDepartment, defaultHospital, defaultDepartment, null)
    }

    private fun modifyInputBundle(
        inputBundle: Bundle,
        inputHospital: String,
        inputDepartment: String,
        defaultHospital: String,
        defaultDepartment: String,
        autoCreateReferringPhysician: String?,
    ): Bundle {
        val parameters = inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameters.parameter.find { it.name == "DefaultHospitalName" }?.value = StringType(defaultHospital)
        parameters.parameter.find { it.name == "DefaultDepartmentId" }?.value = StringType(defaultDepartment)
        if (autoCreateReferringPhysician != null) {
            parameters.parameter.find { it.name == ParametersUtility.AUTO_CREATE_REFERRING_PHYSICIAN }?.value =
                StringType(autoCreateReferringPhysician)
        }

        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.managingOrganization.display = inputHospital

        val careTeam = inputBundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        careTeam.participantFirstRep.member.display = inputDepartment
        return inputBundle
    }

    private fun addParameter(inputBundle: Bundle, key: String, value: String) {
        val parameters = inputBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameters.addParameter(key, StringType(value))
    }

    private fun addPatientIdentifier(
        inputBundle: Bundle,
        system: String,
        value: String
    ) {
        val patient = inputBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        val identifier = Identifier().setSystem(system).setValue(value)
        patient.addIdentifier(identifier)
    }

    private fun getOrganizationBundle(id: String, identifier: String, type: String): Bundle {
        return TestHelper.getOrganizationBundle(id, identifier, type)
    }

    private fun addReferringPhysician(bundle: Bundle, identifierValue: String): Bundle {
        val careTeam = bundle.entry.find { it.resource.fhirType() == "CareTeam" }?.resource as CareTeam
        val identifier =
            Identifier().setSystem("http://varian.com/fhir/identifier/Practitioner/Id").setValue(identifierValue)
        val practitioner =
            Practitioner().setIdentifier(mutableListOf(identifier)).setId("Practitioner-${identifierValue}")
        val reference = Reference("#Practitioner-${identifierValue}")
            .setIdentifier(identifier)
        reference.resource = practitioner
        careTeam.addParticipant().setMember(reference)
            .roleFirstRep.codingFirstRep.setSystem("http://varian.com/fhir/CodeSystem/careteam-participant-role").code =
            "primary-referring-physician"
        careTeam.addContained(practitioner)

        bundle.addEntry(Bundle.BundleEntryComponent().setResource(careTeam))
        return bundle
    }

    private fun addPhysicianParticipant(id: String, roleCode: String, careTeam: CareTeam) {
        addPhysicianParticipant(id, roleCode, careTeam, false)
    }

    private fun addPhysicianParticipant(
        id: String,
        roleCode: String,
        careTeam: CareTeam,
        isPrimaryCareProvider: Boolean
    ) {
        val participant = careTeam.addParticipant()
        participant.setMember(Reference(id).setIdentifier(Identifier().setValue(id).setSystem("system")))
            .roleFirstRep.codingFirstRep.setCode(roleCode).system =
            "http://varian.com/fhir/CodeSystem/careteam-participant-role"
        if (isPrimaryCareProvider) {
            participant.addRole().codingFirstRep.setSystem("http://loinc.org").code = "56802-2"
        }
    }
}
