package dsl.master.inbound.interfaces.adtin.patientcanceladmit

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.varian.fhir.resources.Account
import com.varian.fhir.resources.Patient
import com.varian.fhir.resources.Organization
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory
import com.varian.mappercore.framework.scripting.ScriptInformation
import dsl.master.inbound.interfaces.adtin.patientsave.PatientSaveTest
import org.hl7.fhir.r4.model.*
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import java.util.*

class PatientCancelAdmitTest {
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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientCancelAdmit")!!.get()
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
    }

    @Test
    fun test_PatientShouldBeUpdated_AsOutPatientAndItsAccountShouldBeErrorOut() {
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val dischargeDate = Date()
        val patientClass = "In Patient"
        val inBundle = getPatientCancelAdmitBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)
        val domainCTBundle = parser.parseResource(TestHelper.readResource("/patient/CareTeamBundle.json")) as Bundle

        Mockito.`when`(fhirClient.search(
            eq("CareTeam"), eq("patient"),
            any())
        ).thenReturn(domainCTBundle)
        val domainAccountBundle = getDomainAccountBundle(Date(), null)
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(domainAccountBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val updateResourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = updateResourceCaptor.allValues.find { it.fhirType() == "Patient" } as Patient
        val updatedAccount = updateResourceCaptor.allValues.find { it.fhirType() == "Account" } as Account

        Assert.assertNotNull(updatedPatient)
        Assert.assertNotNull(updatedAccount)
        //domain patient is InPatient and input patient class is out patient
        //so room number and admission date will become null and discharge date will remain as it is
        //and account status will be entered-in-error since this is A11(CancelAdmit) event
        Assert.assertNull(updatedPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(updatedPatient.patientLocationDetails.admissionDate)
        Assert.assertNotNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertEquals(org.hl7.fhir.r4.model.Account.AccountStatus.ENTEREDINERROR, updatedAccount.status)
        Assert.assertFalse(updatedAccount.inPatient.booleanValue())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    private fun getDomainAccountBundle(startDate: Date?, endDate: Date?): Bundle {
        val domainAccountBundleString = TestHelper.readResource("/patientdischarge/DomainAccountBundle.json")
        val domainAccountBundle = parser.parseResource(domainAccountBundleString) as Bundle
        val account = domainAccountBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.servicePeriod.start = startDate
        account.servicePeriod.end = endDate
        return domainAccountBundle
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

    private fun getPatientCancelAdmitBundle(roomNumber: String?, admissionDate: Date?, dischargeDate: Date?, patientClass: String?): Bundle {
        val patientPreAdmitJson = TestHelper.readResource("/patientcanceladmit/PatientCancelAdmitBundle.json")
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
        Mockito.`when`(fhirClient.search("Organization", "name", hospitalId, "type", "prov", "active", "true"))
                .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", hospitalId, "prov"))

        Mockito.`when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
                .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", departmentId, "dept"))
    }

    private fun getOrganizationBundle(id: String, identifier: String, type: String): Bundle {
        val orgBundle = Bundle()
        val organization = Organization()
        organization.id = id
        organization.name = identifier
        organization.identifierFirstRep.value = identifier
        organization.typeFirstRep.codingFirstRep.code = type
        orgBundle.addEntry().resource = organization
        return orgBundle
    }
}
