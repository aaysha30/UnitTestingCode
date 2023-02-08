package dsl.master.outbound.interfaces.siuout

import ca.uhn.fhir.parser.IParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.PropertyTree
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import com.varian.mappercore.framework.utility.ParametersUtility
import com.varian.mappercore.helper.sqlite.SqliteUtility
import com.varian.mappercore.tps.GlobalInit
import com.varian.mappercore.tps.UpocBaseOutbound
import dsl.master.inbound.interfaces.adtin.patientsave.PatientSaveTest
import groovy.util.GroovyTestCase.assertEquals
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.r4.model.ActivityDefinition
import org.hl7.fhir.r4.model.Appointment
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.hl7.fhir.r4.model.CareTeam
import org.hl7.fhir.r4.model.Device
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.HealthcareService
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Organization
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Practitioner
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SiuOutFHIRTest {
    companion object {
        protected lateinit var cloverLogger: CloverLogger
        lateinit var fhirFactory: FhirFactory
        lateinit var scripts: IScripts
        lateinit var scriptInformation: ScriptInformation
        lateinit var parser: IParser
        protected var log: Logger = LogManager.getLogger(UpocBaseOutbound::class.java)
        var bundleString = ""
        lateinit var bundleType: Bundle
        var fullBundle: Bundle? = null

        @BeforeClass
        @JvmStatic
        fun set() {
            FileOperation.setCurrentBasePath("")
            bundleString = TestHelper.readResource("/appointment/appointmentBundle.json")
            fhirFactory = FhirFactory()
            parser = fhirFactory.getFhirParser()
            bundleType = parser.parseResource(Bundle::class.java, bundleString)
            bundleType.entry.removeIf { it.resource.fhirType() != Enumerations.FHIRAllTypes.PARAMETERS.toCode() }
            fullBundle = bundleType
            scripts = TestHelper.scripts
            scriptInformation = scripts.getHandlerFor("Json", "SiuOutFhir")!!.get()
        }
    }

    lateinit var fhirClient: FhirClient
    lateinit var outcome: Outcome
    lateinit var clientDecor: ClientDecor
    lateinit var parameters: MutableMap<String, Any>
    protected var siteDirName: String = ""
    private var objectMapper: ObjectMapper

    init {
        objectMapper = ObjectMapper()
    }

    val mapper = objectMapper

    val bundleJson: ObjectNode = mapper.createObjectNode()

    @Before
    fun setup() {

        bundleJson.put("resourceType", "Bundle")
        parameters = mutableMapOf()
        fhirClient = mock()
        outcome = Outcome(parser)
        clientDecor = ClientDecor(fhirClient, outcome)
        parameters[ParameterConstant.SEVERITY] = OperationOutcome.IssueSeverity.INFORMATION
        var bundleToString = parser.encodeResourceToString(bundleType)
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(bundleToString) as Bundle
        parameters[ParameterConstant.CLIENT_DECOR] = clientDecor
        parameters[ParameterConstant.BUNDLE_UTILITY] =
            fhirFactory.getBundleUtility()     //.getParameters(a as Bundle) as String
        parameters[ParameterConstant.PARAMETERS_UTILITY] = fhirFactory.getParametersUtility()
        parameters[ParameterConstant.PATIENT_UTILITY] = fhirFactory.getPatientUtility()
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters[ParameterConstant.SITE_DIR_NAME] = siteDirName
        parameters[ParameterConstant.SQLITE_UTILITY] = SqliteUtility
        parameters[ParameterConstant.LOG] = log
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger.initCLoverLogger(mock())
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        parameters[ParameterConstant.parser] = parser
    }

    @Test
    public fun siuOutbound_FHIR_Test() {

        var appBundle: Bundle = parser.parseResource(Bundle::class.java, bundleString)
        var foundAppBundle =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.APPOINTMENT.toCode() }
        var appointment = foundAppBundle?.resource as Appointment
        `when`(fhirClient.readById("Appointment", "Appointment-1847692/_history/7")).thenReturn(appointment)
        var patientBundle =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.PATIENT.toCode() }
        var patient = patientBundle?.resource as Patient
        var patientBundl = Bundle()
        patientBundl.addEntry().resource = patient
        `when`(fhirClient.search(eq("Patient"), eq("_id"), eq("Patient-944203"))).thenReturn(patientBundl)
        var orgResource =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.ORGANIZATION.toCode() }
        var org = orgResource?.resource as Organization
        `when`(fhirClient.readById("Organization", "Organization-Dept-10011")).thenReturn(org)
        var healthcareservice =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.HEALTHCARESERVICE.toCode() }
        var hcs = healthcareservice?.resource as HealthcareService
        `when`(fhirClient.readById("HealthcareService", "HealthcareService/HealthcareService-10011")).thenReturn(hcs)
        var location1217 =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.LOCATION.toCode() }
        var location = location1217?.resource as Location
        `when`(fhirClient.readById("Location", "Location/Location-1217")).thenReturn(location)
        var location2796 =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.LOCATION.toCode() }
        var loc2796 = location2796?.resource as Location
        `when`(fhirClient.readById("Location", "Location/Location-2796")).thenReturn(loc2796)
        var location22 = appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.LOCATION.toCode() }
        var loc22 = location2796?.resource as Location
        `when`(fhirClient.readById("Location", "Location/Location-22")).thenReturn(loc22)
        var location4365 =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.LOCATION.toCode() }
        var loc4365 = location4365?.resource as Location
        `when`(fhirClient.readById("Location", "Location/Location-4365")).thenReturn(loc4365)
        var device432 = appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.DEVICE.toCode() }
        var dev432 = device432?.resource as Device
        `when`(fhirClient.readById("Device", "Device/Device-432")).thenReturn(dev432)
        var device390 = appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.DEVICE.toCode() }
        var dev390 = device390?.resource as Device
        `when`(fhirClient.readById("Device", "Device/Device-390")).thenReturn(dev390)
        var prc5333 = appBundle.entry.find { it.resource.idElement.idPart == "Practitioner-5333" }?.resource
        `when`(fhirClient.readById("Practitioner", "Practitioner/Practitioner-5333")).thenReturn(prc5333)
        var prc5328 = appBundle.entry.find { it.resource.idElement.idPart == "Practitioner-5328" }?.resource
        `when`(fhirClient.readById("Practitioner", "Practitioner/Practitioner-5328")).thenReturn(prc5328)
        var prac = appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.PRACTITIONER.toCode() }
        var practitioner = prac?.resource as Practitioner
        var bundl = Bundle()
        bundl.addEntry().resource = bundl
        `when`(fhirClient.search(eq("Practitioner"), eq("identifier"), any())).thenReturn(bundl)
        var careTeam944203 =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.CARETEAM.toCode() }
        var careTeam = careTeam944203?.resource as CareTeam
        var careTeamBundle944203 = Bundle()
        careTeamBundle944203.addEntry().resource = careTeamBundle944203
        `when`(
            fhirClient.search(
                eq("CareTeam"),
                eq("patient"),
                eq("Patient-944203"),
                eq("_include"),
                eq("*")
            )
        ).thenReturn(careTeamBundle944203)
        var actDefBundle =
            appBundle.entry.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.ACTIVITYDEFINITION.toCode() }
        var actDef = actDefBundle?.resource as ActivityDefinition
        var actDefBundl = Bundle()
        actDefBundl.addEntry().resource = actDef
        `when`(
            fhirClient.search(
                eq("ActivityDefinition"),
                eq("name"),
                eq("Blood Test"),
                eq("context-reference"),
                eq("Organization-Dept-10011")
            )
        ).thenReturn(actDefBundl)
        var patRes = argumentCaptor<Patient>()
        var bundleAny = scripts.run(parameters, scriptInformation)
        var bundle: Bundle = bundleAny as Bundle
        assertEquals(16, bundle.entry.size)
    }
}