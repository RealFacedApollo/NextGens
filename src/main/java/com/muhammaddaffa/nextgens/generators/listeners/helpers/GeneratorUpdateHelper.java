package com.muhammaddaffa.nextgens.generators.listeners.helpers;

import com.muhammaddaffa.mdlib.hooks.VaultEconomy;
import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.mdlib.xseries.particles.XParticle;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.api.events.generators.GeneratorUpgradeEvent;
import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.objects.Generator;
import com.muhammaddaffa.nextgens.utils.Utils;
import com.muhammaddaffa.nextgens.utils.VisualAction;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class GeneratorUpdateHelper {

    public static void upgradeGenerator(Player player, ActiveGenerator active, Generator generator, Generator nextGenerator) {
        Block block = active.getLocation().getBlock();
        if (nextGenerator == null) {
            NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.no-upgrade");
            // play bass sound
            Utils.bassSound(player);
            return;
        }
        // money check
        if (VaultEconomy.getBalance(player) < generator.cost()) {
            NextGens.DEFAULT_CONFIG.sendMessage(player, "messages.not-enough-money", new Placeholder()
                    .add("{money}", Common.digits(VaultEconomy.getBalance(player)))
                    .add("{upgradecost}", Common.digits(generator.cost()))
                    .add("{remaining}", Common.digits(VaultEconomy.getBalance(player) - generator.cost())));
            // play bass sound
            Utils.bassSound(player);
            return;
        }
        // Check requirements
        List<String> requirementsNotPassed = generator.checkRequirements(player, generator.upgradeRequirements());
        if (!requirementsNotPassed.isEmpty()) {
            Common.sendMessage(player, requirementsNotPassed);
            Utils.bassSound(player);
            return;
        }
        // call the custom events
        GeneratorUpgradeEvent upgradeEvent = new GeneratorUpgradeEvent(generator, player, nextGenerator);
        Bukkit.getPluginManager().callEvent(upgradeEvent);
        if (upgradeEvent.isCancelled()) {
            return;
        }
        // take the money from player
        VaultEconomy.withdraw(player, generator.cost());
        // register the generator again
        NextGens.getInstance().getGeneratorManager().registerGenerator(player, nextGenerator, block);
        // visual actions
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        VisualAction.send(player, config, "generator-upgrade-options", new Placeholder()
                .add("{previous}", generator.displayName())
                .add("{current}", nextGenerator.displayName())
                .add("{cost}", Common.digits(generator.cost())));
        // play particle
        Executor.async(() -> {
            if (NextGens.DEFAULT_CONFIG.getConfig().getBoolean("generator-upgrade-options.particles")) {
                GeneratorParticle.successParticle(block, generator);
            }
        });
        // give cashback to the player
        Utils.performCashback(player, NextGens.getInstance().getUserManager(), generator.cost());
    }

}
