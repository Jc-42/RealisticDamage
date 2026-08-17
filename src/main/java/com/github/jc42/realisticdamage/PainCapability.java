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

        for (int i = 0; i < wounds.size(); i++) {
            totalBleed += wounds.get(i).getBleed();
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
            Wound wound = new Wound("Laceration", 1, "Head");
            wound.deserialize(woundInput);
            wounds.add(wound);
        }
    }


}