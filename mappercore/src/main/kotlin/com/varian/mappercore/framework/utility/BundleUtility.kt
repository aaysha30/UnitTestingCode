package com.varian.mappercore.framework.utility

import ca.uhn.fhir.rest.gclient.TokenClientParam
import com.varian.fhir.resources.Account
import com.varian.fhir.resources.ActivityDefinition
import com.varian.fhir.resources.Appointment
import com.varian.fhir.resources.CareTeam
import com.varian.fhir.resources.Flag
import com.varian.fhir.resources.InsurancePlan
import com.varian.fhir.resources.Location
import com.varian.fhir.resources.Organization
import com.varian.fhir.resources.Patient
import com.varian.fhir.resources.Practitioner
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Consent
import org.hl7.fhir.r4.model.Enumerations.FHIRAllTypes

open class BundleUtility {

    companion object {
        private fun Bundle.getResource(fhirType: String): Resource? {
            return this.entry.find { it.resource?.fhirType() == fhirType }?.resource
        }

        private fun Bundle.getResources(fhirType: String): List<Resource> {
            return this.entry.filter { it.resource?.fhirType() == fhirType }.map { it.resource }
        }
    }

    fun addResource(bundle: Bundle?, resource: Resource): Bundle {
        val bundleOut = bundle ?: Bundle()
        if (resource.fhirType() == FHIRAllTypes.BUNDLE.toCode()) {
            (resource as Bundle).entry.forEach { bundleOut.addEntry(it) }
        } else {
            bundleOut.addEntry(Bundle.BundleEntryComponent().setResource(resource))
        }
        return bundleOut
    }

    // ToDo: Make these methods as extension methods and access from groovy script
    fun getParameters(bundle: Bundle): Parameters? {
        return bundle.getResource(Enumerations.FHIRAllTypes.PARAMETERS.toCode()) as Parameters?
    }

    fun getMessageHeader(bundle: Bundle): MessageHeader? {
        return bundle.getResource(Enumerations.FHIRAllTypes.MESSAGEHEADER.toCode()) as MessageHeader?
    }

    fun getPatient(bundle: Bundle): Patient? {
        return bundle.getResource(Enumerations.FHIRAllTypes.PATIENT.toCode()) as Patient?
    }

    fun getR4Patient(bundle: Bundle): org.hl7.fhir.r4.model.Patient? {
        return bundle.getResource(Enumerations.FHIRAllTypes.PATIENT.toCode()) as org.hl7.fhir.r4.model.Patient?
    }

    fun getPatients(bundle: Bundle): List<Patient> {
        return bundle.getResources(Enumerations.FHIRAllTypes.PATIENT.toCode()) as List<Patient>
    }

    fun getAccount(bundle: Bundle): Account? {
        return bundle.getResource(Enumerations.FHIRAllTypes.ACCOUNT.toCode()) as Account?
    }

    fun getAccounts(bundle: Bundle): List<Account> {
        return bundle.getResources(Enumerations.FHIRAllTypes.ACCOUNT.toCode()) as List<Account>
    }

    fun getCareTeam(bundle: Bundle): CareTeam? {
        return bundle.getResource(Enumerations.FHIRAllTypes.CARETEAM.toCode()) as CareTeam?
    }

    fun getCareTeams(bundle: Bundle): List<CareTeam> {
        return bundle.getResources(Enumerations.FHIRAllTypes.CARETEAM.toCode()) as List<CareTeam>
    }

    fun getAppointment(bundle: Bundle): Appointment? {
        return bundle.getResource(Enumerations.FHIRAllTypes.APPOINTMENT.toCode()) as Appointment?
    }

    fun getAppointments(bundle: Bundle): List<Appointment> {
        return bundle.getResources(Enumerations.FHIRAllTypes.APPOINTMENT.toCode()) as List<Appointment>
    }

    fun getPractitioner(bundle: Bundle): Practitioner? {
        return bundle.getResource(Enumerations.FHIRAllTypes.PRACTITIONER.toCode()) as Practitioner?
    }

    fun getPractitioners(bundle: Bundle): List<Practitioner> {
        return bundle.getResources(Enumerations.FHIRAllTypes.PRACTITIONER.toCode()) as List<Practitioner>
    }

    fun getOrganization(bundle: Bundle): Organization? {
        return bundle.getResource(Enumerations.FHIRAllTypes.ORGANIZATION.toCode()) as Organization?
    }

    fun getOrganizations(bundle: Bundle): List<Organization> {
        return bundle.getResources(Enumerations.FHIRAllTypes.ORGANIZATION.toCode()) as List<Organization>
    }

    fun getHospitalByName(bundle: Bundle?, name: String): Organization? {
        if(bundle == null)
            return null
        return (bundle.getResources(FHIRAllTypes.ORGANIZATION.toCode()) as? List<Organization>)?.firstOrNull {
            it.name == name && it.typeFirstRep.codingFirstRep.code == "prov"
        }
    }

    fun getAllDepartmentsForHospital(bundle: Bundle?, hospitalId: String): List<Organization>? {
        if(bundle == null)
            return null
        return (bundle.getResources(FHIRAllTypes.ORGANIZATION.toCode()) as? List<Organization>)?.filter {
            it.partOf?.reference == hospitalId && it.typeFirstRep.codingFirstRep.code == "dept"
        }
    }

    fun getDepartmentByIdentifier(bundle: Bundle?, deptId: String, hospitalName: String?): Organization? {
        if(bundle == null)
            return null
        return (bundle.getResources(FHIRAllTypes.ORGANIZATION.toCode()) as? List<Organization>)?.firstOrNull {
            it.identifier.any { id -> id.value == deptId } && (hospitalName.isNullOrEmpty() || it.partOf?.display == hospitalName)
        }
    }



    fun getLocation(bundle: Bundle): Location? {
        return bundle.getResource(Enumerations.FHIRAllTypes.LOCATION.toCode()) as Location?
    }

    fun getLocations(bundle: Bundle): List<Location> {
        return bundle.getResources(Enumerations.FHIRAllTypes.LOCATION.toCode()) as List<Location>
    }

    fun getInsurancePlan(bundle: Bundle): InsurancePlan? {
        return bundle.getResource(Enumerations.FHIRAllTypes.INSURANCEPLAN.toCode()) as InsurancePlan?
    }

    fun getInsurancePlans(bundle: Bundle): List<InsurancePlan> {
        return bundle.getResources(Enumerations.FHIRAllTypes.INSURANCEPLAN.toCode()) as List<InsurancePlan>
    }

    fun getFlags(bundle: Bundle): List<Flag> {
        return bundle.getResources(Enumerations.FHIRAllTypes.FLAG.toCode()) as List<Flag>
    }

    fun getConsents(bundle: Bundle): List<Consent> {
        return bundle.getResources(Enumerations.FHIRAllTypes.CONSENT.toCode()) as List<Consent>
    }

    fun getActivityDefinition(bundle: Bundle): ActivityDefinition? {
        return bundle.getResource(FHIRAllTypes.ACTIVITYDEFINITION.toCode()) as ActivityDefinition?
    }

    fun getDevices(bundle: Bundle): List<com.varian.fhir.resources.Device> {
        return bundle.getResources(FHIRAllTypes.DEVICE.toCode()) as List<com.varian.fhir.resources.Device>
    }

    fun getValueSet(bundle: Bundle): com.varian.fhir.resources.ValueSet? {
        return bundle.getResource(FHIRAllTypes.VALUESET.toCode()) as com.varian.fhir.resources.ValueSet?
    }
}
