package com.muhammaddaffa.nextgens.api.events.generators;

import com.muhammaddaffa.nextgens.objects.Generator;
import org.bukkit.entity.Player;

public class GeneratorUpgradeEvent extends GeneratorEvent {

    private final Player player;
    private final Generator nextGenerator;

    public GeneratorUpgradeEvent(Generator generator, Player player, Generator nextGenerator) {
        super(generator);
        this.player = player;
        this.nextGenerator = nextGenerator;
    }

    public Player getPlayer() {
        return player;
    }

    public Generator getNextGenerator() {
        return nextGenerator;
    }

}
