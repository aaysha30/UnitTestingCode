package com.varian.mappercore.client
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.model.v251.datatype.CE
import ca.uhn.hl7v2.model.v251.message.ACK
import ca.uhn.hl7v2.parser.Parser;

import ca.uhn.hl7v2.model.v251.message.ADT_A01
import ca.uhn.hl7v2.model.v251.message.SIU_S12
import com.varian.fhir.resources.Task
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Annotation
import org.hl7.fhir.r4.model.codesystems.NoteType
import java.util.*


class CreateAMessage(val hl7Parser: Parser = DefaultHapiContext().pipeParser) {

    fun getSIUMsg() {
        val adt = ADT_A01()
        val siu = SIU_S12()

        val msh= siu.msh
        msh.messageType.triggerEvent.value="S12"

        siu.sch.placerAppointmentID.entityIdentifier.value="2021-05-20T10-42-42-901-149@135486497346"
        siu.sch.placerAppointmentID.universalID.value="149"
        siu.sch.fillerAppointmentID.entityIdentifier.value = "2021-05-20T10-42-42-901-149@135486497346"
        siu.sch.fillerAppointmentID.universalID.value = "149"
        siu.sch.eventReason.identifier.value="MD"
        siu.sch.eventReason.text.value="Appointment was modified."
        siu.sch.appointmentReason.text.value="Boost Treatment"
        siu.sch.appointmentType.text.value="siu.schedulableActivity"
        siu.sch.appointmentDuration.value="10"
        siu.sch.appointmentDurationUnits.identifier.value="M"
        val apptime = siu.sch.getAppointmentTimingQuantity(0)
        apptime.startDateTime.time.value = "20210520104100"
        apptime.endDateTime.time.value = "20210520105100"
        siu.sch.fillerStatusCode.identifier.value = "Booked"
        siu.sch.fillerStatusCode.text.value="Open"
        siu.sch.fillerStatusCode.alternateText.value = "Active"


        val ft= siu.nte.insertComment(0)
        ft.value="test"

        val aipseg = siu.resources.personneL_RESOURCE.aip
        aipseg.setIDAIP.value="1"
        aipseg.segmentActionCode.value="A"
        val xcn = aipseg.insertAip3_PersonnelResourceID(0)
        xcn.idNumber.value="123test"
        xcn.familyName.surname.value="LastNameDoctor"
        xcn.givenName.value="firstNameDoctor1"

        aipseg.resourceType.ce2_Text.value = "Doctor"
        aipseg.resourceType.alternateText.value="Required Participant"
        aipseg.startDateTime.time.value = "20210517164100"
        aipseg.duration.value="10"
        aipseg.durationUnits.identifier.value="M"


        val ailseg = siu.resources.locatioN_RESOURCE.ail
        ailseg.setIDAIL.value="1"
        ailseg.segmentActionCode.value="A"
        val loc = ailseg.insertAil3_LocationResourceID(0)
        loc.pointOfCare.value="2100C"
        loc.comprehensiveLocationIdentifier.ei1_EntityIdentifier.value="2100C"
        ailseg.locationTypeAIL.text.value="RadiationDevice"
        ailseg.locationTypeAIL.alternateText.value="Machine"
        ailseg.startDateTime.time.value="20210517164100"
        ailseg.duration.value="10"
        ailseg.durationUnits.identifier.value="M"

        val aisseg = siu.resources.service.ais
        aisseg.ais1_SetIDAIS.value = "1"
        aisseg.ais2_SegmentActionCode.value="X"
        aisseg.ais3_UniversalServiceIdentifier.identifier.value="Boost Treatment"
        aisseg.ais3_UniversalServiceIdentifier.text.value="Boost Treatment"
        aisseg.ais3_UniversalServiceIdentifier.alternateIdentifier.value="Boost Treatment"
        aisseg.ais3_UniversalServiceIdentifier.alternateText.value="Treatment"
        aisseg.ais4_StartDateTime.time.value="20210505141700"
        aisseg.ais7_Duration.value="10"
        aisseg.ais8_DurationUnits.identifier.value="M"

        val ce = CE(siu)
        ce.alternateIdentifier.value = "OIS_ID1"
        ce.alternateText.value = "ACHospital"
        aisseg.ais12_FillerSupplementalServiceInformation.set(0,ce)
        aisseg.ais12_FillerSupplementalServiceInformation.get(0)
        //aisseg.insertAis12_FillerSupplementalServiceInformation()
        aisseg.insertAis12_FillerSupplementalServiceInformation(0)
        //val value: Varies = obx.getObservationValue(0)
        //value.data = ce
        aisseg.ais12_FillerSupplementalServiceInformation.component4().alternateIdentifier.value="OIS_ID1"
        aisseg.ais12_FillerSupplementalServiceInformation.component5().alternateText.value="ACHospital"

        val pv1 = siu.patient.pV1
        pv1.setIDPV1.value="1"
        pv1.patientClass.value="O"
        pv1.assignedPatientLocation.facility.namespaceID.value="ACHospital"
        pv1.assignedPatientLocation.building.value="OIS_ID1"
        pv1.reAdmissionIndicator.value="R"

        val pid1 = siu.patient.pid
        pid1.setIDPID.value="1"
       // val cx = CX(siu1)

       // pid1.pid3_PatientIdentifierList.set(0,cx)
        val cx = pid1.insertPatientIdentifierList(0)
        cx.idNumber.value = "Bill_ID"
        adt.initQuickstart("ADT", "A01", "P")

        val cxAlternatePatientId = pid1.insertAlternatePatientIDPID(0)
        cxAlternatePatientId.idNumber.value = "Bill_UID"
        cxAlternatePatientId.identifierTypeCode.value="UID"
        val patientName = pid1.insertPatientName(0)

        patientName.familyName.fn1_Surname.value="Bill_L"
        patientName.givenName.value="Bill_F"
        patientName.secondAndFurtherGivenNamesOrInitialsThereof.value="Bill_F"
        pid1.dateTimeOfBirth.time.value="19860318000000"
        pid1.administrativeSex.value="M"

        pid1.maritalStatus.identifier.value = "M"
        pid1.maritalStatus.text.value="MARRIED"
        // Populate the MSH Segment

        // Populate the MSH Segment
        val mshSegment = adt.msh
        mshSegment.sendingApplication.namespaceID.value = "TestSendingSystem"
        mshSegment.sequenceNumber.value = "123"

        // Populate the PID Segment

        // Populate the PID Segment
        val pid = adt.pid
        pid.getPatientName(0).familyName.surname.value = "Doe"
        pid.getPatientName(0).givenName.value = "John"
        //pid.getPatientIdentifierList(0).getID().setValue("123456")

        /*
          * In a real situation, of course, many more segments and fields would be populated
           */

        // Now, let's encode the message and look at the output

        /*
          * In a real situation, of course, many more segments and fields would be populated
           */

        // Now, let's encode the message and look at the output
        val parser: Parser = hl7Parser
        val encodedMessage = parser.encode(adt)
        println("Printing ER7 Encoded Message:")
        println(encodedMessage)


        val task : Task = Task()
        task.meta.versionId = "849723"
        val date : Date=Date()
        date.date=16
        date.month=11
        date.year=2022
        task.meta.lastUpdated =date
        val profile:CanonicalType= CanonicalType()
        profile.value= "http://varian.com/fhir/v1/StructureDefinition/Task"
        task.meta.profile.add(profile)
        task.minutesDuration = IntegerType(10)
        val readVal: BooleanType=BooleanType(false)
        task.readOnly=readVal
        task.status = org.hl7.fhir.r4.model.Task.TaskStatus.READY
        task.intent = org.hl7.fhir.r4.model.Task.TaskIntent.ORDER
        task.priority = org.hl7.fhir.r4.model.Task.TaskPriority.URGENT
        val coding : Coding=Coding()
        coding.system = "http://varian.com/fhir/CodeSystem/activityDefinition-category"
        coding.code="Treatment"
        coding.display="Treatment"
        task.code.coding.add(coding)
        task.focus.reference = "ActivityDefinition/ActivityDefinition-244"
        task.focus.display="Boost Treatment"
        task.`for`.reference="Patient/Patient-15"
        task.`for`.display="TestAk1"
        task.authoredOn.time=20220318000000
        task.lastModified.time=20220318000000
        task.requester.reference = "Practitioner/Practitioner-1003"
        task.requester.display="SysAdmin"
        task.owner.reference = "Practitioner/Practitioner-1003"
        task.owner.display="SysAdmin"
        task.owner.id="40"

        val anno : Annotation=Annotation()
        anno.text="update from groovy"
        task.note.add(anno)
        task.restriction.period.end.time = 20220318000000
        val ref1:Reference=Reference()
        ref1.id="40"
        ref1.display="SysAdmin"
        ref1.reference="Practitioner/Practitioner-1003"

        task.restriction.recipient.add(ref1)
        val ref2:Reference=Reference()
        ref2.id="Organization-Dept-1"
        ref2.display="myDepartment"
        ref2.reference="Organization/Organization-Dept-1"
        task.restriction.recipient.add(ref2)
    }

    fun GetHL7(siu: SIU_S12): String? {
        return hl7Parser.encode(siu)
    }

    fun GetHL7Ack(ack: ACK): String? {
        return hl7Parser.encode(ack)
    }

    fun GetMessageFromString(msg: String): Message {
        return hl7Parser.parse(msg)
    }
}