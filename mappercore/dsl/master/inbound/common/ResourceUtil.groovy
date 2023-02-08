package master.inbound.common

def class ResourceUtil {
    def static ACTIVE_NULL_LITERAL = "N_U_L_L"
    static {
        //new master.inbound.common.V2ToFhirMapperExtensions()
        new master.inbound.interfaces.parkpatient.V2AdtExtensions()
    }
    // ToDo: remove all these methods, use instead compiled utility methods like from BundleUtility
    def static extractValueFromResource(bundle, fhirType, elementName, fieldName, fieldValueName) {
        return getResource(bundle, fhirType)?."$elementName"?.find {
            it."$fieldName"?.toString()?.equals(fieldValueName)
        }?.value
    }

    def static extractValue(def resource, def elementName, def fieldName, def fieldValueName) {
        return resource?."$elementName"?.find {
            it."$fieldName"?.toString()?.equals(fieldValueName)
        }?.value
    }

    def static getResource(bundle, fhirType) {
        return bundle?.entry?.find {
            it.getResource()?.fhirType() == fhirType
        }?.getResource()
    }

    def static getResource(bundle, fhirType, value) {
        return bundle?.entry?.find {
            it?.getResource()?.fhirType() == fhirType && it?.getResource()?.getIdentifier()?.any {
                it?.getValue()?.toString()?.equals(value?.toString())
            }
        }?.getResource()
    }

    def static getAllResources(bundle, fhirType) {
        return bundle?.entry?.findAll {
            it.getResource().fhirType() == fhirType
        }*.getResource()
    }

    def static getIdentifierQuery(system, value) {
        return new TokenClientParam("identifier").exactly().systemAndCode(system, value?.trim())
    }

    def static getTokenClientParam(paramName, system, code) {
        return new TokenClientParam(paramName).exactly().systemAndCode(system, code)
    }

    def static getIdentifierQuery(def identifiers) {
        return new TokenClientParam("identifier").exactly().systemAndCode(identifiers.first().system, identifiers.first().value?.trim())
        // ToDo: Search by multiple patient identifiers with AND Operation. This is not supported in AriaFhir, so commented the code.
        /*def tokenOrListParam = new TokenOrListParam()
        patientIdentifiers.forEach { id ->
            def tokenParam = new TokenParam("identifier")
            tokenParam.system = id.system
            tokenParam.value = id.value
            tokenOrListParam.addOr(tokenParam)
        }
        return tokenOrListParam*/
    }

    def static getValue(input, domain) {
        def inputValue = input
        if (input instanceof StringType) {
            inputValue = input?.value
        }
        if (isNullOrEmpty(inputValue)) {
            return domain
        } else if (inputValue == ACTIVE_NULL_LITERAL) {
            return null
        } else return input
    }

    def static isActiveNullList(input_list, value, domain_list) {
        if (value instanceof StringType) {
            value = value?.value
        }
        if (input_list == null || input_list.isEmpty() || !input_list.stream().anyMatch { !isNullOrEmpty(it) }) {
            return domain_list
        } else if (input_list?.size == 1 && value == ACTIVE_NULL_LITERAL) {
            return null
        } else {
            return input_list
        }
    }

    def static isNullOrEmpty(o) {
        if (o == null) {
            return true
        }
        if ((o instanceof String || o instanceof org.hl7.fhir.r4.model.PrimitiveType || o instanceof Collection) && o.isEmpty()) {
            return true
        }
        return false
    }

    def static generateFindParkPatientQueryMap (pkmConfig,patient) {
        def searchIds = pkmConfig.findAll({it1 -> it1.isUsedForFinding == "1"})
        def queryMap = new HashMap<String,String>()
        searchIds.stream()
                .map({it2 -> patient.patientIdentifiers.findAll( {pid -> pid.idType == it2.ariaId}).first()})
                .forEach({ it3 ->
                    queryMap["id"] = it3.idType + "|" + it3.idValue
                })
        return queryMap
    }

    def static upsertParkPatient(patientKeyMapping,inPatient,parkService) {
        def queryMap = generateFindParkPatientQueryMap(patientKeyMapping,inPatient)
        def result = parkService.searchParkedPatientByCriteria(queryMap)
        def alreadyParkedPatient = result.size() > 0 ?  result.first() : null
        inPatient.hstryDateTime = alreadyParkedPatient?.hstryDateTime?: null
        def patientSer = alreadyParkedPatient ? parkService.updateParkPatient(alreadyParkedPatient.patientRecordSer,inPatient)
                : parkService.createParkedPatient(inPatient)
        def patient = parkService.getParkedPatient(patientSer)
        return patient
    }

    def static readMsgHeadersParams(ca.uhn.hl7v2.model.v251.segment.MSH msh) {
        return [ msh.messageType.messageCode.value,
                 msh.messageType.triggerEvent.value,
                 msh.messageControlID.value
               ]
    }

}
