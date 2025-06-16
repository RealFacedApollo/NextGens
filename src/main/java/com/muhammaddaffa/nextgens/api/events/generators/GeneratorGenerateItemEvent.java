package com.muhammaddaffa.nextgens.api.events.generators;

import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.objects.Drop;
import com.muhammaddaffa.nextgens.objects.Generator;

public class GeneratorGenerateItemEvent extends GeneratorEvent{

    private final ActiveGenerator activeGenerator;
    private Drop drop;
    private int dropAmount;
    private boolean dropItem = true;

    public GeneratorGenerateItemEvent(Generator generator, ActiveGenerator activeGenerator, Drop drop, int dropAmount) {
        super(generator);
        this.activeGenerator = activeGenerator;
        this.drop = drop;
        this.dropAmount = dropAmount;
    }

    public ActiveGenerator getActiveGenerator() {
        return activeGenerator;
    }

    public Drop getDrop() {
        return drop;
    }

    public void setDrop(Drop drop) {
        this.drop = drop;
    }

    public int getDropAmount() {
        return dropAmount;
    }

    public void setDropAmount(int dropAmount) {
        this.dropAmount = dropAmount;
    }

    public boolean isDropItem() {
        return dropItem;
    }

    public void setDropItem(boolean dropItem) {
        this.dropItem = dropItem;
    }

}
