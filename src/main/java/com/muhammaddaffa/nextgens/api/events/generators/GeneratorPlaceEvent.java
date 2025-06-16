package com.muhammaddaffa.nextgens.api.events.generators;

import com.muhammaddaffa.nextgens.objects.Generator;
import org.bukkit.entity.Player;

public class GeneratorPlaceEvent extends GeneratorEvent {

    private final Player player;

    public GeneratorPlaceEvent(Generator generator, Player player) {
        super(generator);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

}
