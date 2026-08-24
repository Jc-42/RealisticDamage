package com.github.jc42.realisticdamage.item;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public abstract class Bandage extends Item {
    private float[] bleedScale; //Where index 0 equates to severity 0 and so on
    private final float[] severityHealSpeedScale = {0.25F, 0.50F, 0.75F, 1.0F}; //Multiplied with the healSpeedScale to get the final
    private float painScale;
    private float maxHealSpeedScale;
    //Higher tier bandages can replace a lower tier bandage already applied to a wound. The old bandage is lost in the process.
    private final int tier;


    public Bandage(Properties properties, float[] bleedScale, float healSpeedScale, int tier) {
        super(properties);
        if(bleedScale.length != 4) throw new RuntimeException("bleedScale must be exactly four elements");
        this.bleedScale = bleedScale;
        this.maxHealSpeedScale = healSpeedScale;
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            Minecraft.getInstance().gui.setScreen(new com.github.jc42.realisticdamage.WoundsScreen(player, player.getItemInHand(hand)));
        }
        return InteractionResult.SUCCESS;
    }

    public float getBleedScale(int severity){
        return bleedScale[severity];
    }

    /**
     * @param severity The severity of the wound
     * @return A float to scale the heal speed, will not be less than 1
     */
    public float getHealSpeedScale(int severity){
        return Math.max(maxHealSpeedScale * severityHealSpeedScale[severity], 1.0F);
    }
}
