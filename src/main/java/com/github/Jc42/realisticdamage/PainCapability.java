package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;

class PainCapability implements IPainCapability {
    private float chronicPainLevel = 0;
    private float adrenalineLevel = 0;
    private ArrayList<Wound> wounds = new ArrayList<>();
    private ArrayList<double[]> lodgedArrowPositions = new ArrayList<>();

//    @Override
//    public void addChronicPain(float amount) {
//        this.chronicPainLevel += amount;
//    }

    @Override
    public void addAdrenaline(float amount) {
        this.adrenalineLevel += amount;
    }

    @Override
    public float getChronicPainLevel() {
        calculateChronicPainLevel();
        return this.chronicPainLevel;
    }

    @Override
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

    @Override
    public float getAdrenalineLevel() {
        return this.adrenalineLevel;
    }

    @Override
    public void addWound(Wound w){
        wounds.add(w);
    }

    @Override
    public ArrayList<Wound> getWounds() {
        return wounds;
    }

    @Override
    public void tickWounds(){
        for(int i = 0; i < wounds.size(); i++){
            if(wounds.get(i).tick() <= 0) wounds.remove(i--);
        }
    }

//    @Override
//    public void setChronicPainLevel(float level) {
//        this.chronicPainLevel = level;
//    }

    @Override
    public void setAdrenalineLevel(float level) {
        this.adrenalineLevel = level;
    }

    @Override
    public ArrayList<double[]> getLodgedArrowPositions(){
        return lodgedArrowPositions;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("chronicPainLevel", getChronicPainLevel());
        tag.putFloat("adrenalineLevel", this.adrenalineLevel);

        ListTag woundsTag = new ListTag();
        for (Wound wound : wounds) {
            woundsTag.add(wound.serializeNBT());
        }
        tag.put("wounds", woundsTag);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.chronicPainLevel = nbt.getFloat("chronicPainLevel");
        this.adrenalineLevel = nbt.getFloat("adrenalineLevel");

        this.wounds.clear();
        ListTag woundsTag = nbt.getList("wounds", 10); // 10 for CompoundTag
        for (int i = 0; i < woundsTag.size(); i++) {
            CompoundTag woundTag = woundsTag.getCompound(i);
            Wound wound = new Wound("Incision", 1, "Head"); // Create a new instance
            wound.deserializeNBT(woundTag); // Populate it from the tag
            wounds.add(wound);
        }
    }


}
