package com.muhammaddaffa.nextgens.generators.runnables;

import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.mdlib.xseries.XSound;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.utils.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class NotifyTask extends BukkitRunnable {

    private static NotifyTask currentTask;
    private final GeneratorManager generatorManager;

    private NotifyTask(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;
    }

    public static void start(GeneratorManager generatorManager) {
        if (currentTask != null) {
            currentTask.cancel();
        }

        currentTask = new NotifyTask(generatorManager);
        long intervalTicks = TimeUnit.MINUTES.toSeconds(Settings.CORRUPTION_NOTIFY_INTERVAL) * 20L;
        currentTask.runTaskTimerAsynchronously(NextGens.getInstance(), intervalTicks, intervalTicks);
    }

    @Override
    public void run() {
        if (!Settings.CORRUPTION_ENABLED) {
            return;
        }

        try {
            Map<UUID, Integer> corruptedGeneratorCount = collectCorruptedGenerators();
            notifyPlayers(corruptedGeneratorCount);
        } catch (Exception ex) {
            Logger.severe("Error in NotifyTask: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Map<UUID, Integer> collectCorruptedGenerators() {
        Map<UUID, Integer> corruptedCount = new HashMap<>();

        try {
            // Get all online players
            Set<UUID> onlinePlayers = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                onlinePlayers.add(player.getUniqueId());
            }

            // Get all loaded worlds
            Set<String> loadedWorlds = new HashSet<>();
            for (World world : Bukkit.getWorlds()) {
                loadedWorlds.add(world.getName());
            }

            // Collect all generator IDs that this server should process
            Set<String> generatorIds = new HashSet<>();
            
            // Get generators for online players in loaded worlds
            for (UUID playerId : onlinePlayers) {
                for (String worldName : loadedWorlds) {
                    Set<String> playerWorldGenerators = this.generatorManager.getGeneratorsByOwnerAndWorld(playerId, worldName);
                    if (playerWorldGenerators != null) {
                        generatorIds.addAll(playerWorldGenerators);
                    }
                }
            }

            // Also get generators in loaded worlds (for offline players if online-only is disabled)
            for (String worldName : loadedWorlds) {
                Set<String> worldGenerators = this.generatorManager.getGeneratorsByWorld(worldName);
                if (worldGenerators != null) {
                    generatorIds.addAll(worldGenerators);
                }
            }

            // Only process generators that we hold locks for
            for (String generatorId : generatorIds) {
                if (this.generatorManager.holdsLock(generatorId)) {
                    ActiveGenerator generator = this.generatorManager.getActiveGenerator(generatorId);
                    if (generator != null && generator.isCorrupted()) {
                        corruptedCount.merge(generator.getOwner(), 1, Integer::sum);
                    }
                }
            }

        } catch (Exception ex) {
            Logger.severe("Error in NotifyTask.collectCorruptedGenerators: " + ex.getMessage());
            ex.printStackTrace();
        }

        return corruptedCount;
    }

    private void notifyPlayers(Map<UUID, Integer> corruptedGeneratorCount) {
        corruptedGeneratorCount.forEach((owner, count) -> {
            Player player = Bukkit.getPlayer(owner);

            if (player != null) {
                sendNotification(player, count);
            }
        });
    }

    private void sendNotification(Player player, int corruptedCount) {
        Settings.CORRUPTION_NOTIFY_MESSAGE.send(player, new Placeholder().add("{amount}", Common.digits(corruptedCount)));
        player.playSound(player.getLocation(), XSound.BLOCK_NOTE_BLOCK_PLING.get(), 1.0f, 1.0f);
    }

}
