package dsl.master.inbound.interfaces.adtin.patientcanceldischarge

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.nhaarman.mockitokotlin2.*
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
import org.hl7.fhir.r4.model.*
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import java.util.*

class PatientCancelDischargeTest {
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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientCancelDischarge")!!.get()
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

    @Test(expected = UnprocessableEntityException::class)
    fun testPatientCancelDischarge_GivenAccountExistButErrorOutInDomain() {
        //when account information is coming through pid(account status = null) and given account does not exist
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val dischargeDate = Date()
        val patientClass = "In Patient"
        val inBundle = getPatientCancelDischargeBundle(dischargeDate, "Out Patient", null)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle(patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)


        val domainAccountBundle = getDomainAccountBundle(true, Date())
        (domainAccountBundle.entry[0].resource as Account).status = org.hl7.fhir.r4.model.Account.AccountStatus.ENTEREDINERROR
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-191"), eq("identifier"),
                any())
        ).thenReturn(domainAccountBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val updateResourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)
    }

    @Test
    fun testPatientCancelDischarge_GivenAccountDoesNotExist() {
        //when account information is coming through pid(account status = null) and given account does not exist
        //should add warning and ignore account create/update. should update patient
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val dischargeDate = Date()
        val patientClass = "In Patient"
        val inBundle = getPatientCancelDischargeBundle(dischargeDate, "Out Patient", null)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle(patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        val careTeamBundle = Bundle()
        Mockito.`when`(fhirClient.search(
            eq("CareTeam"), eq("patient"),
            any())
        ).thenReturn(careTeamBundle)

        val domainAccountBundle = Bundle()
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-191"), eq("identifier"),
                any())
        ).thenReturn(domainAccountBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val updateResourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = updateResourceCaptor.allValues.find { it.fhirType() == "Patient" } as Patient
        Assert.assertNotNull(updatedPatient)
        verify(fhirClient, never()).create(isA<Account>())
        verify(fhirClient, never()).update(isA<Account>())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Account with given Account Number does not exists", errorOrWarning[0].details.text)
    }

    @Test
    fun testPatientCancelDischarge_AccountNumberIsNullOrEmpty() {
        //when account number is null
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val dischargeDate = Date()
        val patientClass = "In Patient"
        val inBundle = getPatientCancelDischargeBundle(dischargeDate, "Out Patient", null)
        inBundle.entry.removeIf { it.resource.fhirType() == "Account" }
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle(patientClass)
        Mockito.`when`(fhirClient.search(
            eq("Patient"), eq("identifier"),
            any())
        ).thenReturn(domainPatientBundle)

        val careTeamBundle = Bundle()
        Mockito.`when`(fhirClient.search(
            eq("CareTeam"), eq("patient"),
            any())
        ).thenReturn(careTeamBundle)


        Mockito.`when`(fhirClient.search(
            eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
            any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val updateResourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(updateResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = updateResourceCaptor.allValues.find { it.fhirType() == "Patient" } as Patient
        Assert.assertNotNull(updatedPatient)
        verify(fhirClient, never()).create(isA<Account>())
        verify(fhirClient, never()).update(isA<Account>())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
    }

    @Test
    fun testPatientCancelDischarge_whenAccountStatusIsPresentInInputMessage() {
        //when account status is present, should assign patient class from input account status
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val dischargeDate = Date()
        val patientClass = "Out Patient"
        val inBundle = getPatientCancelDischargeBundle(dischargeDate, "Out Patient", true)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle(patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        val careTeamBundle = Bundle()
        Mockito.`when`(fhirClient.search(
            eq("CareTeam"), eq("patient"),
            any())
        ).thenReturn(careTeamBundle)

        val domainAccountBundle = getDomainAccountBundle(false, Date())
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-191"), eq("identifier"),
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
        Assert.assertEquals("In Patient", updatedPatient.patientClass.codingFirstRep.code)
        Assert.assertNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertNull(updatedAccount.servicePeriod.end)
        Assert.assertTrue(updatedAccount.inPatient.booleanValue())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testPatientCancelDischarge_whenAccountStatusIsNotPresentInInputMessage() {
        //when account information is coming through pid and pv1 does not contain patient class
        //should assign existing(domain) account class to patient class
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val dischargeDate = Date()
        val patientClass = "Out Patient"
        val inBundle = getPatientCancelDischargeBundle(dischargeDate, null, null)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle(patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        val careTeamBundle = Bundle()
        Mockito.`when`(fhirClient.search(
            eq("CareTeam"), eq("patient"),
            any())
        ).thenReturn(careTeamBundle)

        val domainAccountBundle = getDomainAccountBundle(true, Date())
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-191"), eq("identifier"),
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
        Assert.assertEquals("In Patient", updatedPatient.patientClass.codingFirstRep.code)
        Assert.assertNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertNull(updatedAccount.servicePeriod.end)
        Assert.assertTrue(updatedAccount.inPatient.booleanValue())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
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

    private fun getDomainAccountBundle(accountStatus: Boolean?, endDate: Date): Bundle {
        val domainAccountBundleString = TestHelper.readResource("/patientdischarge/DomainAccountBundle.json")
        val domainAccountBundle = parser.parseResource(domainAccountBundleString) as Bundle
        val account = domainAccountBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        if (accountStatus != null) {
            account.inPatient = BooleanType(accountStatus)
        } else {
            account.inPatient = null
        }

        account.servicePeriod.end = endDate
        return domainAccountBundle
    }

    private fun getDomainPatientBundle(patientClass: String?): Bundle {
        val domainPatientBundleString = TestHelper.readResource("/patientpreadmit/DomainPatientBundle.json")
        val domainPatientBundle = parser.parseResource(domainPatientBundleString) as Bundle
        val patient = domainPatientBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        if (patientClass.isNullOrEmpty()) {
            patient.patientClass = null
        } else {
            patient.patientClass.codingFirstRep.code = patientClass
        }
        return domainPatientBundle
    }

    private fun getPatientCancelDischargeBundle(dischargeDate: Date?, patientClass: String?, accountStatus: Boolean?): Bundle {
        val patientCancelDischargeJson = TestHelper.readResource("/patientcanceldischarge/PatientCancelDischargeBundle.json")
        val inBundle = parser.parseResource(patientCancelDischargeJson) as Bundle
        val patient = inBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        val account = inBundle.entry.findLast { it.resource.fhirType() == "Account" }?.resource as Account
        if (patientClass.isNullOrEmpty()) {
            patient.patientClass = null
        } else {
            patient.patientClass.codingFirstRep.code = patientClass
        }

        if (accountStatus != null) {
            account.inPatient = BooleanType(accountStatus)
        } else {
            account.inPatient = null
        }
        account.servicePeriod.end = null
        patient.patientLocationDetails.dischargeDate = DateType(dischargeDate)

        return inBundle
    }
}
