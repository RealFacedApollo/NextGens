package com.muhammaddaffa.nextgens.generators.listeners.helpers;

import com.muhammaddaffa.mdlib.hooks.VaultEconomy;
import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.objects.Generator;
import com.muhammaddaffa.nextgens.utils.Utils;
import com.muhammaddaffa.nextgens.utils.VisualAction;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class GeneratorFixHelper {

    public static void fixGenerator(Player player, ActiveGenerator active, Generator generator) {
        Block block = active.getLocation().getBlock();
        // money check
        if (VaultEconomy.getBalance(player) < generator.fixCost()) {
            NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.not-enough-money", new Placeholder()
                    .add("{money}", Common.digits(VaultEconomy.getBalance(player)))
                    .add("{upgradecost}", Common.digits(generator.fixCost()))
                    .add("{remaining}", Common.digits(VaultEconomy.getBalance(player) - generator.fixCost())));
            // play bass sound
            Utils.bassSound(player);
            return;
        }
        // take the money from player
        VaultEconomy.withdraw(player, generator.fixCost());
        // fix the generator
        active.setCorrupted(false);
        // visual actions
        VisualAction.send(player, NextGens.DEFAULT_CONFIG.getConfig(), "corrupt-fix-options", new Placeholder()
                .add("{gen}", generator.displayName())
                .add("{cost}", Common.digits(generator.fixCost())));
        // play particle
        Executor.async(() -> {
            if (NextGens.DEFAULT_CONFIG.getConfig().getBoolean("corrupt-fix-options.particles")) {
                GeneratorParticle.successParticle(block, generator);
            }
            // Save the generator
            Executor.async(() -> NextGens.getInstance().getGeneratorManager().saveActiveGenerator(active));
        });
        // give cashback to the player
        Utils.performCashback(player, NextGens.getInstance().getUserManager(), generator.fixCost());
    }

}
