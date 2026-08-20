package com.github.jc42.realisticdamage.item;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public abstract class Bandage extends Item {
    public Bandage(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            Minecraft.getInstance().gui.setScreen(new com.github.jc42.realisticdamage.WoundsScreen(player, player.getItemInHand(hand)));
        }
        return InteractionResult.SUCCESS;
    }
}
