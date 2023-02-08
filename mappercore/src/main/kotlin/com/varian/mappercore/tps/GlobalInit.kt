package com.varian.mappercore.tps

import ca.uhn.fhir.parser.IParser
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.v251.datatype.CX
import ca.uhn.hl7v2.model.v251.segment.MSH
import ca.uhn.hl7v2.model.v251.segment.PID
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import ca.uhn.hl7v2.parser.GenericParser
import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.Message
import com.varian.fhir.resources.Practitioner
import com.varian.mappercore.client.FhirFactory
import com.varian.mappercore.client.HL7EncodeCharacterManager
import com.varian.mappercore.client.RetrofitProvider
import com.varian.mappercore.client.TokenManager
import com.varian.mappercore.client.interfaceapi.serviceclient.ParkService
import com.varian.mappercore.client.interfaceapi.serviceclient.ParkServiceClient
import com.varian.mappercore.client.outboundAriaEvent.serviceclient.AriaEventService
import com.varian.mappercore.client.outboundAriaEvent.serviceclient.AriaEventServiceClient
import com.varian.mappercore.configuration.Configuration
import com.varian.mappercore.configuration.ConfigurationLoader
import com.varian.mappercore.constant.AriaConnectConstant
import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.framework.auth.ExpirationClaimSetVerifier
import com.varian.mappercore.framework.auth.JWTAuthentication
import com.varian.mappercore.framework.helper.CloverLogger
import com.varian.mappercore.framework.helper.MessageMetaData
import com.varian.mappercore.framework.utility.BundleUtility
import com.varian.mappercore.framework.utility.ParametersUtility
import com.varian.mappercore.framework.utility.PatientUtility
import com.varian.mappercore.helper.sqlite.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.StringType
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock

open class GlobalInit public constructor(
    cloverEnv: CloverEnv
) {

    companion object {
        private var instance: GlobalInit? = null
        protected var log: Logger = LogManager.getLogger(GlobalInit::class.java)

        @Synchronized
        fun createInstance(
            cloverEnv: CloverEnv
        ): GlobalInit {
            if (instance == null) {
                log.trace("creating new instance of global init")
                instance = GlobalInit(cloverEnv)
            }
            log.trace("returning existing instance of global init")
            return instance!!
        }
    }//end of companion object

    var masterSiteDirName: String
    var siteDirName: String
    var cloverleafRootName: String
    var mappedSqliteDbName: String
   var localConnection: Connection
    var masterConnection: Connection
    private var processingConfigTable: List<ProcessingConfig>? = null
    private var patientMatchingTable: List<PatientMatching>? = null
    private var patientSearchKeys: String = ""
    private var mergePatientSearchKeys: String = ""
    private var patientDisallowUpdateKeys: String = ""
    var identifierTable: List<Identifier>
    var inboundEventList: List<InboundEvent>
    var configuration: Configuration
    var hl7Parser: GenericParser
    var parser: IParser
    var fhirFactory: FhirFactory
    var bundleUtility: BundleUtility
    var parametersUtility: ParametersUtility
    var patientUtility: PatientUtility
    var tokenManager: TokenManager
    var parkService: ParkService
    var ariaEventService: AriaEventService
    var hl7EncodeCharacterManager: HL7EncodeCharacterManager
    var hl7EncodedCharMap = hashMapOf<String, String>()
    var jwtAuthentication: JWTAuthentication
    var user: Practitioner

    init {
        masterSiteDirName = cloverEnv.masterSiteDirName
        siteDirName = cloverEnv.siteDirName
        cloverleafRootName = cloverEnv.rootName
        mappedSqliteDbName = cloverEnv.tableLookup(AriaConnectConstant.INTERFACES_TABLE, getProcessName(cloverEnv))
        configuration = ConfigurationLoader().configuration
        val masterSqliteRelativePath = Paths.get(masterSiteDirName, configuration.masterSqliteDbName).toString()
        log.info("Master SQlite Database: $masterSqliteRelativePath")
        val masterSqliteConnectionString = "jdbc:sqlite:$masterSqliteRelativePath"
        val localSqliteRelativePath = Paths.get(siteDirName, mappedSqliteDbName).toString()
        log.info("Local site interface mapped SQlite Database: $localSqliteRelativePath")
        val localSqliteConnectionString = "jdbc:sqlite:$localSqliteRelativePath"

        val context: HapiContext = DefaultHapiContext()
        context.modelClassFactory = CanonicalModelClassFactory(configuration.defaultHL7Version)
        hl7Parser = context.genericParser
        hl7Parser.parserConfiguration.isValidating = false

        fhirFactory = FhirFactory()
        fhirFactory.setFhirClient(configuration.ariaFhirServerUrl.toString(), configuration)
        log.info("FHIR server url: ${configuration.ariaFhirServerUrl.toString()}")
        log.info("ARIA event server url: ${configuration.ariaEventsClientUrl.toString()}")
        parser = fhirFactory.getFhirParser()
        this.user = getInterfaceUser()
        jwtAuthentication = JWTAuthentication(configuration, ExpirationClaimSetVerifier(Clock.systemUTC()))
        tokenManager = fhirFactory.getTokenManager()
        parkService = ParkServiceClient(
            RetrofitProvider(
                configuration,
                tokenManager,
                configuration.interfaceApiClientUrl.toString()
            )
        )
        ariaEventService = AriaEventServiceClient(
            RetrofitProvider(
                configuration,
                tokenManager,
                configuration.ariaEventsClientUrl.toString()
            )
        )
        hl7EncodeCharacterManager = getHL7EncodeCharacterManager()

        bundleUtility = fhirFactory.getBundleUtility()
        parametersUtility = fhirFactory.getParametersUtility()
        patientUtility = fhirFactory.getPatientUtility()

        System.setProperty("java.io.tmpdir", "$cloverleafRootName/temp")
        localConnection = DriverManager.getConnection(localSqliteConnectionString)
        masterConnection = DriverManager.getConnection(masterSqliteConnectionString)
        identifierTable =
            SqliteUtility.getValues(masterConnection, AriaConnectConstant.IDENTIFIER_TABLE, null)

        inboundEventList = SqliteUtility.getValues(masterConnection, AriaConnectConstant.Inbound_Event_TABLE, null)

        processingConfigTable = getProcessingConfigTable()
        patientMatchingTable = getPatientMatching()
        patientSearchKeys = getPatientSearchKeys()
        patientDisallowUpdateKeys = getPatientDisallowedUpdateKeys()
        mergePatientSearchKeys = getMergePatientSearchKeys()
    }

    fun getProcessName(cloverEnv: CloverEnv): String {
        try {
            return cloverEnv.processName
        } catch (ex: Exception) {
            return "TEST"
        }
    }

    data class SegmentFieldIdentifierValueKey(val segment: String?, val field: String?, val identifierValue: String?)

    private fun PatientMatching.toSegmentFieldIdentifierValueKey() =
        SegmentFieldIdentifierValueKey(this.Segment, this.Field, this.IdentifierValue)

    data class SegmentFieldKey(val segment: String?, val field: String?)

    private fun PatientMatching.toSegmentFieldKey() = SegmentFieldKey(this.Segment, this.Field)

    open fun getHL7EncodeCharacterManager(): HL7EncodeCharacterManager {
        val fieldSeparator = configuration.hl7EncodeCharacters.fieldSeparator.replace("\\", "\\\\")
        val escapeSequence = configuration.hl7EncodeCharacters.escapeSequence.replace("\\", "\\\\")
        val repetitionSeparator = configuration.hl7EncodeCharacters.repetitionSeparator.replace("\\", "\\\\")
        val componentSeparator = configuration.hl7EncodeCharacters.componentSeparator.replace("\\", "\\\\")
        val subcomponentSeparator = configuration.hl7EncodeCharacters.subcomponentSeparator.replace("\\", "\\\\")
        hl7EncodeCharacterManager = HL7EncodeCharacterManager(
            fieldSeparator,
            escapeSequence,
            repetitionSeparator,
            componentSeparator,
            subcomponentSeparator,
            hl7EncodedCharMap
        )
        return hl7EncodeCharacterManager
    }

    private fun getInterfaceUser(): Practitioner {
        val clientName = this.configuration.clientCredentials.clientName
        return this.fhirFactory.getFhirClient()
            .search("Practitioner", "name", clientName)
            .entry?.find { it.resource.fhirType() == Enumerations.FHIRAllTypes.PRACTITIONER.toCode() }?.resource as Practitioner?
            ?: throw ResourceNotFoundException("Invalid user: $clientName")
    }

    fun getProcessingConfigTable(): List<ProcessingConfig> {
        if (processingConfigTable.isNullOrEmpty()) {
            processingConfigTable =
                SqliteUtility.getValues(localConnection, AriaConnectConstant.PROCESSING_CONFIG_TABLE, null)
        }
        return processingConfigTable!!
    }

    fun getPatientMatching(): List<PatientMatching> {
        if (patientMatchingTable.isNullOrEmpty()) {
            log.info("global init: getting new patient matching")
            val values: MutableMap<String, String> = HashMap()
            values[XlateConstant.SQLITE_IF_NOT_MATCHED] = XlateConstant.IGNORE_ORIGINAL
            values[XlateConstant.SQLITE_SEQUENCE] = XlateConstant.SEQUENCE_MASTER
            values[XlateConstant.SQLITE_MASTER_IN] = XlateConstant.IN_VALUE
            values[XlateConstant.SQLITE_MASTER_OUT] = XlateConstant.OUT_VALUE
            values[XlateConstant.SQLITE_TABLE] = AriaConnectConstant.IDENTIFIER_TABLE

            patientMatchingTable =
                SqliteUtility.getValues(localConnection, AriaConnectConstant.PATIENT_MATCHING_TABLE, null)
            patientMatchingTable!!.forEach {
                it.AriaId?.let { ariaId ->
                    values[XlateConstant.SQLITE_IN_VALUE] = ariaId
                    it.FhirAriaId = SqliteUtility.getLookUpValue(values, localConnection, masterConnection)
                }
            }

            // Validate Patient Matching Table has valid entries
            // Check Segment, Field, IdentifierValue should not be duplicated
            val duplicateIdentifierTypeCodes =
                patientMatchingTable!!.groupBy { it.toSegmentFieldIdentifierValueKey() }.filter { it.value.size > 1 }
                    .map { it.value.first() }
            if (duplicateIdentifierTypeCodes.isNotEmpty()) {
                val duplicates: ArrayList<String> = ArrayList()
                duplicateIdentifierTypeCodes.forEach { duplicates.add("Segment:${it.Segment},Field:${it.Field},IdentifierValue:${it.IdentifierValue}") }

                val errorMessage =
                    "PatientMatching SQLite table has invalid configuration. Duplicate entries present for ${
                        duplicates.joinToString("  ;  ")
                    }"
                log.error("Error occurred while validating the patient matching entries: $errorMessage")
                throw Exception(errorMessage)
            }
            // Check IdentifierValue should not be null, if more than one entry present for Segment, Field
            val nullIdentifierTypeCodes =
                patientMatchingTable!!.groupBy { it.toSegmentFieldKey() }.filter { it.value.size > 1 }
                    .map { it.value }.filter { it.any { pkm -> pkm.IdentifierValue.isNullOrEmpty() } }
                    .flatten().filter { it.IdentifierValue.isNullOrEmpty() }
            if (nullIdentifierTypeCodes.isNotEmpty()) {
                val duplicates: ArrayList<String> = ArrayList()
                nullIdentifierTypeCodes.forEach { duplicates.add("Segment:${it.Segment},Field:${it.Field},IdentifierValue:${it.IdentifierValue}") }

                val errorMessage =
                    "PatientMatching SQLite table has invalid configuration. IdentifierValue is set to null for repeat entries of ${
                        duplicates.joinToString(
                            "  ;  "
                        )
                    }"
                log.error("Error occurred while validating identifier value: $errorMessage")
                throw Exception(errorMessage)
            }
        } else {
            log.info("returning old patient matching")
        }
        return patientMatchingTable!!
    }

    fun getPatientSearchKeys(): String {
        if (patientSearchKeys.isNullOrEmpty()) {
            patientSearchKeys = ""
            getPatientMatching()
                .filter { it.IsUsedForFinding == "1" && it.Segment == "PID" && it.FhirAriaId != null }
                .map { it.FhirAriaId!! }
                .forEach { patientSearchKeys = patientSearchKeys.plus(it).plus(" ") }
            patientSearchKeys = patientSearchKeys.trimEnd()
        }
        return patientSearchKeys
    }

    fun getMergePatientSearchKeys(): String {
        if (mergePatientSearchKeys.isEmpty()) {
            mergePatientSearchKeys = ""
            getPatientMatching()
                .filter { it.IsUsedForFinding == "1" && it.Segment == "MRG" && it.FhirAriaId != null }
                .map { it.FhirAriaId!! }
                .forEach { mergePatientSearchKeys = mergePatientSearchKeys.plus(it).plus(" ") }
            mergePatientSearchKeys = mergePatientSearchKeys.trimEnd()
        }
        return mergePatientSearchKeys
    }

    fun getPatientDisallowedUpdateKeys(): String {
        if (patientDisallowUpdateKeys.isEmpty()) {
            patientDisallowUpdateKeys = ""
            getPatientMatching()
                .filter { it.AllowUpdate == "0" && it.Segment == "PID" && it.FhirAriaId != null }
                .map { it.FhirAriaId!! }
                .forEach { patientDisallowUpdateKeys = patientDisallowUpdateKeys.plus(it).plus(" ") }
            patientDisallowUpdateKeys = patientDisallowUpdateKeys.trimEnd()
        }
        return patientDisallowUpdateKeys
    }

    open fun getPatientIdandMessageControlId(message: Message?):MessageMetaData
    {
        var messageMetaData = MessageMetaData()
        log.info("Setting message Meta Data...$messageMetaData")
        try {
            if (message?.content!!.contains("MSH", true)) {
                val hl7Message = hl7Parser.parse(message?.content)
                val msh: MSH = hl7Message.get("MSH") as MSH
                val segments = hl7Message?.getAll("PID")
                var idValue: String = ""
                var ariaId1FieldValue: Int = 2
                var patientMatchingForAriaId1 = SqliteUtility.getValues<PatientMatching>(
                    localConnection,
                    AriaConnectConstant.PATIENT_MATCHING_TABLE,
                    null
                ).filter { (it.AriaId == "ARIAID1") && (it.Segment == "PID") && (it.IsUsedForFinding == "1") }
                if (patientMatchingForAriaId1.any()) {
                    ariaId1FieldValue = patientMatchingForAriaId1.first().Field!!.toInt()
                }
                segments?.map { seg -> seg as PID }?.forEach { pid ->
                    val field = pid.getField(ariaId1FieldValue)
                    if (field.any())
                        idValue = field.map { m -> m as CX }
                            .first { f -> f.cx5_IdentifierTypeCode.value == patientMatchingForAriaId1.first().IdentifierValue }.cx1_IDNumber.value
                }
                messageMetaData.patientId = idValue
                messageMetaData.messageCtrId = msh.messageControlID.toString()
            }
            return messageMetaData
        }
        catch (ex: Exception)
        {
            log.error("Error occurs while parsing HL7 message: ${ex.message}")
            log.debug("At: ${ex.stackTraceToString()}")
            return messageMetaData
        }
    }

    fun reset() {
        processingConfigTable = null
        patientMatchingTable = null
        mergePatientSearchKeys = ""
        patientDisallowUpdateKeys = ""
        mergePatientSearchKeys = ""
    }


}
