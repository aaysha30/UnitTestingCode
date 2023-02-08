package dsl.master.inbound.interfaces.adtin.common.account

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import com.nhaarman.mockitokotlin2.*
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
import org.hl7.fhir.r4.model.*
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import java.text.SimpleDateFormat
import java.util.*

class AccountTest {
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
            scriptInformation = scripts.getHandlerFor("aria", "AccountSave")!!.get()
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
        parameters[ParameterConstant.HOSPITAL_DEPT_BUNDLE] = Bundle()
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        parameters["patientId"] = "Patient/Patient-1"
    }

    @Test
    fun testCreate_Account() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "Out Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = getPractitionerBundle()

        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("partof"), eq(""),
            eq("identifier"), any(),
            eq("active"), eq("true")))
            .thenReturn(organizationBundle)

 //       clientDecor.search("Organization", "type", "dept", "partof", hospitalRef, "identifier", deptIdentifier, "active", "true")

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())

        val accountResource = argumentCaptor<com.varian.fhir.resources.Account>()
        Mockito.`when`(fhirClient.create(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val accountBundleReturned = accountResource.firstValue
        Assert.assertNotNull(accountBundleReturned)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testCreate_Account_ShouldIgnoreReferringPhysician() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "Out Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = getPractitionerBundle(false)

        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(organizationBundle)

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())

        val accountResourceCaptor = argumentCaptor<com.varian.fhir.resources.Account>()
        Mockito.`when`(fhirClient.create(accountResourceCaptor.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val accountResource = accountResourceCaptor.firstValue
        Assert.assertNotNull(accountResource)
        Assert.assertEquals(2, accountResource.subject.size)
        Assert.assertTrue(accountResource.subject[1].reference.contains("Patient"))
        Assert.assertNull(accountResource.subject[0].reference)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals("ACCOUNT_PROVIDER_NOT_ONCOLOGIST", errorOrWarning[0].details.codingFirstRep.code)
    }

    @Test
    fun testCreate_Account_ShouldIgnoreInvalidProvider() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "Out Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = Bundle()

        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(organizationBundle)

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())

        val accountResourceCaptor = argumentCaptor<com.varian.fhir.resources.Account>()
        Mockito.`when`(fhirClient.create(accountResourceCaptor.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val accountResource = accountResourceCaptor.firstValue
        Assert.assertNotNull(accountResource)
        Assert.assertEquals(2, accountResource.subject.size)
        Assert.assertTrue(accountResource.subject[1].reference.contains("Patient"))
        Assert.assertNull(accountResource.subject[0].reference)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals("ACCOUNT_INVALID_PROVIDER", errorOrWarning[0].details.codingFirstRep.code)
    }

    @Test
    fun testCreate_Account_ShouldAddOncologist() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "Out Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = getPractitionerBundle(true)

        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(organizationBundle)

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())

        val accountResourceCaptor = argumentCaptor<com.varian.fhir.resources.Account>()
        Mockito.`when`(fhirClient.create(accountResourceCaptor.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val accountResource = accountResourceCaptor.firstValue
        Assert.assertNotNull(accountResource)
        Assert.assertEquals(2, accountResource.subject.size)
        Assert.assertTrue(accountResource.subject[1].reference.contains("Patient"))
        Assert.assertTrue(accountResource.subject[0].reference.contains("Practitioner"))
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testCreate_Account_PatientClassInToOut() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "In Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = getPractitionerBundle()
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(organizationBundle)

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.create(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(0)).create(any())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Account with given Account Number does not exists", errorOrWarning[0].details.text)
    }

    @Test
    fun testCreate_Account_ShouldNotCreateEnteredInErrorAccount() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val account = inputBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.status = Account.AccountStatus.ENTEREDINERROR
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "Out Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = getPractitionerBundle()
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(organizationBundle)

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())
        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).create(any())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals(
            "Account with given Account Number does not exists, Inactive account can not be created",
            errorOrWarning[0].details.text
        )
    }

    @Test
    fun testCreate_AccountNull_PatientClassInToOut() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        inputBundle.entry.remove(inputBundle.entry.find { it.resource.fhirType() == "Account" })
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "In Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(0)).create(any())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
    }

    @Test
    fun testCreate_AccountNull_PatientClassNotInToOut() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        inputBundle.entry.remove(inputBundle.entry.find { it.resource.fhirType() == "Account" })
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "In Patient"
        patientClassValues["newPatientClass"] = "In Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(0)).create(any())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testCreate_Account_OwnerAsHospital() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        parameters[ParameterConstant.BUNDLE] = inputBundle

        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = "Out Patient"
        patientClassValues["newPatientClass"] = "Out Patient"
        parameters["patientClassValues"] = patientClassValues

        //mock
        val practitionerBundle = getPractitionerBundle()

        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(practitionerBundle)

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(Bundle())


        val organizationBundle = getOrganizationDomainBundle()

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("prov"), eq("name"), any()))
            .thenReturn(organizationBundle)

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(Bundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.create(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(any())
        val accountBundleReturned = accountResource.firstValue as Account
        Assert.assertNotNull(accountBundleReturned)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testUpdate_Account() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val startDate = DateTime.now().minusDays(5).toDate()
        val endDate = DateTime.now().minusDays(1).toDate()
        setServicePeriod(inputBundle, startDate, endDate)
        parameters[ParameterConstant.BUNDLE] = inputBundle
        parameters["patientClassValues"] = getPatientClassValues("In Patient", "In Patient")
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val dischargeDate = formatter.parse(formatter.format(Date()))
        parameters["patientDomain"] = getPatientDomain(dischargeDate)

        //mock
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(getPractitionerBundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("prov"), eq("name"), any()))
            .thenReturn(getOrganizationDomainBundle())

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(getAccountDomainBundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.update(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(any())
        val accountBundleReturned = accountResource.firstValue as Account
        Assert.assertNotNull(accountBundleReturned)
        Assert.assertEquals(startDate, accountBundleReturned.servicePeriod.start)
        Assert.assertEquals(endDate, accountBundleReturned.servicePeriod.end)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }


    @Test
    fun testUpdate_Account_should_clear_practitioner_active_null() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val account = inputBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.subjectFirstRep.identifier.value = "N_U_L_L"
        parameters[ParameterConstant.BUNDLE] = inputBundle
        parameters["patientClassValues"] = getPatientClassValues("In Patient", "In Patient")
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val dischargeDate = formatter.parse(formatter.format(Date()))
        parameters["patientDomain"] = getPatientDomain(dischargeDate)

        //mock
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("prov"), eq("name"), any()))
            .thenReturn(getOrganizationDomainBundle())

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(getAccountDomainBundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.update(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(any())
        val accountBundleReturned = accountResource.firstValue as Account
        Assert.assertNotNull(accountBundleReturned)
        Assert.assertTrue(accountBundleReturned.subject.isEmpty())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testUpdate_Account_should_Keep_domain_practitioner_when_input_blank() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val account = inputBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.subjectFirstRep.identifier.value = ""
        parameters[ParameterConstant.BUNDLE] = inputBundle
        parameters["patientClassValues"] = getPatientClassValues("In Patient", "In Patient")
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val dischargeDate = formatter.parse(formatter.format(Date()))
        parameters["patientDomain"] = getPatientDomain(dischargeDate)

        //mock
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("prov"), eq("name"), any()))
            .thenReturn(getOrganizationDomainBundle())

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(getAccountDomainBundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.update(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(any())
        val accountBundleReturned = accountResource.firstValue as Account
        Assert.assertNotNull(accountBundleReturned)
        Assert.assertFalse(accountBundleReturned.subject.isEmpty())
        Assert.assertEquals("Practitioner/Practitioner-1", accountBundleReturned.subjectFirstRep.reference)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testUpdate_Account_PatientClassInToOut() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val startDate = DateTime.now().minusDays(5).toDate()
        val endDate = DateTime.now().minusDays(1).toDate()
        setServicePeriod(inputBundle, startDate, endDate)
        parameters[ParameterConstant.BUNDLE] = inputBundle
        parameters["patientClassValues"] = getPatientClassValues("In Patient", "Out Patient")
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val dischargeDate = formatter.parse(formatter.format(Date()))
        parameters["patientDomain"] = getPatientDomain(dischargeDate)

        //mock
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(getPractitionerBundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("prov"), eq("name"), any()))
            .thenReturn(getOrganizationDomainBundle())

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(getAccountDomainBundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.update(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(any())
        val accountBundleReturned = accountResource.firstValue as Account
        Assert.assertNotNull(accountBundleReturned)
        Assert.assertEquals(startDate, accountBundleReturned.servicePeriod.start)
        Assert.assertEquals(endDate, accountBundleReturned.servicePeriod.end)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun testUpdate_Account_PatientClassInToOut_UsePatientDomainDischargeDateAsEndDate() {
        //prepare
        val json = TestHelper.readResource("/account/PatientBundleWithAccount.json")
        val inputBundle = parser.parseResource(json) as Bundle
        val startDate = DateTime.now().minusDays(5).toDate()
        val endDate = null
        setServicePeriod(inputBundle, startDate, endDate)
        parameters[ParameterConstant.BUNDLE] = inputBundle
        parameters["patientClassValues"] = getPatientClassValues("In Patient", "Out Patient")
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val dischargeDate = formatter.parse(formatter.format(Date()))
        parameters["patientDomain"] = getPatientDomain(dischargeDate)

        //mock
        Mockito.`when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any()))
            .thenReturn(getPractitionerBundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("dept"), eq("identifier"), any()))
            .thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(eq("Organization"), eq("type"), eq("prov"), eq("name"), any()))
            .thenReturn(getOrganizationDomainBundle())

        Mockito.`when`(fhirClient.search(eq("Account"), eq("patient"), any(), eq("identifier"), any()))
            .thenReturn(getAccountDomainBundle())

        val accountResource = argumentCaptor<Bundle>()
        Mockito.`when`(fhirClient.update(accountResource.capture()))
            .thenReturn(MethodOutcome(IdType("Account-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).update(any())
        val accountBundleReturned = accountResource.firstValue as Account
        Assert.assertNotNull(accountBundleReturned)
        Assert.assertEquals(startDate, accountBundleReturned.servicePeriod.start)
        Assert.assertEquals(dischargeDate, accountBundleReturned.servicePeriod.end)
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    private fun setServicePeriod(inputBundle: Bundle, startDate: Date, endDate: Date?) {
        val servicePeriod = Period().setStart(startDate).setEnd(endDate)
        val account = inputBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.setServicePeriod(servicePeriod)
    }

    private fun getPatientDomain(dischargeDate: Date): Patient {
        val patientDomain = Patient().addIdentifier(
            Identifier().setSystem("http://varian.com/fhir/identifier/Patient/ARIAID1")
                .setValue("TestPatient_210102_up3")
        ) as Patient
        val patientLocationDetails = Patient.PatientLocationComponent()
        patientLocationDetails.dischargeDate = DateType(dischargeDate)
        patientDomain.patientLocationDetails = patientLocationDetails
        return patientDomain
    }

    private fun getPatientClassValues(
        existingPatientClass: String,
        newPatientClass: String
    ): MutableMap<String, String> {
        val patientClassValues: MutableMap<String, String> = mutableMapOf()
        patientClassValues["existingPatientClass"] = existingPatientClass
        patientClassValues["newPatientClass"] = newPatientClass
        return patientClassValues
    }

    private fun getOrganizationDomainBundle(): Bundle {
        return Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(
                com.varian.fhir.resources.Organization().addIdentifier(Identifier().setValue("OIS_ID1"))
                    .setId(IdType("Organization-1")) as Organization
            )
        )
    }

    private fun getPractitionerBundle(): Bundle {
        val practitioner = com.varian.fhir.resources.Practitioner()
        practitioner.setId(IdType("Practitioner-1"))
        val roleCode = "oncologist"
        val practitionerRole = CodeableConcept()
        practitionerRole.codingFirstRep
            .setSystem("http://varian.com/fhir/CodeSystem/Practitioner/practitioner-role").code = roleCode
        practitioner.practitionerRoleInARIA = practitionerRole
        practitioner.active = true
        return Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(practitioner)
        )
    }

    private fun getPractitionerBundle(isOncologist: Boolean): Bundle {
        val practitioner = com.varian.fhir.resources.Practitioner()
        practitioner.setId(IdType("Practitioner-1"))
        val roleCode = if (isOncologist) {
            "oncologist"
        } else {
            "referring-physician"
        }
        val practitionerRole = CodeableConcept()
        practitionerRole.codingFirstRep
            .setSystem("http://varian.com/fhir/CodeSystem/Practitioner/practitioner-role").code = roleCode
        practitioner.practitionerRoleInARIA = practitionerRole
        practitioner.active = true
        return Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(practitioner)
        )
    }

    private fun getAccountDomainBundle(): Bundle {
        val account = com.varian.fhir.resources.Account().addIdentifier(
            Identifier().setSystem("http://varian.com/fhir/identifier/Account/Id").setValue("1112")
        ).setStatus(Account.AccountStatus.ACTIVE).setName("ALbl")
        account.subjectFirstRep.reference = "Practitioner/Practitioner-1"
        return Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(
                account
            )
        )
    }
}
