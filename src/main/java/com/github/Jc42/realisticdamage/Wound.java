package com.github.Jc42.realisticdamage;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

public class Wound {
    private int posX;
    private int posY;
    private int severity;
    private String type;
    private boolean isFatal;
    private String bodyPart;
    private int ticksRemaining;
    private int pain;
    private float bleed;
    private final int SEVERITY_ZERO_TICKS = 800; // ~40 seconds
    private final int SEVERITY_ONE_TICKS = 12000; // ~10 minutes
    private final int SEVERITY_TWO_TICKS = 36000; // ~30 minutes
    private final int SEVERITY_THREE_TICKS = 72000; // ~1 hour

    /**
     * If severity is set to 3 and type is an open wound there is a 10% chance of the wound being fatal (bleeding cannot be fully stopped)
     *
     * @param type     <h4>The type of wound - stored in lowercase</h4>
     *                 <h6>Open Wounds:</h6>
     *                 <ol>
     *                 <li>Laceration - Wound caused by a sharp-edged or blunt object piercing the skin</li>
     *                 <li>Abrasion - Wound to the top most layer of skin (scrape)</li>
     *                 <li>Puncture - Wound caused by an object puncturing the skin</li>
     *                 </ol>
     *                 <h6>Closed Wounds:</h6>
     *                 <ol>
     *                 <li>Hematoma - Wound caused by blunt force which did not pierce the skin</li>
     *                 <li>Fracture - Wound consisting of a partial or complete break in a bone</li>
     *                 <li>Burn - Wound caused by extreme temperature</li>
     *                 </ol>
     * @param severity The severity of the wound ranging from 0-3
     * @param bodyPart The location of the wound - stored in lowercase.
     *                 <h6>Can be:</h6>
     *                 <ul>
     *                 <li>Head</li>
     *                 <li>Chest</li>
     *                 <li>Left Arm</li>
     *                 <li>Right Arm</li>
     *                 <li>Left Leg</li>
     *                 <li>Right Leg</li>
     *                 <li>Left Foot</li>
     *                 <li>Right Foot</li>
     *                 </ul>
     *
     */
    public Wound(String type, int severity, String bodyPart) {
        this.type = type.toLowerCase();
        this.severity = severity;
        this.bodyPart = bodyPart;
        Random r = new Random();
        int isFatalRoll = r.nextInt(101);

        //24000 = 1 day
        if(severity == 0){
            ticksRemaining = SEVERITY_ZERO_TICKS; // .03 days
        }
        if(severity == 1){
            ticksRemaining = SEVERITY_ONE_TICKS; //.5 days
        }
        else if(severity == 2){
            ticksRemaining = SEVERITY_TWO_TICKS; //1.5 days
        }
        else if(severity == 3){
            ticksRemaining = SEVERITY_THREE_TICKS; // 3 days
        }

        //TODO add a bleed amount to the wounds
        //Set fatal if severity is 3, type is an open wound, and isFatalRoll <= 10
        switch (this.type){
            case "laceration":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = this.severity == 0 ? .000033f : (this.severity == 1 ? .000056f : (this.severity == 2 ? .000083f : .000167f));
                //                           5 mins to death                3 mins                          2 mins 1 mins
                break;
            case "abrasion":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = this.severity == 0 ? .000033f : (this.severity == 1 ? .000056f : (this.severity == 2 ? .000083f : .000167f));
                break;
            case "puncture":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = this.severity == 0 ? .000033f : (this.severity == 1 ? .000056f : (this.severity == 2 ? .000083f : .000167f));
                break;
            case "hematoma":
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = 0;
                break;
            case "fracture":
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = 0;
                break;
            case "burn":
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = 0;
                break;
        }

        setRandomPosition(bodyPart);

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
        tag.putFloat("bleed", bleed);
        tag.putInt("posX", posX);
        tag.putInt("posY", posY);
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
        this.bleed = tag.getFloat("bleed");
        this.posX = tag.getInt("posX");
        this.posY = tag.getInt("posY");
    }

    /**
     * Reduces ticksRemaining by one
     * @return ticksRemaining
     */
    public int tick(){
        return --ticksRemaining;
    }

    private void setRandomPosition(String bodyPart) {
        int sizeOffset = 1; // Prevent overlap with the player outline. Calculated   based on the wound size in WoundsScreen

        // These ranges will need adjustment based on your UI layout
        Random r = new Random();
        switch(bodyPart.toLowerCase()) {
            case "head":
                posX = 49 + r.nextInt(24 - sizeOffset); // 49-72 x range
                posY = 33 + r.nextInt(22 - sizeOffset); // 15-40 y range
                break;
            case "chest":
                posX = 49 + r.nextInt(24 - sizeOffset); // 49-72 x range
                posY = 57 + r.nextInt(38 - sizeOffset); // 57-94 y range
                break;
            case "left arm":
                posX = 75 + r.nextInt(11 - sizeOffset); // 75-85 x range
                posY = 57 + r.nextInt(38 - sizeOffset); // 45-85 y range
                break;
            case "right arm":
                posX = 36 + r.nextInt(11 - sizeOffset); // 36-65  x range
                posY = 57 + r.nextInt(38 - sizeOffset); // 57-94 y range
                break;
            case "left leg":
                posX = 64 + r.nextInt(9 - sizeOffset); // 65-85 x range
                posY = 97 + r.nextInt(31 - sizeOffset); // 90-140 y range
                break;
            case "right leg":
                posX = 49 + r.nextInt(9 - sizeOffset); // 90-110 x range
                posY = 97 + r.nextInt(31 - sizeOffset); // 90-140 y range
                break;
            case "left foot":
                posX = 64 + r.nextInt(9 - sizeOffset); // 65-85 x range
                posY = 128 + r.nextInt(6 - sizeOffset); // 140-160 y range
                break;
            case "right foot":
                posX = 49 + r.nextInt(9 - sizeOffset); // 90-110 x range
                posY = 128 + r.nextInt(6 - sizeOffset); // 140-160 y range
                break;
        }
    }

    public int getTicksRemaining(){
        return ticksRemaining;
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

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }


    /**
     *
     * @return the original pain of this wound
     */
    public int getBasePain(){
        return pain;
    }

    /**
     *
     * @return the pain of this wound accounting for how much it healed
     */
    public float getPain(){
        float ticksPercentage = 1 - ((float)ticksRemaining / (this.severity == 0 ? SEVERITY_ZERO_TICKS : (this.severity == 1 ? SEVERITY_ONE_TICKS : (this.severity == 2 ? SEVERITY_TWO_TICKS : SEVERITY_THREE_TICKS))));
        return (float)pain * (float)Math.sqrt(-ticksPercentage + 1);
    }

    public float getBleed(){
        return bleed;
    }

    public boolean isFatal() {
        return isFatal;
    }

    public static boolean validWoundType(String woundType){
        if(woundType.equalsIgnoreCase("laceration") ||
                woundType.equals("abrasion") ||
                woundType.equals("puncture") ||
                woundType.equals("hematoma") ||
                woundType.equals("fracture") ||
                woundType.equals("burn") ||
                woundType.equals("blunt")){
            return true;
        }
        return false;
    }


}

