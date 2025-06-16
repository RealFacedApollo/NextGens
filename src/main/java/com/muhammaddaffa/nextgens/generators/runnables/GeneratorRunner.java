package com.muhammaddaffa.nextgens.generators.runnables;

import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.LocationUtils;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.api.events.generators.GeneratorGenerateItemEvent;
import com.muhammaddaffa.nextgens.autosell.Autosell;
import com.muhammaddaffa.nextgens.generators.CorruptedHologram;
import com.muhammaddaffa.nextgens.managers.EventManager;
import com.muhammaddaffa.nextgens.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.managers.UserManager;
import com.muhammaddaffa.nextgens.objects.*;
import com.muhammaddaffa.nextgens.redis.RedisGeneratorManager;
import com.muhammaddaffa.nextgens.sell.SellDataCalculator;
import com.muhammaddaffa.nextgens.utils.Settings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GeneratorRunner extends BukkitRunnable {

    private static GeneratorRunner runnable;

    public static void start(RedisGeneratorManager redisGeneratorManager, GeneratorManager generatorManager, 
                            EventManager eventManager, UserManager userManager) {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
        
        runnable = new GeneratorRunner(redisGeneratorManager, generatorManager, eventManager, userManager);
        // Run every 5 ticks (0.25 seconds)
        runnable.runTaskTimerAsynchronously(NextGens.getInstance(), 20L, 5L);
    }

    public static void stop() {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
    }

    private final Map<String, CorruptedHologram> hologramMap = new ConcurrentHashMap<>();
    
    private final RedisGeneratorManager redisGeneratorManager;
    private final GeneratorManager generatorManager;
    private final EventManager eventManager;
    private final UserManager userManager;

    public GeneratorRunner(RedisGeneratorManager redisGeneratorManager, GeneratorManager generatorManager, 
                          EventManager eventManager, UserManager userManager) {
        this.redisGeneratorManager = redisGeneratorManager;
        this.generatorManager = generatorManager;
        this.eventManager = eventManager;
        this.userManager = userManager;
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

            // Collect all generator IDs that this server should process
            Set<String> generatorIds = new HashSet<>();
            
            // Get generators for online players in loaded worlds
            for (UUID playerId : onlinePlayers) {
                for (String worldName : loadedWorlds) {
                    Set<String> playerWorldGenerators = redisGeneratorManager.getGeneratorsByOwnerAndWorld(playerId, worldName);
                    if (playerWorldGenerators != null) {
                        generatorIds.addAll(playerWorldGenerators);
                    }
                }
            }

            // Also get generators in loaded worlds (for offline players if online-only is disabled)
            for (String worldName : loadedWorlds) {
                Set<String> worldGenerators = redisGeneratorManager.getGeneratorsByWorld(worldName);
                if (worldGenerators != null) {
                    generatorIds.addAll(worldGenerators);
                }
            }

            // Process generators that we hold locks for
            for (String generatorId : generatorIds) {
                if (redisGeneratorManager.holdsLock(generatorId)) {
                    processGenerator(generatorId);
                }
            }

        } catch (Exception ex) {
            Logger.severe("Error in GeneratorRunner: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void processGenerator(String generatorId) {
        try {
            ActiveGenerator active = redisGeneratorManager.getActiveGenerator(generatorId);
            if (active == null) {
                return;
            }

            // Get variables
            Generator generator = active.getGenerator();
            Player player = Bukkit.getPlayer(active.getOwner());
            Event event = this.eventManager.getActiveEvent();
            User user = this.userManager.getUser(active.getOwner());
            
            // if generator is invalid or chunk is not loaded, skip it
            if (generator == null || !active.isChunkLoaded()) {
                return;
            }
            
            if (active.getLocation().getWorld() == null ||
                    Settings.BLACKLISTED_WORLDS.contains(active.getLocation().getWorld().getName())) {
                return;
            }
            
            // check for online-only option
            boolean onlineOnly;
            if (generator.onlineOnly() == null) {
                onlineOnly = Settings.ONLINE_ONLY;
            } else {
                onlineOnly = generator.onlineOnly();
            }

            if (onlineOnly) {
                if (player == null || !player.isOnline()) {
                    return;
                }
            }
            
            String serialized = LocationUtils.serialize(active.getLocation());
            
            // check for corruption option
            if (Settings.CORRUPTION_ENABLED && active.isCorrupted()) {
                // check if hologram is enabled
                if (Settings.CORRUPTION_HOLOGRAM && !this.hologramMap.containsKey(serialized)) {
                    CorruptedHologram hologram = new CorruptedHologram(active);
                    // show the hologram
                    Executor.sync(() -> hologram.spawn());
                    // store it on the cache
                    this.hologramMap.put(serialized, hologram);
                }
                return;
            }
            
            // if the generator not corrupt but exists on the hologram map
            CorruptedHologram hologram = this.hologramMap.remove(serialized);
            if (!active.isCorrupted() && hologram != null) {
                Executor.sync(() -> hologram.destroy());
            }
            
            Generator chosenGenerator = generator;
            double interval = generator.interval();
            int dropAmount = 1;
            
            // World multipliers code
            double worldDiscount = NextGens.DEFAULT_CONFIG.getDouble("world-multipliers." + active.getLocation().getWorld().getName() + ".speed-multiplier");
            if (worldDiscount > 0) {
                double discount = (generator.interval() * worldDiscount) / 100;
                interval -= discount;
            }
            
            // Event-related code
            if (event != null) {
                if (event.getType() == Event.Type.GENERATOR_SPEED &&
                        event.getSpeedMultiplier() != null &&
                        !event.getBlacklistedGenerators().contains(generator.id())) {
                    Double boost = event.getSpeedMultiplier();
                    double discount = (generator.interval() * boost) / 100;
                    interval = interval - discount;
                }
                
                if (event.getType() == Event.Type.GENERATOR_UPGRADE &&
                        event.getTierUpgrade() != null &&
                        !event.getBlacklistedGenerators().contains(generator.id())) {
                    Integer amount = event.getTierUpgrade();
                    for (int i = 0; i < amount; i++) {
                        if (chosenGenerator.nextTier() == null) {
                            break;
                        }
                        Generator upgraded = this.generatorManager.getGenerator(chosenGenerator.nextTier());
                        if (upgraded != null) {
                            chosenGenerator = upgraded;
                        }
                    }
                }
                
                if (event.getType() == Event.Type.MIXED_UP &&
                        !event.getBlacklistedGenerators().contains(generator.id())) {
                    chosenGenerator = this.generatorManager.getRandomGenerator();
                }
                
                if (event.getType() == Event.Type.DROP_MULTIPLIER &&
                        event.getDropMultiplier() != null &&
                        !event.getBlacklistedGenerators().contains(generator.id())) {
                    dropAmount = Math.max(1, event.getDropMultiplier());
                }
            }
            
            // add timer
            active.addTimer(0.25);
            
            // check if the generator should drop
            if (active.getTimer() >= interval) {
                // Save updated timer to Redis
                redisGeneratorManager.saveGenerator(active);
                
                // execute drop mechanics
                Block block = active.getLocation().getBlock();
                // execute it in sync task
                Generator finalChosenGenerator = chosenGenerator;
                int finalDropAmount = dropAmount;
                
                Executor.sync(() -> {
                    // set the block to the desired type
                    if (Settings.FORCE_UPDATE_BLOCKS) {
                        block.setType(generator.item().getType());
                    }
                    
                    // Generate the random drop
                    Drop drop = finalChosenGenerator.getRandomDrop();
                    
                    // create the event
                    GeneratorGenerateItemEvent generatorEvent = new GeneratorGenerateItemEvent(finalChosenGenerator, active, drop, finalDropAmount);
                    Bukkit.getPluginManager().callEvent(generatorEvent);
                    
                    if (generatorEvent.isCancelled()) {
                        active.setTimer(0);
                        return;
                    }
                    
                    // Set the drop
                    drop = generatorEvent.getDrop();
                    int realDropAmount = generatorEvent.getDropAmount();
                    
                    if (drop == null) {
                        active.setTimer(0);
                        return;
                    }
                    
                    // Process the drop
                    if (user.isToggleGensAutoSell()) {
                        // Auto-sell the items
                        SellDataCalculator calculator = new SellDataCalculator(user, drop, realDropAmount);
                        calculator.calculate();
                        
                        if (calculator.isSuccessful()) {
                            // Add to autosell
                            Autosell.addToAutosell(user, calculator.getSellData());
                        }
                    } else {
                        // Drop the items
                        if (drop.item() != null) {
                            for (int i = 0; i < realDropAmount; i++) {
                                active.getLocation().getWorld().dropItem(active.getLocation().add(0, 1, 0), drop.item());
                            }
                        }
                    }
                    
                    // Reset timer
                    active.setTimer(0);
                });
            }
            
            // Save generator state periodically
            if (active.getTimer() % 5 == 0) {
                redisGeneratorManager.saveGenerator(active);
            }
            
        } catch (Exception ex) {
            Logger.severe("Error processing generator " + generatorId + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void forceRemoveHologram(ActiveGenerator active) {
        String serialized = LocationUtils.serialize(active.getLocation());
        CorruptedHologram hologram = this.hologramMap.remove(serialized);
        if (hologram != null) {
            Executor.sync(() -> hologram.destroy());
        }
    }

    public void clearHologram() {
        for (CorruptedHologram hologram : this.hologramMap.values()) {
            Executor.sync(() -> hologram.destroy());
        }
        this.hologramMap.clear();
    }

} 