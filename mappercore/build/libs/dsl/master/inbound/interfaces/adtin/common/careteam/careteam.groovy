@HandlerFor(source = "aria", subject = "CareTeam") dsl

def parameters = bundleUtility.getParameters(bundle)
def allowPrimaryUpdateForPractitioner = parametersUtility.allowPrimaryUpdateForPractitioner(parameters)
cloverLogger.log(2, "Allowing the primary practioner update, is: $allowPrimaryUpdateForPractitioner", messageMetaData)
def careTeam = bundleUtility.getCareTeam(bundle)
def careTeamDomain = bundleUtility.getCareTeam(bundleDomain)

if (patientCreated) {
    HospitalDepartmentHelper.updatePrimaryDepartment(careTeam, careTeamDomain)
}

def autoCreateReferringPhysician = parametersUtility.getAutoCreateReferringPhysician(parameters)
cloverLogger.log(2, "autoCreateReferringPhysician config value is $autoCreateReferringPhysician", messageMetaData)
def removePrimaryCareProvider = CareTeamHelper.isPrimaryCareProviderNULL(careTeam)
if (removePrimaryCareProvider) {
    cloverLogger.log(2, "primary care provider will be removed for patient", messageMetaData)
    CareTeamHelper.removeNULLProviders(careTeam)
}

CareTeamHelper.resolvePhysiciansReferences(careTeam, clientDecor, outcome, autoCreateReferringPhysician, allowPrimaryUpdateForPractitioner)
if (careTeamDomain != null) {
    CareTeamHelper.map(careTeam, careTeamDomain, allowPrimaryUpdateForPractitioner, removePrimaryCareProvider)
    clientDecor.updateSafely(careTeamDomain)
}
