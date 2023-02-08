package dsl.master.inbound.interfaces.adtin.patientarriving

import TestHelper
import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import com.nhaarman.mockitokotlin2.*
import com.varian.fhir.resources.Appointment
import com.varian.fhir.resources.Location
import com.varian.fhir.resources.Patient
import com.varian.fhir.resources.Organization
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
import org.mockito.Mockito.`when`
import java.time.Duration
import java.util.*

class PatientArrivingTest {
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
            scriptInformation = scripts.getHandlerFor("Hl7", "PatientArriving")!!.get()
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
        //below mocks is required as patient save will be called after check in
        val existingPatient = parser.parseResource(TestHelper.readResource("/patient/PatientBundle.json")) as Bundle
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        )
            .thenReturn(existingPatient)
        `when`(fhirClient.update(isA<Patient>())).thenReturn(MethodOutcome(IdType("Patient-1")))
        val bundle = Bundle()
        bundle.entryFirstRep.resource = Organization().setType(
            mutableListOf(CodeableConcept(Coding().setCode("prov")))).setName("ACHospital").setId("Organization-1")
        `when`(fhirClient.search("Organization", "name", "ACHospital", "type", "prov", "active", "true"))
            .thenReturn(bundle)
    }

    @Test
    fun test_PatientArriving_CheckIn_Success_singleAppointmentScheduledOnPastTimeForSameDay() {
        //prepare
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(patientArrivingJson)

        //mock
        val appointmentBundle =
                parser.parseResource(TestHelper.readResource("/patientarriving/AppointmentBundle.json")) as Bundle
        val today = Date()
        val appointmentStartDate = Date.from(today.toInstant().minus(Duration.ofHours(3)))
        val appointmentEndDate = Date.from(today.toInstant().minus(Duration.ofHours(2)))
        val appointmentStatus = org.hl7.fhir.r4.model.Appointment.AppointmentStatus.BOOKED
        var index = 0
        //make single valid appointment in Bundle
        appointmentBundle.entry.forEach {
            if (index == 0) {
                updateAppointment(
                        it.resource as Appointment,
                        "Appointment/Appointment-$index",
                        appointmentStartDate,
                        appointmentEndDate,
                        appointmentStatus
                )
            } else {
                updateAppointment(
                        it.resource as Appointment,
                        "Appointment/Appointment-$index",
                        appointmentStartDate,
                        appointmentEndDate,
                        org.hl7.fhir.r4.model.Appointment.AppointmentStatus.ENTEREDINERROR
                )
            }
            index++
        }

        val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd")
        val formatted = inputDateFormat.format(today)

        val locationBundle = getLocationBundle("Location/Location-0", "locationIdentifier")
        `when`(
                fhirClient.search(
                        eq("Location"), eq("identifier"),
                        any()
                )
        ).thenReturn(locationBundle)

        `when`(
                fhirClient.search(
                        "Appointment", "patient",
                        "Patient-191",
                        "date",
                        formatted
                )
        ).thenReturn(appointmentBundle)

        val appointmentCaptor = argumentCaptor<Appointment>()
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
                fhirClient.operation(
                        appointmentCaptor.capture(), eq("\$checkin"),
                        eq("Appointment"), parameterCaptor.capture(), isNull()
                )
        ).thenReturn(Parameters())

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).operation(any(), any(), any(), any(), isNull())
        val parameter = parameterCaptor.firstValue
        val appointment = appointmentCaptor.firstValue
        Assert.assertNotNull(parameter)
        Assert.assertNotNull(appointment)
        Assert.assertEquals("Appointment-0", appointment.idElement.idPart)
        Assert.assertEquals("locationKey", parameter.parameter[0].name)
        Assert.assertEquals("Location-0", parameter.parameter[0].value.primitiveValue())
        val errorOrWarning =
                outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
    }

    @Test
    fun test_PatientArriving_CheckIn_Success_singleAppointment() {
        //prepare
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(patientArrivingJson)

        //mock
        val appointmentBundle =
            parser.parseResource(TestHelper.readResource("/patientarriving/AppointmentBundle.json")) as Bundle
        val today = Date()
        val appointmentStartDate = Date.from(today.toInstant().plus(Duration.ofHours(1)))
        val appointmentEndDate = Date.from(today.toInstant().plus(Duration.ofHours(2)))
        val appointmentStatus = org.hl7.fhir.r4.model.Appointment.AppointmentStatus.BOOKED
        var index = 0
        //make single valid appointment in Bundle
        appointmentBundle.entry.forEach {
            if (index == 0) {
                updateAppointment(
                    it.resource as Appointment,
                    "Appointment/Appointment-$index",
                    appointmentStartDate,
                    appointmentEndDate,
                    appointmentStatus
                )
            } else {
                updateAppointment(
                    it.resource as Appointment,
                    "Appointment/Appointment-$index",
                    appointmentStartDate,
                    appointmentEndDate,
                    org.hl7.fhir.r4.model.Appointment.AppointmentStatus.ENTEREDINERROR
                )
            }
            index++
        }

        val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd")
        val formatted = inputDateFormat.format(today)

        val patientBundle = TestHelper.getPatientBundle("Patient/Patient-1", "patientIdentifier", "family", "given")
        val locationBundle = getLocationBundle("Location/Location-0", "locationIdentifier")
/*
        `when`(
            fhirClient.search(
                eq("Patient"), eq("identifier"),
                any()
            )
        ).thenReturn(patientBundle)
*/
        `when`(
            fhirClient.search(
                eq("Location"), eq("identifier"),
                any()
            )
        ).thenReturn(locationBundle)

        `when`(
            fhirClient.search(
                "Appointment", "patient",
                "Patient-191",
                "date",
                formatted
            )
        ).thenReturn(appointmentBundle)

        val appointmentCaptor = argumentCaptor<Appointment>()
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
            fhirClient.operation(
                appointmentCaptor.capture(), eq("\$checkin"),
                eq("Appointment"), parameterCaptor.capture(), isNull()
            )
        ).thenReturn(Parameters())

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).operation(any(), any(), any(), any(), isNull())
        val parameter = parameterCaptor.firstValue
        val appointment = appointmentCaptor.firstValue
        Assert.assertNotNull(parameter)
        Assert.assertNotNull(appointment)
        Assert.assertEquals("Appointment-0", appointment.idElement.idPart)
        Assert.assertEquals("locationKey", parameter.parameter[0].name)
        Assert.assertEquals("Location-0", parameter.parameter[0].value.primitiveValue())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
    }

    @Test
    fun test_PatientArriving_CheckIn_Success_MultipleAppointment() {
        //prepare
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(patientArrivingJson)

        //mock
        val appointmentBundle =
            parser.parseResource(TestHelper.readResource("/patientarriving/AppointmentBundle.json")) as Bundle
        val today = Date()
        val appointmentStartDate = Date.from(today.toInstant().plus(Duration.ofHours(1)))
        val appointmentEndDate = Date.from(today.toInstant().plus(Duration.ofHours(2)))
        val appointmentStatus = org.hl7.fhir.r4.model.Appointment.AppointmentStatus.BOOKED
        var index = 0

        appointmentBundle.entry.forEach {
            updateAppointment(
                it.resource as Appointment,
                "Appointment/Appointment-$index",
                appointmentStartDate,
                appointmentEndDate,
                appointmentStatus
            )
            index++
        }

        val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd")
        val formatted = inputDateFormat.format(today)

        val patientBundle = TestHelper.getPatientBundle("Patient/Patient-1", "patientIdentifier", "family", "given")
        val locationBundle = getLocationBundle("Location/Location-0", "locationIdentifier")
        `when`(
            fhirClient.search(
                eq("Location"), eq("identifier"),
                any()
            )
        ).thenReturn(locationBundle)

        `when`(
            fhirClient.search(
                "Appointment", "patient",
                "Patient-191",
                "date",
                formatted
            )
        ).thenReturn(appointmentBundle)

        val appointmentCaptor = argumentCaptor<Appointment>()
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
            fhirClient.operation(
                appointmentCaptor.capture(), eq("\$checkin"),
                eq("Appointment"), parameterCaptor.capture(),
                isNull()
            )
        ).thenReturn(Parameters())

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(3)).operation(any(), any(), any(), any(), isNull())
        val parameters = parameterCaptor.allValues
        val appointments = appointmentCaptor.allValues
        Assert.assertEquals(3, parameters.count())
        Assert.assertEquals(3, appointments.count())
        Assert.assertEquals("Appointment-0", appointments[0].idElement.idPart)
        Assert.assertEquals("locationKey", parameters[0].parameter[0].name)
        Assert.assertEquals("Location-0", parameters[0].parameter[0].value.primitiveValue())
        Assert.assertEquals("Appointment-1", appointments[1].idElement.idPart)
        Assert.assertEquals("locationKey", parameters[1].parameter[0].name)
        Assert.assertEquals("Location-0", parameters[1].parameter[0].value.primitiveValue())
        Assert.assertEquals("Appointment-2", appointments[2].idElement.idPart)
        Assert.assertEquals("locationKey", parameters[2].parameter[0].name)
        Assert.assertEquals("Location-0", parameters[2].parameter[0].value.primitiveValue())
        verify(fhirClient, times(1)).update(isA<Patient>())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isEmpty())
        Assert.assertEquals(1, errorOrWarning.size)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[0].details.text)
    }

    @Test
    fun test_patientArriving_PatientNotPresent_CreatePatient_AutoCreationEnabled_RaiseWarning() {
        //prepare
        val hospitalName = "ACHospital"
        val deptIdentifier = "OIS_ID"
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        val bundle = parser.parseResource(patientArrivingJson) as Bundle
        parameters[ParameterConstant.BUNDLE] = bundle
        (bundle.entry[2].resource as Patient).patientLocationDetails.dischargeDate = DateType(Date())
        (bundle.entry[3].resource as CareTeam).participantFirstRep.member.display = deptIdentifier

        //mock
        val orgBundle = Bundle()
        val organization = Organization()
        val department = Organization()
        organization.id = "Organization/Organization-prov-1"
        organization.name = hospitalName
        organization.typeFirstRep.codingFirstRep.code = "prov"
        department.id = "Organization/Organization-dept-1"
        department.identifierFirstRep.value = deptIdentifier
        department.name = deptIdentifier
        department.typeFirstRep.codingFirstRep.code = "dept"
        orgBundle.addEntry().resource = organization
        orgBundle.addEntry().resource = department
        `when`(
            fhirClient.search(
                eq("Patient"),
                eq("identifier"),
                any()
            )
        ).thenReturn(Bundle())
        `when`(fhirClient.create(isA<Patient>())).thenReturn(MethodOutcome(IdType("Patient-1")))
        `when`(fhirClient.search("Organization", "name", hospitalName, "type", "prov", "active", "true"))
            .thenReturn(orgBundle)
        `when`(fhirClient.search("Organization", "type", "dept", "partof", "Organization-prov-1", "active", "true"))
            .thenReturn(orgBundle)

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(1)).create(isA<Patient>())
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(1, errorOrWarning.count())
        Assert.assertEquals(
            "Patient (pId_2021020811345442) is not present. System will create patient as auto create is configured for this event",
            errorOrWarning[0].details.text
        )
    }

    @Test(expected = ResourceNotFoundException::class)
    fun test_PatientArriving_PatientNotPresent_AutoCreateEventIsNotSet_throwsError() {
        //prepare
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        val bundle = parser.parseResource(patientArrivingJson) as Bundle
        val parametersResource = bundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        val event = parametersResource.parameter.find { it.name == "Event" }
        parametersResource.parameter.remove(event)
        parameters[ParameterConstant.BUNDLE] = bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle())

        //execute
        scripts.run(parameters, scriptInformation)
    }

    @Test
    fun test_patientArriving_PatientNotPresent_AutoCreationNotConfigured_ThrowsError() {
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        val bundle = parser.parseResource(patientArrivingJson) as Bundle
        val parametersResource = bundle.entry.find { it.resource.fhirType() == "Parameters" }?.resource as Parameters
        parametersResource.parameter.find { it.name == "AutoCreateEvents" }?.value = StringType("ADT^A10,ADT^ADT^11")
        parameters[ParameterConstant.BUNDLE] = bundle
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(Bundle())

        //execute
        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: Exception) {
            Assert.assertTrue(ex is ResourceNotFoundException)
            outcome.addError(ex)
            val errorOrWarnings = outcome.getOperationOutcome().issue
            Assert.assertEquals(1, errorOrWarnings.size)
            Assert.assertEquals("PATIENT_NOT_FOUND", errorOrWarnings[0].details.codingFirstRep.code)
            Assert.assertEquals("Patient (pId_2021020811345442) is not present", errorOrWarnings[0].details.text)
        }
    }

    @Test
    fun test_patientArriving_Location_Invalid() {
        //prepare
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(patientArrivingJson)

        //mock
        val patientBundle = TestHelper.getPatientBundle("Patient/Patient-1", "patientIdentifier", "family", "given")
        `when`(fhirClient.search(eq("Patient"), eq("identifier"), any())).thenReturn(patientBundle)
        `when`(fhirClient.search(eq("Location"), eq("identifier"), any())).thenReturn(Bundle())

        try {
            scripts.run(parameters, scriptInformation)
            Assert.fail()
        } catch (ex: Exception) {
            verify(fhirClient, never()).update(isA<Patient>())
            Assert.assertTrue(ex is ResourceNotFoundException)
            outcome.addError(ex)
            val errorOrWarnings = outcome.getOperationOutcome().issue
            Assert.assertEquals(1, errorOrWarnings.size)
            Assert.assertEquals("PATIENT_ARRIVING_INVALID_LOCATION", errorOrWarnings[0].details.codingFirstRep.code)
            Assert.assertEquals("Location is not valid", errorOrWarnings[0].details.text)
        }
    }

    @Test
    fun test_patientArriving_returns_error_No_Scheduled_Activity() {
        //prepare
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        val parseResource = parser.parseResource(patientArrivingJson) as Bundle
        (parseResource.entry[2].resource as Patient).identifierFirstRep.value = "patientIdentifier"
        parameters[ParameterConstant.BUNDLE] = parseResource

        //mock
        val locationBundle = getLocationBundle("Location/Location-0", "locationIdentifier")

        `when`(fhirClient.search(eq("Location"), eq("identifier"), any())).thenReturn(locationBundle)

        val today = Date()
        val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd")
        val formatted = inputDateFormat.format(today)
        `when`(
            fhirClient.search(
                "Appointment", "patient",
                "Patient-191",
                "date",
                formatted
            )
        ).thenReturn(Bundle())

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, never()).operation(any(), any(), any(), any(), any())
        val expectedErrorMessage =
            "There is no scheduled activity for today for the given Patient (patientIdentifier) and Hospital (ACHospital)"
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(2, errorOrWarning.count())
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[0].severity)
        Assert.assertEquals(expectedErrorMessage, errorOrWarning[0].details.text)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[1].details.text)
        verify(fhirClient, times(1)).update(isA<Patient>())
    }

    @Test
    fun test_patientArriving_returns_error_All_Scheduled_Activity_CheckIn_Failed() {
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(patientArrivingJson)

        //mock
        val appointmentBundle =
            parser.parseResource(TestHelper.readResource("/patientarriving/AppointmentBundle.json")) as Bundle
        val today = Date()
        val appointmentStartDate = Date.from(today.toInstant().plus(Duration.ofHours(1)))
        val appointmentEndDate = Date.from(today.toInstant().plus(Duration.ofHours(2)))
        val appointmentStatus = org.hl7.fhir.r4.model.Appointment.AppointmentStatus.BOOKED
        var index = 0

        appointmentBundle.entry.forEach {
            updateAppointment(
                it.resource as Appointment,
                "Appointment/Appointment-$index",
                appointmentStartDate,
                appointmentEndDate,
                appointmentStatus
            )
            index++
        }

        val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd")
        val formatted = inputDateFormat.format(today)

        val locationBundle = getLocationBundle("Location/Location-0", "locationIdentifier")
        `when`(fhirClient.search(eq("Location"), eq("identifier"), any())).thenReturn(locationBundle)
        `when`(
            fhirClient.search(
                "Appointment",
                "patient",
                "Patient-191",
                "date",
                formatted
            )
        ).thenReturn(appointmentBundle)

        val appointmentCaptor = argumentCaptor<Appointment>()
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
            fhirClient.operation(
                appointmentCaptor.capture(), eq("\$checkin"),
                eq("Appointment"), parameterCaptor.capture(), isNull()
            )
        ).thenThrow(UnprocessableEntityException("activity failed"))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(3)).operation(any(), any(), any(), any(), isNull())
        val exceptionMessage = "activity failed"
        val expectedErrorMessage = "Scheduled activity check in failed"
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(5, errorOrWarning.count())
        Assert.assertEquals(exceptionMessage, errorOrWarning[0].details.text)
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[0].severity)
        Assert.assertEquals(exceptionMessage, errorOrWarning[1].details.text)
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[1].severity)
        Assert.assertEquals(exceptionMessage, errorOrWarning[2].details.text)
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[2].severity)
        Assert.assertEquals(expectedErrorMessage, errorOrWarning[3].details.text)
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, errorOrWarning[3].severity)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[4].details.text)
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, errorOrWarning[4].severity)
        verify(fhirClient, times(1)).update(isA<Patient>())
    }

    @Test
    fun test_patientArriving_returns_warning_CheckIn_Partially_successful() {
        val patientArrivingJson = TestHelper.readResource("/patientarriving/PatientArriving.json")
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(patientArrivingJson)

        //mock
        val appointmentBundle =
            parser.parseResource(TestHelper.readResource("/patientarriving/AppointmentBundle.json")) as Bundle
        val today = Date()
        val appointmentStartDate = Date.from(today.toInstant().plus(Duration.ofHours(1)))
        val appointmentEndDate = Date.from(today.toInstant().plus(Duration.ofHours(2)))
        val appointmentStatus = org.hl7.fhir.r4.model.Appointment.AppointmentStatus.BOOKED
        var index = 0

        appointmentBundle.entry.forEach {
            updateAppointment(
                it.resource as Appointment,
                "Appointment/Appointment-$index",
                appointmentStartDate,
                appointmentEndDate,
                appointmentStatus
            )
            index++
        }

        val inputDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd")
        val formatted = inputDateFormat.format(today)

        val locationBundle = getLocationBundle("Location/Location-0", "locationIdentifier")
        `when`(
            fhirClient.search(
                eq("Location"), eq("identifier"),
                any()
            )
        ).thenReturn(locationBundle)

        `when`(
            fhirClient.search(
                "Appointment", "patient",
                "Patient-191",
                "date",
                formatted
            )
        ).thenReturn(appointmentBundle)

        val appointmentCaptor = argumentCaptor<Appointment>()
        val parameterCaptor = argumentCaptor<Parameters>()
        `when`(
            fhirClient.operation(
                appointmentCaptor.capture(), eq("\$checkin"),
                eq("Appointment"), parameterCaptor.capture(), isNull()
            )
        ).thenReturn(Parameters()).thenThrow(UnprocessableEntityException("activity failed"))

        //execute
        scripts.run(parameters, scriptInformation)

        //assert
        verify(fhirClient, times(3)).operation(any(), any(), any(), any(), isNull())
        val exceptionMessage = "activity failed"
        val expectedErrorMessage = "Scheduled activity check in completed partially with 1/3 activity(s)."
        val errorOrWarning =
            outcome.getOperationOutcome().issue.filter { issue -> issue.severity.toCode() == "warning" || issue.severity.toCode() == "error" }
        Assert.assertFalse(errorOrWarning.isNullOrEmpty())
        Assert.assertEquals(4, errorOrWarning.count())
        errorOrWarning.forEach { Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, it.severity) }
        Assert.assertEquals(exceptionMessage, errorOrWarning[0].details.text)
        Assert.assertEquals(exceptionMessage, errorOrWarning[1].details.text)
        Assert.assertEquals(expectedErrorMessage, errorOrWarning[2].details.text)
        Assert.assertEquals("Patient Identification. Billing Account is null", errorOrWarning[3].details.text)
        verify(fhirClient, times(1)).update(isA<Patient>())
    }

    private fun getLocationBundle(id: String, identifier: String): Bundle {
        val bundle = Bundle()
        val addEntry = bundle.addEntry()
        val location = Location()
        location.id = id
        location.identifierFirstRep.value = identifier
        addEntry.resource = location
        return bundle
    }

    private fun updateAppointment(
        appointment: Appointment,
        id: String,
        startDate: Date,
        endDate: Date,
        status: org.hl7.fhir.r4.model.Appointment.AppointmentStatus
    ) {
        appointment.id = id
        appointment.start = startDate
        appointment.end = endDate
        appointment.status = status
    }
}
