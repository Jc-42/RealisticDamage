package com.github.Jc42.realisticdamage;

import net.minecraft.nbt.CompoundTag;

class PainCapability implements IPainCapability {
    private float chronicPainLevel = 0;
    private float adrenalineLevel = 0;

    @Override
    public void addChronicPain(float amount) {
        this.chronicPainLevel += amount;
    }
    @Override
    public void addAdrenaline(float amount) {
        this.adrenalineLevel += amount;
    }

    @Override
    public float getChronicPainLevel() {
        return this.chronicPainLevel;
    }

    @Override
    public float getAdrenalineLevel() {
        return this.adrenalineLevel;
    }

    @Override
    public void setChronicPainLevel(float level) {
        this.chronicPainLevel = level;
    }

    @Override
    public void setAdrenalineLevel(float level) {
        this.adrenalineLevel = level;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("chronicPainLevel", this.chronicPainLevel);
        tag.putFloat("adrenalineLevel", this.adrenalineLevel);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.chronicPainLevel = nbt.getFloat("chronicPainLevel");
        this.adrenalineLevel = nbt.getFloat("adrenalineLevel");
    }
}
