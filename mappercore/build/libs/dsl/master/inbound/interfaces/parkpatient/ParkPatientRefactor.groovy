@HandlerFor(source = "HL7", subject = "ParkPatientRefactor") dsl

//Read MSH segment for meta information
def msh = hl7Message.get("MSH") as ca.uhn.hl7v2.model.v251.segment.MSH
def location = null
switch (hl7Message.getClass()) {
    case ca.uhn.hl7v2.model.v251.message.ADT_A39:
        if (hl7Message?.getPATIENT()?.getPV1()?.assignedPatientLocation?.room?.value != null) {
            location = hl7Message?.getPATIENT()?.getPV1()?.assignedPatientLocation?.room?.value
        }
        break
    case ca.uhn.hl7v2.model.v251.message.ADT_A43:
        break
    default:
        def pv1 = hl7Message.get("PV1") as ca.uhn.hl7v2.model.v251.segment.PV1
        location = pv1.assignedPatientLocation?.room?.value
        break
}
(msgType, msgEvent, msgControlId) = readMsgHeadersParams(msh)
def repeatGroupName = null

switch (hl7Message.getClass()) {
    case ca.uhn.hl7v2.model.v251.message.ADT_A01:
    case ca.uhn.hl7v2.model.v251.message.ADT_A02:
    case ca.uhn.hl7v2.model.v251.message.ADT_A03:
    case ca.uhn.hl7v2.model.v251.message.ADT_A05:
    case ca.uhn.hl7v2.model.v251.message.ADT_A06:
    case ca.uhn.hl7v2.model.v251.message.ADT_A09:
    case ca.uhn.hl7v2.model.v251.message.ADT_A12:
    case ca.uhn.hl7v2.model.v251.message.ADT_A15:
    case ca.uhn.hl7v2.model.v251.message.ADT_A16:
    case ca.uhn.hl7v2.model.v251.message.ADT_A21:
    case ca.uhn.hl7v2.model.v251.message.ADT_A38:
    case ca.uhn.hl7v2.model.v251.message.ADT_A54:
    case ca.uhn.hl7v2.model.v251.message.ADT_A60:
    case ca.uhn.hl7v2.model.v251.message.ADT_A17:
    case ca.uhn.hl7v2.model.v251.message.ADT_A24:
    case ca.uhn.hl7v2.model.v251.message.ADT_A37:
    case ca.uhn.hl7v2.model.v251.message.ADT_A52:
        def segs = hl7Message?.getAll("PID")
        segs?.toList().stream()?.map({ seg -> (seg as ca.uhn.hl7v2.model.v251.segment.PID)?.toParkPatient(patientKeyMapping, location, processName, siteName) })
                ?.findAll({ pat -> pat != null })
                ?.forEach({ pat ->
                    def patient = upsertParkPatient(patientKeyMapping, pat, parkService)
                    def msg = patient.genLinkedMessage(hl7RawMessage, msgControlId, msgType, msgEvent, traceId, processName)
                    parkService.createParkedMessage(msg)
                });
        break;
    case ca.uhn.hl7v2.model.v251.message.ADT_A18:
    case ca.uhn.hl7v2.model.v251.message.ADT_A30:
    case ca.uhn.hl7v2.model.v251.message.ADT_A50:
        def pidseg = hl7Message?.get("PID")
        def pidReqPat = (pidseg as ca.uhn.hl7v2.model.v251.segment.PID)?.toParkPatient(patientKeyMapping, location, processName, siteName)
        def pidPatient = upsertParkPatient(patientKeyMapping, pidReqPat, parkService)

        def msg = pidPatient?.genLinkedMessage(hl7RawMessage, msgControlId, msgType, msgEvent, traceId, processName)
        if (msg) parkService.createParkedMessage(msg)

        def mrgseg = hl7Message?.get("MRG")
        def mrgReqPat = (mrgseg as ca.uhn.hl7v2.model.v251.segment.MRG)?.toParkPatient(patientKeyMapping, pidseg, location, processName, siteName)
        def mrgPatient = upsertParkPatient(patientKeyMapping, mrgReqPat, parkService)
        parkService.mergeParkedPatient(pidPatient.patientRecordSer, mrgPatient.patientRecordSer)
        break
    case ca.uhn.hl7v2.model.v251.message.ADT_A39:
        repeatGroupName = repeatGroupName ?: "PATIENT"
        def groups = hl7Message.getPATIENTReps()
        for (def grp = 0; grp < groups; grp++) {
            //groups?.toList()?.forEach({ grp ->
            def pidSeg = hl7Message.getPATIENT(grp).getPID()
            def pidReqPat = pidSeg.toParkPatient(patientKeyMapping, location, processName, siteName)
            def pidPatient = upsertParkPatient(patientKeyMapping, pidReqPat, parkService)

            def msg = pidPatient?.genLinkedMessage(hl7RawMessage, msgControlId, msgType, msgEvent, traceId, processName)
            if (msg) parkService.createParkedMessage(msg)

            def mrgseg = hl7Message.getPATIENT(grp).getMRG()
            def mrgReqPat = mrgseg.toParkPatient(patientKeyMapping, pidSeg, location, processName, siteName)
            def mrgPatient = upsertParkPatient(patientKeyMapping, mrgReqPat, parkService)
            parkService.mergeParkedPatient(pidPatient.patientRecordSer, mrgPatient.patientRecordSer)
        }
        break
    case ca.uhn.hl7v2.model.v251.message.ADT_A43:
        repeatGroupName = repeatGroupName ?: "PATIENT"
        def groups = hl7Message.getPATIENTReps()
        for (def grp = 0; grp < groups; grp++) {
            //groups?.toList()?.forEach({ grp ->
            def pidSeg = hl7Message.getPATIENT(grp).getPID()
            def pidReqPat = pidSeg.toParkPatient(patientKeyMapping, location, processName, siteName)
            def pidPatient = upsertParkPatient(patientKeyMapping, pidReqPat, parkService)

            def msg = pidPatient?.genLinkedMessage(hl7RawMessage, msgControlId, msgType, msgEvent, traceId, processName)
            if (msg) parkService.createParkedMessage(msg)

            def mrgseg = hl7Message.getPATIENT(grp).getMRG()
            def mrgReqPat = mrgseg.toParkPatient(patientKeyMapping, pidSeg, location, processName, siteName)
            def mrgPatient = upsertParkPatient(patientKeyMapping, mrgReqPat, parkService)
            parkService.mergeParkedPatient(pidPatient.patientRecordSer, mrgPatient.patientRecordSer)
        }
        break
    case ca.uhn.hl7v2.model.v251.message.ADT_A45:
        def pidseg = hl7Message?.get("PID")
        def pidReqPat = (pidseg as ca.uhn.hl7v2.model.v251.segment.PID)?.toParkPatient(patientKeyMapping, location, processName, siteName)
        def pidPatient = upsertParkPatient(patientKeyMapping, pidReqPat, parkService)

        def msg = pidPatient?.genLinkedMessage(hl7RawMessage, msgControlId, msgType, msgEvent, traceId, processName)
        if (msg) parkService.createParkedMessage(msg)

        repeatGroupName = "MERGE_INFO"
        def groups = hl7Message?.getAll(repeatGroupName)
        groups?.toList()?.forEach({ grp ->
            def mrgseg = grp?.get("MRG")
            def mrgReqPat = (mrgseg as ca.uhn.hl7v2.model.v251.segment.MRG)?.toParkPatient(patientKeyMapping, pidseg, location, processName, siteName)
            def mrgPatient = upsertParkPatient(patientKeyMapping, mrgReqPat, parkService)
            parkService.mergeParkedPatient(pidPatient.patientRecordSer, mrgPatient.patientRecordSer)
        })
        break
}
