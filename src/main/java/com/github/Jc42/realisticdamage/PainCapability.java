package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;

public class PainCapability{
    private float chronicPainLevel = 0;
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

    public float getBleedLevel() {
        float maxBleed = 0;

        for(int i = 0; i < wounds.size(); i++){
            if(wounds.get(i).getBleed() > maxBleed){
                maxBleed = wounds.get(i).getPain();
            }
        }
        return maxBleed;
    }



    public void calculateChronicPainLevel() {
        float maxPain = 0;

        //TODO make this better, maybe make it so that the more wounds you have the slower they heal?
        for(int i = 0; i < wounds.size(); i++){
            if(wounds.get(i).getPain() > maxPain){
                maxPain = wounds.get(i).getPain();
            }
        }
        this.chronicPainLevel = maxPain;
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

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("chronicPainLevel", getChronicPainLevel());
        tag.putFloat("adrenalineLevel", this.adrenalineLevel);
        tag.putFloat("bleedLevel", this.bleedLevel);

        ListTag woundsTag = new ListTag();
        for (Wound wound : wounds) {
            woundsTag.add(wound.serializeNBT());
        }
        tag.put("wounds", woundsTag);

        return tag;
    }

    public void deserializeNBT(CompoundTag nbt) {
        this.chronicPainLevel = nbt.getFloat("chronicPainLevel");
        this.adrenalineLevel = nbt.getFloat("adrenalineLevel");
        this.bleedLevel = nbt.getFloat("bleedLevel");

        this.wounds.clear();
        ListTag woundsTag = nbt.getList("wounds", 10); // 10 for CompoundTag
        for (int i = 0; i < woundsTag.size(); i++) {
            CompoundTag woundTag = woundsTag.getCompound(i);
            Wound wound = new Wound("Laceration", 1, "Head"); // Create a new instance
            wound.deserializeNBT(woundTag); // Populate it from the tag
            wounds.add(wound);
        }
    }


}