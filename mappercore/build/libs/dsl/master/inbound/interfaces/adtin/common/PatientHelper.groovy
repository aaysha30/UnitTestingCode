package master.inbound.interfaces.adtin.common

def class PatientHelper {
    def static client
    def static bundleUtility
    def static IN_PATIENT = "In Patient"
    def static OUT_PATIENT = "Out Patient"
    def static ACTIVE_NULL_BIRTH_DATE = "ActiveNullBirthDate"
    def static ACTIVE_NULL_DECEASED_DATE = "ActiveNullDeceasedDate"
    def static VALUESET_TRANSPORT_CONTACT_URL = "http://varian.com/fhir/ValueSet/patient-transportation-contact"
    def static VALUESET_TRANSPORT_CONTACT_SYSTEM ="http://varian.com/fhir/CodeSystem/patient-transportationcontact"

    static def map(input_patient, domainPatient, contactSnapshotUpdateMode, interfaceUser, outcome) {
        mapOrganization(domainPatient, input_patient)
        mapName(domainPatient, input_patient)
        mapTelcom(domainPatient, input_patient)
        mapMothersMaidenName(domainPatient, input_patient)
        mapPatientGender(domainPatient, input_patient)
        mapRace(domainPatient, input_patient)
        mapEthnicity(domainPatient, input_patient)
        mapPatientCitizenship(domainPatient, input_patient)
        mapPatientBirthPlace(domainPatient, input_patient)
        mapPatientReligion(domainPatient, input_patient)
        mapPatientStatus(domainPatient, input_patient)
        mapPatientBirthDate(domainPatient, input_patient)
        mapPatientDeathReason(domainPatient, input_patient)
        mapPatientAddress(domainPatient, input_patient)
        mapPatientContact(domainPatient, input_patient, contactSnapshotUpdateMode, interfaceUser, outcome)
        mapPatientLanguage(domainPatient, input_patient)
        mapMaritalStatus(domainPatient, input_patient)
        mapCustomAttributes(domainPatient, input_patient)
        mapAutopsyDetails(domainPatient, input_patient)
        mapDeceasedDateTime(domainPatient, input_patient)
        mapSexOrientation(domainPatient, input_patient)
        mapGenderIdentity(domainPatient, input_patient)
        mapClinicalTrial(domainPatient, input_patient)
        mapRetirementDetails(domainPatient, input_patient)
        mapOccupation(domainPatient, input_patient)
        mapMobilePhoneProvider(domainPatient, input_patient)
    }

    static def mapMobilePhoneProvider(domainPatient, input_patient) {
        domainPatient.mobilePhoneProvider = getValue(input_patient.mobilePhoneProvider, domainPatient.mobilePhoneProvider)
    }

    static def mapOccupation(domainPatient, input_patient) {
        domainPatient.occupation = getValue(input_patient.occupation, domainPatient.occupation)
    }

    static def mapRetirementDetails(domainPatient, input_patient) {
        if (isNullOrEmpty(domainPatient.retirementDetails)) {
            domainPatient.retirementDetails = new com.varian.fhir.resources.Patient.RetirementDetailsComponent()
        }

        domainPatient.retirementDetails.retirementNote = getValue(input_patient.retirementDetails?.retirementNote, domainPatient.retirementDetails.retirementNote)
        domainPatient.retirementDetails.retirementReason = getValue(input_patient.retirementDetails?.retirementReason, domainPatient.retirementDetails.retirementReason)
        if (input_patient.retirementDetails?.retirementDate?.extensionFirstRep?.value?.value == ResourceUtil.ACTIVE_NULL_LITERAL) {
            domainPatient.retirementDetails.retirementDate = null
        } else if (input_patient.retirementDetails?.retirementDate?.hasValue()) {
            domainPatient.retirementDetails.retirementDate = input_patient.retirementDetails.retirementDate
        }
    }

    static def mapDeceasedDateTime(domainPatient, input_patient) {
        def inputValue = input_patient.deceased
        if (isNullOrEmpty(inputValue)) {
            inputValue = input_patient.getExtensionByUrl(ACTIVE_NULL_DECEASED_DATE)?.value
        }
        domainPatient.deceased = getValue(inputValue, domainPatient.deceased)
    }

    static def mapAutopsyDetails(domainPatient, input_patient) {
        if (isNullOrEmpty(domainPatient.autopsyDetails)) {
            domainPatient.autopsyDetails = new com.varian.fhir.resources.Patient.AutopsyDetailsComponent()
        }

        if (!isNullOrEmpty(input_patient.autopsyDetails)) {
            if (!isNullOrEmpty(input_patient.autopsyDetails.status)
                    && !isNullOrEmpty(input_patient.autopsyDetails.status.text)) {
                domainPatient.autopsyDetails.status = input_patient.autopsyDetails.status
            }
            if (!isNullOrEmpty(input_patient.autopsyDetails.outcome?.text)) {
                domainPatient.autopsyDetails.outcome = input_patient.autopsyDetails.outcome
            }
        }
    }

    static def mapSexOrientation(domainPatient, input_patient) {
        def domainCode = domainPatient.sexOrientation?.coding?.find { it.system == input_patient.sexOrientation?.codingFirstRep?.system }?.code
        input_patient.sexOrientation?.codingFirstRep?.code = getValue(input_patient.sexOrientation?.codingFirstRep?.code, domainCode)
        domainPatient.sexOrientation = input_patient.sexOrientation
    }

    static def mapGenderIdentity(domainPatient, input_patient) {
        def domainCode = domainPatient.genderIdentity?.coding?.find { it.system == input_patient.genderIdentity?.codingFirstRep?.system }?.code
        input_patient.genderIdentity?.codingFirstRep?.code = getValue(input_patient.genderIdentity?.codingFirstRep?.code, domainCode)
        domainPatient.genderIdentity = input_patient.genderIdentity
    }

    static def mapClinicalTrial(domainPatient, input_patient) {
        if (!isNullOrEmpty(input_patient.patientClinicalTrial)) {
            domainPatient.patientClinicalTrial = input_patient.patientClinicalTrial
        }
    }

    static def mapCustomAttributes(domainPatient, input_patient) {
        input_patient.customAttributes?.each { attribute ->
            def domainAttribute = domainPatient.customAttributes?.find { it.code.codingFirstRep.code == attribute.code.codingFirstRep.code }
            if (!isNullOrEmpty(domainAttribute)) {
                domainAttribute.value = attribute.value
            } else {
                if (isNullOrEmpty(domainPatient.customAttributes)) {
                    domainPatient.customAttributes = []
                }
                domainPatient.customAttributes.add(attribute)
            }
        }
    }

    static def mapOrganization(domainPatient, input_patient) {
        if (input_patient.managingOrganization.reference != null) {
            domainPatient.managingOrganization.id = input_patient.managingOrganization.id
            domainPatient.managingOrganization.reference = input_patient.managingOrganization.reference
        }
    }

    static def mapTelcom(domainPatient, input_patient) {
        for (telcom in input_patient.telecom) {
            def contactPoint = domainPatient.telecom.find { it.system == telcom.system && it.use == telcom.use }
            if (contactPoint == null) {
                domainPatient.telecom.add(telcom)
            } else {
                contactPoint.value = getValue(telcom.value, contactPoint.value)
            }
        }
    }

    static def assignDisplayNameAndMaidenName(displayName, maidenName, legalName) {
        displayName?.each { it.use = HumanName.NameUse.OFFICIAL }
        maidenName?.each { it.use = HumanName.NameUse.MAIDEN }
        legalName?.each { it.use = HumanName.NameUse.TEMP }
    }

    static def mapName(domainPatient, input_patient) {
        assignMultipleName(input_patient)
        domainPatient.name.removeIf { it?.use == HumanName.NameUse.TEMP }
        for (name in input_patient.name) {
            if (name.use == HumanName.NameUse.TEMP) {
                domainPatient.name.add(name)
            } else {
                def humanName = domainPatient.name.find { it?.use == name.use }
                if (humanName == null) {
                    domainPatient.name.add(name)
                } else {
                    humanName.family = getValue(name.family, humanName.family)
                    humanName.suffix[0] = getValue(name.suffix.find { true }, humanName.suffix.find { true })
                    if (name.givenAsSingleString.contains("N_U_L_L")) {
                        humanName.given = []
                    } else {
                        if (!isNullOrEmpty(name.givenAsSingleString)) {
                            humanName.given = name.given
                        }
                    }
                }
            }
        }
    }

    static def assignMultipleName(input_patient) {
        def names = input_patient.name
        input_patient.name = []
        def displayName = getDisplayName(names)
        if (displayName != null) {
            input_patient.name.add(displayName)
        }
        def maidenName = getMaidenName(names)
        if (maidenName != null) {
            input_patient.name.add(maidenName)
        }

        names.each {
            if (!(it.family == displayName?.family && it.givenAsSingleString == displayName?.givenAsSingleString &&
                    it.use == HumanName.NameUse.OFFICIAL)
                    && !(it.family == maidenName?.family && it.givenAsSingleString == maidenName?.givenAsSingleString
                    && it.use == HumanName.NameUse.MAIDEN)) {
                if (it.use == HumanName.NameUse.MAIDEN || it.use == HumanName.NameUse.OFFICIAL) {
                    it.use = HumanName.NameUse.TEMP
                }
                input_patient.name.add(it)
            }
        }
    }

    static def getDisplayName(names) {
        def addDuplicate = false
        def displayName
        if (names.size == 1 && names[0].use != HumanName.NameUse.MAIDEN) {
            displayName = names[0]
            if (displayName.use == HumanName.NameUse.USUAL || displayName.use == HumanName.NameUse.OLD) {
                addDuplicate = true
            }
        } else {
            def name = names.findAll { !isNullOrEmpty(it.family) && it.use == HumanName.NameUse.OFFICIAL }
            if (name == null || name.size == 0) {
                name = names.findAll { !isNullOrEmpty(it.family) && it.use == HumanName.NameUse.TEMP }
            }
            if (name == null || name.size == 0) {
                name = names.findAll {
                    (!isNullOrEmpty(it.family) && it.use != HumanName.NameUse.OLD
                            && it.use != HumanName.NameUse.MAIDEN && it.use != HumanName.NameUse.USUAL)
                }
            }

            if (name == null || name.size == 0) {
                name = names.findAll { !isNullOrEmpty(it.family) && it.use != HumanName.NameUse.MAIDEN }
                addDuplicate = true
            }

            if (name?.size == 1) {
                displayName = name[0]
            } else {
                displayName = getNameBasedOnPriority(name)
            }
        }
        if (addDuplicate && displayName != null) {
            def hname = getDuplicateName(displayName)
            names.add(hname)
        }
        displayName?.extension = null
        displayName?.use = HumanName.NameUse.OFFICIAL

        return displayName
    }

    static def getMaidenName(names) {
        def maidenName = null
        def addDuplicate = false
        def maidenNames = names.findAll { !isNullOrEmpty(it.family) && it.use == HumanName.NameUse.MAIDEN }
        if (maidenNames == null || maidenNames.size == 0) {
            maidenNames = names.findAll { !isNullOrEmpty(it.family) && it.use == HumanName.NameUse.TEMP }
            addDuplicate = true
        }

        if (maidenNames?.size == 1) {
            maidenName = maidenNames[0]
        } else {
            maidenName = getNameBasedOnPriority(maidenName)
        }
        if (addDuplicate && maidenName != null) {
            def hname = getDuplicateName(maidenName)
            names.add(hname)
        }
        maidenName?.extension = null
        maidenName?.use = HumanName.NameUse.MAIDEN
        return maidenName
    }

    static def getNameBasedOnPriority(name) {
        if (name?.size == 1)
            return name[0]

        def foundName
        foundName = name?.find { it.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation")?.value?.code == "IDE" }
        if (foundName == null) {
            foundName = name?.find { it.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation")?.value?.code == "SYL" }
        }
        if (foundName == null) {
            foundName = name?.find { it.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/iso21090-EN-representation")?.value?.code == "ABC" }
        }

        if (foundName == null && name?.size >= 1) {
            foundName = name[0]
        }
        return foundName
    }

    static def getDuplicateName(name) {
        def hname = new HumanName()
        hname.extension = name.extension
        hname.use = name.use
        hname.family = name.family
        hname.given = name.given
        hname.suffix = name.suffix
        hname.prefix = name.prefix
        return hname
    }

    def static mapIdentifiers(patient, patientDomain, patientDisallowUpdateKeys, outcome) {
        if (patientDomain != null) {
            for (patientIdentifier in patient.identifier) {
                def patientDomainIdentifier = patientDomain.identifier.find { it.system == patientIdentifier.system }
                if (patientDomainIdentifier == null) {
                    patientDomain.identifier.add(patientIdentifier)
                } else if (patientIdentifier.value != null) {
                    if (!patientDisallowUpdateKeys.contains(patientIdentifier.system)) {
                        patientDomainIdentifier.value = patientIdentifier.value
                    } else {
                        outcome.addWarning(String.format(ResponseCode.IGNORE_PATIENT_IDENTIFIER_UPDATE.value, patientIdentifier.system))
                    }
                }
            }
        }
    }

    static def mapMothersMaidenName(domainPatient, input_patient) {
        domainPatient.patientMothersMaidenName = getValue(input_patient.patientMothersMaidenName, domainPatient.patientMothersMaidenName)
    }

    static def mapRace(domainPatient, input_patient) {
        domainPatient.usCoreRace = isActiveNullList(input_patient.usCoreRace, input_patient.usCoreRace?.find { true }?.text?.value, domainPatient.usCoreRace)
    }

    static def mapEthnicity(domainPatient, input_patient) {
        domainPatient.usCoreEthnicity = isActiveNullList(input_patient.usCoreEthnicity, input_patient.usCoreEthnicity?.find { true }?.text?.value, domainPatient.usCoreEthnicity)
    }

    static def mapPatientCitizenship(domainPatient, input_patient) {
        domainPatient.patientCitizenship = isActiveNullList(input_patient.patientCitizenship, input_patient.patientCitizenship?.find { true }?.code?.codingFirstRep?.code, domainPatient.patientCitizenship)
    }

    static def mapPatientBirthPlace(domainPatient, input_patient) {
        if (isNullOrEmpty(domainPatient.patientBirthPlace)) {
            domainPatient.patientBirthPlace = new com.varian.fhir.resources.Address()
        }

        domainPatient.patientBirthPlace.city = getValue(input_patient.patientBirthPlace?.city, domainPatient.patientBirthPlace?.city)
        domainPatient.patientBirthPlace.country = getValue(input_patient.patientBirthPlace?.country, domainPatient.patientBirthPlace?.country)
    }

    static def mapPatientReligion(domainPatient, input_patient) {
        domainPatient.patientReligion = isActiveNullList(input_patient.patientReligion, input_patient.patientReligion?.find { true }?.codingFirstRep?.code, domainPatient.patientReligion)
    }

    static def mapPatientStatus(domainPatient, input_patient) {
        if (input_patient.patientStatus?.codingFirstRep?.system == "default") {
            input_patient.patientStatus?.codingFirstRep?.system = ""
            input_patient.patientStatus?.codingFirstRep?.code = ""
        }
        domainPatient.patientStatus?.codingFirstRep?.code = getValue(input_patient.patientStatus?.codingFirstRep?.code, domainPatient.patientStatus?.codingFirstRep?.code)
    }

    static def mapPatientGender(domainPatient, input_patient) {
        if (isNullOrEmpty(domainPatient.personGender)) {
            domainPatient.personGender = new CodeableConcept()
        }
        def inputCode = input_patient.personGender?.codingFirstRep?.code
        if (inputCode == ResourceUtil.ACTIVE_NULL_LITERAL) {
            domainPatient.personGender = null
            domainPatient.gender = null
        } else if (!isNullOrEmpty(inputCode)) {
            domainPatient.gender = null
            domainPatient.personGender = input_patient.personGender
        }
    }

    static def mapPatientBirthDate(domainPatient, input_patient) {
        def inputValue = input_patient.birthDate
        if (isNullOrEmpty(inputValue)) {
            inputValue = input_patient.getExtensionByUrl(ACTIVE_NULL_BIRTH_DATE)?.value
        }
        domainPatient.birthDate = getValue(inputValue, domainPatient.birthDate)
    }

    static def mapPatientDeathReason(domainPatient, input_patient) {
        if (isNullOrEmpty(domainPatient.patientDeathReason)) {
            domainPatient.patientDeathReason = new CodeableConcept()
        }

        domainPatient.patientDeathReason.codingFirstRep.code = getValue(input_patient.patientDeathReason?.codingFirstRep?.code, domainPatient.patientDeathReason.codingFirstRep.code)
    }

    static def mapPatientAddress(domainPatient, input_patient) {
        def inputPatientHomeAddress = input_patient.address.find { it?.hasUse() && it.use.toCode() == "home" }
        def domainHomeAddress = domainPatient.address.find { it?.hasUse() && it.use.toCode() == "home" }
        def addresses = []
        if (inputPatientHomeAddress != null && domainHomeAddress == null) {
            addresses.add(inputPatientHomeAddress)
        } else if (domainHomeAddress != null && inputPatientHomeAddress == null) {
            addresses.add(domainHomeAddress)
        } else if(domainHomeAddress != null && inputPatientHomeAddress != null) {
            mapAddressFields(inputPatientHomeAddress, domainHomeAddress)
            addresses.add(domainHomeAddress)
        }

        def inputPatientLocal1Address = input_patient.address.find { it?.hasUse() && it.use.toCode() == "temp" && it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/isPrimary")?.value?.booleanValue() == true }
        def domainPatientLocal1Address = domainPatient.address.find { it?.hasUse() && it.use.toCode() == "temp" && it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/isPrimary")?.value?.booleanValue() == true }
        if (inputPatientLocal1Address != null && domainPatientLocal1Address == null) {
            addresses.add(inputPatientLocal1Address)
        } else if (domainPatientLocal1Address != null && inputPatientLocal1Address == null) {
            addresses.add(domainPatientLocal1Address)
        } else if(domainPatientLocal1Address != null && inputPatientLocal1Address != null) {
            mapAddressFields(inputPatientLocal1Address, domainPatientLocal1Address)
            addresses.add(domainPatientLocal1Address)
        }

        def inputPatientLocal2Address = input_patient.address.find { it?.hasUse() && it.use.toCode() == "temp" && it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/isPrimary")?.value?.booleanValue() == false }
        def domainPatientLocal2Address = domainPatient.address.find { it?.hasUse() && it.use.toCode() == "temp" && it.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/isPrimary")?.value?.booleanValue() == false }
        if (inputPatientLocal2Address != null && domainPatientLocal2Address == null) {
            addresses.add(inputPatientLocal2Address)
        } else if (domainPatientLocal2Address != null && inputPatientLocal2Address == null) {
            addresses.add(domainPatientLocal2Address)
        } else if(domainPatientLocal2Address != null && inputPatientLocal2Address != null) {
            mapAddressFields(inputPatientLocal2Address, domainPatientLocal2Address)
            addresses.add(domainPatientLocal2Address)
        }
        domainPatient.address = addresses
    }

    static def mapPatientContact(domainPatient, input_patient, snapshotUpdateMode, interfaceUser, outcome) {
        if (snapshotUpdateMode == "SnapshotAll") {
            //do not snapshot transport contacts
            domainPatient.contact.removeIf { it.relationshipFirstRep.codingFirstRep.code != "O" }
        } else if (snapshotUpdateMode == "SnapshotOwn") {
            domainPatient.contact.removeIf {
                def modifiedUser = it.extension.find { it.url == "http://varian.com/fhir/v1/StructureDefinition/lastModificationUser" }?.value?.reference?.replace('Practitioner/', '')
                //do not snapshot transport contacts
                modifiedUser == interfaceUser.idElement.idPart && it.relationshipFirstRep.codingFirstRep.code != "O"
            }
        }

        //filter invalid contacts
        filterInvalidContact(input_patient.contact, outcome, input_patient)

        def emergencyContacts = input_patient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "C" }
        def domainEmergencyContacts = domainPatient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "C" }
        def employerContacts = input_patient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "E" }
        def domainEmployerContacts = domainPatient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "E" }
        def transportContact = input_patient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "O" }
        def domainTransportContact = domainPatient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "O" }

        unmarkPrimaryAllButLast(emergencyContacts, domainEmergencyContacts)
        unmarkPrimaryAllButLast(employerContacts, domainEmployerContacts)
        unmarkPrimaryAllButLast(transportContact, domainTransportContact)

        input_patient.contact.forEach {
            //if contact found in database then update else add new contact in existing database contact list
            if (!isContactMatched(it, domainPatient, outcome)) {
                domainPatient.contact.add(it)
            }
        }
    }

    static def mapPatientLanguage(domainPatient, input_patient) {
        domainPatient.communicationFirstRep.language.codingFirstRep.code = getValue(input_patient.communicationFirstRep.language.codingFirstRep.code, domainPatient.communicationFirstRep.language.codingFirstRep.code)
    }

    static def mapMaritalStatus(domainPatient, input_patient) {
        if (isNullOrEmpty(domainPatient.maritalStatus)) {
            domainPatient.maritalStatus = new CodeableConcept()
        }
        def inputCode = input_patient.maritalStatus?.codingFirstRep?.code
        if (inputCode == ResourceUtil.ACTIVE_NULL_LITERAL) {
            domainPatient.maritalStatus = null
        } else if (!isNullOrEmpty(inputCode)) {
            domainPatient.maritalStatus = input_patient.maritalStatus
        }
    }

    def static mapAdvancePatientClass(patient, patientDomain, patientClassValues, domainAccounts, inputAccount, defaultRoomNumber, outcome) {
        def futurePatientClass = AccountHelper.getFutureAccountPatientClass(domainAccounts, inputAccount)
        if (patient.patientClass?.codingFirstRep?.code == null) {
            patient.patientClass = new CodeableConcept()
            patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
        }
        patient.patientClass.codingFirstRep.code = futurePatientClass


        if (patient.patientClass.codingFirstRep.code == OUT_PATIENT) {
            if (patientDomain.patientClass.codingFirstRep.code == IN_PATIENT) {
                // Set Admission Date to null & discharge date to now.
                patient.patientLocationDetails.admissionDate = null
                patient.patientLocationDetails.dischargeDate = new DateType(new Date())
            } else {
                patient.patientLocationDetails.admissionDate = null
                patient.patientLocationDetails.dischargeDate = null
            }
            //patient.patientLocationDetails.roomNumber = null
        } else if (patient.patientClass.codingFirstRep.code == IN_PATIENT) {
            if (patientDomain.patientClass.codingFirstRep.code == IN_PATIENT) {
                // Set Admission Date to null & discharge date to now.
                patient.patientLocationDetails.admissionDate = patientDomain.patientLocationDetails.admissionDate
                patient.patientLocationDetails.dischargeDate = null
            } else {
                patient.patientLocationDetails.admissionDate = new DateType(new Date())
                patient.patientLocationDetails.dischargeDate = null
            }

            if (isNullOrEmpty(patient.patientLocationDetails.roomNumber?.toString())) {
                if (!isNullOrEmpty(defaultRoomNumber)) {
                    outcome.addWarning(String.format(ResponseCode.DEFAULT_ROOM_NUMBER_CONSIDERED.value, defaultRoomNumber), ResponseCode.DEFAULT_ROOM_NUMBER_CONSIDERED.toString())
                    patient.patientLocationDetails.roomNumber = new StringType(defaultRoomNumber)
                }
            }
        }

        patientClassValues."existingPatientClass" = patientDomain.patientClass.codingFirstRep.code
        patientDomain.patientClass.codingFirstRep.code = patient.patientClass.codingFirstRep.code
        patientDomain.patientLocationDetails.admissionDate = patient.patientLocationDetails.admissionDate
        patientDomain.patientLocationDetails.dischargeDate = patient.patientLocationDetails.dischargeDate
        patientDomain.patientLocationDetails.roomNumber = patient.patientLocationDetails.roomNumber
        patientClassValues."newPatientClass" = patient.patientClass.codingFirstRep.code
    }

    def static mapPatientClass(patient, patientDomain, outcome, defaultRoomNumber, currentDateTime, advPatientClassProcess, patientClassValues) {
        if (patient.patientClass?.codingFirstRep?.code == null || !(patient.patientClass?.codingFirstRep?.code == IN_PATIENT
                || patient.patientClass?.codingFirstRep?.code == OUT_PATIENT)) {
            if (patientDomain?.patientClass?.codingFirstRep?.code == null) {
                patient.patientClass = new CodeableConcept()
                patient.patientClass.codingFirstRep.system = "http://varian.com/fhir/CodeSystem/patient-class"
                patient.patientClass.codingFirstRep.code = OUT_PATIENT
            } else {
                patient.patientClass = patientDomain.patientClass
            }
        }

        if (advPatientClassProcess == "0" && patient.patientClass.codingFirstRep.code == IN_PATIENT) {
            // If Admission Date is null, then set Admission Date from Current Date Configuration of Parameters resource.- If Current Date Configuration is not set, then Warning should be raised.
            if (patient.patientLocationDetails.admissionDate == null) {
                if (patientDomain?.patientLocationDetails?.admissionDate != null) {
                    patient.patientLocationDetails.admissionDate = patientDomain.patientLocationDetails.admissionDate
                } else {
                    if (isNullOrEmpty(currentDateTime)) {
                        outcome.addWarning(ResponseCode.CURRENT_DATE_NOT_SET.value.toString(), ResponseCode.CURRENT_DATE_NOT_SET.toString())
                        def oo = outcome.getErrorOperationOutcome(ResponseCode.ADMIT_DATE_NULL_FOR_INPATIENT.value, ResponseCode.ADMIT_DATE_NULL_FOR_INPATIENT.toString())
                        throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException(ResponseCode.ADMIT_DATE_NULL_FOR_INPATIENT.toString(), oo)
                    } else {
                        outcome.addWarning(ResponseCode.CURRENT_DATE_FOR_ADMIT_DATE.value.toString(), ResponseCode.CURRENT_DATE_FOR_ADMIT_DATE.toString())
                        patient.patientLocationDetails.admissionDate = getDateType(currentDateTime)
                    }
                }
            }

            // Set Discharge Date to null.
            patient.patientLocationDetails.dischargeDate = null

            // If Patient Location is null, then set Patient Location from Default Configured location if domain patient not found.-
            // If Default Configured location is not set, then Warning should be raised and marked patient as Out Patient
            if (isNullOrEmpty(patient.patientLocationDetails.roomNumber?.toString())) {
                if (patientDomain?.patientLocationDetails?.roomNumber != null) {
                    patient.patientLocationDetails.roomNumber = patientDomain.patientLocationDetails.roomNumber
                } else {
                    if (isNullOrEmpty(defaultRoomNumber)) {
                        outcome.addWarning(ResponseCode.ROOM_NUMBER_NOT_CONFIGURED.value.toString(), ResponseCode.ROOM_NUMBER_NOT_CONFIGURED.toString())
                        outcome.addWarning(ResponseCode.IGNORE_PATIENTCLASS_ROOM_NUMBER_NULL.value.toString(), ResponseCode.IGNORE_PATIENTCLASS_ROOM_NUMBER_NULL.toString())
                        patient.patientClass.codingFirstRep.code = OUT_PATIENT
                    } else {
                        outcome.addWarning(String.format(ResponseCode.DEFAULT_ROOM_NUMBER_CONSIDERED.value, defaultRoomNumber), ResponseCode.DEFAULT_ROOM_NUMBER_CONSIDERED.toString())
                        patient.patientLocationDetails.roomNumber = new StringType(defaultRoomNumber)
                    }
                }
            }
        }

        if (advPatientClassProcess == "0" && patientDomain == null && patient.patientClass.codingFirstRep.code == OUT_PATIENT) {
            // Set Admission Date to null
            patient.patientLocationDetails.admissionDate = null

            // Set Discharge Date to null
            patient.patientLocationDetails.dischargeDate = null

            // Set Patient Location to null
            patient.patientLocationDetails.roomNumber = null
        }

        if (advPatientClassProcess == "0" && patientDomain != null && patient.patientClass.codingFirstRep.code == OUT_PATIENT) {
            // For update case, patientDomain is used in fhirClient
            if (patientDomain.patientClass.codingFirstRep.code == IN_PATIENT) {
                // Set Admission Date to null.
                patient.patientLocationDetails.admissionDate = null

                // Set Patient Location to null.
                patient.patientLocationDetails.roomNumber = null

                // If Discharge Date is null.
                if (patient.patientLocationDetails.dischargeDate == null) {
                    if (isNullOrEmpty(currentDateTime)) {
                        outcome.addWarning(ResponseCode.CURRENT_DATE_NOT_SET.value.toString(), ResponseCode.CURRENT_DATE_NOT_SET.toString())
                        throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException(ResponseCode.DISCHARGE_DATE_NULL_FOR_OUTPATIENT.toString()) as Throwable
                    } else {
                        outcome.addWarning(ResponseCode.CURRENT_DATE_FOR_DISCHARGE_DATE.value.toString(), ResponseCode.CURRENT_DATE_FOR_DISCHARGE_DATE.toString())
                        patient.patientLocationDetails.dischargeDate = getDateType(currentDateTime)
                    }
                }
            } else {
                patient.patientLocationDetails.admissionDate = null
                patient.patientLocationDetails.roomNumber = null
                patient.patientLocationDetails.dischargeDate = patientDomain.patientLocationDetails.dischargeDate
            }
        }

        // For update case, patientDomain is used in fhirClient
        if (patientDomain != null) {
            patientClassValues."existingPatientClass" = patientDomain.patientClass.codingFirstRep.code
            patientDomain.patientClass.codingFirstRep.code = patient.patientClass.codingFirstRep.code
            patientDomain.patientLocationDetails.admissionDate = patient.patientLocationDetails.admissionDate
            patientDomain.patientLocationDetails.dischargeDate = patient.patientLocationDetails.dischargeDate
            patientDomain.patientLocationDetails.roomNumber = patient.patientLocationDetails.roomNumber
        }

        patientClassValues."newPatientClass" = patient.patientClass.codingFirstRep.code
    }

    static def unmarkPrimaryAllButLast(contacts) {
        //in case of multiple primary contact from input list, last primary contact will retain as primary, all other contact will have primary check unset.
        def isPrimaryFound = false
        for (contact in contacts.reverse()) {
            def isPrimary = contact.extension.find { it.url == "http://varian.com/fhir/v1/StructureDefinition/patient-contact-isPrimary" }
            if (isPrimary != null && (isPrimary.value as BooleanType).booleanValue()) {
                isPrimary.setValue(new BooleanType(!isPrimaryFound))
                isPrimaryFound = true
            }
        }
        return isPrimaryFound
    }

    static def unmarkPrimaryAll(domainContacts) {
        domainContacts.each {
            def primaryExtension = it.extension.find { it.url == "http://varian.com/fhir/v1/StructureDefinition/patient-contact-isPrimary" }
            if (primaryExtension != null && primaryExtension.hasValue() && (primaryExtension.value as BooleanType).booleanValue()) {
                primaryExtension.setValue(new BooleanType(false))
            }
        }
    }

    static def unmarkPrimaryAllButLast(contacts, domainContacts) {
        def isPrimaryFound = unmarkPrimaryAllButLast(contacts)

        //if input list has a primary contact, then all contact from database should have primary check unset.
        if (isPrimaryFound) {
            unmarkPrimaryAll(domainContacts)
        }
    }

    static def removeMultiplePrimaryContacts(input_patient) {
        def emergencyContacts = input_patient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "C" }
        def employerContacts = input_patient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "E" }
        def transportContact = input_patient.contact.findAll { it.relationshipFirstRep.codingFirstRep.code == "O" }

        unmarkPrimaryAllButLast(emergencyContacts)
        unmarkPrimaryAllButLast(employerContacts)
        unmarkPrimaryAllButLast(transportContact)
    }

    static def filterInvalidContact(contacts, outcome, def inputPatient) {
        contacts.removeIf { isContactInvalid(it, outcome, inputPatient) }
    }

    static def isContactInvalid(inputPatient_contact, outcome, inputPatient) {
        if (inputPatient_contact.relationshipFirstRep.codingFirstRep.code == "C") {
            if (isNullOrEmpty(inputPatient_contact.name?.family)) {
                outcome.addWarning(ResponseCode.INVALID_POINT_OF_CONTACT.value, ResponseCode.INVALID_POINT_OF_CONTACT.toString())
                return true
            }
        } else if (inputPatient_contact.relationshipFirstRep.codingFirstRep.code == "E") {
            if (isNullOrEmpty(inputPatient_contact.name?.givenAsSingleString)) {
                outcome.addWarning(ResponseCode.INVALID_EMPLOYER_CONTACT.value, ResponseCode.INVALID_EMPLOYER_CONTACT.toString())
                return true
            }
        } else if (inputPatient_contact.relationshipFirstRep.codingFirstRep.code == "O") {
            if (isNullOrEmpty(inputPatient_contact.name?.givenAsSingleString)) {
                outcome.addWarning(ResponseCode.INVALID_TRANSPORT_CONTACT.value, ResponseCode.INVALID_TRANSPORT_CONTACT.toString())
                return true
            } else {
                def transportationContact = inputPatient_contact.telecomFirstRep.value
                if (isNullOrEmpty(transportationContact) || transportationContact == ResourceUtil.ACTIVE_NULL_LITERAL) {
                    def hospital = inputPatient.managingOrganization.reference.replace("Organization/", "")
                    def valueSetBundle = getTransportValueSet(hospital)
                    if (valueSetBundle != null) {
                        def valueSet = bundleUtility.getValueSet(valueSetBundle)
                        def concepts = valueSet?.expansion?.containsFirstRep
                        def conceptValue = concepts?.contains?.find { it.system == VALUESET_TRANSPORT_CONTACT_SYSTEM && it.code == inputPatient_contact.name?.givenAsSingleString }?.display
                        if (!isNullOrEmpty(conceptValue)) {
                            inputPatient_contact.telecomFirstRep.value = conceptValue
                        }
                    }
                }
            }
        }
        return false
    }

    static def getTransportValueSet(hospital) {
        def resource = new ValueSet()
        def inParams = new Parameters()
        inParams.addParameter().setName("url").value = new StringType(VALUESET_TRANSPORT_CONTACT_URL)
        inParams.addParameter().setName("publisher").value = new StringType(hospital)
        return client.operation(resource, "\$expand", "ValueSet", inParams, new Bundle())
    }

    static def isContactMatched(inputPatient_contact, domainPatient, outcome) {
        def subRelationCode = inputPatient_contact.extension.find { it.url == "http://varian.com/fhir/v1/StructureDefinition/patient-contact-subrelation" }
        def patientContactFound
        if (inputPatient_contact.relationshipFirstRep.codingFirstRep.code == "C") {
            patientContactFound = domainPatient.contact.find {
                def subrelation = it.extension.find { ext -> ext.url == "http://varian.com/fhir/v1/StructureDefinition/patient-contact-subrelation" }
                (it.relationshipFirstRep.codingFirstRep.code == "C"
                        && subrelation != null && subRelationCode != null
                        && subrelation.value.toString() == subRelationCode.value.toString()
                        && it.name != null && inputPatient_contact.name != null
                        && it.name.family == inputPatient_contact.name.family
                        && it.name.givenAsSingleString == inputPatient_contact.name.givenAsSingleString)
            }
        } else if (inputPatient_contact.relationshipFirstRep.codingFirstRep.code == "E") {
            patientContactFound = domainPatient.contact.find {
                def subrelation = it.extension.find { ext -> ext.url == "http://varian.com/fhir/v1/StructureDefinition/patient-contact-subrelation" }
                (it.relationshipFirstRep.codingFirstRep.code == "E" && subrelation != null
                        && subRelationCode != null && subrelation.value.toString() == subRelationCode.value.toString()
                        && it.name.givenAsSingleString == inputPatient_contact.name.givenAsSingleString)
            }
        } else {
            patientContactFound = domainPatient.contact.find {
                (it.relationshipFirstRep.codingFirstRep.code == "O"
                        && it.name?.givenAsSingleString == inputPatient_contact.name.givenAsSingleString)
            }
            if (patientContactFound != null) {
                def domainCommentsExt = patientContactFound.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival")
                def inputCommentsExt = inputPatient_contact.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival")
                def domainComments = (domainCommentsExt?.value as CodeableConcept)?.text
                def inputComments = (inputCommentsExt?.value as CodeableConcept)?.text
                inputComments = getValue(inputComments, domainComments)
                def c = new CodeableConcept()
                c.text = inputComments
                if(inputCommentsExt == null) {
                    inputPatient_contact.addExtension("http://varian.com/fhir/v1/StructureDefinition/patient-modeOfArrival", c)
                } else {
                    inputCommentsExt.setValue(c)
                }
            }
        }

        //update database contact attributes with new input contact value
        if (patientContactFound != null) {
            patientContactFound.extension = inputPatient_contact.extension
            mapTelcom(patientContactFound, inputPatient_contact)
            patientContactFound.name = inputPatient_contact.name
            inputPatient_contact.address.id = patientContactFound.address.id
            mapAddressFields(inputPatient_contact.address, patientContactFound.address)
            return true
        }
        return false
    }

    static def mapAddressFields(inputAddress, domainAddress) {
        if (inputAddress?.hasLine()) {
            int lineCounter = 0
            inputAddress.line.each {
                if (domainAddress.line.size > lineCounter) {
                    domainAddress.line[lineCounter] = new StringType(getValue(inputAddress.line[lineCounter].value.trim(), domainAddress.line[lineCounter].value))
                } else {
                    domainAddress.line.add(new StringType(getValue(inputAddress.line[lineCounter].value.trim(), null)))
                }
                lineCounter++
            }
        }
        domainAddress.city = getValue(inputAddress.city, domainAddress.city)
        domainAddress.district = getValue(inputAddress.district, domainAddress.district)
        domainAddress.state = getValue(inputAddress.state, domainAddress.state)
        domainAddress.country = getValue(inputAddress.country, domainAddress.country)
        domainAddress.postalCode = getValue(inputAddress.postalCode, domainAddress.postalCode)
        def domainTelephoneExt = domainAddress.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/address-telephone1")
        def inputTelephoneExt = inputAddress.getExtensionByUrl("http://varian.com/fhir/v1/StructureDefinition/address-telephone1")
        def outValue = getValue(inputTelephoneExt?.value, domainTelephoneExt?.value)
        if(domainTelephoneExt == null) {
            domainAddress.addExtension("http://varian.com/fhir/v1/StructureDefinition/address-telephone1", outValue)
        } else {
            domainTelephoneExt.setValue(outValue)
        }
    }

    static def removeInvalidAddresses(inputPatient) {
        inputPatient.address.removeIf { it.use.toCode() != "home" &&  it.use.toCode() != "temp" }
    }
}