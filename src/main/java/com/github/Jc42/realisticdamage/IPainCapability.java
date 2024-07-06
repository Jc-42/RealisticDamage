package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

public interface IPainCapability extends INBTSerializable<CompoundTag> {
    void addChronicPain(float amount);
    void addAdrenaline(float amount);
    float getChronicPainLevel();
    float getAdrenalineLevel();
    void setChronicPainLevel(float level);
    void setAdrenalineLevel(float level);
}
