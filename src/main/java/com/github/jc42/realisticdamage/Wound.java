package com.github.jc42.realisticdamage;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    private final float BASE_BLEED_ONE = damagePerTick(2, 0);
    private final float BASE_BLEED_TWO = damagePerTick(1, 0);
    private final float BASE_BLEED_THREE = damagePerTick(0, 30);
    private final float BASE_BLEED_FATAL = damagePerTick(1, 30);
    //TODO make fatal wounds bleed be 80%? reduced after applying a bandage

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

        //Set fatal if severity is 3, type is an open wound, and isFatalRoll <= 10
        switch (this.type){
            case "laceration":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                    bleed = this.severity == 0 ? BASE_BLEED_ONE : (this.severity == 1 ? BASE_BLEED_TWO : (this.severity == 2 ? BASE_BLEED_THREE : BASE_BLEED_FATAL));
                    break;
            case "abrasion":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = this.severity == 0 ? BASE_BLEED_ONE : (this.severity == 1 ? BASE_BLEED_TWO : (this.severity == 2 ? BASE_BLEED_THREE : BASE_BLEED_FATAL));
                break;
            case "puncture":
                isFatal = isFatalRoll <= 10 && this.severity == 3;
                pain = this.severity == 0 ? 10 : (this.severity == 1 ? 25 : (this.severity == 2 ? 40 : 80));
                bleed = this.severity == 0 ? BASE_BLEED_ONE : (this.severity == 1 ? BASE_BLEED_TWO : (this.severity == 2 ? BASE_BLEED_THREE : BASE_BLEED_FATAL));
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

    public static float damagePerTick(int minutes, int seconds) {
        int totalSeconds = minutes * 60 + seconds;
        int totalTicks = totalSeconds * 20;

        //25 for the 5 saturation hearts
        return 25.0F / totalTicks;
    }

    public void serialize(ValueOutput output) {
        output.putInt("severity", severity);
        output.putString("type", type);
        output.putBoolean("isFatal", isFatal);
        output.putString("bodyPart", bodyPart);
        output.putInt("ticksRemaining", ticksRemaining);
        output.putInt("pain", pain);
        output.putFloat("bleed", bleed);
        output.putInt("posX", posX);
        output.putInt("posY", posY);
    }

    public void deserialize(ValueInput input) {
        this.severity = input.getIntOr("severity", 0);
        this.type = input.getStringOr("type", "");
        this.isFatal = input.getBooleanOr("isFatal", false);
        this.bodyPart = input.getStringOr("bodyPart", "");
        this.ticksRemaining = input.getIntOr("ticksRemaining", 0);
        this.pain = input.getIntOr("pain", 0);
        this.bleed = input.getFloatOr("bleed", 0.0F);
        this.posX = input.getIntOr("posX", 0);
        this.posY = input.getIntOr("posY", 0);
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

    public static final StreamCodec<ByteBuf, Wound> STREAM_CODEC = StreamCodec.of(
            (buffer, wound) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, wound.severity);
                ByteBufCodecs.STRING_UTF8.encode(buffer, wound.type);
                ByteBufCodecs.BOOL.encode(buffer, wound.isFatal);
                ByteBufCodecs.STRING_UTF8.encode(buffer, wound.bodyPart);
                ByteBufCodecs.VAR_INT.encode(buffer, wound.ticksRemaining);
                ByteBufCodecs.VAR_INT.encode(buffer, wound.pain);
                ByteBufCodecs.FLOAT.encode(buffer, wound.bleed);
                ByteBufCodecs.INT.encode(buffer, wound.posX);
                ByteBufCodecs.INT.encode(buffer, wound.posY);
            },
            buffer -> {
                int severity = ByteBufCodecs.VAR_INT.decode(buffer);
                String type = ByteBufCodecs.STRING_UTF8.decode(buffer);
                boolean isFatal = ByteBufCodecs.BOOL.decode(buffer);
                String bodyPart = ByteBufCodecs.STRING_UTF8.decode(buffer);

                Wound wound = new Wound(type, severity, bodyPart);
                wound.isFatal = isFatal;
                wound.ticksRemaining = ByteBufCodecs.VAR_INT.decode(buffer);
                wound.pain = ByteBufCodecs.VAR_INT.decode(buffer);
                wound.bleed = ByteBufCodecs.FLOAT.decode(buffer);
                wound.posX = ByteBufCodecs.INT.decode(buffer);
                wound.posY = ByteBufCodecs.INT.decode(buffer);
                return wound;
            }
    );


}

