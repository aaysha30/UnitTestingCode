import ca.uhn.fhir.rest.client.impl.GenericClient
import com.varian.fhir.common.Stu3ContextHelper
import com.varian.fhir.resources.Account
import com.varian.fhir.resources.Organization
import com.varian.fhir.resources.Patient
import com.varian.fhir.resources.Practitioner
import com.varian.mappercore.client.FhirClient
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.client.TokenManager
import com.varian.mappercore.framework.helper.ClientDecor
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory
import org.hl7.fhir.r4.model.*
import java.util.*

class TestHelper {
    companion object {
        @JvmStatic
        private val parser = Stu3ContextHelper.forR4().newJsonParser()

        @JvmStatic
        var scripts: IScripts = ScriptFactory(
            "PARK_PATIENT", mapOf(
                Pair(
                    "PARK_PATIENT",
                    listOf(
                        "dsl/master/inbound",
                        "dsl/master/outbound"
                    )
                ),
                Pair(
                    "ADT_IN",
                    listOf(
                        "dsl/master/inbound/interfaces/adtin",
                        "dsl/master/inbound"
                    )
                ),
                Pair(
                    "SIU_Out",
                    listOf(
                        "dsl/master/outbound/interfaces/siuout","dsl/master/outbound/common","dsl/master/outbound/helper"
                    )
                )

            ), "ac_production"
        ).scripts

        fun readResource(path: String): String {
            return TestHelper::class.java.getResource(path).readText()

        }

        fun getPatientBundle(id: String, identifier: String, familyName: String, givenName: String): Bundle {
            val bundle = Bundle()
            val addEntry = bundle.addEntry()
            val patient = Patient()
            patient.id = id
            patient.identifierFirstRep.system = ""
            patient.identifierFirstRep.value = identifier
            patient.nameFirstRep.family = familyName
            patient.nameFirstRep.addGiven(givenName)
            patient.managingOrganization = Reference("Organization/Organization-1")
            addEntry.resource = patient
            return bundle
        }

        fun getDomainAccountBundle(startDate: Date?, endDate: Date?): Bundle {
            val domainAccountBundleString = TestHelper.readResource("/patientdischarge/DomainAccountBundle.json")
            val domainAccountBundle = parser.parseResource(domainAccountBundleString) as Bundle
            val account = domainAccountBundle.entry.find { it.resource.fhirType() == "Account" }?.resource as Account
            account.servicePeriod.start = startDate
            account.servicePeriod.end = endDate
            return domainAccountBundle
        }

        fun getDomainPatientBundle(
            roomNumber: String?,
            admissionDate: Date?,
            dischargeDate: Date?,
            patientClass: String?
        ): Bundle {
            val domainPatientBundleString = TestHelper.readResource("/patientpreadmit/DomainPatientBundle.json")
            val domainPatientBundle = parser.parseResource(domainPatientBundleString) as Bundle
            val patient = domainPatientBundle.entry.find { it.resource.fhirType() == "Patient" }?.resource as Patient
            patient.patientLocationDetails.admissionDate = DateType(admissionDate)
            patient.patientLocationDetails.dischargeDate = DateType(dischargeDate)
            patient.patientLocationDetails.roomNumber = StringType(roomNumber)
            if (patientClass.isNullOrEmpty()) {
                patient.patientClass = null
            } else {
                patient.patientClass.codingFirstRep.code = patientClass
            }
            return domainPatientBundle
        }

        fun getOrganizationBundle(id: String, identifier: String, type: String): Bundle {
            val orgBundle = Bundle()
            val organization = Organization()
            organization.id = id
            organization.identifierFirstRep.value = identifier
            organization.name = identifier
            orgBundle.addEntry().resource = organization
            organization.typeFirstRep.codingFirstRep.code = type
            return orgBundle
        }

        fun getOrganizationBundle(id: String, identifier: String): Bundle {
            val orgBundle = Bundle()
            val organization = Organization()
            organization.id = id
            organization.identifierFirstRep.value = identifier
            organization.name = identifier
            orgBundle.addEntry().resource = organization
            return orgBundle
        }

        fun getPractitioner(id: String, username: String) : Practitioner {
            val practitioner = Practitioner()
            practitioner.idElement = IdType(id)
            practitioner.identifierFirstRep.setSystem("http://varian.com/fhir/identifier/Practitioner/UserName").value = username
            return  practitioner
        }
    }
}
