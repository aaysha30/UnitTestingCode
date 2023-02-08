package master.inbound.interfaces.parkpatient

def class V2AdtExtensions {
    static {
        ca.uhn.hl7v2.model.v251.segment.PID.metaClass.toParkPatient = { ArrayList<com.varian.mappercore.helper.sqlite.PatientMatching> pkmCfg,
                                                                        String location, String interfaceId, String siteId ->

            final def patient = new com.varian.mappercore.client.interfaceapi.dto.Patient()
            if(delegate.patientName.toList().size != 0) {
                patient.firstName = delegate.patientName?.first().givenName?.value
                patient.lastName = delegate.patientName?.first().familyName?.surname?.value
                patient.middleName = delegate.patientName?.first().secondAndFurtherGivenNamesOrInitialsThereof?.value
            }

            if (delegate.dateTimeOfBirth?.time?.valueAsDate?.time != null) {
                patient.dob = new java.sql.Timestamp(delegate.dateTimeOfBirth.time.valueAsDate.time)
            }

            if(delegate.motherSMaidenName.toList().size !=0) {
                patient.maidenName = [delegate.motherSMaidenName?.first().familyName?.surname?.value,
                                      delegate.motherSMaidenName?.first().givenName?.value].findAll( { it != null })?.join()
            }

            patient.sex = delegate.administrativeSex?.value
            patient.maritalStatus = delegate.maritalStatus?.identifier?.value
            patient.location = location
            patient.interfaceId = interfaceId
            patient.siteId = siteId
            pkmCfg.findAll({ final it -> it.segment == "PID" })
                    ?.forEach({ pkmPID ->
                        final def fields = delegate.getField(pkmPID.field as int)
                        fields?.findAll({ final it -> it instanceof ca.uhn.hl7v2.model.v251.datatype.CX })
                                ?.toList()?.stream()
                                ?.map({ i2 -> i2 as ca.uhn.hl7v2.model.v251.datatype.CX })
                                ?.findAll({ i3 -> (isNullOrEmpty(i3.identifierTypeCode?.value) && isNullOrEmpty(pkmPID.identifierValue)) || i3.identifierTypeCode?.value == pkmPID.identifierValue })
                                ?.forEach({ cx ->
                                    final def patId = new com.varian.mappercore.client.interfaceapi.dto.PatientIdentifier()
                                    patId.idType = pkmPID.ariaId
                                    patId.idValue = cx.cx1_IDNumber.value
                                    patient.patientIdentifiers = patient.patientIdentifiers ?: new ArrayList<com.varian.mappercore.client.interfaceapi.dto.PatientIdentifier>()
                                    if (pkmPID.ariaId != null && cx.cx1_IDNumber.value != null)
                                        patient.patientIdentifiers.add(patId)

                                    switch (pkmPID.ariaId) {
                                        case "ARIAID1":
                                            patient.id1 = cx.cx1_IDNumber.value
                                            break
                                        case "ARIAID2":
                                            patient.id2 = cx.cx1_IDNumber.value
                                            break
                                    }
                                }
                                )

                        fields?.findAll({ final it -> it instanceof ca.uhn.hl7v2.model.v251.datatype.ST })
                                ?.toList()?.stream()
                                ?.map({ i2 -> i2 as ca.uhn.hl7v2.model.v251.datatype.ST })
                                ?.forEach({ st ->
                                    final def patId = new com.varian.mappercore.client.interfaceapi.dto.PatientIdentifier()
                                    patId.idType = pkmPID.ariaId
                                    patId.idValue = st.getValue()
                                    patient.patientIdentifiers = patient.patientIdentifiers ?: new ArrayList<com.varian.mappercore.client.interfaceapi.dto.PatientIdentifier>()
                                    if (pkmPID.ariaId != null && st != null)
                                        patient.patientIdentifiers.add(patId)

                                    switch (pkmPID.ariaId) {
                                        case "ARIAID1":
                                            patient.id1 = st.getValue()
                                            break
                                        case "ARIAID2":
                                            patient.id2 = st.getValue()
                                            break
                                    }
                                })
                    })
            patient
        }

        ca.uhn.hl7v2.model.v251.segment.MRG.metaClass.toParkPatient = { ArrayList<com.varian.mappercore.helper.sqlite.PatientMatching> pkmCfg,
                                                                        ca.uhn.hl7v2.model.v251.segment.PID pid,
                                                                        String location, String interfaceId, String siteId ->

                final def patient = new com.varian.mappercore.client.interfaceapi.dto.Patient()

            if (delegate.priorPatientName.toList().size != 0) {
                patient.firstName = delegate.priorPatientName?.first()?.givenName?.value
                        ?: pid.patientName?.first()?.givenName?.value
                patient.lastName = delegate.priorPatientName?.first()?.familyName?.surname?.value
                        ?: pid.patientName?.first().familyName?.surname?.value
                patient.middleName = delegate.priorPatientName?.first()?.secondAndFurtherGivenNamesOrInitialsThereof?.value
                        ?: pid.patientName?.first()?.secondAndFurtherGivenNamesOrInitialsThereof?.value
            } else if(pid.patientName.toList().size != 0){
                patient.firstName = pid.patientName?.first()?.givenName?.value
                patient.lastName = pid.patientName?.first()?.familyName?.surname?.value
                patient.middleName = pid.patientName?.first()?.secondAndFurtherGivenNamesOrInitialsThereof?.value
            }

                if (pid.dateTimeOfBirth?.time?.valueAsDate?.time != null) {
                    patient.dob = new java.sql.Timestamp(pid.dateTimeOfBirth.time.valueAsDate.time)
                }

                if(pid.motherSMaidenName.toList().size !=0) {
                    patient.maidenName = [pid.motherSMaidenName?.first().familyName?.surname?.value,
                                          pid.motherSMaidenName?.first().givenName?.value].findAll( { it != null })?.join()
                }

                patient.sex = pid.administrativeSex?.value
                patient.maritalStatus = pid.maritalStatus?.identifier?.value
                patient.location = location
                patient.interfaceId = interfaceId
                patient.siteId = siteId

                pkmCfg.findAll({ final it -> it.segment == "MRG" })
                        .forEach({ pkmMRG ->
                            final def fields = delegate.getField(pkmMRG.field as int)
                            fields?.toList()?.stream()?.map({ i2 -> i2 as ca.uhn.hl7v2.model.v251.datatype.CX })
                                    ?.findAll({ i3 -> (isNullOrEmpty(i3.identifierTypeCode?.value) && isNullOrEmpty(pkmMRG.identifierValue)) || i3.identifierTypeCode?.value == pkmMRG.identifierValue })
                                    ?.forEach({ cx ->
                                        final def patId = new com.varian.mappercore.client.interfaceapi.dto.PatientIdentifier()
                                        patient.patientIdentifiers = patient.patientIdentifiers ?: new ArrayList<com.varian.mappercore.client.interfaceapi.dto.PatientIdentifier>()
                                        patId.idType = pkmMRG.ariaId
                                        patId.idValue = cx.cx1_IDNumber.value
                                        if (pkmMRG.ariaId != null && cx.cx1_IDNumber.value != null)
                                            patient.patientIdentifiers.add(patId)

                                        switch (pkmMRG.ariaId) {
                                            case "ARIAID1":
                                                patient.id1 = cx.cx1_IDNumber.value
                                                break
                                            case "ARIAID2":
                                                patient.id2 = cx.cx1_IDNumber.value
                                                break
                                        }
                                    }
                                    )
                        })
                patient
        }

        com.varian.mappercore.client.interfaceapi.dto.Patient.metaClass.genLinkedMessage = {
            String rawMessage, String msgControlId, String msgType, String msgEvent, String traceId, String interfaceId ->

                def msg = new com.varian.mappercore.client.interfaceapi.dto.FilteredMessage()
                msg.hl7Base64 = rawMessage.bytes.encodeBase64().toString()
                msg.interfaceId = interfaceId
                msg.msgControlId = msgControlId
                msg.traceId = traceId
                msg.msgType = msgType
                msg.msgEvent = msgEvent
                msg.patientRecordSer = delegate.patientRecordSer
                msg.siteId = delegate.siteId

                msg
        }
    }

}
