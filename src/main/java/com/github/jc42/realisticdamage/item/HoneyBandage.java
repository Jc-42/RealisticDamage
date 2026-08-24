package com.github.jc42.realisticdamage.item;

import net.minecraft.world.item.Item;

public class HoneyBandage extends Bandage{
    public HoneyBandage(Item.Properties properties){
        super(properties, new float[]{0, 0, 0, 0.20F}, 8, 3);
    }
}
