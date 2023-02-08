package master.inbound.interfaces.adtin.common.careteam

def class CareTeamHelper {
    def static PRIMARY_CARE_PROVIDER_SYSTEM = "http://loinc.org"
    def static PRIMARY_CARE_PROVIDER_CODE = "56802-2"
    def static PHYSICIAN_ROLE_SYSTEM = "http://varian.com/fhir/CodeSystem/careteam-participant-role"
    def static PRIMARY_ONCOLOGIST_CODE = "primary-oncologist"
    def static PRIMARY_REFERRING_PHYSICIAN_CODE = "primary-referring-physician"
    def static ONCOLOGIST_CODE = "oncologist"
    def static REFERRING_PHYSICIAN_CODE = "referring-physician"

    def static resolvePhysiciansReferences(careTeamFromBundle, clientDecor, outcome, autoCreateReferringPhysician, allowPrimaryUpdateForPractitioner) {
        if (careTeamFromBundle != null) {
            def listOfIds = []
            def pracBundle
            mergeReferringPhysicianAndPrimaryCareProvider(careTeamFromBundle)
            def physicians = getCareTeamPhysiciansParticipant(careTeamFromBundle)
            physicians.each {
                if(it?.member?.identifier?.value != null) {
                    listOfIds.add(it.member.identifier.value)
                }
            }
            if(listOfIds?.size > 0) {
                pracBundle = clientDecor.search("Practitioner", "identifier", new TokenClientParam("identifier").exactly().systemAndValues("http://varian.com/fhir/identifier/Practitioner/Id", listOfIds))
                //pracBundle = clientDecor.search("Practitioner", "identifier", getIdentifierQuery("http://varian.com/fhir/identifier/Practitioner/Id", listOfIds)) as Bundle
            }
            if (allowPrimaryUpdateForPractitioner) {
                //validate primary oncologist. if not exist skip all oncologist
                def primaryOncologist = getParticipant(careTeamFromBundle, PHYSICIAN_ROLE_SYSTEM, PRIMARY_ONCOLOGIST_CODE)
                if (primaryOncologist != null) {
                    def primaryOncologistId = primaryOncologist.member.identifier.value
                    //def domainPrimaryOncologist = getResource(clientDecor.search("Practitioner", "identifier", getIdentifierQuery(primaryOncologist.member.identifier.system, primaryOncologistId)), "Practitioner")
                    def domainPrimaryOncologist = getPractitionerByIdentifier(pracBundle, primaryOncologistId)
                    if (domainPrimaryOncologist == null || !domainPrimaryOncologist.active) {
                        outcome.addWarning(String.format(ResponseCode.PRACTITIONER_NOT_FOUND.value, primaryOncologistId), ResponseCode.PRACTITIONER_NOT_FOUND.toString())
                        outcome.addWarning(String.format(ResponseCode.ONCOLOGIST_NOT_PROCESSED.value), ResponseCode.ONCOLOGIST_NOT_PROCESSED.toString())
                        removeAllOncologist(careTeamFromBundle)
                        physicians = getCareTeamPhysiciansParticipant(careTeamFromBundle)
                    }
                }
            }

            physicians.each {
                //search practitioners and resolve its reference. if not exists create referring physician based on auto create flag
                def practitionerSystem = it.member.identifier.system
                def practitionerValue = it.member.identifier.value
                if (!isNullOrEmpty(practitionerValue)) {
                    //def practitionerBundleDomain = clientDecor.search("Practitioner", "identifier", getIdentifierQuery(practitionerSystem, practitionerValue))
                    def practitionerDomain = getPractitionerByIdentifier(pracBundle, practitionerValue)
                    //def practitionerDomain = getResource(practitionerBundleDomain, "Practitioner")
                    if (practitionerDomain == null) {
                        if (autoCreateReferringPhysician && it.member.resource != null && it.member.resource instanceof Practitioner) {
                            def opOutcome = clientDecor.createSafely(it.member.resource)
                            if (opOutcome != null) {
                                it.member.reference = "Practitioner/${opOutcome.id.idPart}"
                            }
                        } else {
                            outcome.addWarning(String.format(ResponseCode.PRACTITIONER_NOT_FOUND.value, practitionerValue), ResponseCode.PRACTITIONER_NOT_FOUND.toString())
                            //message contains dummy reference for contained practitioners.
                            // so remove dummy references if not resolved
                            it.member?.reference = null
                        }
                    } else if (practitionerDomain.active) {
                        it.member.reference = "Practitioner/${practitionerDomain.idElement.idPart}"
                    } else {
                        it.member?.reference = null
                    }
                }
            }
        }

        //remove all invalid participants
        careTeamFromBundle?.participant?.removeAll { isNullOrEmpty(it.member.reference) }
        careTeamFromBundle?.contained = null
    }

    static def map(careTeam, domainCareTeamBundle, allowPrimaryUpdateForPractitioner, removePrimaryCareProvider) {
        if (domainCareTeamBundle != null) {
            def physicians = getCareTeamPhysiciansParticipant(careTeam)
            if (!physicians?.isEmpty()) {
                // clear all existing primary-oncologist and primary-referring physician when system is configured to update primary with input practitioners.
                if (allowPrimaryUpdateForPractitioner) {
                    def hasPrimaryOncologist = getParticipant(careTeam, PHYSICIAN_ROLE_SYSTEM, PRIMARY_ONCOLOGIST_CODE)
                    def hasPrimaryReferringPhysician = getParticipant(careTeam, PHYSICIAN_ROLE_SYSTEM, PRIMARY_REFERRING_PHYSICIAN_CODE)
                    domainCareTeamBundle?.participant?.each {
                        it.role.stream().flatMap { x ->
                            x.coding.stream()
                        }.forEach { y ->
                            if (y.code == PRIMARY_ONCOLOGIST_CODE && hasPrimaryOncologist) {
                                y.code = ONCOLOGIST_CODE
                            } else if (y.code == PRIMARY_REFERRING_PHYSICIAN_CODE && hasPrimaryReferringPhysician) {
                                y.code = REFERRING_PHYSICIAN_CODE
                            }
                        }
                    }
                }
            }
            physicians.each { physician ->
                def domainPhysician = domainCareTeamBundle?.participant?.find { it.member.reference == physician.member.reference }
                if (domainPhysician == null) {
                    //if participant does not exist add them
                    domainCareTeamBundle.participant.add(physician)
                } else {
                    //if participant exist
                    //mark existing oncologist and referring physician as primary when system is configured to mark it primary with input practitioners.
                    if (allowPrimaryUpdateForPractitioner) {
                        def practitionerRole = physician
                                .role.stream().flatMap { x -> x.coding.stream() }
                                .find { y -> y.system == PHYSICIAN_ROLE_SYSTEM }

                        if (practitionerRole != null && (practitionerRole.code == PRIMARY_ONCOLOGIST_CODE || practitionerRole.code == PRIMARY_REFERRING_PHYSICIAN_CODE)) {
                            def role = domainPhysician.role.stream().flatMap { x ->
                                x.coding.stream()
                            }.find { y -> y.system == PHYSICIAN_ROLE_SYSTEM }

                            if (role) {
                                role.code = practitionerRole.code
                            } else {
                                domainPhysician.addRole().setSystem(PHYSICIAN_ROLE_SYSTEM).setCode(practitionerRole.code)
                            }
                        }
                    }

                    //add or update role to mark physician as primary care provider
                    if (!removePrimaryCareProvider) {
                        def primaryCareProviderRole = getPrimaryCareProviderRole(physician)
                        def domainPrimaryCareProviderRole = getPrimaryCareProviderRole(domainPhysician)
                        if (primaryCareProviderRole && !domainPrimaryCareProviderRole) {
                            domainPhysician.addRole(primaryCareProviderRole)
                        }
                    }
                }
            }
            if (removePrimaryCareProvider) {
                domainCareTeamBundle.participant?.each {
                    removePrimaryCareProviderRole(it)
                }
            }
        }
    }

    def static getCareTeamPhysiciansParticipant(careTeam) {
        return careTeam?.participant?.findAll {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }.noneMatch { y ->
                y.code == "service-organization" || y.code == "default-service-organization"
            }
        }
    }

    def static getParticipant(careTeam, roleSystem, roleCode) {
        return careTeam?.participant?.find {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == roleCode && y.system == roleSystem
            }
        }
    }

    def static getParticipants(careTeam, roleSystem, roleCode) {
        return careTeam?.participant?.findAll {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == roleCode && y.system == roleSystem
            }
        }
    }

    def static removeAllOncologist(careTeam) {
        careTeam?.participant?.removeAll {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == PRIMARY_ONCOLOGIST_CODE || y.code == ONCOLOGIST_CODE
            }
        }
    }

    def static mergeReferringPhysicianAndPrimaryCareProvider(careTeam) {
        //it will merge multiple physician with same id but different role(primary-referring-physician/primary-care-provider)
        //into single participant
        def primaryCareProviders = getParticipants(careTeam, PRIMARY_CARE_PROVIDER_SYSTEM, PRIMARY_CARE_PROVIDER_CODE)
        removePrimaryCareProvider(careTeam)
        def addToCareTeam = []
        primaryCareProviders.each { provider ->
            def rpFound = careTeam.participant.find {
                it.member.identifier.value == provider.member.identifier.value
            }

            if (rpFound) {
                rpFound.addRole(getPrimaryCareProviderRole(provider))
            } else {
                addToCareTeam.add(provider)
            }
        }

        addToCareTeam.each {
            careTeam.participant.add(it)
        }
    }

    def static removePrimaryCareProvider(careTeam) {
        careTeam?.participant?.removeAll {
            it.role.stream().flatMap { x ->
                x.coding.stream()
            }?.anyMatch { y ->
                y.code == PRIMARY_CARE_PROVIDER_CODE && y.system == PRIMARY_CARE_PROVIDER_SYSTEM
            }
        }
    }

    def static getPrimaryCareProviderRole(physician) {
        return physician.role.find { it.codingFirstRep.code == PRIMARY_CARE_PROVIDER_CODE && it.codingFirstRep.system == PRIMARY_CARE_PROVIDER_SYSTEM }
    }

    def static removePrimaryCareProviderRole(physician) {
        return physician.role.removeIf { it.codingFirstRep.code == PRIMARY_CARE_PROVIDER_CODE && it.codingFirstRep.system == PRIMARY_CARE_PROVIDER_SYSTEM }
    }

    def static isPrimaryCareProviderNULL(careTeam) {
        def primaryCareProviders = getParticipants(careTeam, PRIMARY_CARE_PROVIDER_SYSTEM, PRIMARY_CARE_PROVIDER_CODE)
        return primaryCareProviders.find { it.member.identifier.value == ResourceUtil.ACTIVE_NULL_LITERAL }
    }

    def static removeNULLProviders(careTeam) {
        careTeam.participant.removeAll {
            it.member?.identifier?.value == ResourceUtil.ACTIVE_NULL_LITERAL
        }
    }

    def static getPractitionerByIdentifier(pracBundle, pracId) {
        return getResource(pracBundle, "Practitioner", pracId)
        //return getAllResources(pracBundle, "Practitioner")?.find { it.identifier?.any { id -> id.value == pracId } }
    }
}