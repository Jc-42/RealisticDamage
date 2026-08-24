package com.github.jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.util.ArrayList;

public class PainCapability implements ValueIOSerializable {
    private int chronicPainLevel = 0;
    private float bleedLevel = 0;
    private float adrenalineLevel = 0;
    private ArrayList<Wound> wounds = new ArrayList<>();
    private ArrayList<double[]> lodgedArrowPositions = new ArrayList<>();

    //Per-player runtime state, not persisted since it's only meaningful for the current session
    private long lastJumpTime = -1;
    private float jumpCooldown = 0;
    private long lastAdrenalineRushTime = -1;
    private boolean lastAdrenalineRushReset = false;

    public long getLastJumpTime() {
        return lastJumpTime;
    }

    public void setLastJumpTime(long lastJumpTime) {
        this.lastJumpTime = lastJumpTime;
    }

    public float getJumpCooldown() {
        return jumpCooldown;
    }

    public void setJumpCooldown(float jumpCooldown) {
        this.jumpCooldown = jumpCooldown;
    }

    public long getLastAdrenalineRushTime() {
        return lastAdrenalineRushTime;
    }

    public void setLastAdrenalineRushTime(long lastAdrenalineRushTime) {
        this.lastAdrenalineRushTime = lastAdrenalineRushTime;
    }

    public boolean isLastAdrenalineRushReset() {
        return lastAdrenalineRushReset;
    }

    public void setLastAdrenalineRushReset(boolean lastAdrenalineRushReset) {
        this.lastAdrenalineRushReset = lastAdrenalineRushReset;
    }

    //Body part ("head" or "chest") of a lethal wound waiting to be applied on the next server tick, or null if none is pending
    private String pendingInstaKillBodyPart = null;

    public String getPendingInstaKillBodyPart() {
        return pendingInstaKillBodyPart;
    }

    public void setPendingInstaKillBodyPart(String pendingInstaKillBodyPart) {
        this.pendingInstaKillBodyPart = pendingInstaKillBodyPart;
    }

    public void addAdrenaline(float amount) {
        this.adrenalineLevel += amount;
    }

    public float getChronicPainLevel() {
        calculateChronicPainLevel();
        return this.chronicPainLevel;
    }

    /**
     * Returns the bleed level of the player, which is equal to the total pain of all wounds (must be scaled before reducing health)
     * @return
     */
    public float getBleedLevel() {
        float totalBleed = 0;

        for (Wound wound : wounds) {
            totalBleed += wound.getBleed();
        }

        return totalBleed;
    }



    public void calculateChronicPainLevel() {
        float maxPain = 0;

        //TODO make this better, maybe make it so that the more wounds you have the slower they heal?
        for(int i = 0; i < wounds.size(); i++){
            if(wounds.get(i).getPain() > maxPain){
                maxPain = wounds.get(i).getPain();
            }
        }
        this.chronicPainLevel = (int) maxPain;
    }

    public float calculateMovementSpeedPain(){
        float maxPain = 0;

        for(int i = 0; i < wounds.size(); i++){
            Wound w = wounds.get(i);
            float adjustedPain = wounds.get(i).getPain();
            adjustedPain *= w.getBodyPart().contains("leg") || w.getBodyPart().contains("foot") ? 1.0F : 0.5F;
            if(adjustedPain > maxPain){
                maxPain = adjustedPain;
            }
        }

        return maxPain;
    }

    public float calcualteMiningSpeedPain(){
        float maxPain = 0;

        for(int i = 0; i < wounds.size(); i++){
            Wound w = wounds.get(i);
            float adjustedPain = wounds.get(i).getPain();
            adjustedPain *= w.getBodyPart().contains("arm") ? 1.0F : 0.5F;
            if(adjustedPain > maxPain){
                maxPain = adjustedPain;
            }
        }

        return maxPain;
    }

    public float calcualteAttackSpeedPain(){
        float maxPain = 0;

        for(int i = 0; i < wounds.size(); i++){
            Wound w = wounds.get(i);
            float adjustedPain = wounds.get(i).getPain();
            adjustedPain *= w.getBodyPart().contains("arm") ? 1.0F : 0.5F;
            if(adjustedPain > maxPain){
                maxPain = adjustedPain;
            }
        }

        return maxPain;
    }
    public float getAdrenalineLevel() {
        return this.adrenalineLevel;
    }

    public void addWound(Wound w){
        wounds.add(w);
    }

    public ArrayList<Wound> getWounds() {
        return wounds;
    }

    public void tickWounds(){
        for(int i = 0; i < wounds.size(); i++){
            if(wounds.get(i).tick() <= 0) wounds.remove(i--);
        }
    }

    public void setAdrenalineLevel(float level) {
        this.adrenalineLevel = level;
    }

    public ArrayList<double[]> getLodgedArrowPositions(){
        return lodgedArrowPositions;
    }

    @Override
    public void serialize(ValueOutput output) {
        output.putFloat("chronicPainLevel", getChronicPainLevel());
        output.putFloat("adrenalineLevel", this.adrenalineLevel);
        output.putFloat("bleedLevel", this.bleedLevel);

        ValueOutput.ValueOutputList woundsList = output.childrenList("wounds");
        for (Wound wound : wounds) {
            wound.serialize(woundsList.addChild());
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        this.chronicPainLevel = (int) input.getFloatOr("chronicPainLevel", 0.0F);
        this.adrenalineLevel = input.getFloatOr("adrenalineLevel", 0.0F);
        this.bleedLevel = input.getFloatOr("bleedLevel", 0.0F);

        this.wounds.clear();
        for (ValueInput woundInput : input.childrenListOrEmpty("wounds")) {
            Wound wound = new Wound("Laceration", 1, "head");
            wound.deserialize(woundInput);
            wounds.add(wound);
        }
    }


}