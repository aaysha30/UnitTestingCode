package dsl.master.inbound.interfaces.adtin.patientpreadmit

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.varian.fhir.resources.Patient
import com.varian.fhir.resources.Organization
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import org.hl7.fhir.r4.model.*
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.*

class PatientPreAdmitTest {

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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientPreadmit")!!.get()
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

        val json = TestHelper.readResource("/patient/CareTeamDomainBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val ct = inputBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.CARETEAM.toCode() }?.resource
        `when`(fhirClient.search(eq("CareTeam"), any())).thenReturn(Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(ct)))
    }

    @Test
    fun test_PatientShouldBeCreated_AsOutPatient() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val dischargeDate = Date()
        val patientClass = "Out Patient"
        val inBundle = getPatientPreAdmitBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle


        `when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val createResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.create(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = createResourceCaptor.firstValue
        Assert.assertNotNull(createdPatient)

        Assert.assertNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_InPatientShouldBeCreated_AsOutPatient() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val dischargeDate = Date()
        val patientClass = "In Patient"
        val inBundle = getPatientPreAdmitBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle


        `when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val createResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.create(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = createResourceCaptor.firstValue
        Assert.assertNotNull(createdPatient)
        Assert.assertEquals("Out Patient", createdPatient.patientClass.codingFirstRep.code)
        Assert.assertNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_PatientShouldBeCreated_AsOutPatient_IfPatientClassIsNull() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val dischargeDate = Date()
        val patientClass = null
        val inBundle = getPatientPreAdmitBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle


        `when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val createResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.create(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = createResourceCaptor.firstValue
        Assert.assertNotNull(createdPatient)
        Assert.assertEquals("Out Patient", createdPatient.patientClass.codingFirstRep.code)
        Assert.assertNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test(expected = ResourceNotFoundException::class)
    fun test_PatientShouldNotBeCreated_IfAutoCreateFalse() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val dischargeDate = Date()
        val patientClass = null
        val inBundle = getPatientPreAdmitBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        val inParams = inBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        inParams.parameter.removeIf { it.name == "AutoCreateEvents" }

        parameters[ParameterConstant.BUNDLE] = inBundle


        `when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val createResourceCaptor = argumentCaptor<Bundle>()
        `when`(fhirClient.create(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val bundle = createResourceCaptor.firstValue
        val createdPatient = bundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        Assert.assertNotNull(createdPatient)
        Assert.assertEquals("Out Patient", createdPatient.patientClass.codingFirstRep.code)
        Assert.assertNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_InPatientShouldBeUpdated_AsOutPatient_IfExistingPatientIsOutPatient() {
        val inBundle = getPatientPreAdmitBundle("1", Date(), null, "In Patient")
        parameters[ParameterConstant.BUNDLE] = inBundle

        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = ""
        val admissionDate = null
        val dischargeDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()
        val patientClass = "Out Patient"

        val domainPatientBundle = getDomainPatientBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        `when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val updateResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = updateResourceCaptor.firstValue
        Assert.assertNotNull(updatedPatient)
        Assert.assertEquals("Out Patient", updatedPatient.patientClass.codingFirstRep.code)
        Assert.assertNull( updatedPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(updatedPatient.patientLocationDetails.admissionDate)
        Assert.assertNotNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertEquals(15, updatedPatient.patientLocationDetails.dischargeDate.day)

        //2 because month is start from zero index
        Assert.assertEquals(2, updatedPatient.patientLocationDetails.dischargeDate.month)
        Assert.assertEquals(2020, updatedPatient.patientLocationDetails.dischargeDate.year)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_OutPatientShouldBeUpdated_AsInPatient_IfExistingPatientIsInPatient() {
        val inBundle = getPatientPreAdmitBundle("", null, Date(), "Out Patient")
        parameters[ParameterConstant.BUNDLE] = inBundle

        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"

        val roomNumber = "1234"
        val admissionDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()
        val patientClass = "In Patient"
        val dischargeDate = null
        val domainPatientBundle = getDomainPatientBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        `when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val updateResourceCaptor = argumentCaptor<Patient>()
        `when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = updateResourceCaptor.firstValue
        Assert.assertNotNull(updatedPatient)
        Assert.assertEquals("In Patient", updatedPatient.patientClass.codingFirstRep.code)
        Assert.assertEquals("1234", updatedPatient.patientLocationDetails.roomNumber.value)
        Assert.assertNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertNotNull(updatedPatient.patientLocationDetails.admissionDate)
        Assert.assertEquals(15, updatedPatient.patientLocationDetails.admissionDate.day)

        //2 because month is start from zero index
        Assert.assertEquals(2, updatedPatient.patientLocationDetails.admissionDate.month)
        Assert.assertEquals(2020, updatedPatient.patientLocationDetails.admissionDate.year)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals (0, errorOrWarning.size)
    }

    private fun getDomainPatientBundle(roomNumber: String?, admissionDate: Date?, dischargeDate: Date?, patientClass: String?): Bundle {
        val domainPatientBundleString = TestHelper.readResource("/patientpreadmit/DomainPatientBundle.json")
        val domainPatientBundle = parser.parseResource(domainPatientBundleString) as Bundle
        val patient = domainPatientBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.patientLocationDetails.admissionDate = DateType(admissionDate)
        patient.patientLocationDetails.dischargeDate = DateType(dischargeDate)
        patient.patientLocationDetails.roomNumber = StringType(roomNumber)
        if (patientClass.isNullOrEmpty()) {
            patient.patientClass = null
        } else {
            patient.patientClass.codingFirstRep.code = patientClass
        }
        return domainPatientBundle
    }

    private fun getPatientPreAdmitBundle(roomNumber: String?, admissionDate: Date?, dischargeDate: Date?, patientClass: String?): Bundle {
        val patientPreAdmitJson = TestHelper.readResource("/patientpreadmit/PatientPreAdmitBundle.json")
        val inBundle = parser.parseResource(patientPreAdmitJson) as Bundle
        val patient = inBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.patientLocationDetails.admissionDate = DateType(admissionDate)
        patient.patientLocationDetails.dischargeDate = DateType(dischargeDate)
        patient.patientLocationDetails.roomNumber = StringType(roomNumber)
        if (patientClass.isNullOrEmpty()) {
            patient.patientClass = null
        } else {
            patient.patientClass.codingFirstRep.code = patientClass
        }
        return inBundle
    }

    private fun mockHospitalAndDepartmentSearch(hospitalId: String, departmentId: String) {
        `when`(fhirClient.search("Organization", "name", hospitalId, "type", "prov", "active", "true"))
                .thenReturn(TestHelper.getOrganizationBundle("Organization/Organization-prov-1", hospitalId, "prov"))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
                .thenReturn(TestHelper.getOrganizationBundle("Organization/Organization-dept-1", departmentId, "dept"))
    }
}
