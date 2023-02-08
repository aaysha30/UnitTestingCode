package dsl.master.inbound.interfaces.adtin.patientswaplocation

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.varian.fhir.resources.Patient
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory
import com.varian.mappercore.framework.scripting.ScriptInformation
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.IdType
import org.hl7.fhir.r4.model.Organization
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class PatientSwapLocationTest {
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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientSwapLocation")!!.get()
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
        parameters[ParameterConstant.USER] = "Practitioner-1014"
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger.initCLoverLogger(mock())
        parameters[ParameterConstant.HOSPITAL_DEPT_BUNDLE] = Bundle()
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
    }

    @Test
    fun test_throwsErrorIfBothPatientsAreNotPresent() {
        val json = TestHelper.readResource("/patientswaplocation/PatientSwapLocation.json")
        val bundle = parser.parseResource(json) as Bundle
        //remove one patient
        bundle.entry.removeAt(2)
        parameters[ParameterConstant.BUNDLE] = bundle

        //execute
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: ResourceNotFoundException) {
            outcome.addError(ex)
            val errorOrWarnings = outcome.getOperationOutcome().issue
            Assert.assertEquals(1, errorOrWarnings.size)
            Assert.assertEquals("PATIENT_SWAP_LOCATION", errorOrWarnings[0].details.codingFirstRep.code)
            Assert.assertEquals("Patient identifier and visit details must be present for both patient", errorOrWarnings[0].details.text)
        }
    }

    @Test
    fun test_throwsErrorIfVisitDetailsAreNotPresent() {
        val json = TestHelper.readResource("/patientswaplocation/PatientSwapLocation.json")
        val bundle = parser.parseResource(json) as Bundle
        //remove one patient
        bundle.entry.find { it.resource.fhirType() == "Patient" }?.resource.let { (it as Patient).patientLocationDetails?.roomNumber = null }
        parameters[ParameterConstant.BUNDLE] = bundle

        //execute
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: ResourceNotFoundException) {
            outcome.addError(ex)
            val errorOrWarnings = outcome.getOperationOutcome().issue
            Assert.assertEquals(1, errorOrWarnings.size)
            Assert.assertEquals("PATIENT_SWAP_LOCATION", errorOrWarnings[0].details.codingFirstRep.code)
            Assert.assertEquals("Patient identifier and visit details must be present for both patient", errorOrWarnings[0].details.text)
        }
    }

    @Test
    fun test_returnWarningForOutPatient() {
        val json = TestHelper.readResource("/patientswaplocation/PatientSwapLocation.json")

        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(
                fhirClient.search(
                        eq("Patient"), eq("identifier"),
                        any(), eq("_revinclude"), eq("CareTeam:patient")
                )
        )
                .thenReturn(existingPatient)

        `when`(fhirClient.update(isA<Patient>())).thenReturn(MethodOutcome(IdType("Patient-1")), MethodOutcome(IdType("Patient-2")))
        mockHospitalAndDepartmentSearch("ACHospital", "TEST_ID1")

        parameters[ParameterConstant.BUNDLE] = parser.parseResource(json)

        //execute
        scripts.run(parameters, scriptInformation)
        val errorOrWarnings = outcome.getOperationOutcome().issue
        Assert.assertNotNull(errorOrWarnings)
        val expectedMessage = "Location can not be swapped for first Outpatient"
        Assert.assertNotNull(errorOrWarnings.find { it.details.text == expectedMessage })
        Assert.assertNull(errorOrWarnings.find { it.details.text == "Location can not be swapped for second Outpatient" })
    }

    private fun mockHospitalAndDepartmentSearch(hospitalId: String, departmentId: String) {
        `when`(fhirClient.search("Organization", "name", hospitalId, "type", "prov", "active", "true"))
                .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", hospitalId))

        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
                .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", departmentId))
    }

    private fun getOrganizationBundle(id: String, identifier: String): Bundle {
        val orgBundle = Bundle()
        val organization = Organization()
        organization.id = id
        organization.identifierFirstRep.value = identifier
        orgBundle.addEntry().resource = organization
        return orgBundle
    }
}
