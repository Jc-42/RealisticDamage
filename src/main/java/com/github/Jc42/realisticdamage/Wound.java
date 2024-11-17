package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;

import java.util.Random;

public class Wound {
    private int severity;
    private String type;
    private boolean isFatal;
    private String bodyPart;
    private int ticksRemaining;
    private int pain;

    /**
     * If severity is set to 3 and type is an open wound there is a 10% chance of the wound being fatal (bleeding cannot be fully stopped)
     *
     * @param type     <h4>The type of wound - stored in lowercase</h4>
     *                 <h6>Open Wounds:</h6>
     *                 <ol>
     *                 <li>Incision - Wound caused by a sharp-edged object</li>
     *                 <li>Laceration - Wound caused by some blunt trauma</li>
     *                 <li>Abrasion - Wound to the top most layer of skin (scrape)</li>
     *                 <li>Puncture - Wound caused by an object puncturing the skin</li>
     *                 </ol>
     *                 <h6>Closed Wounds:</h6>
     *                 <ol>
     *                 <li>Hematoma - Wound caused by blunt force which did not pierce the skin</li>
     *                 <li>Fracture - Wound consisting of a partial or complete break in a bone</li>
     *                 <li>Burn - Wound caused by extreme temperature</li>
     *                 </ol>
     * @param severity The severity of the wound ranging from 1-3
     * @param bodyPart The location of the wound.
     *                 <h6>Can be:</h6>
     *                 <ul>
     *                 <li>Head</li>
     *                 <li>Chest</li>
     *                 <li>Left Arm</li>
     *                 <li>Right Arm</li>
     *                 <li>Left Leg</li>
     *                 <li>Right Leg</li>
     *                 </ul>
     *                 (All converted into lowercase)
     */
    public Wound(String type, int severity, String bodyPart) {
        this.type = type.toLowerCase();
        this.severity = severity;
        this.bodyPart = bodyPart;
        Random r = new Random();
        int isFatalRoll = r.nextInt(101);

        //24000 = 1 day
        if(severity == 1){
            ticksRemaining = 12000; //.5 days
        }
        else if(severity == 2){
            ticksRemaining = 36000; //1.5 days
        }
        else if(severity == 3){
            ticksRemaining = 72000; // 3 days
        }

        //TODO allow the wound to slowly heal and lower its pain on a sqrt(x) scale
        //Set fatal if severity is 3, type is an open wound, and isFatalRoll <= 10
        switch (this.type){
            case "incision":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 1 ? 15 : (this.severity == 2 ? 40 : 85);
            case "laceration":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 1 ? 15 : (this.severity == 2 ? 40 : 85);
            case "abrasion":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 1 ? 5 : (this.severity == 2 ? 20 : 40);
            case "puncture":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 1 ? 5 : (this.severity == 2 ? 40 : 80);
            case "hematoma":
                pain = this.severity == 1 ? 10 : (this.severity == 2 ? 20 : 30);
            case "fracture":
                pain = this.severity == 1 ? 30 : (this.severity == 2 ? 60 : 85);
            case "burn":
                pain = this.severity == 1 ? 10 : (this.severity == 2 ? 20 : 30);
        }

    }

    /**
     * Serializes this Wound object to a CompoundTag.
     * @return A CompoundTag representing this Wound.
     */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("severity", severity);
        tag.putString("type", type);
        tag.putBoolean("isFatal", isFatal);
        tag.putString("bodyPart", bodyPart);
        tag.putInt("ticksRemaining", ticksRemaining);
        tag.putInt("pain", pain);
        return tag;
    }

    /**
     * Deserializes the provided CompoundTag into a Wound object.
     * @param tag The CompoundTag to deserialize.
     */
    public void deserializeNBT(CompoundTag tag) {
        this.severity = tag.getInt("severity");
        this.type = tag.getString("type");
        this.isFatal = tag.getBoolean("isFatal");
        this.bodyPart = tag.getString("bodyPart");
        this.ticksRemaining = tag.getInt("ticksRemaining");
        this.pain = tag.getInt("pain");
    }

    /**
     * Reduces ticksRemaining by one
     * @return true if ticksRemaining is now 0 or less, otherwise false
     */
    public boolean tick(){
        return --ticksRemaining <= 0;
    }

    public int getSeverity() {
        return severity;
    }

    public String getType() {
        return type;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public int getPain(){
        return pain;
    }

    public boolean isFatal() {
        return isFatal;
    }



}
