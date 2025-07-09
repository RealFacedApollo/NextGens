package com.muhammaddaffa.nextgens.generators.listeners;

import com.muhammaddaffa.mdlib.utils.LocationUtils;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.database.DatabaseManager;
import com.muhammaddaffa.nextgens.generators.ActiveGenerator;
import com.muhammaddaffa.nextgens.generators.Generator;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Set;
import java.util.UUID;

public class WorldLoadListener implements Listener {

    private final GeneratorManager generatorManager;
    private final DatabaseManager dbm;

    public WorldLoadListener(GeneratorManager generatorManager, DatabaseManager dbm) {
        this.generatorManager = generatorManager;
        this.dbm = dbm;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        // Check if world load restoration is enabled
        if (!NextGens.DEFAULT_CONFIG.getConfig().getBoolean("world-load-restoration.enabled", true)) {
            return;
        }
        
        World loadedWorld = event.getWorld();
        
        // Load generators from the newly loaded world
        loadGeneratorsFromWorld(loadedWorld);
    }

    private void loadGeneratorsFromWorld(World world) {
        String query = "SELECT * FROM " + DatabaseManager.GENERATOR_TABLE;
        this.dbm.executeQuery(query, result -> {
            int loadedCount = 0;
            while (result.next()) {
                // get the uuid string
                String uuidString = result.getString(1);
                // if the uuid string is null, skip the iteration
                if (uuidString == null) {
                    continue;
                }
                
                // otherwise, continue to load
                UUID owner = UUID.fromString(uuidString);
                String serialized = result.getString(2);
                Location location = LocationUtils.deserialize(serialized);
                String generatorId = result.getString(3);
                double timer = result.getDouble(4);
                boolean isCorrupted = result.getBoolean(5);

                // Only process generators from the newly loaded world
                if (location.getWorld() == null || !location.getWorld().equals(world)) {
                    continue;
                }
                
                // Check if this generator is already loaded in memory
                if (this.generatorManager.getActiveGenerator(location) != null) {
                    continue;
                }

                Generator generator = this.generatorManager.getGenerator(generatorId);
                if (generatorId == null || generator == null) {
                    continue;
                }

                // Safety check: Only restore if the location is safe to place a generator
                boolean safeRestoration = NextGens.DEFAULT_CONFIG.getConfig().getBoolean("world-load-restoration.safe-restoration", true);
                if (safeRestoration && !isSafeToRestoreGenerator(location, generator)) {
                    // Log the skipped generator and optionally clean up the database entry
                    Logger.warning("Skipped restoring generator at " + LocationUtils.serialize(location) + 
                                   " - location not safe (block type: " + location.getBlock().getType() + ")");
                    
                    // Optionally clean up orphaned database entries
                    if (NextGens.DEFAULT_CONFIG.getConfig().getBoolean("world-load-restoration.cleanup-orphaned", false)) {
                        cleanupOrphanedGenerator(owner, location);
                    }
                    continue;
                }

                // Register the generator into memory
                ActiveGenerator activeGen = this.generatorManager.registerActiveGenerator(owner, location, generator, timer, isCorrupted);
                
                // Safely set the block type (only if it's air or same generator type)
                if (location.getBlock().getType() != generator.item().getType()) {
                    location.getBlock().setType(generator.item().getType());
                }
                
                loadedCount++;
            }
            
            if (loadedCount > 0) {
                Logger.info("Loaded " + loadedCount + " generators from world: " + world.getName());
            }
        });
    }

    /**
     * Checks if it's safe to restore a generator at the given location
     * @param location The location to check
     * @param generator The generator type to restore
     * @return true if safe to restore, false otherwise
     */
    private boolean isSafeToRestoreGenerator(Location location, Generator generator) {
        // Check if chunk is loaded
        if (!location.getChunk().isLoaded()) {
            return false;
        }

        Material currentBlock = location.getBlock().getType();
        Material generatorBlock = generator.item().getType();

        // Safe cases: Air or the exact generator type we want to place
        if (currentBlock == Material.AIR || currentBlock == generatorBlock) {
            return true;
        }

        // Check if it's another generator type (might be safe to replace)
        if (this.generatorManager.getActiveGenerator(location) != null) {
            return true; // Let the existing generator system handle conflicts
        }

        // Check if current block is a "safe to replace" block type
        Set<Material> safeToReplace = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.STONE,
            Material.COBBLESTONE,
            Material.SAND,
            Material.GRAVEL,
            Material.GRASS,
            Material.TALL_GRASS,
            Material.DEAD_BUSH,
            Material.SNOW,
            Material.ICE
        );

        if (safeToReplace.contains(currentBlock)) {
            Logger.info("Replacing natural block " + currentBlock + " with generator at " + 
                       LocationUtils.serialize(location));
            return true;
        }

        // Dangerous blocks that should NOT be replaced
        Set<Material> dangerousBlocks = Set.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.ENDER_CHEST,
            Material.BARREL,
            Material.SHULKER_BOX,
            Material.SPAWNER,
            Material.BEACON,
            Material.ENCHANTING_TABLE,
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BREWING_STAND,
            Material.HOPPER,
            Material.DROPPER,
            Material.DISPENSER,
            Material.JUKEBOX,
            Material.NOTE_BLOCK,
            Material.LECTERN,
            Material.COMPOSTER,
            Material.CAULDRON,
            Material.WATER_CAULDRON,
            Material.LAVA_CAULDRON,
            Material.POWDER_SNOW_CAULDRON
        );

        if (dangerousBlocks.contains(currentBlock)) {
            return false; // Never replace valuable/functional blocks
        }

        // For all other blocks, be conservative and don't replace
        // This includes player-placed decorative blocks, builds, etc.
        return false;
    }

    /**
     * Cleans up orphaned generator data from the database
     * @param owner The generator owner's UUID
     * @param location The location of the orphaned generator
     */
    private void cleanupOrphanedGenerator(UUID owner, Location location) {
        // Create a dummy ActiveGenerator for deletion
        ActiveGenerator dummy = new ActiveGenerator(owner, location, null);
        this.dbm.deleteGenerator(dummy);
        Logger.info("Cleaned up orphaned generator data at " + LocationUtils.serialize(location));
    }
} 