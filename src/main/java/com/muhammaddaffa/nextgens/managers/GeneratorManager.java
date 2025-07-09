package com.muhammaddaffa.nextgens.managers;

import com.google.gson.Gson;
import com.muhammaddaffa.mdlib.utils.LocationUtils;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.objects.Generator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Transaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GeneratorManager {

    private static final String GENERATOR_PREFIX = "nextgens:generator:";
    private static final String GENERATOR_OWNER_PREFIX = "nextgens:generators:owner:";
    private static final String GENERATOR_WORLD_PREFIX = "nextgens:generators:world:";
    private static final String GENERATOR_COUNT_PREFIX = "nextgens:generator:count:";
    private static final String GENERATOR_LOCK_PREFIX = "nextgens:generator:lock:";
    private static final String GENERATOR_OWNER_INDEX = "nextgens:owners";
    private static final String GENERATOR_WORLD_INDEX = "nextgens:worlds";
    
    private final DataManager dataManager;
    private final Gson gson;
    
    // Local cache for active generators that this server is managing
    private final Map<String, ActiveGenerator> localActiveGenerators = new ConcurrentHashMap<>();

    public GeneratorManager(DataManager dataManager) {
        this.dataManager = dataManager;
        this.gson = new Gson();
    }

    /**
     * Get all generator IDs for a specific owner and world
     */
    public Set<String> getGeneratorsByOwnerAndWorld(UUID owner, String worldName) {
        return redisManager.executeWithJedis(jedis -> {
            String key = GENERATOR_OWNER_PREFIX + owner.toString() + ":" + worldName;
            return jedis.smembers(key);
        });
    }

    /**
     * Get all generator IDs in a specific world
     */
    public Set<String> getGeneratorsByWorld(String worldName) {
        return redisManager.executeWithJedis(jedis -> {
            String key = GENERATOR_WORLD_PREFIX + worldName;
            return jedis.smembers(key);
        });
    }

    /**
     * Get all generator IDs for a specific owner across all worlds - OPTIMIZED VERSION
     */
    public Set<String> getGeneratorsByOwner(UUID owner) {
        return redisManager.executeWithJedis(jedis -> {
            // Get all worlds this owner has generators in
            String ownerWorldsKey = GENERATOR_OWNER_PREFIX + owner.toString() + ":worlds";
            Set<String> worlds = jedis.smembers(ownerWorldsKey);
            
            if (worlds.isEmpty()) {
                return new HashSet<>();
            }
            
            Set<String> allGenerators = new HashSet<>();
            for (String world : worlds) {
                String ownerKey = GENERATOR_OWNER_PREFIX + owner.toString() + ":" + world;
                allGenerators.addAll(jedis.smembers(ownerKey));
            }
            
            return allGenerators;
        });
    }

    @Nullable
    public ActiveGenerator getActiveGenerator(@NotNull Location location) {
        return getActiveGenerator(LocationUtils.serialize(location));
    }

    @Nullable
    public ActiveGenerator getActiveGenerator(@Nullable Block block) {
        if (block == null) return null;
        return getActiveGenerator(block.getLocation());
    }

    @Nullable
    public ActiveGenerator getActiveGenerator(String serialized) {
        // Check local cache first
        ActiveGenerator local = localActiveGenerators.get(serialized);
        if (local != null) {
            return local;
        }
        
        // Query Redis
        return redisManager.executeWithJedis(jedis -> {
            String generatorJson = jedis.get(GENERATOR_PREFIX + serialized);
            if (generatorJson != null) {
                return gson.fromJson(generatorJson, ActiveGenerator.class);
            }
            return null;
        });
    }

    @NotNull
    public List<ActiveGenerator> getActiveGenerator(Player player) {
        return getActiveGenerator(player.getUniqueId());
    }

    @NotNull
    public List<ActiveGenerator> getActiveGenerator(UUID uuid) {
        Set<String> generatorIds = getGeneratorsByOwner(uuid);
        if (generatorIds == null || generatorIds.isEmpty()) {
            return new ArrayList<>();
        }

        return redisManager.executeWithJedis(jedis -> {
            List<ActiveGenerator> generators = new ArrayList<>();
            for (String generatorId : generatorIds) {
                String generatorJson = jedis.get(GENERATOR_PREFIX + generatorId);
                if (generatorJson != null) {
                    ActiveGenerator generator = gson.fromJson(generatorJson, ActiveGenerator.class);
                    generators.add(generator);
                }
            }
            return generators;
        });
    }

    /**
     * Register generator with ATOMIC transaction to ensure data consistency
     */
    public ActiveGenerator registerGenerator(UUID owner, @NotNull Generator generatorType, @NotNull Block block) {
        String serialized = LocationUtils.serialize(block.getLocation());
        String worldName = block.getWorld().getName();
        
        return redisManager.executeWithJedis(jedis -> {
            // Check if generator already exists
            String existingJson = jedis.get(GENERATOR_PREFIX + serialized);
            ActiveGenerator active;
            
            if (existingJson != null) {
                // Update existing generator (no transaction needed for single update)
                active = gson.fromJson(existingJson, ActiveGenerator.class);
                active.setGenerator(generatorType);
                String generatorJson = gson.toJson(active);
                jedis.set(GENERATOR_PREFIX + serialized, generatorJson);
            } else {
                // Create new generator with ATOMIC transaction
                active = new ActiveGenerator(owner, block.getLocation(), generatorType);
                String generatorJson = gson.toJson(active);
                
                // Use transaction to ensure atomicity
                Transaction transaction = jedis.multi();
                try {
                    // Save generator data
                    transaction.set(GENERATOR_PREFIX + serialized, generatorJson);
                    
                    // Add to owner index
                    String ownerKey = GENERATOR_OWNER_PREFIX + owner.toString() + ":" + worldName;
                    transaction.sadd(ownerKey, serialized);
                    
                    // Add to world index
                    String worldKey = GENERATOR_WORLD_PREFIX + worldName;
                    transaction.sadd(worldKey, serialized);
                    
                    // Track owner's worlds
                    String ownerWorldsKey = GENERATOR_OWNER_PREFIX + owner.toString() + ":worlds";
                    transaction.sadd(ownerWorldsKey, worldName);
                    
                    // Increment generator count
                    transaction.incr(GENERATOR_COUNT_PREFIX + owner.toString());
                    
                    // Add to global indexes for efficient queries
                    transaction.sadd(GENERATOR_OWNER_INDEX, owner.toString());
                    transaction.sadd(GENERATOR_WORLD_INDEX, worldName);
                    
                    // Execute transaction
                    transaction.exec();
                    
                    Logger.info("Successfully registered generator for owner " + owner + " at " + serialized);
                } catch (Exception e) {
                    transaction.discard();
                    Logger.severe("Failed to register generator: " + e.getMessage());
                    throw new RuntimeException("Generator registration failed", e);
                }
            }
            
            return active;
        });
    }

    /**
     * Unregister generator with ATOMIC transaction to ensure data consistency
     */
    public void unregisterGenerator(@Nullable Block block) {
        if (block == null) return;
        unregisterGenerator(block.getLocation());
    }

    public void unregisterGenerator(Location location) {
        unregisterGenerator(LocationUtils.serialize(location));
    }

    public void unregisterGenerator(String serialized) {
        redisManager.executeWithJedis(jedis -> {
            // Get generator data first
            String generatorJson = jedis.get(GENERATOR_PREFIX + serialized);
            if (generatorJson != null) {
                ActiveGenerator generator = gson.fromJson(generatorJson, ActiveGenerator.class);
                String worldName = generator.getLocation().getWorld().getName();
                String ownerStr = generator.getOwner().toString();
                
                // Use transaction to ensure atomicity
                Transaction transaction = jedis.multi();
                try {
                    // Remove generator data
                    transaction.del(GENERATOR_PREFIX + serialized);
                    
                    // Remove from owner index
                    String ownerKey = GENERATOR_OWNER_PREFIX + ownerStr + ":" + worldName;
                    transaction.srem(ownerKey, serialized);
                    
                    // Remove from world index
                    String worldKey = GENERATOR_WORLD_PREFIX + worldName;
                    transaction.srem(worldKey, serialized);
                    
                    // Decrement generator count
                    transaction.decr(GENERATOR_COUNT_PREFIX + ownerStr);
                    
                    // Remove any locks
                    transaction.del(GENERATOR_LOCK_PREFIX + serialized);
                    
                    // Check if owner has no more generators in this world
                    long remainingInWorld = jedis.scard(ownerKey);
                    if (remainingInWorld <= 1) { // Will be 0 after transaction
                        String ownerWorldsKey = GENERATOR_OWNER_PREFIX + ownerStr + ":worlds";
                        transaction.srem(ownerWorldsKey, worldName);
                    }
                    
                    // Execute transaction
                    transaction.exec();
                    
                    // Remove from local cache
                    localActiveGenerators.remove(serialized);
                    
                    Logger.info("Successfully unregistered generator at " + serialized);
                } catch (Exception e) {
                    transaction.discard();
                    Logger.severe("Failed to unregister generator: " + e.getMessage());
                    throw new RuntimeException("Generator unregistration failed", e);
                }
            }
        });
    }

    public void removeAllGenerator(Player player) {
        removeAllGenerator(player.getUniqueId());
    }

    public void removeAllGenerator(UUID uuid) {
        Set<String> generatorIds = getGeneratorsByOwner(uuid);
        if (generatorIds != null && !generatorIds.isEmpty()) {
            // Use batch operation for efficiency
            redisManager.executeWithJedis(jedis -> {
                Transaction transaction = jedis.multi();
                try {
                    for (String generatorId : generatorIds) {
                        transaction.del(GENERATOR_PREFIX + generatorId);
                        transaction.del(GENERATOR_LOCK_PREFIX + generatorId);
                    }
                    
                    // Clear all owner-related keys
                    String ownerWorldsKey = GENERATOR_OWNER_PREFIX + uuid.toString() + ":worlds";
                    Set<String> worlds = jedis.smembers(ownerWorldsKey);
                    
                    for (String world : worlds) {
                        String ownerKey = GENERATOR_OWNER_PREFIX + uuid.toString() + ":" + world;
                        transaction.del(ownerKey);
                        
                        // Remove from world indexes
                        String worldKey = GENERATOR_WORLD_PREFIX + world;
                        for (String generatorId : generatorIds) {
                            transaction.srem(worldKey, generatorId);
                        }
                    }
                    
                    // Clear owner data
                    transaction.del(ownerWorldsKey);
                    transaction.del(GENERATOR_COUNT_PREFIX + uuid.toString());
                    transaction.srem(GENERATOR_OWNER_INDEX, uuid.toString());
                    
                    transaction.exec();
                    
                    // Clear from local cache
                    for (String generatorId : generatorIds) {
                        localActiveGenerators.remove(generatorId);
                    }
                    
                    Logger.info("Successfully removed all generators for owner " + uuid);
                } catch (Exception e) {
                    transaction.discard();
                    Logger.severe("Failed to remove all generators: " + e.getMessage());
                    throw new RuntimeException("Bulk generator removal failed", e);
                }
            });
        }
    }

    public int getGeneratorCount(Player player) {
        return getGeneratorCount(player.getUniqueId());
    }

    public int getGeneratorCount(UUID uuid) {
        return redisManager.executeWithJedis(jedis -> {
            String countStr = jedis.get(GENERATOR_COUNT_PREFIX + uuid.toString());
            return countStr != null ? Integer.parseInt(countStr) : 0;
        });
    }

    /**
     * Try to acquire lock for a generator with proper expiration
     * @param generatorId The generator ID
     * @param ttlSeconds Time to live in seconds
     * @return true if lock was acquired
     */
    public boolean acquireLock(String generatorId, int ttlSeconds) {
        return redisManager.executeWithJedis(jedis -> {
            String lockKey = GENERATOR_LOCK_PREFIX + generatorId;
            String serverId = redisManager.getServerId();
            
            // Use SET with NX and EX for atomic lock acquisition
            String result = jedis.set(lockKey, serverId, "NX", "EX", ttlSeconds);
            return "OK".equals(result);
        });
    }

    /**
     * Check if this server holds the lock for a generator
     */
    public boolean holdsLock(String generatorId) {
        return redisManager.executeWithJedis(jedis -> {
            String lockKey = GENERATOR_LOCK_PREFIX + generatorId;
            String lockHolder = jedis.get(lockKey);
            return redisManager.getServerId().equals(lockHolder);
        });
    }

    /**
     * Release lock for a generator with atomic check-and-delete
     */
    public void releaseLock(String generatorId) {
        redisManager.executeWithJedis(jedis -> {
            String lockKey = GENERATOR_LOCK_PREFIX + generatorId;
            String serverId = redisManager.getServerId();
            
            // Use Lua script for atomic check-and-delete
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            jedis.eval(luaScript, Collections.singletonList(lockKey), Collections.singletonList(serverId));
        });
    }

    /**
     * Add generator to local cache (for this server's processing)
     */
    public void addToLocalCache(ActiveGenerator generator) {
        String serialized = LocationUtils.serialize(generator.getLocation());
        localActiveGenerators.put(serialized, generator);
    }

    /**
     * Remove generator from local cache
     */
    public void removeFromLocalCache(String serialized) {
        localActiveGenerators.remove(serialized);
    }

    /**
     * Get all locally cached generators
     */
    public Collection<ActiveGenerator> getLocalActiveGenerators() {
        return localActiveGenerators.values();
    }

    /**
     * Save generator state to Redis
     */
    public void saveGenerator(ActiveGenerator generator) {
        redisManager.executeWithJedis(jedis -> {
            String serialized = LocationUtils.serialize(generator.getLocation());
            String generatorJson = gson.toJson(generator);
            jedis.set(GENERATOR_PREFIX + serialized, generatorJson);
        });
    }

    /**
     * Batch save multiple generators for efficiency
     */
    public void saveGenerators(Collection<ActiveGenerator> generators) {
        if (generators.isEmpty()) return;
        
        redisManager.executeWithJedis(jedis -> {
            Transaction transaction = jedis.multi();
            try {
                for (ActiveGenerator generator : generators) {
                    String serialized = LocationUtils.serialize(generator.getLocation());
                    String generatorJson = gson.toJson(generator);
                    transaction.set(GENERATOR_PREFIX + serialized, generatorJson);
                }
                transaction.exec();
                Logger.info("Batch saved " + generators.size() + " generators");
            } catch (Exception e) {
                transaction.discard();
                Logger.severe("Failed to batch save generators: " + e.getMessage());
            }
        });
    }

    /**
     * Get all active generator owners for monitoring
     */
    public Set<String> getAllOwners() {
        return redisManager.executeWithJedis(jedis -> {
            return jedis.smembers(GENERATOR_OWNER_INDEX);
        });
    }

    /**
     * Get all worlds with active generators
     */
    public Set<String> getAllWorlds() {
        return redisManager.executeWithJedis(jedis -> {
            return jedis.smembers(GENERATOR_WORLD_INDEX);
        });
    }

    /**
     * Clear local caches
     */
    public void clearLocalCache() {
        localActiveGenerators.clear();
    }

    /**
     * Get detailed statistics about Redis usage
     */
    public Map<String, Object> getRedisStats() {
        return redisManager.executeWithJedis(jedis -> {
            Map<String, Object> stats = new HashMap<>();
            
            // Count total generators
            Set<String> allKeys = jedis.keys(GENERATOR_PREFIX + "*");
            stats.put("total_generators", allKeys.size());
            
            // Count owners
            stats.put("total_owners", jedis.scard(GENERATOR_OWNER_INDEX));
            
            // Count worlds
            stats.put("total_worlds", jedis.scard(GENERATOR_WORLD_INDEX));
            
            // Count active locks
            Set<String> lockKeys = jedis.keys(GENERATOR_LOCK_PREFIX + "*");
            stats.put("active_locks", lockKeys.size());
            
            // Local cache size
            stats.put("local_cache_size", localActiveGenerators.size());
            
            return stats;
        });
    }

} 