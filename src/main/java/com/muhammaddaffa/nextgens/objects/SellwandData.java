package com.muhammaddaffa.nextgens.objects;

import org.bukkit.inventory.ItemStack;

public class SellwandData {

    private final ItemStack stack;
    private final double multiplier;

    public SellwandData(ItemStack stack, double multiplier) {
        this.stack = stack;
        this.multiplier = multiplier;
    }

    public ItemStack getStack() {
        return stack;
    }

    public double getMultiplier() {
        return multiplier;
    }

}
