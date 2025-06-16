package com.muhammaddaffa.nextgens.listeners;

import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.Generator;
import com.muhammaddaffa.nextgens.managers.GeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public record PlayerJoinListener(
        GeneratorManager generatorManager
) implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        // check if option is enabled
        if (config.getBoolean("first-join-generator.enabled") && !player.hasPlayedBefore()) {
            // get the generator
            Generator generator = this.generatorManager.getGenerator(config.getString("first-join-generator.generator"));
            // give the generator to the player
            if (generator != null) {
                ItemStack stack = generator.createItem(config.getInt("first-join-generator.amount", 1));
                Common.addInventoryItem(player, stack);
            }
            // execute the commands
            for (String command : config.getStringList("first-join-generator.commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
            }
        }
    }

}
