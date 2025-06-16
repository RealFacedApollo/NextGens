package com.muhammaddaffa.nextgens.generators.listeners.helpers;

import com.muhammaddaffa.mdlib.xseries.particles.XParticle;
import com.muhammaddaffa.nextgens.objects.Generator;
import org.bukkit.block.Block;

public class GeneratorParticle {

    public static void successParticle(Block block, Generator generator) {
        block.getWorld().spawnParticle(XParticle.BLOCK.get(), block.getLocation().add(0.5, 0.85, 0.5), 30, 0.5, 0.5, 0.5, 2.5, generator.item().getType().createBlockData());
        block.getWorld().spawnParticle(XParticle.HAPPY_VILLAGER.get(), block.getLocation().add(0.5, 0.85, 0.5), 50, 0.5, 0.5, 0.5, 2.5);
    }

}
