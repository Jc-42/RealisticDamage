package com.github.jc42.realisticdamage.item;

import net.minecraft.world.item.Item;

public class LeatherBandage extends Bandage  {
    public LeatherBandage(Item.Properties properties) {
        super(properties, new float[]{0, 0F, .50F, .75F}, 3F);
    }
}
