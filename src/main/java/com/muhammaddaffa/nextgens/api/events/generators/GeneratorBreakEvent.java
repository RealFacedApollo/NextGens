package com.muhammaddaffa.nextgens.api.events.generators;

import com.muhammaddaffa.nextgens.objects.Generator;
import org.bukkit.entity.Player;

public class GeneratorBreakEvent extends GeneratorEvent {

    private final Player player;

    public GeneratorBreakEvent(Generator generator, Player player) {
        super(generator);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

}
