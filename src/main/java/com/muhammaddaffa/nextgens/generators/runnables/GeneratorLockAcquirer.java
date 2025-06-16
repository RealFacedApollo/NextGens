package com.muhammaddaffa.nextgens.generators.runnables;

import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.redis.RedisGeneratorManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GeneratorLockAcquirer extends BukkitRunnable {

    private static GeneratorLockAcquirer runnable;
    private static final int LOCK_TTL_SECONDS = 30;

    public static void start(RedisGeneratorManager redisGeneratorManager) {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
        
        runnable = new GeneratorLockAcquirer(redisGeneratorManager);
        // Run every 30 seconds (600 ticks)
        runnable.runTaskTimerAsynchronously(NextGens.getInstance(), 20L, 600L);
    }

    public static void stop() {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
    }

    private final RedisGeneratorManager redisGeneratorManager;

    public GeneratorLockAcquirer(RedisGeneratorManager redisGeneratorManager) {
        this.redisGeneratorManager = redisGeneratorManager;
    }

    @Override
    public void run() {
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

            // Collect all generator IDs that need to be processed
            Set<String> allGeneratorIds = new HashSet<>();

            // Get generators for online players in loaded worlds
            for (UUID playerId : onlinePlayers) {
                for (String worldName : loadedWorlds) {
                    Set<String> playerWorldGenerators = redisGeneratorManager.getGeneratorsByOwnerAndWorld(playerId, worldName);
                    if (playerWorldGenerators != null) {
                        allGeneratorIds.addAll(playerWorldGenerators);
                    }
                }
            }

            // Also get generators in loaded worlds (for offline players if online-only is disabled)
            for (String worldName : loadedWorlds) {
                Set<String> worldGenerators = redisGeneratorManager.getGeneratorsByWorld(worldName);
                if (worldGenerators != null) {
                    allGeneratorIds.addAll(worldGenerators);
                }
            }

            Logger.info("Attempting to acquire locks for " + allGeneratorIds.size() + " generators");

            // Try to acquire locks for all generators
            int locksAcquired = 0;
            for (String generatorId : allGeneratorIds) {
                boolean acquired = redisGeneratorManager.acquireLock(generatorId, LOCK_TTL_SECONDS);
                if (acquired) {
                    locksAcquired++;
                }
            }

            Logger.info("Successfully acquired " + locksAcquired + " generator locks out of " + allGeneratorIds.size());

        } catch (Exception ex) {
            Logger.severe("Error in GeneratorLockAcquirer: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

} 