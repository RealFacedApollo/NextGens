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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RedisGeneratorManager {

    private static final String GENERATOR_PREFIX = "nextgens:generator:";
    private static final String GENERATOR_OWNER_PREFIX = "nextgens:generators:owner:";
    private static final String GENERATOR_WORLD_PREFIX = "nextgens:generators:world:";
    private static final String GENERATOR_COUNT_PREFIX = "nextgens:generator:count:";
    private static final String GENERATOR_LOCK_PREFIX = "nextgens:generator:lock:";
    
    private final RedisManager redisManager;
    private final Gson gson;
    
    // Local cache for active generators that this server is managing
    private final Map<String, ActiveGenerator> localActiveGenerators = new ConcurrentHashMap<>();

    public RedisGeneratorManager(RedisManager redisManager) {
        this.redisManager = redisManager;
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
     * Get all generator IDs for a specific owner across all worlds
     */
    public Set<String> getGeneratorsByOwner(UUID owner) {
        return redisManager.executeWithJedis(jedis -> {
            String pattern = GENERATOR_OWNER_PREFIX + owner.toString() + ":*";
            Set<String> allKeys = jedis.keys(pattern);
            Set<String> allGenerators = new HashSet<>();
            
            for (String key : allKeys) {
                allGenerators.addAll(jedis.smembers(key));
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

    public ActiveGenerator registerGenerator(UUID owner, @NotNull Generator generatorType, @NotNull Block block) {
        String serialized = LocationUtils.serialize(block.getLocation());
        
        return redisManager.executeWithJedis(jedis -> {
            // Check if generator already exists
            String existingJson = jedis.get(GENERATOR_PREFIX + serialized);
            ActiveGenerator active;
            
            if (existingJson != null) {
                // Update existing generator
                active = gson.fromJson(existingJson, ActiveGenerator.class);
                active.setGenerator(generatorType);
            } else {
                // Create new generator
                active = new ActiveGenerator(owner, block.getLocation(), generatorType);
                
                // Add to owner index
                String ownerKey = GENERATOR_OWNER_PREFIX + owner.toString() + ":" + block.getWorld().getName();
                jedis.sadd(ownerKey, serialized);
                
                // Add to world index
                String worldKey = GENERATOR_WORLD_PREFIX + block.getWorld().getName();
                jedis.sadd(worldKey, serialized);
                
                // Increment generator count
                jedis.incr(GENERATOR_COUNT_PREFIX + owner.toString());
            }
            
            // Save generator data
            String generatorJson = gson.toJson(active);
            jedis.set(GENERATOR_PREFIX + serialized, generatorJson);
            
            return active;
        });
    }

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
                
                // Remove from owner index
                String ownerKey = GENERATOR_OWNER_PREFIX + generator.getOwner().toString() + 
                                 ":" + generator.getLocation().getWorld().getName();
                jedis.srem(ownerKey, serialized);
                
                // Remove from world index
                String worldKey = GENERATOR_WORLD_PREFIX + generator.getLocation().getWorld().getName();
                jedis.srem(worldKey, serialized);
                
                // Decrement generator count
                jedis.decr(GENERATOR_COUNT_PREFIX + generator.getOwner().toString());
                
                // Remove generator data
                jedis.del(GENERATOR_PREFIX + serialized);
                
                // Remove from local cache
                localActiveGenerators.remove(serialized);
                
                // Remove any locks
                jedis.del(GENERATOR_LOCK_PREFIX + serialized);
            }
        });
    }

    public void removeAllGenerator(Player player) {
        removeAllGenerator(player.getUniqueId());
    }

    public void removeAllGenerator(UUID uuid) {
        Set<String> generatorIds = getGeneratorsByOwner(uuid);
        if (generatorIds != null) {
            for (String generatorId : generatorIds) {
                unregisterGenerator(generatorId);
            }
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
     * Try to acquire lock for a generator
     * @param generatorId The generator ID
     * @param ttlSeconds Time to live in seconds
     * @return true if lock was acquired
     */
    public boolean acquireLock(String generatorId, int ttlSeconds) {
        return redisManager.executeWithJedis(jedis -> {
            String lockKey = GENERATOR_LOCK_PREFIX + generatorId;
            String serverId = redisManager.getServerId();
            
            // SET key value NX EX ttl
            String result = jedis.setex(lockKey, ttlSeconds, serverId);
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
     * Release lock for a generator
     */
    public void releaseLock(String generatorId) {
        redisManager.executeWithJedis(jedis -> {
            String lockKey = GENERATOR_LOCK_PREFIX + generatorId;
            String serverId = redisManager.getServerId();
            
            // Only release if we hold the lock
            String lockHolder = jedis.get(lockKey);
            if (serverId.equals(lockHolder)) {
                jedis.del(lockKey);
            }
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
     * Clear local caches
     */
    public void clearLocalCache() {
        localActiveGenerators.clear();
    }

} 