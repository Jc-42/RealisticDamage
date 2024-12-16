package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.ArrayList;

public interface IPainCapability extends INBTSerializable<CompoundTag> {
    //void addChronicPain(float amount);
    void addAdrenaline(float amount);
    void addWound(Wound w);
    void tickWounds();
    float getChronicPainLevel();
    void calculateChronicPainLevel();
    float getAdrenalineLevel();
    ArrayList<Wound> getWounds();
    ArrayList<double[]> getLodgedArrowPositions();
   // void setChronicPainLevel(float level);
    void setAdrenalineLevel(float level);
}
