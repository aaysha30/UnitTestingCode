package master.outbound.interfaces.siuout

def class AppointmentHelper {
    def static PRACTITIONER_USER_NAME_IDENTIFIER = "http://varian.com/fhir/identifier/Practitioner/UserName"
    def static PRACTITIONER_ID_IDENTIFIER = "http://varian.com/fhir/identifier/Practitioner/Id"
    def static SCHEDULABLE_ACTIVITY = "SchedulableActivity"
    def static NON_SCHEDULABLE_ACTIVITY = "NonSchedulableActivity"
    def SqliteUtility
    def siteDirName
    def mappedSqliteDbName
    def deptId
    def appointmentBundle
    def patientBundle
    def departmentBundle
    def practitionerBundle
    def deviceBundle
    def locationBundle
    def siu_out
    def appointmentType
    def localConnection
    def masterConnection
    def careTeamBundle
    def bundleUtility
    def patientSer
    def departmentSer
    def eventID
    def cloverLogger
    def messageMetaData
    def activityDefinition
    def modifiedByPractitioner
    def bundle

    def setDepartmentBundle(departmentSer) {
        def deptSearchKey = "Organization-Dept-" + departmentSer
        departmentBundle = bundleUtility.getOrganizations(bundle).find { it.idElement.idPart == deptSearchKey }
    }

    def setPractitionerBundle(practitionerSer) {
        def pract = "Practitioner-" + practitionerSer
        practitionerBundle = bundleUtility.getPractitioners(bundle).find { it.idElement.idPart == pract }
    }

    def setDeviceBundle(deviceSer) {
        def device = "Device-" + deviceSer
        deviceBundle = bundleUtility.getDevices(bundle).find { it.idElement.idPart == device }
    }

    def setLocationBundle(locationSer) {
        def location = "Location-" + locationSer
        locationBundle = bundleUtility.getLocations(bundle).find { it.idElement.idPart == location }
    }

    def setSegments(siu_outS) {
        siu_out = siu_outS
        setMSHSegment()
        setDepartmentBundle(departmentSer)
        setAILandAIPSegment()
        setSCHSegment()
        setNTESegment()
        if (patientSer != null) {
            setPIDSegment()
            setPV1Segment()
        }
        setAISSegment()
        setRGSSegment()
    }

    def setRGSSegment() {
        def rgsSegment = siu_out.getRESOURCES().getRGS()
        rgsSegment.getSetIDRGS().value = "1"
    }

    def setMSHSegment() {
        def mshSegment = siu_out.getMSH()

        def sqliteConn = SqliteUtility.getSqliteConnectionObject(siteDirName, mappedSqliteDbName)
        def activityOutValue = [:] // Initialize empty map

        activityOutValue.put('Key', "SendingApplication")
        def sendingApplicationValue = SqliteUtility.getValue(sqliteConn, "ProcessingConfig", "Value", activityOutValue)

        activityOutValue.put('Key', "SendingFacility")
        def sendingFacilityValue = SqliteUtility.getValue(sqliteConn, "ProcessingConfig", "Value", activityOutValue)

        activityOutValue.put('Key', "ReceivingApplication")
        def receivingApplicationValue = SqliteUtility.getValue(sqliteConn, "ProcessingConfig", "Value", activityOutValue)

        activityOutValue.put('Key', "ReceivingFacility")
        def receivingFacilityValue = SqliteUtility.getValue(sqliteConn, "ProcessingConfig", "Value", activityOutValue)
        cloverLogger.log(2, "SendingApplication: $sendingApplicationValue, SendingFacility: $sendingFacilityValue, ReceivingApplication: $receivingApplicationValue, ReceivingFacility: $receivingFacilityValue", messageMetaData)

        mshSegment.sendingApplication.namespaceID.value = sendingApplicationValue
        mshSegment.sendingFacility.namespaceID.value = sendingFacilityValue
        mshSegment.receivingApplication.namespaceID.value = receivingApplicationValue
        mshSegment.receivingFacility.namespaceID.value = receivingFacilityValue
        mshSegment.messageType.messageStructure.value = ""
        mshSegment.dateTimeOfMessage.time.value = mshSegment.dateTimeOfMessage.time.value.tokenize('.')[0]
        Date date = new Date()
        String datePart = date.format("yyyyMMdd")
        cloverLogger.log(2, "Message Control Id i: $datePart" + eventID, messageMetaData)
        mshSegment.messageControlID.value = datePart + eventID
    }

    def getEventReasonOutValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'AppointmentEventCode')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getAppointmentStatusValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'AppointmentStatus')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getEthnicGroupValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'EthnicGroup')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getCountryValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'InValue')
        values.put('MASTERIN', 'InValue')
        values.put('LOCALOUT', 'OutValue')
        values.put('MASTEROUT', 'OutValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'Country')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getRaceValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'Race')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getLanguageValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'Language')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getReligionValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'Religion')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getAddressTypeValue(inputText) {
        def values = [:]
        values.put('INVALUE', inputText)
        values.put('LOCALIN', 'OutValue')
        values.put('MASTERIN', 'OutValue')
        values.put('LOCALOUT', 'InValue')
        values.put('MASTEROUT', 'InValue')
        values.put('SEQUENCE', 'Local,Master')
        values.put('TABLE', 'AddressType')
        values.put('IfNotMatched', 'Original')
        def eventReason = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
        return eventReason
    }

    def getActivityCodeValue() {
        def sqliteConn = SqliteUtility.getSqliteConnectionObject(siteDirName, mappedSqliteDbName)
        def configSuppressUnmappedValue = [:] // Initialize empty map
        configSuppressUnmappedValue.put('Key', "SuppressUnmapped")
        def suppressUnmappedValue = SqliteUtility.getValue(sqliteConn, "ProcessingConfig", "Value", configSuppressUnmappedValue)
        cloverLogger.log(2, "suppressUnmappedValue config value is: $suppressUnmappedValue", messageMetaData)

        if (suppressUnmappedValue == "1") {
            def deptSer = getParticipantSer("HealthcareService", 0)

            setDepartmentBundle(deptSer)

            def departmentId = departmentBundle?.identifier?.find { it.system == "http://varian.com/fhir/identifier/Organization/dept/Id" }?.
                    with { it.value }
            def hospitalName = departmentBundle?.partOf?.display
            def activityName = getServiceType(appointmentBundle)[0]

            def activityOutValue = [:]
            activityOutValue.put('OutValue', activityName)
            activityOutValue.put('HospitalName', hospitalName)
            activityOutValue.put('DepartmentId', departmentId)
            def activityCodeList = SqliteUtility.getValues(localConnection, "ActivityCode", "InValue", activityOutValue)
            cloverLogger.log(2, "number of activity code found is: $activityCodeList.size", messageMetaData)
            return (activityCodeList != null && activityCodeList.size == 0)
        } else {
            return false
        }
    }

    def static getLocationDetails(careTeam, roleCode, roleSystem) {
        return careTeam?.participant?.find {
            it.type.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == roleCode && y.system == roleSystem
            }
        }?.with { it?.actor?.reference }
    }

    def setSCHSegment() {
        def serviceType = getServiceType(appointmentBundle)[0]

        def sch = siu_out.getSCH()
        def appointmentStatusInAria = appointmentBundle?.statusInAria?.coding?.find { it.system == "http://varian.com/fhir/CodeSystem/aria-appointment-status" }?.code
        sch.placerAppointmentID.entityIdentifier.value = getAppointmentIdentifier("http://varian.com/fhir/identifier/UID")
        sch.placerAppointmentID.universalID.value = getAppointmentIdentifier("http://varian.com/fhir/identifier/RadOnc-Id")
        sch.fillerAppointmentID.entityIdentifier.value = getAppointmentIdentifier("http://varian.com/fhir/identifier/UID")
        sch.fillerAppointmentID.universalID.value = getAppointmentIdentifier("http://varian.com/fhir/identifier/RadOnc-Id")

        def statusChangedOpenValue = getEventReasonOutValue("StatusChanged - Open")
        def statusChangedInProgressValue = getEventReasonOutValue("StatusChanged - In Progress (Manually Set)")
        def statusChangedCancelledPatientNoShowValue = getEventReasonOutValue("StatusChanged - Cancelled - Patient No-Show")
        def statusChangedManuallyCompletedValue = getEventReasonOutValue("StatusChanged - Manually Completed")
        def statusChangedCompletedValue = getEventReasonOutValue("StatusChanged - Completed")
        def statusChangedCancelledValue = getEventReasonOutValue("StatusChanged - Cancelled")
        def statusChangedDeletedValue = getEventReasonOutValue("StatusChanged - Deleted")
        def createdValue = getEventReasonOutValue("Created")
        def rescheduledValue = getEventReasonOutValue("Rescheduled")
        def modifiedValue = getEventReasonOutValue("Modified")
        def cancelledValue = getEventReasonOutValue("Cancelled")
        def cancelledPatientNoShowValue = getEventReasonOutValue("Cancelled - Patient No-Show")
        def deletedValue = getEventReasonOutValue("Deleted")
        def patientCheckInValue = getEventReasonOutValue("PatientCheckIn")
        def undoPatientCheckInValue = getEventReasonOutValue("UndoPatientCheckIn")
        def patientCheckInLocationChangedValue = getEventReasonOutValue("PatientCheckInLocationChanged - Open")

        switch (appointmentType) {
            case "Varian.ARIA.Appointment.Create":
                sch.eventReason.text.value = "Appointment was created."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S12"
                sch.eventReason.identifier.value = createdValue
                break
            case "Varian.ARIA.Appointment.StatusChange":
                sch.eventReason.text.value = "Appointment status changed."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                def statusChangedReason = "StatusChanged - $appointmentStatusInAria".toString()
                sch.eventReason.identifier.value = getEventReasonOutValue(statusChangedReason)
                break
            case "Varian.ARIA.Appointment.Update":
                sch.eventReason.text.value = "Appointment was modified."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = modifiedValue
                break
            case "Varian.ARIA.Appointment.CheckIn":
                sch.eventReason.text.value = "Patient check-in."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = patientCheckInValue
                def locSer = getLocationDetails(appointmentBundle, "checkin", "http://varian.com/fhir/CodeSystem/Appointment/SupportParticipationType")
                if (locSer != null) {
                    def locBundle = bundleUtility.getLocations(bundle).find { "Location/$it.idElement.idPart" == locSer }
                    def pv1 = siu_out.getPATIENT().getPV1()
                    pv1.getAssignedPatientLocation().pointOfCare.value = locBundle?.identifier?.find { it?.system == "http://varian.com/fhir/identifier/Location/Id" }?.with { it?.value }
                }
                break
            case "Varian.ARIA.Appointment.ReCheckIn":
                sch.eventReason.text.value = "Patient check-in location changed"
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = patientCheckInLocationChangedValue
                def locSer = getLocationDetails(appointmentBundle, "checkin", "http://varian.com/fhir/CodeSystem/Appointment/SupportParticipationType")
                if (locSer != null) {
                    def locBundle = bundleUtility.getLocations(bundle).find { "Location/$it.idElement.idPart" == locSer }
                    def pv1 = siu_out.getPATIENT().getPV1()
                    pv1.getAssignedPatientLocation().pointOfCare.value = locBundle?.identifier?.find { it?.system == "http://varian.com/fhir/identifier/Location/Id" }?.with { it?.value }
                }
                break
            case "Varian.ARIA.Appointment.UndoCheckIn":
                sch.eventReason.text.value = "Undo patient check-in."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = undoPatientCheckInValue
                break
            case "Varian.ARIA.Appointment.Cancelled":
                sch.eventReason.text.value = "Appointment was cancelled."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S15"
                sch.eventReason.identifier.value = cancelledValue
                break
            case "Varian.ARIA.Appointment.Deleted":
                sch.eventReason.text.value = "Appointment was deleted."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S17"
                sch.eventReason.identifier.value = deletedValue
                break
            case "Varian.ARIA.Appointment.WaitList":
                sch.eventReason.text.value = "Appointment was deleted."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S17"
                sch.eventReason.identifier.value = deletedValue
                break
            case "Varian.ARIA.Appointment.DeWaitList":
                sch.eventReason.text.value = "Appointment was deleted."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S17"
                sch.eventReason.identifier.value = deletedValue
                break
            case "Varian.ARIA.Appointment.PatientNoShow":
                sch.eventReason.text.value = "Appointment cancelled, patient no-show."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S26"
                sch.eventReason.identifier.value = cancelledPatientNoShowValue
                break
            case "Varian.ARIA.Appointment.Reschedule":
                sch.eventReason.text.value = "Appointment was rescheduled."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S13"
                sch.eventReason.identifier.value = rescheduledValue
                break
            case "Varian.ARIA.Appointment.ScheduleUpdate":
                sch.eventReason.text.value = "Appointment was modified."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = modifiedValue
                break
            case "Varian.ARIA.Appointment.ActivityChange":
                sch.eventReason.text.value = "Appointment was modified."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = modifiedValue
                break
            case "Varian.ARIA.Appointment.AttendeesUpdate":
                sch.eventReason.text.value = "Appointment was modified."
                def msh = siu_out.getMSH()
                msh.messageType.triggerEvent.value = "S14"
                sch.eventReason.identifier.value = modifiedValue
                break
        }

        sch.appointmentReason.text.value = serviceType
        sch.appointmentType.text.value = getActivityType()
        sch.appointmentDuration.value = appointmentBundle?.minutesDuration
        sch.appointmentDurationUnits.identifier.value = "M"
        def appTime = sch.getAppointmentTimingQuantity(0)
        appTime.startDateTime.time.value = getAppointmentStartDateTime()
        appTime.endDateTime.time.value = getAppointmentEndDateTime()

        def bookedValue = getAppointmentStatusValue("Open")
        def startedValue = getAppointmentStatusValue("In Progress (Manually Set)")
        def noShowValue = getAppointmentStatusValue("Cancelled - Patient No-Show")
        def completeValue = getAppointmentStatusValue("Completed")
        def cancelledApptStatusValue = getAppointmentStatusValue("Cancelled")
        def deletedApptStatusValue = getAppointmentStatusValue("Deleted")
        def patientOnBreakValue = getAppointmentStatusValue("Patient On-Break")
        def ptCompltActiveValue = getAppointmentStatusValue("Pt. CompltActive")
        def ptCompltFinishValue = getAppointmentStatusValue("Pt. CompltFinish")
        def rescheduleActivityInActiveValue = getAppointmentStatusValue("Reschedule Activity (In-Active)")
        def manuallyCompletedValue = getAppointmentStatusValue("Manually Completed")
        def waitlistValue = getAppointmentStatusValue("Waitlist")

        switch (appointmentStatusInAria) {

            case "Open":
                sch.fillerStatusCode.identifier.value = bookedValue
                break
            case "In Progress (Manually Set)":
                sch.fillerStatusCode.identifier.value = startedValue
                break
            case "Cancelled - Patient No-Show":
                sch.fillerStatusCode.identifier.value = noShowValue
                break
            case "Completed":
                sch.fillerStatusCode.identifier.value = completeValue
                break
            case "Cancelled":
                sch.fillerStatusCode.identifier.value = cancelledApptStatusValue
                break
            case "Deleted":
                sch.fillerStatusCode.identifier.value = deletedApptStatusValue
                break
            case "Patient On-Break":
                sch.fillerStatusCode.identifier.value = patientOnBreakValue
                break
            case "Pt. CompltActive":
                sch.fillerStatusCode.identifier.value = ptCompltActiveValue
                break
            case "Pt. CompltFinish":
                sch.fillerStatusCode.identifier.value = ptCompltFinishValue
                break
            case "Reschedule Activity (In-Active)":
                sch.fillerStatusCode.identifier.value = rescheduleActivityInActiveValue
                break
            case "Manually Completed":
                sch.fillerStatusCode.identifier.value = manuallyCompletedValue
                break
            case "Waitlist":
                sch.fillerStatusCode.identifier.value = waitlistValue
                break
            default:
                break
        }

        sch.fillerStatusCode.text.value = getAppointmentExtension("http://varian.com/fhir/CodeSystem/aria-appointment-status")
        if (appointmentBundle.status?.toCode() == "entered-in-error") {
            sch.fillerStatusCode.alternateText.value = "Deleted"
        } else {
            sch.fillerStatusCode.alternateText.value = "Active"
        }

        def schFillerContactPersonDetails = sch.insertFillerContactPerson(0)
        def modifiedByPractitionerId = modifiedByPractitioner?.identifier?.find { it.system == PRACTITIONER_ID_IDENTIFIER }?.value
        schFillerContactPersonDetails.IDNumber.value = modifiedByPractitionerId
        def schFillerEnteredByPersonDetails = sch.insertEnteredByPerson(0)
        schFillerEnteredByPersonDetails.IDNumber.value = modifiedByPractitionerId
        if (modifiedByPractitioner?.name != null) {
            def modifiedByPractitionerName = modifiedByPractitioner?.name?.find { it.use == HumanName.NameUse.OFFICIAL }
            schFillerContactPersonDetails.familyName.surname.value = modifiedByPractitionerName?.family
            schFillerContactPersonDetails.givenName.value = modifiedByPractitionerName?.givenAsSingleString
            schFillerEnteredByPersonDetails.familyName.surname.value = modifiedByPractitionerName?.family
            schFillerEnteredByPersonDetails.givenName.value = modifiedByPractitionerName?.givenAsSingleString
        }
    }

    def setAILandAIPSegment() {
        def participants = appointmentBundle?.participant
        cloverLogger.log(2, "Setting up AIS and AIP segments..", messageMetaData)
        def practcnt = 0
        def machinecnt = 0
        def locationcnt = 0
        def AILcnt = 0
        participants.each {
            switch (it.type.coding.display[0][0]) {
                case "Practitioner":
                    def practSer = getParticipantSer("Practitioner", practcnt)
                    setAIPSegment(practSer, practcnt)
                    practcnt = practcnt + 1
                    break
                case "Device":
                    def deviceSer = getParticipantSer("Device", machinecnt)
                    setAILSegment(deviceSer, AILcnt)
                    machinecnt = machinecnt + 1
                    AILcnt = AILcnt + 1
                    break
                case "Location":
                    def locationSer = getParticipantSer("Location", locationcnt)
                    setAILSegmentForLocation(locationSer, AILcnt)
                    AILcnt = AILcnt + 1
                    locationcnt = locationcnt + 1

                default:
                    break
            }
        }
    }

    def setAIPSegment(practSer, cnt) {
        setPractitionerBundle(practSer)
        cloverLogger.log(2, "Setting up AIS for ser: $practSer", messageMetaData)
        def aipSeg = siu_out.getRESOURCES().getPERSONNEL_RESOURCE(cnt).getAIP()
        aipSeg.setIDAIP.value = cnt + 1
        aipSeg.segmentActionCode.value = "A"
        def xcn = aipSeg.insertAip3_PersonnelResourceID(0)
        xcn.IDNumber.value = getPractionerId()
        xcn.familyName.surname.value = practitionerBundle?.name?.family[1]
        xcn.givenName.value = practitionerBundle?.name?.given[1][0]

        aipSeg.resourceType.ce2_Text.value = "Doctor"
        aipSeg.resourceType.alternateText.value = "Required Participant"
        aipSeg.startDateTime.time.value = getAppointmentStartDateTime()
        aipSeg.duration.value = appointmentBundle?.minutesDuration
        aipSeg.durationUnits.identifier.value = "M"
    }

    def setAILSegmentForLocation(locationSer, cnt) {
        cloverLogger.log(2, "Setting up AIL for ser: $locationSer", messageMetaData)
        setLocationBundle(locationSer)
        def ailSeg = siu_out.getRESOURCES().getLOCATION_RESOURCE(cnt).getAIL()
        ailSeg.setIDAIL.value = cnt + 1
        ailSeg.segmentActionCode.value = "A"
        def loc = ailSeg.insertAil3_LocationResourceID(0)
        def locationName = locationBundle?.name
        def departmentId = departmentBundle?.identifier?.find { it.system == "http://varian.com/fhir/identifier/Organization/dept/Id" }?.
                with { it.value }
        def locationOutValue = [:]
        locationOutValue.put('OutValue', locationName)
        locationOutValue.put('DepartmentId', departmentId)
        def locationList = SqliteUtility.getValues(localConnection, "Venue", "InValue", locationOutValue)

        if (locationList != null && locationList.size() > 0) {
            loc.pointOfCare.value = locationList[0].toString()
        } else {
            loc.pointOfCare.value = locationName
        }
        loc.comprehensiveLocationIdentifier.ei1_EntityIdentifier.value = locationName
        ailSeg.locationTypeAIL.text.value = getLocationType("http://varian.com/fhir/ValueSet/Location-physical-type")
        ailSeg.locationTypeAIL.alternateText.value = getLocationType("http://varian.com/fhir/ValueSet/Location-type")
        ailSeg.startDateTime.time.value = getAppointmentStartDateTime()
        ailSeg.duration.value = appointmentBundle?.minutesDuration
        ailSeg.durationUnits.identifier.value = "M"
    }

    def setAILSegment(deviceSer, cnt) {
        cloverLogger.log(2, "Setting up AIL for ser: $deviceSer", messageMetaData)
        setDeviceBundle(deviceSer)
        def ailSeg = siu_out.getRESOURCES().getLOCATION_RESOURCE(cnt).getAIL()
        ailSeg.setIDAIL.value = cnt + 1
        ailSeg.segmentActionCode.value = "A"
        def loc = ailSeg.insertAil3_LocationResourceID(0)
        loc.pointOfCare.value = getDeviceId()
        loc.comprehensiveLocationIdentifier.ei1_EntityIdentifier.value = deviceBundle?.modelNumber
        ailSeg.locationTypeAIL.text.value = deviceBundle?.type?.text
        ailSeg.locationTypeAIL.alternateText.value = "Machine"
        ailSeg.startDateTime.time.value = getAppointmentStartDateTime()
        ailSeg.duration.value = appointmentBundle?.minutesDuration
        ailSeg.durationUnits.identifier.value = "M"
    }

    def setNTESegment() {//notes and segment
        def nte = siu_out.getNTE()
        cloverLogger.log(2, "Setting up Appointment Note", messageMetaData)
        def sqliteConn = SqliteUtility.getSqliteConnectionObject(siteDirName, mappedSqliteDbName)
        def concatNoteCharMap = [:] // Initialize empty map

        concatNoteCharMap.put('Key', "ConcatNoteChar")
        def concatNoteCharValue = SqliteUtility.getValue(sqliteConn, "ProcessingConfig", "Value", concatNoteCharMap)

        def comment = appointmentBundle?.comment

        if (comment != null && comment != "") {
            def updatedComment = (concatNoteCharValue == null) ? comment.replace("\r\n", "") :
                    comment.replace("\r\n", concatNoteCharValue)
            nte.getComment(0).setValue(updatedComment)
            cloverLogger.log(2, "Appointment Note: " + updatedComment, messageMetaData)
        }
    }

    def setPIDSegment() {
        def pid = siu_out.getPATIENT().getPID()
        cloverLogger.log(2, "Setting the PID segment", messageMetaData)
        def ARIAID1 = ""
        def patientUID = ""

        def identifierList = patientBundle?.identifier

        identifierList.each {
            switch (it.system) {
                case "http://varian.com/fhir/identifier/Patient/ARIAID1":
                    ARIAID1 = it.value
                    break
                case "http://varian.com/fhir/identifier/Patient/UID":
                    patientUID = it.value
                    break
            }
        }

        pid.getPatientID().data[0].value = patientBundle?.identifier?.find { it.system == "http://varian.com/fhir/identifier/Patient/ARIAID2" }?.with { it.value }
        pid.getPatientIdentifierList(0).data[0].value = ARIAID1

        def patPPN = patientBundle?.identifier?.find { it.system == "http://varian.com/fhir/identifier/Patient/passport" }?.with { it.value }
        if (patPPN != null) {
            pid.getPatientIdentifierList(1).data[0].value = patPPN
            pid.getPatientIdentifierList(1).data[4].value = pid.getPatientIdentifierList(1).data[0].value != null ? "PPN" : null
        }
        def patientNameBundle = patientBundle?.name

        pid.getSetIDPID().value = "1"
        pid.getSSNNumberPatient().value = patientBundle?.identifier?.find { it.system == "http://varian.com/fhir/identifier/Patient/SSN" }?.with { it.value }

        pid.getMotherSMaidenName(0).data[0].data[0].value = patientBundle?.patientMothersMaidenName?.value

        def raceCount = patientBundle?.usCoreRace?.size
        for (def i = 0; i < raceCount; i++) {
            def patRace = patientBundle?.usCoreRace[i]?.text
            def patRaceCode = getRaceValue(patRace.toString())
            pid.getRace(i).data[0].value = patRaceCode
            pid.getRace(i).data[1].value = patRace
        }
        def addCount = patientBundle?.address?.size
        for (def i = 0; i < addCount; i++) {

            pid.getPatientAddress(i).data[0].data[0].value = patientBundle?.address[i]?.line[0]?.value
            pid.getPatientAddress(i).data[1].value = patientBundle?.address[i]?.line[1]?.value
            pid.getPatientAddress(i).data[2].value = patientBundle?.address[i]?.city
            pid.getPatientAddress(i).data[3].value = patientBundle?.address[i]?.state
            pid.getPatientAddress(i).data[4].value = patientBundle?.address[i]?.postalCode
            pid.getPatientAddress(i).data[5].value = patientBundle?.address[i]?.country

            def addressType = patientBundle?.address[i]?.use?.toString()
            def addressTypeEncode = ""
            if(addressType?.toLowerCase() == "temp") {
                def isPermanentAddress =  patientBundle?.address[i]?.find { it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/isPrimary")?.value?.booleanValue() == true } != null
                if(isPermanentAddress) {
                    addressType = "Permanent"
                } else {
                    addressType = "Temporary"
                }
            }
            addressTypeEncode = getAddressTypeValue(addressType)
            pid.getPatientAddress(i).data[6].value = addressTypeEncode
            pid.getPatientAddress(i).data[8].value = patientBundle?.address[i]?.district
        }

        pid.getPatientAddress(0).data[6].value = "H"
        pid.getPatientAddress(1).data[6].value = "P"
        pid.getPatientAddress(2).data[6].value = "C"

        pid.getPhoneNumberHome(0).data[0].value = patientBundle?.telecom?.find {
            (it.use.toString() == "HOME"
                    && it.system.toString() == "PHONE")
        }?.with { it.value }
        pid.getPhoneNumberHome(0).data[2].value = "PH"

        pid.getPhoneNumberHome(1).data[0].value = patientBundle?.telecom?.find {
            (it.use.toString() == "MOBILE"
                    && it.system.toString() == "PHONE")
        }?.with { it.value }
        pid.getPhoneNumberHome(1).data[2].value = "CP"
        pid.getPhoneNumberHome(1).data[8].value = patientBundle?.mobilePhoneProvider

        def patWorkPhone1 = patientBundle?.telecom?.find { (it.use.toString() == "WORK" && it.system.toString() == "PHONE") }?.with { it.value }
        def patWorkPhone2 = patientBundle?.address?.find { it.use.toString() == "HOME" }?.with { it?.extension?.value[0] }
        pid.getPhoneNumberHome(2).data[0].value = patWorkPhone2 != null ? patWorkPhone2 : patWorkPhone1
        pid.getPhoneNumberHome(2).data[2].value = "PH"

        pid.getPhoneNumberHome(3).data[0].value = patientBundle?.telecom?.find {
            (it.use.toString() == "TEMP"
                    && it.system.toString() == "PHONE")
        }?.with { it.value }
        pid.getPhoneNumberHome(3).data[1].value = "ORN"

        pid.getPhoneNumberHome(4).data[0].value = patientBundle?.telecom?.find {
            (it.use.toString() == "HOME"
                    && it.system.toString() == "PAGER")
        }?.with { it.value }
        pid.getPhoneNumberHome(4).data[1].value = "BPN"
        pid.getPhoneNumberHome(4).data[2].value = "BP"

        pid.getPhoneNumberHome(5).data[0].value = patientBundle?.telecom?.find {
            (it.use.toString() == "HOME"
                    && it.system.toString() == "FAX")
        }?.with { it.value }
        pid.getPhoneNumberHome(5).data[2].value = "FX"

        pid.getPhoneNumberHome(6).data[3].value = patientBundle?.telecom?.find {
            (it.use.toString() == "HOME"
                    && it.system.toString() == "EMAIL")
        }?.with { it.value }
        pid.getPhoneNumberHome(6).data[1].value = "NET"
        pid.getPhoneNumberHome(6).data[2].value = "Internet"

        pid.getPhoneNumberBusiness(0).data[0].value = patientBundle?.telecom?.find {
            (it.use.toString() == "WORK"
                    && it.system.toString() == "PHONE")
        }?.with { it.value }

        def patLanguage = patientBundle?.communication?.language?.stream()?.flatMap { x -> x.coding.stream() }?.find { y -> y.system == "http://varian.com/fhir/CodeSystem/communication-language" }?.with { it?.code }
        if (patLanguage != null) {
            def languageCode = getLanguageValue(patLanguage)
            pid.getPrimaryLanguage().data[0].value = languageCode
            pid.getPrimaryLanguage().data[1].value = patLanguage
        }

        pid.getMaritalStatus().data[0].value = patientBundle?.maritalStatus?.coding?.find { it.system == "http://hl7.org/fhir/ValueSet/marital-status" }?.with { it.code }
        pid.getMaritalStatus().data[1].value = patientBundle?.maritalStatus?.coding?.find { it.system == "http://varian.com/fhir/CodeSystem/patient-marital-status" }?.with { it.code }

        def patReligion = patientBundle?.patientReligion?.stream()?.flatMap { x -> x.coding.stream() }?.find { y -> y.system == "http://varian.com/fhir/CodeSystem/patient-religion" }?.with { it?.code }
        if (patReligion != null) {
            def relCode = getReligionValue(patReligion)
            pid.getReligion().data[0].value = relCode
            pid.getReligion().data[1].value = patReligion
        }

        pid.getBirthPlace().value = patientBundle?.patientBirthPlace?.city
        pid.getCitizenship(0).data[0].value = patientBundle?.patientCitizenship?.code?.stream()?.flatMap { x -> x.coding.stream() }?.find { y -> y.system == "http://varian.com/fhir/CodeSystem/patient-citizenship" }?.with { it?.code }
        pid.getCitizenship(0).data[1].value = patientBundle?.patientCitizenship?.code?.stream()?.flatMap { x -> x.coding.stream() }?.find { y -> y.system == "http://varian.com/fhir/CodeSystem/patient-citizenship" }?.with { it?.display }

        def patCountryCode = patientBundle?.patientBirthPlace?.country
        if (patCountryCode != null) {
            def patCountry = getCountryValue(patCountryCode)
            pid.getNationality().data[0].value = patientBundle?.patientBirthPlace?.country
            pid.getNationality().data[1].value = patCountry
        }

        def hispanicOrLatinoValue = getEthnicGroupValue("Hispanic or Latino")
        def notHispanicOrLatinoValue = getEthnicGroupValue("Not Hispanic or Latino")
        def patientDoesNotKnowValue = getEthnicGroupValue("Patient does not know")
        def patientAskedButHasNotProvidedValue = getEthnicGroupValue("Patient asked, but has not provided")
        def capturingProhibitedByStateLawValue = getEthnicGroupValue("Capturing prohibited by state law")
        def usCoreEthnicity = patientBundle?.usCoreEthnicity?.text.toString().replaceAll("[()\\[\\]]", "")
        switch (usCoreEthnicity) {

            case "Hispanic or Latino":
                pid.getEthnicGroup(0).data[0].value = hispanicOrLatinoValue
                pid.getEthnicGroup(0).data[1].value = patientBundle?.usCoreEthnicity[0]?.text
                break
            case "Not Hispanic or Latino":
                pid.getEthnicGroup(0).data[0].value = notHispanicOrLatinoValue
                pid.getEthnicGroup(0).data[1].value = patientBundle?.usCoreEthnicity[0]?.text
                break
            case "Patient does not know":
                pid.getEthnicGroup(0).data[0].value = patientDoesNotKnowValue
                pid.getEthnicGroup(0).data[1].value = patientBundle?.usCoreEthnicity[0]?.text
                break
            case "Patient asked, but has not provided":
                pid.getEthnicGroup(0).data[0].value = patientAskedButHasNotProvidedValue
                pid.getEthnicGroup(0).data[1].value = patientBundle?.usCoreEthnicity[0]?.text
                break
            case "Capturing prohibited by state law":
                pid.getEthnicGroup(0).data[0].value = capturingProhibitedByStateLawValue
                pid.getEthnicGroup(0).data[1].value = patientBundle?.usCoreEthnicity[0]?.text
                break
            default:
                break
        }

        if (patientUID != "") {
            def cxAlternatePatientId = pid.insertAlternatePatientIDPID(0)
            cxAlternatePatientId.IDNumber.value = patientUID
            cxAlternatePatientId.identifierTypeCode.value = "UID"
        }

        def officialName = patientBundle?.name?.find { it?.use?.toString()?.replaceAll("[()\\[\\]]", "") == "OFFICIAL" }
        def maidenName = patientBundle?.name?.find { it?.use?.toString()?.replaceAll("[()\\[\\]]", "") == "MAIDEN" }

        if (officialName != null && !officialName.isEmpty()) {
            def patientName = pid.getPatientName(0)
            patientName.secondAndFurtherGivenNamesOrInitialsThereof.value = officialName?.given[1]?.toString()
            patientName.data[3].value = officialName?.suffix[0]?.toString()
            patientName.familyName.fn1_Surname.value = officialName?.family
            patientName.givenName.value = officialName?.given[0]?.toString()
            patientName.data[6].value = "D"
        }

        if ((maidenName != null && !maidenName.isEmpty()) && pid.getPatientName(0) != null) {
            def patientName = pid.getPatientName(1)
            patientName.familyName.fn1_Surname.value = maidenName.given[0]?.toString()
            patientName.givenName.value = null
            patientName.data[6].value = "M"
        }


        def birthdate = patientBundle?.birthDate
        if (birthdate != null && birthdate != "") {
            pid.dateTimeOfBirth.time.value = birthdate.format("YYYYMMddHHmmss")
        }

        if (patientBundle?.gender != null) {
            def gender = patientBundle?.gender
            if (gender != null) {
                pid.administrativeSex.value = gender.toString().charAt(0).toUpperCase()
            }
        }
        cloverLogger.log(2, "PID is mapping is done.", messageMetaData)
    }

    def setPV1Segment() {
        cloverLogger.log(2, "Setting the PV1 segment..", messageMetaData)
        def pv1 = siu_out.getPATIENT().getPV1()
        def ptClassValue = patientBundle?.patientClass?.coding?.find { it.system == "http://varian.com/fhir/CodeSystem/patient-class" }?.with { it.code }
        def ptClassEncode = (ptClassValue == "In Patient") ? 'I' : 'O'
        pv1.setIDPV1.value = "1"
        pv1.patientClass.value = ptClassEncode
        pv1.assignedPatientLocation.facility.namespaceID.value = patientBundle?.managingOrganization?.display
        pv1.assignedPatientLocation.room.value = patientBundle?.patientLocationDetails?.roomNumber
        //pv1.assignedPatientLocation.pointOfCare.value = locationBundle.name
        def orgSerList = getParticipantDetails(careTeamBundle, "default-service-organization", "http://varian.com/fhir/CodeSystem/careteam-participant-role")
        def deptBundle = bundleUtility.getOrganizations(bundle)
        for (def i = 0; i < orgSerList?.size; i++) {
            pv1.assignedPatientLocation.building.value = deptBundle?.find { it?.name == orgSerList[i] }?.with { it?.identifier?.find { it?.system == "http://varian.com/fhir/identifier/Organization/dept/Id" }?.with { it?.value } }
        }

        pv1.reAdmissionIndicator.value = patientBundle?.patientStatus?.coding?.find { it.system == "http://varian.com/fhir/CodeSystem/patient-status" }?.with { it.code }
        def admissionDate = patientBundle?.patientLocationDetails?.admissionDate?.value
        if (admissionDate != null && admissionDate != "") {
            pv1.getAdmitDateTime().time.value = admissionDate.format("YYYYMMddHHmmss")
        }

        def primaryOncoSerList = getParticipantDetails(careTeamBundle, "primary-oncologist", "http://varian.com/fhir/CodeSystem/careteam-participant-role")
        def oncoSerList = getParticipantDetails(careTeamBundle, "oncologist", "http://varian.com/fhir/CodeSystem/careteam-participant-role")
        def primaryRefSerList = getParticipantDetails(careTeamBundle, "primary-referring-physician", "http://varian.com/fhir/CodeSystem/careteam-participant-role")
        def refSerList = getParticipantDetails(careTeamBundle, "referring-physician", "http://varian.com/fhir/CodeSystem/careteam-participant-role")
        def oncoDetail = bundleUtility.getPractitioners(bundle)

        populateAttendingDoctors(primaryOncoSerList, oncoDetail, pv1, 0)
        populateAttendingDoctors(oncoSerList, oncoDetail, pv1, primaryOncoSerList?.size)
        populateReferringDoctors(primaryRefSerList, oncoDetail, pv1, 0)
        populateReferringDoctors(refSerList, oncoDetail, pv1, primaryRefSerList?.size)
        cloverLogger.log(2, "PID setting is done.", messageMetaData)
    }

    def populateAttendingDoctors(docList, oncoBundle, pv, position) {
        for (def i = 0; i < docList?.size; i++) {
            if (oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }) {
                def attendingDoctor = pv.getAttendingDoctor(position++)
                attendingDoctor.data[0].value = oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }?.with { it?.identifier[0]?.find { it?.system == "http://varian.com/fhir/identifier/Practitioner/Id" }?.with { it?.value } }
                attendingDoctor.data[1].data[0].value = oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }?.with { it?.name[1].family }
                attendingDoctor.data[2].value = oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }?.with { it?.name[1].given[0].toString() }
                attendingDoctor.data[9].value = "L"
                attendingDoctor.data[12].value = "DN"
            }
        }
    }

    def populateReferringDoctors(docList, oncoBundle, pv, position) {
        for (def i = 0; i < docList?.size; i++) {
            if (oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }) {
                def referringDoctor = pv.getReferringDoctor(position++)
                referringDoctor.data[0].value = oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }?.with { it?.identifier[0]?.find { it?.system == "http://varian.com/fhir/identifier/Practitioner/Id" }?.with { it?.value } }
                referringDoctor.data[1].data[0].value = oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }?.with { it?.name[1].family }
                referringDoctor.data[2].value = oncoBundle?.find { it?.name[0]?.given[0]?.toString() == docList[i] }?.with { it?.name[1].given[0].toString() }
                referringDoctor.data[9].value = null
                referringDoctor.data[12].value = "D"
            }
        }
    }

    def static getParticipantDetails(careTeam, roleCode, roleSystem) {
        return careTeam?.participant?.findAll {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == roleCode && y.system == roleSystem
            }
        }?.with { it.member.display }
    }

    def setAISSegment() {
        cloverLogger.log(2, "Setting the AIS segment...", messageMetaData)
        deptId = getParticipant("HealthcareService")
        def servicetype = getServiceType(appointmentBundle)[0]
        def getServiceCategory = getServiceCategory(appointmentBundle)[0]
        def aisseg = siu_out.getRESOURCES().getSERVICE().getAIS()
        aisseg.ais1_SetIDAIS.value = "1"
        aisseg.ais2_SegmentActionCode.value = "A"

        def departmentId = departmentBundle?.identifier?.find { it.system == "http://varian.com/fhir/identifier/Organization/dept/Id" }?.
                with { it.value }
        def hospitalName = departmentBundle?.partOf?.display
        def activityName = getServiceType(appointmentBundle)[0]

        def activityOutValue = [:]
        activityOutValue.put('OutValue', activityName)
        activityOutValue.put('HospitalName', hospitalName)
        activityOutValue.put('DepartmentId', departmentId)
        def activityCodeList = SqliteUtility.getValues(localConnection, "ActivityCode", "InValue", activityOutValue)

        if (activityCodeList != null && activityCodeList?.size() > 0) {
            aisseg.ais3_UniversalServiceIdentifier.identifier.value = activityCodeList[0].toString()
        } else {
            aisseg.ais3_UniversalServiceIdentifier.identifier.value = servicetype
        }
        aisseg.ais3_UniversalServiceIdentifier.text.value = servicetype
        aisseg.ais3_UniversalServiceIdentifier.alternateIdentifier.value = servicetype
        aisseg.ais3_UniversalServiceIdentifier.alternateText.value = getServiceCategory
        aisseg.ais4_StartDateTime.time.value = getAppointmentStartDateTime()
        aisseg.ais7_Duration.value = appointmentBundle?.minutesDuration
        aisseg.ais8_DurationUnits.identifier.value = "M"

        def ce = aisseg.insertAis12_FillerSupplementalServiceInformation(0)
        ce.alternateIdentifier.value = departmentId
        ce.alternateText.value = getHospitalNameFromDeptBundle()
    }


    def getAppointmentStartDateTime() {
        def startDateTime = appointmentBundle?.start
        if (startDateTime != null && startDateTime != "") {
            return startDateTime.format("YYYYMMddHHmmss")
        }

    }

    def getAppointmentEndDateTime() {
        def endDateTime = appointmentBundle?.end
        if (endDateTime != null && endDateTime != "") {
            return endDateTime.format("YYYYMMddHHmmss")
        }

    }

    def getAppointmentExtension(code) {
        def extension = appointmentBundle?.statusInAria?.coding?.find {
            it.system == code
        }

        return extension?.code
    }

    def getPractionerId() {
        def identifier = practitionerBundle?.identifier?.find {
            it.system == "http://varian.com/fhir/identifier/Practitioner/Id"
        }

        return identifier?.value
    }

    def getDeviceId() {
        def identifier = deviceBundle?.identifier?.find {
            it.system == "http://varian.com/fhir/identifier/Device/Id"
        }
        return identifier?.value

    }

    def getLocationId() {
        def identifier = locationBundle?.identifier?.find {
            it.system == "http://varian.com/fhir/identifier/Location/Id"
        }
        return identifier?.value

    }

    def getAppointmentIdentifier(code) {
        def identifier = appointmentBundle?.identifier?.find {
            it.system == code
        }
        return identifier?.value

    }

    def getLocationType(code) {
        def type = locationBundle?.type?.find {
            it.coding.find { y ->
                y.system == code
            }
        }
        return type?.text
    }

    def getParticipantSer(codevalue, cnt) {
        def partiSer = null
        def partistr = appointmentBundle?.participant?.findAll {
            it.type.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.display == codevalue
            }
        }?.actor?.reference[cnt]

        if (partistr != null) {
            partiSer = partistr.substring(partistr.lastIndexOf('-') + 1)
        }
        return partiSer
    }

    def getServiceType(bundleout) {
        return bundleout.serviceType[0].coding.display
    }

    def getServiceCategory(bundleout) {
        return bundleout.serviceCategory[0].coding.display
    }

    def getHospitalNameFromDeptBundle() {
        return departmentBundle?.partOf?.display
    }

    def getParticipant(codevalue) {
        return appointmentBundle?.participant?.find {
            it.type.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.display == codevalue
            }
        }?.actor?.display
    }

    def getActivityType() {
        def activityType = null
        if (activityDefinition != null && activityDefinition.schedulable != null) {
            if (activityDefinition.schedulable.booleanValue()) {
                activityType = SCHEDULABLE_ACTIVITY
            } else {
                activityType = NON_SCHEDULABLE_ACTIVITY
            }
        }
        return activityType
    }
}