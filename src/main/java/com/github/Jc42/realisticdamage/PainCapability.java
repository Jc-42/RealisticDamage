package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

import java.util.ArrayList;
import java.util.Optional;

@AutoRegisterCapability
public class PainCapability{
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
        this.chronicPainLevel = nbt.getFloat("chronicPainLevel").orElse(0.0F).intValue();
        this.adrenalineLevel = nbt.getFloat("adrenalineLevel").orElse(0.0F);
        this.bleedLevel = nbt.getFloat("bleedLevel").orElse(0.0F);

        this.wounds.clear();
        ListTag woundsTag = nbt.getList("wounds").orElse(new ListTag());
        for (int i = 0; i < woundsTag.size(); i++) {
            CompoundTag woundTag = woundsTag.getCompound(i).orElse(new CompoundTag());
            Wound wound = new Wound("Laceration", 1, "Head"); // Create a new instance
            wound.deserializeNBT(woundTag); // Populate it from the tag
            wounds.add(wound);
        }
    }


}