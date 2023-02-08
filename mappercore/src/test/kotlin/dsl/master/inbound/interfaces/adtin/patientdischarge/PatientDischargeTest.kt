package dsl.master.inbound.interfaces.adtin.patientdischarge

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.Account
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
import java.util.*

class PatientDischargeTest {

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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientDischarge")!!.get()
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
        Mockito.`when`(fhirClient.search(eq("CareTeam"), any())).thenReturn(Bundle().addEntry(
            Bundle.BundleEntryComponent().setResource(ct)))
    }

    @Test
    fun test_PatientShouldBeCreated_AsOutPatient_AndAccountShouldNotBeCreated() {
        //patient and account does not exist. should create out patient
        //gives warning for account. Account does not exit
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val dischargeDate = Date()
        val patientClass = "Out Patient"
        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle

        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val createResourceCaptor = argumentCaptor<Patient>()
        Mockito.`when`(fhirClient.create(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val createdPatient = createResourceCaptor.firstValue
        Assert.assertNotNull(createdPatient)

        Assert.assertNull(createdPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(createdPatient.patientLocationDetails.admissionDate)
        Assert.assertNull(createdPatient.patientLocationDetails.dischargeDate)
        verify(fhirClient, never()).create(isA<Account>())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Account with given Account Number does not exists", errorOrWarning[0].details.text)
    }

    @Test
    fun test_OutPatientShouldBeUpdated_AsOutPatient_AndAccountShouldNotBeCreated() {
        //out patient exist and account does not exist.
        //should update out patient.room number admit date should be null.  discharge date should remain same
        //warning for account. Account does not exist
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = DateTime(2019, 3, 15, 10, 0, 0).toDate()
        val newDischargeDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()
        val patientClass = "Out Patient"
        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, newDischargeDate, patientClass)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val existingDischargeDate = DateTime(2020, 3, 12, 10, 0, 0).toDate()
        val domainPatientBundle = getDomainPatientBundle("1", admissionDate, existingDischargeDate, patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val createResourceCaptor = argumentCaptor<Patient>()
        Mockito.`when`(fhirClient.update(createResourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = createResourceCaptor.firstValue
        Assert.assertNotNull(updatedPatient)

        Assert.assertNull(updatedPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(updatedPatient.patientLocationDetails.admissionDate)
        Assert.assertEquals(12, updatedPatient.patientLocationDetails.dischargeDate.day)
        verify(fhirClient, never()).create(isA<Account>())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Account with given Account Number does not exists", errorOrWarning[0].details.text)
    }

    @Test
    fun test_ExistingOutPatient_AndAccountShouldBeUpdated_WhenInputHasOutPatient() {
        //out patient and account exist.
        //should update account start and end date
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val patientClass = "Out Patient"
        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, Date(), patientClass)
        val accountStartDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()
        val accountEndDate = DateTime(2020, 3, 16, 10, 0, 0).toDate()
        modifyAccountServicePeriod(inBundle, accountStartDate, accountEndDate)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle("1", admissionDate, Date(), patientClass)
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        val domainAccountBundle = getDomainAccountBundle(Date(), null)
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(domainAccountBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))


        //execute
        scripts.run(parameters, scriptInformation)

        val updatedAccount = resourceCaptor.allValues.find { it.fhirType() == "Account" } as Account
        Assert.assertNotNull(updatedAccount)
        val startDate = DateTime(updatedAccount.servicePeriod.start)
        val endDate = DateTime(updatedAccount.servicePeriod.end)
        Assert.assertEquals(15, startDate.dayOfMonth)
        Assert.assertEquals(16, endDate.dayOfMonth)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    @Test
    fun test_ShouldUpdateInPatientToOutPatient() {
        //In patient and account does not exist
        //should update patient to Out patient. set admission date, room number null
        //should update discharge date
        //should not create account
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = DateTime(2019, 3, 15, 10, 0, 0).toDate()
        val patientClass = "Out Patient"
        val dischargeDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()

        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, dischargeDate, patientClass)

        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle("1234", admissionDate, null, "In Patient")
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = resourceCaptor.allValues.find { it.fhirType() == "Patient" } as Patient
        Assert.assertNotNull(updatedPatient)
        Assert.assertNull(updatedPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(updatedPatient.patientLocationDetails.admissionDate)
        Assert.assertNotNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertEquals(15, updatedPatient.patientLocationDetails.dischargeDate.day)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Account with given Account Number does not exists", errorOrWarning[0].details.text)
        Assert.assertEquals("ACCOUNT_DOES_NOT_EXISTS", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, errorOrWarning[0].severity)
    }

    @Test
    fun test_ShouldNotUpdateInPatientToOutPatient_DischargeDatePriorToAdmitDate() {
        //In patient and account does not exist
        //should update patient to Out patient. set admission date, room number null
        //should update discharge date
        //should not create account
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = Date()
        val patientClass = "Out Patient"
        val dischargeDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()

        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, dischargeDate, patientClass)

        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle("1234", admissionDate, null, "In Patient")
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        //execute
        scripts.run(parameters, scriptInformation)

        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(2, errorOrWarning.size)
        Assert.assertEquals("Ignoring patient class because admit/discharge date is invalid", errorOrWarning[0].details.text)
        Assert.assertEquals("IGNORE_PATIENT_CLASS", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[0].severity)
        Assert.assertEquals("Discharge date can not be before admit date", errorOrWarning[1].details.text)
        Assert.assertEquals("INVALID_ADMIT_DISCHARGE_DATE", errorOrWarning[1].details.codingFirstRep.code)
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[1].severity)
    }

    @Test
    fun test_ShouldUpdateInPatientToOutPatient_AccountDetailsNotInMessage() {
        //In patient exist.Message does not contain account details
        //should return warning billing account null
        //should not create account
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = DateTime(2019, 3, 15, 10, 0, 0).toDate()
        val patientClass = "Out Patient"
        val dischargeDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()

        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        inBundle.entry.removeIf { it.resource.fhirType() == "Account" }
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle("1234", admissionDate, null, "In Patient")
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(Bundle())

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedPatient = resourceCaptor.allValues.find { it.fhirType() == "Patient" } as Patient
        Assert.assertNotNull(updatedPatient)
        Assert.assertNull(updatedPatient.patientLocationDetails.roomNumber)
        Assert.assertNull(updatedPatient.patientLocationDetails.admissionDate)
        Assert.assertNotNull(updatedPatient.patientLocationDetails.dischargeDate)
        Assert.assertEquals(15, updatedPatient.patientLocationDetails.dischargeDate.day)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
        Assert.assertEquals("ACCOUNT_IS_NULL", errorOrWarning[0].details.codingFirstRep.code)
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, errorOrWarning[0].severity)
    }

    @Test
    fun test_ShouldUpdateInPatientToOutPatient_AndUpdateAccountServicePeriod() {
        //In patient and account exist
        //should update account end date with discharge date because input message does not have account end date
        val inputHospital = "ACHospital"
        val inputDepartment = "OIS_ID1"
        val roomNumber = "1234"
        val admissionDate = DateTime(2019, 3, 15, 10, 0, 0).toDate()
        val patientClass = "Out Patient"
        val dischargeDate = DateTime(2020, 3, 15, 10, 0, 0).toDate()

        val inBundle = getPatientDischargeBundle(roomNumber, admissionDate, dischargeDate, patientClass)
        val accountStartDate = DateTime(2020, 3, 12, 10, 0, 0).toDate()
        modifyAccountServicePeriod(inBundle, accountStartDate, null)
        parameters[ParameterConstant.BUNDLE] = inBundle

        val domainPatientBundle = getDomainPatientBundle("1234", admissionDate, null, "In Patient")
        Mockito.`when`(fhirClient.search(
                eq("Patient"), eq("identifier"),
                any())
        ).thenReturn(domainPatientBundle)

        val domainAccountBundle = getDomainAccountBundle(Date(), null)
        Mockito.`when`(fhirClient.search(
                eq("Account"), eq("patient"), eq("Patient-1"), eq("identifier"),
                any())
        ).thenReturn(domainAccountBundle)

        mockHospitalAndDepartmentSearch(inputHospital, inputDepartment)

        val resourceCaptor = argumentCaptor<BaseResource>()
        Mockito.`when`(fhirClient.update(resourceCaptor.capture())).thenReturn(MethodOutcome(IdType("Patient-1")))

        //execute
        scripts.run(parameters, scriptInformation)

        val updatedAccount = resourceCaptor.allValues.find { it.fhirType() == "Account" } as Account
        Assert.assertNotNull(updatedAccount)
        val startDate = DateTime(updatedAccount.servicePeriod.start)
        val endDate = DateTime(updatedAccount.servicePeriod.end)
        Assert.assertEquals(12, startDate.dayOfMonth)
        Assert.assertEquals(15, endDate.dayOfMonth)
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertTrue(errorOrWarning.isEmpty())
    }

    private fun getDomainPatientBundle(roomNumber: String?, admissionDate: Date?, dischargeDate: Date?, patientClass: String?): Bundle {
        val domainPatientBundleString = TestHelper.readResource("/patientdischarge/DomainPatientBundle.json")
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

    private fun getDomainAccountBundle(startDate: Date?, endDate: Date?): Bundle {
        val domainAccountBundleString = TestHelper.readResource("/patientdischarge/DomainAccountBundle.json")
        val domainAccountBundle = parser.parseResource(domainAccountBundleString) as Bundle
        val account = domainAccountBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.servicePeriod.start = startDate
        account.servicePeriod.end = endDate
        return domainAccountBundle
    }

    private fun modifyAccountServicePeriod(bundle: Bundle, startDate: Date?, endDate: Date?): Bundle {
        val account = bundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
        account.servicePeriod.start = startDate
        account.servicePeriod.end = endDate
        return bundle
    }

    private fun getPatientDischargeBundle(roomNumber: String?, admissionDate: Date?, dischargeDate: Date?, patientClass: String?): Bundle {
        val patientPreAdmitJson = TestHelper.readResource("/patientdischarge/PatientDischargeBundle.json")
        val inBundle = parser.parseResource(patientPreAdmitJson) as Bundle
        val patient = inBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
        patient.patientLocationDetails.admissionDate = DateType(admissionDate)
        patient.patientLocationDetails.admissionDate.value.after(patient.patientLocationDetails.dischargeDate.value)
        patient.patientLocationDetails.dischargeDate = DateType(dischargeDate)
        patient.patientLocationDetails.roomNumber = StringType(roomNumber)
        if (patientClass.isNullOrEmpty()) {
            patient.patientClass = null
        } else {
            patient.patientClass.codingFirstRep.code = patientClass
        }
        return inBundle
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

    private fun mockHospitalAndDepartmentSearch(hospitalId: String, departmentId: String) {
        Mockito.`when`(fhirClient.search("Organization", "name", hospitalId, "type", "prov", "active", "true"))
                .thenReturn(getOrganizationBundle("Organization/Organization-prov-1", hospitalId, "prov"))

        Mockito.`when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
                .thenReturn(getOrganizationBundle("Organization/Organization-dept-1", departmentId, "dept"))
    }
}
