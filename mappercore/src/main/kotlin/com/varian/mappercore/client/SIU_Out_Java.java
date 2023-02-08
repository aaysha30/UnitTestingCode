package com.varian.mappercore.client;


import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.v251.datatype.CE;
import ca.uhn.hl7v2.model.v251.group.*;
import ca.uhn.hl7v2.model.v251.segment.*;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.parser.ModelClassFactory;
import ca.uhn.hl7v2.parser.DefaultModelClassFactory;
import ca.uhn.hl7v2.model.*;
import ca.uhn.hl7v2.parser.Parser;


/**
 * <p>Represents a SIU_S12 message structure (see chapter 10.4). This structure contains the
 * following elements: </p>
 * <ul>
 * <li>1: MSH (Message Header) <b> </b> </li>
 * <li>2: SCH (Scheduling Activity Information) <b> </b> </li>
 * <li>3: TQ1 (Timing/Quantity) <b>optional repeating</b> </li>
 * <li>4: NTE (Notes and Comments) <b>optional repeating</b> </li>
 * <li>5: SIU_S12_PATIENT (a Group object) <b>optional repeating</b> </li>
 * <li>6: SIU_S12_RESOURCES (a Group object) <b> repeating</b> </li>
 * </ul>
 */
//@SuppressWarnings("unused")
public class SIU_Out_Java extends AbstractMessage  {

    /**
     * Creates a new SIU_S12 message with DefaultModelClassFactory.
     */
    public SIU_Out_Java() {
        this(new DefaultModelClassFactory());
    }

    /**
     * Creates a new SIU_S12 message with custom ModelClassFactory.
     */
    public SIU_Out_Java(ModelClassFactory factory) {
        super(factory);
        init(factory);
    }

    private void init(ModelClassFactory factory) {
        try {
            this.add(MSH.class, true, false);
            this.add(SCH.class, true, false);
            this.add(NTE.class, false, true);
            this.add(PID.class, false, true);
            this.add(PV1.class, false, true);
            this.add(RGS.class, false, true);
            this.add(AIS.class, false, true);
            this.add(AIP.class, false, true);
            this.add(TQ1.class, false, true);
            this.add(SIU_S12_PATIENT.class, false, true);
            this.add(SIU_S12_RESOURCES.class, false, true);
        } catch(HL7Exception e) {
            log.error("Unexpected error creating SIU_S12 - this is probably a bug in the source code generator.", e);
        }
    }


    /**
     * Returns "2.5.1"
     */
    public String getVersion() {
        return "2.5.1";
    }

    public void insertMSH(MSH structure, int rep) throws HL7Exception {
        super.insertRepetition( "MSH", structure, rep);
    }

    public void insertSCH(SCH structure, int rep) throws HL7Exception {
        super.insertRepetition( "SCH", structure, rep);
    }

    public MSH getPID() {
        return getTyped("PID", MSH.class);
    }
    public void insertPID(PID structure, int rep) throws HL7Exception {
        super.insertRepetition( "PID", structure, rep);
    }

    public MSH getPV1() {
        return getTyped("PV1", MSH.class);
    }
    public void insertPV1(PV1 structure, int rep) throws HL7Exception {
        super.insertRepetition( "PV1", structure, rep);
    }

    public CE getCE(SIU_OUT siu)
    {
        return new CE(siu);
    }
    public static String GetHL7(SIU_OUT siu)
    {
        HapiContext context = new DefaultHapiContext();
        Parser parser = context.getPipeParser();
        String encodedMessage = null;
        try {
            encodedMessage = parser.encode(siu);
        } catch (HL7Exception e) {
            throw new RuntimeException(e);
        }
        return  encodedMessage;
    }


    public MSH getRGS() {
        return getTyped("RGS", MSH.class);
    }
    public void insertRGS(RGS structure, int rep) throws HL7Exception {
        super.insertRepetition( "RGS", structure, rep);
    }

    public MSH getAIS() {
        return getTyped("AIS", MSH.class);
    }
    public void insertAIS(AIS structure, int rep) throws HL7Exception {
        super.insertRepetition( "AIS", structure, rep);
    }

    public MSH getAIP() {
        return getTyped("AIP", MSH.class);
    }
    public void insertAIP(AIP structure, int rep) throws HL7Exception {
        super.insertRepetition( "AIP", structure, rep);
    }

    /**
     * <p>
     * Returns
     * MSH (Message Header) - creates it if necessary
     * </p>
     *
     *
     */
    public MSH getMSH() {
        return getTyped("MSH", MSH.class);
    }





    /**
     * <p>
     * Returns
     * SCH (Scheduling Activity Information) - creates it if necessary
     * </p>
     *
     *
     */
    public SCH getSCH() {
        return getTyped("SCH", SCH.class);
    }





    /**
     * <p>
     * Returns
     * the first repetition of
     * TQ1 (Timing/Quantity) - creates it if necessary
     * </p>
     *
     *
     */
    public TQ1 getTQ1() {
        return getTyped("TQ1", TQ1.class);
    }


    /**
     * <p>
     * Returns a specific repetition of
     * TQ1 (Timing/Quantity) - creates it if necessary
     * </p>
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     *     greater than the number of existing repetitions.
     */
    public TQ1 getTQ1(int rep) {
        return getTyped("TQ1", rep, TQ1.class);
    }

    /**
     * <p>
     * Returns the number of existing repetitions of TQ1
     * </p>
     *
     */
    public int getTQ1Reps() {
        return getReps("TQ1");
    }

    /**
     * <p>
     * Returns a non-modifiable List containing all current existing repetitions of TQ1.
     * <p>
     * <p>
     * Note that unlike {@link #getTQ1()}, this method will not create any reps
     * if none are already present, so an empty list may be returned.
     * </p>
     *
     */
    public java.util.List<TQ1> getTQ1All() throws HL7Exception {
        return getAllAsList("TQ1", TQ1.class);
    }

    /**
     * <p>
     * Inserts a specific repetition of TQ1 (Timing/Quantity)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public void insertTQ1(TQ1 structure, int rep) throws HL7Exception {
        super.insertRepetition( "TQ1", structure, rep);
    }


    /**
     * <p>
     * Inserts a specific repetition of TQ1 (Timing/Quantity)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public TQ1 insertTQ1(int rep) throws HL7Exception {
        return (TQ1)super.insertRepetition("TQ1", rep);
    }


    /**
     * <p>
     * Removes a specific repetition of TQ1 (Timing/Quantity)
     * </p>
     *
     *
     * @see AbstractGroup#removeRepetition(String, int)
     */
    public TQ1 removeTQ1(int rep) throws HL7Exception {
        return (TQ1)super.removeRepetition("TQ1", rep);
    }




    /**
     * <p>
     * Returns
     * the first repetition of
     * NTE (Notes and Comments) - creates it if necessary
     * </p>
     *
     *
     */
    public NTE getNTE() {
        return getTyped("NTE", NTE.class);
    }


    /**
     * <p>
     * Returns a specific repetition of
     * NTE (Notes and Comments) - creates it if necessary
     * </p>
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     *     greater than the number of existing repetitions.
     */
    public NTE getNTE(int rep) {
        return getTyped("NTE", rep, NTE.class);
    }

    /**
     * <p>
     * Returns the number of existing repetitions of NTE
     * </p>
     *
     */
    public int getNTEReps() {
        return getReps("NTE");
    }

    /**
     * <p>
     * Returns a non-modifiable List containing all current existing repetitions of NTE.
     * <p>
     * <p>
     * Note that unlike {@link #getNTE()}, this method will not create any reps
     * if none are already present, so an empty list may be returned.
     * </p>
     *
     */
    public java.util.List<NTE> getNTEAll() throws HL7Exception {
        return getAllAsList("NTE", NTE.class);
    }

    /**
     * <p>
     * Inserts a specific repetition of NTE (Notes and Comments)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public void insertNTE(NTE structure, int rep) throws HL7Exception {
        super.insertRepetition( "NTE", structure, rep);
    }


    /**
     * <p>
     * Inserts a specific repetition of NTE (Notes and Comments)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public NTE insertNTE(int rep) throws HL7Exception {
        return (NTE)super.insertRepetition("NTE", rep);
    }


    /**
     * <p>
     * Removes a specific repetition of NTE (Notes and Comments)
     * </p>
     *
     *
     * @see AbstractGroup#removeRepetition(String, int)
     */
    public NTE removeNTE(int rep) throws HL7Exception {
        return (NTE)super.removeRepetition("NTE", rep);
    }




    /**
     * <p>
     * Returns
     * the first repetition of
     * PATIENT (a Group object) - creates it if necessary
     * </p>
     *
     *
     */
    public SIU_S12_PATIENT getPATIENT() {
        return getTyped("PATIENT", SIU_S12_PATIENT.class);
    }


    /**
     * <p>
     * Returns a specific repetition of
     * PATIENT (a Group object) - creates it if necessary
     * </p>
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     *     greater than the number of existing repetitions.
     */
    public SIU_S12_PATIENT getPATIENT(int rep) {
        return getTyped("PATIENT", rep, SIU_S12_PATIENT.class);
    }

    /**
     * <p>
     * Returns the number of existing repetitions of PATIENT
     * </p>
     *
     */
    public int getPATIENTReps() {
        return getReps("PATIENT");
    }

    /**
     * <p>
     * Returns a non-modifiable List containing all current existing repetitions of PATIENT.
     * <p>
     * <p>
     * Note that unlike {@link #getPATIENT()}, this method will not create any reps
     * if none are already present, so an empty list may be returned.
     * </p>
     *
     */
    public java.util.List<SIU_S12_PATIENT> getPATIENTAll() throws HL7Exception {
        return getAllAsList("PATIENT", SIU_S12_PATIENT.class);
    }

    /**
     * <p>
     * Inserts a specific repetition of PATIENT (a Group object)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public void insertPATIENT(SIU_S12_PATIENT structure, int rep) throws HL7Exception {
        super.insertRepetition( "PATIENT", structure, rep);
    }


    /**
     * <p>
     * Inserts a specific repetition of PATIENT (a Group object)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public SIU_S12_PATIENT insertPATIENT(int rep) throws HL7Exception {
        return (SIU_S12_PATIENT)super.insertRepetition("PATIENT", rep);
    }


    /**
     * <p>
     * Removes a specific repetition of PATIENT (a Group object)
     * </p>
     *
     *
     * @see AbstractGroup#removeRepetition(String, int)
     */
    public SIU_S12_PATIENT removePATIENT(int rep) throws HL7Exception {
        return (SIU_S12_PATIENT)super.removeRepetition("PATIENT", rep);
    }




    /**
     * <p>
     * Returns
     * the first repetition of
     * RESOURCES (a Group object) - creates it if necessary
     * </p>
     *
     *
     */
    public SIU_S12_RESOURCES getRESOURCES() {
        return getTyped("RESOURCES", SIU_S12_RESOURCES.class);
    }


    /**
     * <p>
     * Returns a specific repetition of
     * RESOURCES (a Group object) - creates it if necessary
     * </p>
     *
     *
     * @param rep The repetition index (0-indexed, i.e. the first repetition is at index 0)
     * @throws HL7Exception if the repetition requested is more than one
     *     greater than the number of existing repetitions.
     */
    public SIU_S12_RESOURCES getRESOURCES(int rep) {
        return getTyped("RESOURCES", rep, SIU_S12_RESOURCES.class);
    }

    /**
     * <p>
     * Returns the number of existing repetitions of RESOURCES
     * </p>
     *
     */
    public int getRESOURCESReps() {
        return getReps("RESOURCES");
    }

    /**
     * <p>
     * Returns a non-modifiable List containing all current existing repetitions of RESOURCES.
     * <p>
     * <p>
     * Note that unlike {@link #getRESOURCES()}, this method will not create any reps
     * if none are already present, so an empty list may be returned.
     * </p>
     *
     */
    public java.util.List<SIU_S12_RESOURCES> getRESOURCESAll() throws HL7Exception {
        return getAllAsList("RESOURCES", SIU_S12_RESOURCES.class);
    }

    /**
     * <p>
     * Inserts a specific repetition of RESOURCES (a Group object)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public void insertRESOURCES(SIU_S12_RESOURCES structure, int rep) throws HL7Exception {
        super.insertRepetition( "RESOURCES", structure, rep);
    }


    /**
     * <p>
     * Inserts a specific repetition of RESOURCES (a Group object)
     * </p>
     *
     *
     * @see AbstractGroup#insertRepetition(Structure, int)
     */
    public SIU_S12_RESOURCES insertRESOURCES(int rep) throws HL7Exception {
        return (SIU_S12_RESOURCES)super.insertRepetition("RESOURCES", rep);
    }


    /**
     * <p>
     * Removes a specific repetition of RESOURCES (a Group object)
     * </p>
     *
     *
     * @see AbstractGroup#removeRepetition(String, int)
     */
    public SIU_S12_RESOURCES removeRESOURCES(int rep) throws HL7Exception {
        return (SIU_S12_RESOURCES)super.removeRepetition("RESOURCES", rep);
    }



}

