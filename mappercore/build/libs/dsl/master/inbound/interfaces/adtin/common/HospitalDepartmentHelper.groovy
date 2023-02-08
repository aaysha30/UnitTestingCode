package master.inbound.interfaces.adtin.common

def class HospitalDepartmentHelper {
    def static attachHospitalDepartments
    def static snapshotDepartments
    def static updatePrimaryDepartment
    def static client
    def static hospitalDeptBundle
    def static bundleUtility

    def static validateAndUpdateHospital_DepartmentReference(newBundle, existingBundle, defaultHospitalName, defaultDepartmentIdentifier, clientDecor, outcome) {
        def newPatient = getResource(newBundle, "Patient")
        def existingPatient = getResource(existingBundle, "Patient")

        def careTeamFromBundle = getResource(newBundle, "CareTeam")
        def careTeamFromDomainBundle = getResource(existingBundle, "CareTeam")

        def newHospitalName = newPatient?.managingOrganization?.display
        def existingHospitalId = existingPatient?.managingOrganization?.reference

        def newDepartmentIdentifier = getCareTeamDepartmentParticipant(careTeamFromBundle)?.member?.display
        def existingDeptId = null
        if (careTeamFromDomainBundle != null) {
            existingDeptId = getCareTeamDepartmentParticipant(careTeamFromDomainBundle)?.member?.reference
        }
        def isPatientUpdate = existingHospitalId != null && existingDeptId != null
        def hospitalToUse
        def departmentToUse
        def validHospital
        def validDept
        def useExistingPatientsHospitalAndDepartment = isPatientUpdate
        if (!isNullOrEmpty(newHospitalName)) {
            validHospital = getHospitalOrganizationByName(newHospitalName, clientDecor)?.idElement?.idPart
            if (validHospital == null) {
                outcome.addWarning(String.format(ResponseCode.PATIENT_INVALID_HOSPITAL.value, newHospitalName), ResponseCode.PATIENT_INVALID_HOSPITAL.toString())
            } else {
                validDept = getValidDepartmentForHospital(validHospital, newDepartmentIdentifier, defaultDepartmentIdentifier, clientDecor, outcome, isPatientUpdate)
                if (validDept != null) {
                    //if input department is valid then update existing patient's hospital and department with new input values
                    useExistingPatientsHospitalAndDepartment = false
                }
            }
        }
        if (useExistingPatientsHospitalAndDepartment) {
            //use existing patients hospital and department in case of patient update if given hospital or department is  invalid
            hospitalToUse = existingHospitalId
            departmentToUse = existingDeptId
            updateHospitalAndDepartmentReference(newBundle, existingBundle, hospitalToUse, departmentToUse, newPatient)
            return
        }

        if (validDept != null) {
            hospitalToUse = "Organization/" + validHospital
            departmentToUse = "Organization/" + validDept
            updateHospitalAndDepartmentReference(newBundle, existingBundle, hospitalToUse, departmentToUse, newPatient)
            return
        }

        //getAllHospitals
        def allHospitals = getAllHospitalOrganizations(clientDecor)

        if (allHospitals?.size == 1) {
            //if single hospital is found then use this hospital
            outcome.addWarning(String.format(ResponseCode.PATIENT_SINGLE_HOSPITAL.value, allHospitals[0].identifierFirstRep.value), ResponseCode.PATIENT_SINGLE_HOSPITAL.toString())
            validHospital = allHospitals[0].idElement.idPart
            if (validHospital != null) {
                validDept = getValidDepartmentForHospital(validHospital, newDepartmentIdentifier, defaultDepartmentIdentifier, clientDecor, outcome)
                if (validDept != null) {
                    hospitalToUse = "Organization/" + validHospital
                    departmentToUse = "Organization/" + validDept
                    updateHospitalAndDepartmentReference(newBundle, existingBundle, hospitalToUse, departmentToUse, newPatient)
                    return
                }
            }
        }

        //if default hospital is configured use default configured hospital
        if (!isNullOrEmpty(defaultHospitalName)) {
            def defaultOrganization = getHospitalOrganizationByName(defaultHospitalName, clientDecor)
            validHospital = defaultOrganization?.idElement?.idPart
            if (validHospital != null) {
                outcome.addWarning(String.format(ResponseCode.PATIENT_DEFAULT_HOSPITAL.value, defaultHospitalName), ResponseCode.PATIENT_DEFAULT_HOSPITAL.toString())
                validDept = getValidDepartmentForHospital(validHospital, newDepartmentIdentifier, defaultDepartmentIdentifier, clientDecor, outcome)
                if (validDept != null) {
                    hospitalToUse = "Organization/" + validHospital
                    departmentToUse = "Organization/" + validDept
                    updateHospitalAndDepartmentReference(newBundle, existingBundle, hospitalToUse, departmentToUse, newPatient)
                    return
                }
            } else {
                outcome.addWarning(String.format(ResponseCode.PATIENT_INVALID_DEFAULT_HOSPITAL.value, defaultHospitalName), ResponseCode.PATIENT_INVALID_DEFAULT_HOSPITAL.toString())
            }
        } else {
            outcome.addWarning(ResponseCode.PATIENT_HOSPITAL_NOT_CONFIGURED.value, ResponseCode.PATIENT_HOSPITAL_NOT_CONFIGURED.toString())
        }

        //if no valid combination of hospital and department found, throw error
        if (isNullOrEmpty(hospitalToUse) || isNullOrEmpty(departmentToUse)) {
            throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException(ResponseCode.PATIENT_INVALID_HOSPITAL_DEPARTMENT.toString(),
                    outcome.getErrorOperationOutcome(ResponseCode.PATIENT_INVALID_HOSPITAL_DEPARTMENT.value, ResponseCode.PATIENT_INVALID_HOSPITAL_DEPARTMENT.toString()))
        }
    }

    def static isdepartmentBelongstoHospital(departmentId, hospitalDepartments) {
        def departmentResource = hospitalDepartments?.find {
            it.identifier.any { x -> x.value == departmentId }
        }
        return departmentResource?.idElement?.idPart
    }

    def static getValidDepartmentForHospital(hospitalId, inputDepartmentIdentifier, defaultDepartmentIdentifier, clientDecor, outcome, isPatientUpdate = false) {
        def departmentId = null
        def hospitalDepartments = getHospitalDepartments(hospitalId, clientDecor, inputDepartmentIdentifier)

        if (!isNullOrEmpty(inputDepartmentIdentifier)) {
            departmentId = isdepartmentBelongstoHospital(inputDepartmentIdentifier, hospitalDepartments)
            if (departmentId == null) {
                outcome.addWarning(String.format(ResponseCode.PATIENT_DEPARTMENT_INVALID.value, inputDepartmentIdentifier), ResponseCode.PATIENT_DEPARTMENT_INVALID.toString())
            }
        }

        if (isPatientUpdate) {
            //return in case of patient update, even if department is not valid.
            return departmentId
        }

        //in case of patient create, find valid department
        if (departmentId == null && hospitalDepartments?.size == 1) {
            //if single department associated with hospital return single department
            departmentId = hospitalDepartments[0].idElement?.idPart
            outcome.addWarning(String.format(ResponseCode.PATIENT_SINGLE_DEPARTMENT.value, hospitalDepartments[0].identifierFirstRep.value, hospitalId), ResponseCode.PATIENT_SINGLE_DEPARTMENT.toString())
        }

        if (departmentId == null && !isNullOrEmpty(defaultDepartmentIdentifier)) {
            //if default configured department is valid, then return configured department
            departmentId = isdepartmentBelongstoHospital(defaultDepartmentIdentifier, hospitalDepartments)
            outcome.addWarning(String.format(ResponseCode.PATIENT_DEFAULT_DEPARTMENT.value, defaultDepartmentIdentifier), ResponseCode.PATIENT_DEFAULT_DEPARTMENT.toString())
        }

        return departmentId
    }

    def static updateHospitalAndDepartmentReference(newBundle, existingBundle, hospitalToUse, departmentToUse, patientFromBundle) {
        def careTeamFromBundle = getResource(newBundle, "CareTeam")
        def careTeamFromDomainBundle = getResource(existingBundle, "CareTeam")

        patientFromBundle.managingOrganization.id = hospitalToUse
        patientFromBundle.managingOrganization.reference = hospitalToUse
        def careTeamDeptParticipantComponent = getCareTeamDepartmentParticipant(careTeamFromBundle)
        if (careTeamDeptParticipantComponent != null) {
            careTeamDeptParticipantComponent?.member?.id = departmentToUse
            careTeamDeptParticipantComponent?.member?.reference = departmentToUse
        } else {
            if (careTeamFromBundle == null) {
                def careTeam = new com.varian.fhir.resources.CareTeam()
                def bundleEntryComponent = newBundle.addEntry()
                bundleEntryComponent.resource = careTeam
                careTeamFromBundle = careTeam
            }
            addCareTeamDepartmentParticipant(careTeamFromBundle, departmentToUse)
        }

        if (careTeamFromDomainBundle != null) {
            //update patient department in case of patient update
            def careTeamDeptParticipantComponentFromDomain = getCareTeamDepartmentParticipant(careTeamFromDomainBundle)
            def existingPrimaryDepartment = careTeamDeptParticipantComponentFromDomain?.member?.reference
            if (snapshotDepartments || (!snapshotDepartments && updatePrimaryDepartment)) {
                //if snapshotDepartment is false and update primary is true than add existing primary as not primary and update primary with given dept
                if (!snapshotDepartments) {
                    addCareTeamDepartmentParticipant(careTeamFromDomainBundle, existingPrimaryDepartment, "service-organization")
                }
                careTeamDeptParticipantComponentFromDomain?.member?.id = departmentToUse
                careTeamDeptParticipantComponentFromDomain?.member?.reference = departmentToUse
                //if careteam already contains dept which will become primary then remove its occurrence as its already been added as primary
                careTeamFromDomainBundle.participant.removeIf {
                    (it.member.reference == departmentToUse
                            && it.role.stream().flatMap { x ->
                        x.coding.stream()
                    }?.any { y -> y.code == "service-organization" })
                }
            } else {
                if (departmentToUse != existingPrimaryDepartment) {
                    //if primary update is not allowed than add department as non primary only when its not already a primary dept
                    addCareTeamDepartmentParticipant(careTeamFromDomainBundle, departmentToUse, "service-organization")
                }
            }

            if (snapshotDepartments) {
                careTeamFromDomainBundle.participant.removeIf {
                    (it.member?.reference != departmentToUse
                            && it.role.stream().flatMap { x ->
                        x.coding.stream()
                    }?.any { y -> y.code == "service-organization" || y.code == "default-service-organization" })
                }
            }

            if (attachHospitalDepartments) {
                def hospitalId = hospitalToUse.replace("Organization/", "")
                def departments = getHospitalDepartments(hospitalId, client, null)
                departments.each { dept ->
                    def deptId = "Organization/" + dept.idElement.idPart
                    if (!careTeamFromDomainBundle.participant.any { ct -> ct.member?.reference == deptId }) {
                        addCareTeamDepartmentParticipant(careTeamFromDomainBundle, deptId, "service-organization")
                    }
                }
            }
        }
    }

    def static updatePrimaryDepartment(careTeamFromBundle, domainCareTeam) {
        def careTeamDeptParticipantComponent = getCareTeamDepartmentParticipant(careTeamFromBundle)
        def domainCareTeamDeptParticipantComponent = getCareTeamDepartmentParticipant(domainCareTeam)
        if (!attachHospitalDepartments) {
            //remove all departments but not new primary
            domainCareTeam.participant.removeIf {
                (it.member?.reference != careTeamDeptParticipantComponent.member?.reference
                        && it.role.stream().flatMap { x ->
                    x.coding.stream()
                }?.any { y -> y.code == "service-organization" || y.code == "default-service-organization" })
            }
        }

        if (careTeamDeptParticipantComponent?.member?.reference != domainCareTeamDeptParticipantComponent?.member?.reference) {
            //make input department as primary(default) department and unmark all other as default if any
            if (isNullOrEmpty(domainCareTeam.participant)) {
                addCareTeamDepartmentParticipant(domainCareTeam, careTeamDeptParticipantComponent?.member?.reference)
            }
            domainCareTeam.participant.each {
                if (it.member?.reference == careTeamDeptParticipantComponent.member?.reference) {
                    it.role.stream().flatMap { x ->
                        x.coding.stream()
                    }?.each { y ->
                        if (y.code == "service-organization") {
                            y.code = "default-service-organization"
                            y.display = "default-service-organization"
                        }
                    }
                } else {
                    it.role.stream().flatMap { x ->
                        x.coding.stream()
                    }?.each { y ->
                        if (y.code == "default-service-organization") {
                            y.code = "service-organization"
                            y.display = "service-organization"
                        }
                    }
                }
            }
        }
    }

    def static getHospitalOrganizationByName(hospitalName, clientDecor) {
        def hospital = bundleUtility.getHospitalByName(hospitalDeptBundle, hospitalName)
        if (hospital == null) {
            def hospitalBundle = clientDecor.search("Organization", "name", hospitalName, "type", "prov", "active", "true")
            if (hospitalBundle != null) {
                hospital = bundleUtility.getHospitalByName(hospitalBundle, hospitalName)
                hospitalDeptBundle = bundleUtility.addResource(hospitalDeptBundle, hospitalBundle)
            }
        }
        return hospital
    }

    def static getAllHospitalOrganizations(clientDecor) {
        def allHospitals = clientDecor.search("Organization", "type", "prov", "active", "true")?.entry
                ?.findAll { it.getResource()?.fhirType() == "Organization" }
                ?.collect { it.getResource() }
        allHospitals?.each {
            hospitalDeptBundle = bundleUtility.addResource(hospitalDeptBundle, it)
        }
    }

    def static getHospitalDepartments(organizationId, clientDecor, departmentName) {
        def partOfId = "Organization/" + organizationId
        def depts = bundleUtility.getAllDepartmentsForHospital(hospitalDeptBundle, partOfId)
        if (isNullOrEmpty(depts)) {
            def hospDepartments = clientDecor.search("Organization", "type", "dept", "partof", organizationId, "active", "true")?.entry
                    ?.findAll { it.getResource()?.fhirType() == "Organization" }
                    ?.collect { it.getResource() }
            depts = hospDepartments
            if (!isNullOrEmpty(depts)) {
                depts.each { bundleUtility.addResource(hospitalDeptBundle, it) }
            }
        } else {
            if (!isNullOrEmpty(departmentName) && !isdepartmentBelongstoHospital(departmentName, depts)) {
                def hospDepartment = clientDecor.search("Organization", "type", "dept", "partof", organizationId, "identifier", departmentName, "active", "true")?.entry
                        ?.find { it.getResource()?.fhirType() == "Organization" }?.getResource()
                if (!isNullOrEmpty(hospDepartment)) {
                    bundleUtility.addResource(hospitalDeptBundle, hospDepartment)
                    depts.add(hospDepartment)
                }
            }
        }
        return depts
    }

    def static getDepartmentByIdentifier(deptIdentifier, hospitalName, clientDecor) {
        def hospitalRef = ""
        if (!isNullOrEmpty(hospitalName)) {
            hospitalRef = getHospitalOrganizationByName(hospitalName, clientDecor)?.idElement?.idPart
        }
        def dept = bundleUtility.getDepartmentByIdentifier(hospitalDeptBundle, deptIdentifier, hospitalName)
        if (dept == null) {
            def hospDepartments = clientDecor.search("Organization", "type", "dept", "partof", hospitalRef, "identifier", deptIdentifier, "active", "true")?.entry
                    ?.findAll { it.getResource()?.fhirType() == "Organization" }
                    ?.collect { it.getResource() }
            dept = hospDepartments?.first()
            if (!isNullOrEmpty(hospDepartments)) {
                hospDepartments.each { bundleUtility.addResource(hospitalDeptBundle, it) }
            }
        }
        return dept
    }

    def static getCareTeamDepartmentParticipant(careTeam) {
        return careTeam?.participant?.find {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == "default-service-organization"
            }
        }
    }

    def static addCareTeamDepartmentParticipant(careTeam, departmentIdReference) {
        addCareTeamDepartmentParticipant(careTeam, departmentIdReference, "default-service-organization")
    }

    def static addCareTeamDepartmentParticipant(careTeam, departmentIdReference, code) {
        def cp = new CareTeam.CareTeamParticipantComponent()
        cp.getRoleFirstRep().getCodingFirstRep().setSystem("http://varian.com/fhir/CodeSystem/careteam-participant-role")
        cp.getRoleFirstRep().getCodingFirstRep().setCode(code)
        cp.getMember().setReference(departmentIdReference)
        cp.getMember().setId(departmentIdReference)
        careTeam.addParticipant(cp)
    }
}
