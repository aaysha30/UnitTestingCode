package dsl.master.outbound.interfaces.siuout

import ca.uhn.fhir.parser.IParser
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.model.v251.message.SIU_S12
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nhaarman.mockitokotlin2.anyArray
import com.nhaarman.mockitokotlin2.mock
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory

import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.*
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptInformation
import com.varian.mappercore.helper.sqlite.SqliteUtility
import com.varian.mappercore.tps.GlobalInit
import com.varian.mappercore.tps.UpocBaseOutbound
import groovy.util.GroovyTestCase.assertEquals
import io.mockk.every
import io.mockk.mockkObject
import junit.framework.TestCase.assertNull
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.OperationOutcome
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito

import java.sql.Connection

class SiuOutTest {
   companion object{
       protected lateinit var cloverLogger: CloverLogger
       lateinit var fhirFactory: FhirFactory
       lateinit var scripts: IScripts
       lateinit var scriptInformation: ScriptInformation
       lateinit var parser: IParser
       protected var log: Logger = LogManager.getLogger(UpocBaseOutbound::class.java)
       var bundleString=""
       lateinit var bundleType: Bundle
       @BeforeClass
       @JvmStatic
       fun set(){
           FileOperation.setCurrentBasePath("")
           bundleString=TestHelper.readResource("/appointment/appointmentBundle.json")
           fhirFactory = FhirFactory()
           parser = fhirFactory.getFhirParser()
           bundleType =parser.parseResource(Bundle::class.java,
              bundleString
           )
           scripts = TestHelper.scripts
           scriptInformation = scripts.getHandlerFor("Json", "SiuOut")!!.get()

       }
   }
    lateinit var fhirClient: FhirClient
    lateinit var outcome: Outcome
    lateinit var clientDecor: ClientDecor
    lateinit var parameters: MutableMap<String, Any>
    protected var siteDirName: String = ""
    protected var mappedSqliteDbName:String=""
    private var objectMapper: ObjectMapper
    init {
        objectMapper = ObjectMapper()
    }
    val mapper = objectMapper
    val bundleJson: ObjectNode = mapper.createObjectNode()
    lateinit var siuS12: SIU_S12
    val globalInit:GlobalInit = mock()
    @Before
    fun setup() {
        bundleJson.put("resourceType", "Bundle")
        siuS12 = SIU_S12()
        siuS12.initQuickstart("SIU", "S12", "P")
        var bundleToString= parser.encodeResourceToString(bundleType)
        parameters = mutableMapOf()
        fhirClient = mock()
        outcome = Outcome(parser)
        clientDecor = ClientDecor(fhirClient, outcome)
        parameters[ParameterConstant.SEVERITY] = OperationOutcome.IssueSeverity.INFORMATION
        parameters[ParameterConstant.BUNDLE] = parser.parseResource(bundleToString ) as Bundle
        parameters[ParameterConstant.CLIENT_DECOR] = clientDecor
        parameters[ParameterConstant.BUNDLE_UTILITY] = fhirFactory.getBundleUtility()
        parameters[ParameterConstant.PARAMETERS_UTILITY] = fhirFactory.getParametersUtility()
        parameters[ParameterConstant.PATIENT_UTILITY] = fhirFactory.getPatientUtility()
        parameters[ParameterConstant.OUTCOME] = outcome
        parameters[ParameterConstant.SITE_DIR_NAME] = siteDirName
        parameters[ParameterConstant.SQLITE_UTILITY] = SqliteUtility
        parameters[ParameterConstant.LOG] = log
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger.initCLoverLogger(mock())
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        parameters[ParameterConstant.parser] = parser
        parameters[ParameterConstant.MAPPED_SQLITE_DB_NAME]=mappedSqliteDbName
        parameters[ParameterConstant.SIUOUT]=siuS12
        parameters[ ParameterConstant.GLOBAL_INIT]= globalInit
    }
    @Test
    fun test(){
        val mockConnection =Mockito.mock(Connection::class.java)
        var alist= arrayListOf<String>()
        mockkObject(SqliteUtility.Companion)
        every { SqliteUtility.Companion.getSqliteConnectionObject(any(),any()) }answers {mockConnection}
        every{SqliteUtility.Companion.getValue(any(),any(),any(),any(),any())}answers {"getValue"}
        every{SqliteUtility.Companion.getValues(any(),any(),any(),any())}answers { alist}
        val outcome = scripts.run(parameters,scriptInformation)
        Assert.assertNotNull(outcome)
//        var siuS12=outcome as SIU_S12
        var siuS12=outcome as SIU_S12
      //  Assert.assertEquals(,siuS12.msh);
        println(siuS12.sch.placerAppointmentID.entityIdentifier.value)
        assertEquals("2023-01-09T00-04-46-901-1847692@958500816058",siuS12.sch.placerAppointmentID.entityIdentifier.value)
        assertEquals("1847692",siuS12.sch.placerAppointmentID.universalID.value )
        assertEquals("2023-01-09T00-04-46-901-1847692@958500816058",siuS12.sch.fillerAppointmentID.entityIdentifier.value)
        assertEquals("1847692",siuS12.sch.fillerAppointmentID.universalID.value)
        assertEquals("getValue",siuS12.sch.eventReason.identifier.value)
        assertEquals("Appointment status changed.",siuS12.sch.eventReason.text.value)
        assertEquals("Blood Test",siuS12.sch.appointmentReason.text.value)
        assertNull(siuS12.sch.appointmentType.text.value)
        assertEquals("15",siuS12.sch.appointmentDuration.value)
        assertEquals("M",siuS12.sch.appointmentDurationUnits.identifier.value)
        assertEquals("20230109133300",siuS12.sch.appointmentTimingQuantity.get(0).startDateTime.time.value)
        assertEquals("20230109134800",siuS12.sch.appointmentTimingQuantity.get(0).endDateTime.time.value)
        assertEquals("ACUser",siuS12.sch.fillerContactPerson.get(0).idNumber.value)
        assertEquals("User",siuS12.sch.fillerContactPerson.get(0).familyName.surname.value)
        assertEquals("AC",siuS12.sch.fillerContactPerson.get(0).givenName.value)
        assertEquals("ACUser",siuS12.sch.enteredByPerson.get(0).idNumber.value)
        assertEquals("User",siuS12.sch.enteredByPerson.get(0).familyName.surname.value)
        assertEquals("AC",siuS12.sch.enteredByPerson.get(0).givenName.value)
        assertEquals("getValue",siuS12.sch.fillerStatusCode.identifier.value)
        assertEquals("Open",siuS12.sch.fillerStatusCode.text.value)
        assertEquals("Active",siuS12.sch.fillerStatusCode.alternateText.value)
    }
}