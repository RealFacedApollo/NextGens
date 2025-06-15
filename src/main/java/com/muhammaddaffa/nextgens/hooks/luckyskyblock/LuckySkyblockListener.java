package com.muhammaddaffa.nextgens.hooks.luckyskyblock;

import id.luckynetwork.luckyskyblock.api.event.AssuredIslandDisbandEvent;
import id.luckynetwork.luckyskyblock.api.event.AssuredIslandKickEvent;
import id.luckynetwork.luckyskyblock.api.event.AssuredIslandQuitEvent;
import id.luckynetwork.luckyskyblock.api.event.AssuredIslandBannedEvent;
import id.luckynetwork.luckyskyblock.api.event.AssuredIslandUnloadEvent;
import id.luckynetwork.luckyskyblock.commons.object.RemoteIsland;
import id.luckynetwork.luckyskyblock.commons.object.RemotePlayer;

import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.generators.ActiveGenerator;
import com.muhammaddaffa.nextgens.generators.Generator;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.refund.RefundManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public record LuckySkyblockListener(GeneratorManager generatorManager, RefundManager refundManager) implements Listener {


    // Assured Events are events that has been guaranteed by majority of instances to happen
    @EventHandler(priority = EventPriority.MONITOR)
    private void onIslandDisband(AssuredIslandDisbandEvent event) {
        if (!event.isLocal()) return;
        for (RemotePlayer member : event.getRemoteIsland().getMembers()) {
            this.check(member);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onIslandKick(AssuredIslandKickEvent event) {
        if (!event.isLocal()) return;
        this.check(event.getRemotePlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onIslandLeave(AssuredIslandQuitEvent event) {
        if (!event.isLocal()) return;
        this.check(event.getRemotePlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onIslandUnload(AssuredIslandUnloadEvent event) {
        if (!event.isLocal()) return;
        this.check(event.getRemotePlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onIslandBan(AssuredIslandBannedEvent event) {
        if (!event.isLocal()) return;
        RemotePlayer player = event.getRemotePlayer();
        this.check(player);
    }

    private void check(RemotePlayer remotePlayer) {
        List<ActiveGenerator> generators = this.generatorManager.getActiveGenerator(remotePlayer.getUniqueId());
        // loop through them all
        for (ActiveGenerator active : generators) {
            Generator generator = active.getGenerator();
            // unregister the generator
            this.generatorManager.unregisterGenerator(active.getLocation());
            // set the block to air
            active.getLocation().getBlock().setType(Material.AIR);
            // check for island pickup option
            if (NextGens.DEFAULT_CONFIG.getConfig().getBoolean("island-pickup")) {
                // give the generator back
                if (player == null) {
                    // if player not online, register it to item join
                    this.refundManager.delayedGiveGeneratorItem(remotePlayer.getUniqueId(), generator.id());
                } else {
                    // if player is online, give them the generators
                    Executor.sync(() -> Common.addInventoryItem(player, generator.createItem(1)));
                }
            }

        }
    }

}
