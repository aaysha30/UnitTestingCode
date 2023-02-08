package com.varian.mappercore.client

import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.model.AbstractMessage
import ca.uhn.hl7v2.model.AbstractStructure
import ca.uhn.hl7v2.model.v251.datatype.CE
import ca.uhn.hl7v2.model.v251.group.SIU_S12_PATIENT
import ca.uhn.hl7v2.model.v251.group.SIU_S12_RESOURCES
import ca.uhn.hl7v2.model.v251.segment.*
import ca.uhn.hl7v2.parser.DefaultModelClassFactory
import ca.uhn.hl7v2.parser.ModelClassFactory

/**
 *
 * Represents a SIU_S12 message structure (see chapter 10.4). This structure contains the
 * following elements:
 *
 *  * 1: MSH (Message Header) ** **
 *  * 2: SCH (Scheduling Activity Information) ** **
 *  * 3: TQ1 (Timing/Quantity) **optional repeating**
 *  * 4: NTE (Notes and Comments) **optional repeating**
 *  * 5: SIU_S12_PATIENT (a Group object) **optional repeating**
 *  * 6: SIU_S12_RESOURCES (a Group object) ** repeating**
 *
 */
//@SuppressWarnings("unused")
class SIU_OUT @JvmOverloads constructor(factory: ModelClassFactory = DefaultModelClassFactory()) :
    AbstractMessage(factory) {
    private fun init(factory: ModelClassFactory) {
        try {
            this.add(MSH::class.java, true, false)
            this.add(SCH::class.java, false, false)
            this.add(NTE::class.java, false, true)
            this.add(PID::class.java, false, true)
            this.add(PV1::class.java, false, true)
            this.add(RGS::class.java, false, true)
            this.add(AIS::class.java, false, true)
            this.add(AIL::class.java, false, true)
            this.add(AIP::class.java, false, true)
            this.add(TQ1::class.java, false, true)
            this.add(SIU_S12_PATIENT::class.java, false, true)
            this.add(SIU_S12_RESOURCES::class.java, false, true)
        } catch (e: HL7Exception) {
            log.error("Unexpected error creating SIU_S12 - this is probably a bug in the source code generator.", e)
        }
    }
    fun getCE(siu: SIU_OUT?): Array<CE>? {
        return arrayOf(CE(siu))
    }

    /**
     * Returns "2.5.1"
     */
    override fun getVersion(): String {
        return "2.5.1"
    }

    @Throws(HL7Exception::class)
    fun insertMSH(structure: MSH?, rep: Int) {
        super.insertRepetition("MSH", structure, rep)
    }

    @Throws(HL7Exception::class)
    fun insertSCH(structure: SCH?, rep: Int) {
        super.insertRepetition("SCH", structure, rep)
    }

    val pID: PID
        get() = getTyped("PID", PID::class.java)

    @Throws(HL7Exception::class)
    fun insertPID(structure: PID?, rep: Int) {
        super.insertRepetition("PID", structure, rep)
    }

    val pV1: PV1
        get() = getTyped("PV1", PV1::class.java)

    @Throws(HL7Exception::class)
    fun insertPV1(structure: PV1?, rep: Int) {
        super.insertRepetition("PV1", structure, rep)
    }

    val rGS: RGS
        get() = getTyped("RGS", RGS::class.java)

    @Throws(HL7Exception::class)
    fun insertRGS(structure: RGS?, rep: Int) {
        super.insertRepetition("RGS", structure, rep)
    }

    val aIS: AIS
        get() = getTyped("AIS", AIS::class.java)

    @Throws(HL7Exception::class)
    fun insertAIS(structure: AIS?, rep: Int) {
        super.insertRepetition("AIS", structure, rep)
    }

    val aIL: AIL
        get() = getTyped("AIL", AIL::class.java)

    fun getAILRep(rep: Int): AIL {

        return getTyped("AIL", rep, AIL::class.java)
    }

    @Throws(HL7Exception::class)
    fun insertAIL(structure: AIL?, rep: Int) {
        super.insertRepetition("AIL", structure, rep)
    }
    val aIP: AIP
        get() = getTyped("AIP", AIP::class.java)

    fun getAIPRep(rep: Int): AIP {

        return getTyped("AIP", rep, AIP::class.java)
    }

    @Throws(HL7Exception::class)
    fun insertAIP(structure: AIP?, rep: Int) {
        super.insertRepetition("AIP", structure, rep)
    }


    /**
     *
     *
     * Returns
     * MSH (Message Header) - creates it if necessary
     *
     *
     *
     */
    val mSH: MSH
        get() = getTyped("MSH", MSH::class.java)

    /**
     *
     *
     * Returns
     * SCH (Scheduling Activity Information) - creates it if necessary
     *
     *
     *
     */
    val sCH: SCH
        get() = getTyped("SCH", SCH::class.java)

    /**
     *
     *
     * Returns
     * the first repetition of
     * TQ1 (Timing/Quantity) - creates it if necessary
     *
     *
     *
     */
    val tQ1: TQ1
        get() = getTyped("TQ1", TQ1::class.java)

    /**
     *
     *
     * Returns a specific repetition of
     * TQ1 (Timing/Quantity) - creates it if necessary
     *
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     * greater than the number of existing repetitions.
     */
    fun getTQ1(rep: Int): TQ1 {
        return getTyped("TQ1", rep, TQ1::class.java)
    }

    /**
     *
     *
     * Returns the number of existing repetitions of TQ1
     *
     *
     */
    val tQ1Reps: Int
        get() = getReps("TQ1")

    /**
     *
     *
     * Returns a non-modifiable List containing all current existing repetitions of TQ1.
     *
     *
     *
     *
     * Note that unlike [.getTQ1], this method will not create any reps
     * if none are already present, so an empty list may be returned.
     *
     *
     */
    @get:Throws(HL7Exception::class)
    val tQ1All: List<TQ1>
        get() = getAllAsList("TQ1", TQ1::class.java)

    /**
     *
     *
     * Inserts a specific repetition of TQ1 (Timing/Quantity)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertTQ1(structure: TQ1?, rep: Int) {
        super.insertRepetition("TQ1", structure, rep)
    }

    /**
     *
     *
     * Inserts a specific repetition of TQ1 (Timing/Quantity)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertTQ1(rep: Int): TQ1 {
        return super.insertRepetition("TQ1", rep) as TQ1
    }

    /**
     *
     *
     * Removes a specific repetition of TQ1 (Timing/Quantity)
     *
     *
     *
     * @see AbstractGroup.removeRepetition
     */
    @Throws(HL7Exception::class)
    fun removeTQ1(rep: Int): TQ1 {
        return super.removeRepetition("TQ1", rep) as TQ1
    }

    /**
     *
     *
     * Returns
     * the first repetition of
     * NTE (Notes and Comments) - creates it if necessary
     *
     *
     *
     */
    val nTE: NTE
        get() = getTyped("NTE", NTE::class.java)

    /**
     *
     *
     * Returns a specific repetition of
     * NTE (Notes and Comments) - creates it if necessary
     *
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     * greater than the number of existing repetitions.
     */
    fun getNTE(rep: Int): NTE {
        return getTyped("NTE", rep, NTE::class.java)
    }

    /**
     *
     *
     * Returns the number of existing repetitions of NTE
     *
     *
     */
    val nTEReps: Int
        get() = getReps("NTE")

    /**
     *
     *
     * Returns a non-modifiable List containing all current existing repetitions of NTE.
     *
     *
     *
     *
     * Note that unlike [.getNTE], this method will not create any reps
     * if none are already present, so an empty list may be returned.
     *
     *
     */
    @get:Throws(HL7Exception::class)
    val nTEAll: List<NTE>
        get() = getAllAsList("NTE", NTE::class.java)

    /**
     *
     *
     * Inserts a specific repetition of NTE (Notes and Comments)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertNTE(structure: NTE?, rep: Int) {
        super.insertRepetition("NTE", structure, rep)
    }

    /**
     *
     *
     * Inserts a specific repetition of NTE (Notes and Comments)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertNTE(rep: Int): NTE {
        return super.insertRepetition("NTE", rep) as NTE
    }

    /**
     *
     *
     * Removes a specific repetition of NTE (Notes and Comments)
     *
     *
     *
     * @see AbstractGroup.removeRepetition
     */
    @Throws(HL7Exception::class)
    fun removeNTE(rep: Int): NTE {
        return super.removeRepetition("NTE", rep) as NTE
    }

    /**
     *
     *
     * Returns
     * the first repetition of
     * PATIENT (a Group object) - creates it if necessary
     *
     *
     *
     */
    val pATIENT: SIU_S12_PATIENT
        get() = getTyped("PATIENT", SIU_S12_PATIENT::class.java)

    /**
     *
     *
     * Returns a specific repetition of
     * PATIENT (a Group object) - creates it if necessary
     *
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     * greater than the number of existing repetitions.
     */
    fun getPATIENT(rep: Int): SIU_S12_PATIENT {
        return getTyped("PATIENT", rep, SIU_S12_PATIENT::class.java)
    }

    /**
     *
     *
     * Returns the number of existing repetitions of PATIENT
     *
     *
     */
    val pATIENTReps: Int
        get() = getReps("PATIENT")

    /**
     *
     *
     * Returns a non-modifiable List containing all current existing repetitions of PATIENT.
     *
     *
     *
     *
     * Note that unlike [.getPATIENT], this method will not create any reps
     * if none are already present, so an empty list may be returned.
     *
     *
     */
    @get:Throws(HL7Exception::class)
    val pATIENTAll: List<SIU_S12_PATIENT>
        get() = getAllAsList("PATIENT", SIU_S12_PATIENT::class.java)

    /**
     *
     *
     * Inserts a specific repetition of PATIENT (a Group object)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertPATIENT(structure: SIU_S12_PATIENT?, rep: Int) {
        super.insertRepetition("PATIENT", structure, rep)
    }

    /**
     *
     *
     * Inserts a specific repetition of PATIENT (a Group object)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertPATIENT(rep: Int): SIU_S12_PATIENT {
        return super.insertRepetition("PATIENT", rep) as SIU_S12_PATIENT
    }

    /**
     *
     *
     * Removes a specific repetition of PATIENT (a Group object)
     *
     *
     *
     * @see AbstractGroup.removeRepetition
     */
    @Throws(HL7Exception::class)
    fun removePATIENT(rep: Int): SIU_S12_PATIENT {
        return super.removeRepetition("PATIENT", rep) as SIU_S12_PATIENT
    }

    /**
     *
     *
     * Returns
     * the first repetition of
     * RESOURCES (a Group object) - creates it if necessary
     *
     *
     *
     */
    val rESOURCES: SIU_S12_RESOURCES
        get() = getTyped("RESOURCES", SIU_S12_RESOURCES::class.java)

    /**
     *
     *
     * Returns a specific repetition of
     * RESOURCES (a Group object) - creates it if necessary
     *
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     * greater than the number of existing repetitions.
     */
    fun getRESOURCES(rep: Int): SIU_S12_RESOURCES {
        return getTyped("RESOURCES", rep, SIU_S12_RESOURCES::class.java)
    }

    /**
     *
     *
     * Returns the number of existing repetitions of RESOURCES
     *
     *
     */
    val rESOURCESReps: Int
        get() = getReps("RESOURCES")

    /**
     *
     *
     * Returns a non-modifiable List containing all current existing repetitions of RESOURCES.
     *
     *
     *
     *
     * Note that unlike [.getRESOURCES], this method will not create any reps
     * if none are already present, so an empty list may be returned.
     *
     *
     */
    @get:Throws(HL7Exception::class)
    val rESOURCESAll: List<SIU_S12_RESOURCES>
        get() = getAllAsList("RESOURCES", SIU_S12_RESOURCES::class.java)

    /**
     *
     *
     * Inserts a specific repetition of RESOURCES (a Group object)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertRESOURCES(structure: SIU_S12_RESOURCES?, rep: Int) {
        super.insertRepetition("RESOURCES", structure, rep)
    }

    /**
     *
     *
     * Inserts a specific repetition of RESOURCES (a Group object)
     *
     *
     *
     * @see AbstractGroup.insertRepetition
     */
    @Throws(HL7Exception::class)
    fun insertRESOURCES(rep: Int): SIU_S12_RESOURCES {
        return super.insertRepetition("RESOURCES", rep) as SIU_S12_RESOURCES
    }

    /**
     *
     *
     * Removes a specific repetition of RESOURCES (a Group object)
     *
     *
     *
     * @see AbstractGroup.removeRepetition
     */
    @Throws(HL7Exception::class)
    fun removeRESOURCES(rep: Int): SIU_S12_RESOURCES {
        return super.removeRepetition("RESOURCES", rep) as SIU_S12_RESOURCES
    }
    /**
     * Creates a new SIU_S12 message with custom ModelClassFactory.
     */
    /**
     * Creates a new SIU_S12 message with DefaultModelClassFactory.
     */
    init {
        init(factory)
    }
}