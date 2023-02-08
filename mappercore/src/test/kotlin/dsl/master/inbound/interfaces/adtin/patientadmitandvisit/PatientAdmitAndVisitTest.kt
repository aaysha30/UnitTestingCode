package dsl.master.inbound.interfaces.adtin.patientadmitandvisit

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.varian.fhir.resources.Patient
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import org.hl7.fhir.r4.model.*
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import java.util.*
import com.nhaarman.mockitokotlin2.*

class PatientAdmitAndVisitTest {
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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientAdmitAndVisit")!!.get()
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

        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger.initCLoverLogger(mock())
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        parameters[ParameterConstant.ATTACH_HOSPITAL_Departments] = false
        parameters[ParameterConstant.UPDATE_PRIMARY_DEPARTMENT] = true
        parameters[ParameterConstant.SNAPSHOT_DEPARTMENTS] = true
        parameters[ParameterConstant.HOSPITAL_DEPT_BUNDLE] = Bundle()

        val json = TestHelper.readResource("/patient/CareTeamDomainBundle.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val ct = inputBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.CARETEAM.toCode() }?.resource
        Mockito.`when`(fhirClient.search(eq("CareTeam"), any())).thenReturn(Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(ct)))
    }

    @Test
    fun test_PatientShouldBeCreated_AsInPatient() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val patientClass = "In Patient"
        val inBundle = getPatientAdmitAndVisitBundle(patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle

        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<Patient>()
        Mockito.`when`(fhirClient.create(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = resourceCaptor.firstValue

        Assert.assertNotNull(createdPatient)
        //input patient class is InPatient
        //so room number and admission date will be non null and discharge date will be null
        Assert.assertEquals("In Patient", createdPatient.patientClass.codingFirstRep.code)
        Assert.assertNotNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNotNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_PatientShouldBeCreated_AsInPatient_IfPatientClassIsNull() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val patientClass = "In Patient"
        val inBundle = getPatientAdmitAndVisitBundle(null)
        parameters[ParameterConstant.BUNDLE] = inBundle

        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<Patient>()
        Mockito.`when`(fhirClient.create(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = resourceCaptor.firstValue

        Assert.assertNotNull(createdPatient)
        //input patient class is InPatient
        //so room number and admission date will be non null and discharge date will be null
        Assert.assertEquals("In Patient", createdPatient.patientClass.codingFirstRep.code)
        Assert.assertNotNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNotNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_PatientShouldBeCreated_AsOutPatientAndWarningShouldBeRaised() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val patientClass = "Out Patient"
        val inBundle = getPatientAdmitAndVisitBundle(patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle

        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        val domainAccountBundle = TestHelper.getDomainAccountBundle(Date(), null)
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(domainAccountBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<Patient>()
        Mockito.`when`(fhirClient.create(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = resourceCaptor.firstValue

        Assert.assertNotNull(createdPatient)
        //input patient is OutPatient
        //so room number and admission date  and discharge date will be null
        Assert.assertEquals("Out Patient", createdPatient.patientClass.codingFirstRep.code)
        Assert.assertNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)

        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, errorOrWarning[0].severity)
        Assert.assertEquals("Patient class is invalid. Expected value is 'I'", errorOrWarning[0].details.text)
        Assert.assertEquals("INVALID_PATIENT_CLASS_ADT_A01", errorOrWarning[0].details.codingFirstRep.code)
    }

    @Test(expected = ResourceNotFoundException::class)
    fun test_PatientShouldNotBeCreated_IfAutoCreateIsFalse() {
        val patientClass = "Out Patient"
        val inBundle = getPatientAdmitAndVisitBundle(patientClass)
        val parametersResource = inBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.removeIf { it.name == "AutoCreateEvents" }
        parameters[ParameterConstant.BUNDLE] = inBundle

        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        //execute
        scripts.run(parameters, scriptInformation)
    }

    private fun getPatientAdmitAndVisitBundle(patientClass: String?): Bundle {
        val patientPreAdmitJson = TestHelper.readResource("/patientpreadmit/PatientPreAdmitBundle.json")
        val inBundle = parser.parseResource(patientPreAdmitJson) as Bundle
        val parameters = inBundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parameters.parameter.find { it.name == "Event" }?.value = StringType("ADT^A01")
        val patient = inBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        if (patientClass.isNullOrEmpty()) {
            patient.patientClass = null
        } else {
            patient.patientClass.codingFirstRep.code = patientClass
        }
        return inBundle
    }

    private fun mockHospitalAndDepartmentSearch(hospitalId: String, departmentId: String) {
        Mockito.`when`(fhirClient.search("Organization", "name", hospitalId, "type", "prov", "active", "true"))
                .thenReturn(TestHelper.getOrganizationBundle("Organization/Organization-prov-1", hospitalId, "prov"))

        Mockito.`when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
                .thenReturn(TestHelper.getOrganizationBundle("Organization/Organization-dept-1", departmentId, "dept"))
    }
}
