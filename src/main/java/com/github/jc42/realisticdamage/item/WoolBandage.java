package com.github.jc42.realisticdamage.item;

import net.minecraft.world.item.Item;

public class WoolBandage extends Bandage{
    public WoolBandage(Item.Properties properties) {
        super(properties, new float[]{0, 0F, .30F, .50F}, 4F);
    }
}
