package dsl.master.inbound.interfaces.adtin.parkpatient

import TestHelper
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.parser.PipeParser
import com.nhaarman.mockitokotlin2.argThat
import com.varian.mappercore.client.interfaceapi.dto.Patient
import com.varian.mappercore.client.interfaceapi.serviceclient.ParkService
import com.varian.mappercore.constant.ParameterConstant
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.FileOperation
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.helper.sqlite.PatientMatching
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.eq
import java.util.ArrayList

class ParkPatientTest {

    lateinit var parameters:MutableMap<String,Any>
    var parkService = Mockito.mock(ParkService::class.java)
    lateinit var hl7Parser:PipeParser
    lateinit var scripts: IScripts

    @Before
    fun setup(){
        var hapiContext = DefaultHapiContext()
        hl7Parser = hapiContext?.pipeParser!!
        parameters = mutableMapOf()
        parameters[ParameterConstant.PARK_SERVICE] = parkService

        FileOperation.setCurrentBasePath("")
        scripts = TestHelper.scripts
    }

    @Test
    fun test_park_ADTA04() {
        val hl7Msg  = TestHelper.readResource("/parkpatient/ADT_A04.hl7")
        val eventid = Pair("HL7", mutableListOf("ParkPatientRefactor"))

        parameters[ParameterConstant.HL7_MESSAGE_OBJECT] = hl7Parser?.parse(hl7Msg)!!
        parameters[ParameterConstant.HL7_MESSAGE] = hl7Msg
        parameters[ParameterConstant.MESSAGE_CLOVERLEAF_ID] = "0.0.0.1"
        parameters[ParameterConstant.PROCESS_NAME] = "ADT-EPIC"
        parameters[ParameterConstant.SITE_NAME] = "dev"
        parameters[ParameterConstant.TRACE_ID] = "T.0.0.1"
        parameters[ParameterConstant.CLOVERLOGGER] = CloverLogger
        parameters[ParameterConstant.MSGMETADATA] = MessageMetaData()
        var ids = ArrayList<PatientMatching>()
        var pid2= PatientMatching()
        // PID.2
        pid2.AriaId ="ARIAID2"
        pid2.Field = "2"
        pid2.Segment = "PID"
        pid2.IsUsedForFinding = "0"
        ids.add(pid2)
        //PID.3
        var pid3= PatientMatching()
        pid3.AriaId ="ARIAID1"
        pid3.Field = "3"
        pid3.Segment = "PID"
        pid3.IdentifierValue = "NPI"
        pid3.IsUsedForFinding = "1"
        ids.add(pid3)

        parameters[ParameterConstant.PATIENT_KEY_MAPPING_CONFIG] = ids

        var scriptInformation = scripts!!.getHandlerFor(eventid.first, eventid.second[0])

        assert(scriptInformation != null)
        //Mock service Calls//
        `when`(
            parkService.searchParkedPatientByCriteria(
               argThat ({ m -> m.containsKey("ARIAID1") && m.containsValue("Pat1-ID1") })
            )
        ).thenReturn(arrayListOf<Patient>())

        `when`(
            parkService.createParkedPatient(
                argThat({m -> m.lastName == "JONES1" && m.id1 == "PatientID3333" })
            )
        ).thenReturn(1)
        var pat = Patient()
        pat.patientRecordSer = 1L

        `when`(
            parkService.getParkedPatient(
                eq(1L)
            )
        ).thenReturn(pat)

        `when`(
            parkService.createParkedMessage(
                argThat({m -> m.patientRecordSer == 1L && m.msgType == "ADT"})
            )
        ).thenReturn(1L)
            //Mock End
        var ackResponse = scripts!!.run(parameters, scriptInformation?.get()!!)

    }
}
