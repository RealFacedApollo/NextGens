package com.muhammaddaffa.nextgens.managers;

import com.google.gson.Gson;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Transaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserManager {

    private static final String USER_PREFIX = "nextgens:user:";
    private static final String USER_NAME_PREFIX = "nextgens:user:name:";
    private static final String USER_INDEX = "nextgens:users";
    
    private final DataManager dataManager;
    private final Gson gson;
    
    // Local cache for frequently accessed users
    private final Map<UUID, User> userCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameCache = new ConcurrentHashMap<>();

    public UserManager(DataManager dataManager) {
        this.dataManager = dataManager;
        this.gson = new Gson();
    }

    @Nullable
    public User getUser(String name) {
        // Check name cache first
        UUID uuid = nameCache.get(name.toLowerCase());
        if (uuid != null) {
            return getUser(uuid);
        }
        
        // Query Redis for user by name
        return dataManager.executeWithJedis(jedis -> {
            String userJson = jedis.get(USER_NAME_PREFIX + name.toLowerCase());
            if (userJson != null) {
                User user = gson.fromJson(userJson, User.class);
                // Cache the user
                userCache.put(user.getUniqueId(), user);
                nameCache.put(name.toLowerCase(), user.getUniqueId());
                return user;
            }
            return null;
        });
    }

    @NotNull
    public User getUser(Player player) {
        return getUser(player.getUniqueId());
    }

    @NotNull
    public User getUser(UUID uuid) {
        // Check local cache first
        User cachedUser = userCache.get(uuid);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // Query Redis
        User user = redisManager.executeWithJedis(jedis -> {
            String userJson = jedis.get(USER_PREFIX + uuid.toString());
            if (userJson != null) {
                return gson.fromJson(userJson, User.class);
            }
            return null;
        });
        
        // If user doesn't exist, create new one
        if (user == null) {
            user = new User(uuid);
            saveUser(user);
        }
        
        // Cache the user
        userCache.put(uuid, user);
        return user;
    }

    /**
     * Save user with atomic transaction to ensure data consistency
     */
    public void saveUser(User user) {
        redisManager.executeWithJedis(jedis -> {
            String userJson = gson.toJson(user);
            String userKey = USER_PREFIX + user.getUniqueId().toString();
            
            Transaction transaction = jedis.multi();
            try {
                // Save user data
                transaction.set(userKey, userJson);
                
                // Also save by name if available
                if (user.getName() != null) {
                    String nameKey = USER_NAME_PREFIX + user.getName().toLowerCase();
                    transaction.set(nameKey, userJson);
                }
                
                // Add to user index for efficient querying
                transaction.sadd(USER_INDEX, user.getUniqueId().toString());
                
                // Execute transaction
                transaction.exec();
                
                // Update local caches
                userCache.put(user.getUniqueId(), user);
                if (user.getName() != null) {
                    nameCache.put(user.getName().toLowerCase(), user.getUniqueId());
                }
                
                Logger.info("Successfully saved user: " + user.getUniqueId());
            } catch (Exception e) {
                transaction.discard();
                Logger.severe("Failed to save user: " + e.getMessage());
                throw new RuntimeException("User save failed", e);
            }
        });
    }

    /**
     * Batch save multiple users for efficiency
     */
    public void saveUsers(Collection<User> users) {
        if (users.isEmpty()) return;
        
        redisManager.executeWithJedis(jedis -> {
            Transaction transaction = jedis.multi();
            try {
                for (User user : users) {
                    String userJson = gson.toJson(user);
                    String userKey = USER_PREFIX + user.getUniqueId().toString();
                    
                    transaction.set(userKey, userJson);
                    
                    if (user.getName() != null) {
                        String nameKey = USER_NAME_PREFIX + user.getName().toLowerCase();
                        transaction.set(nameKey, userJson);
                    }
                    
                    transaction.sadd(USER_INDEX, user.getUniqueId().toString());
                }
                
                transaction.exec();
                
                // Update local caches
                for (User user : users) {
                    userCache.put(user.getUniqueId(), user);
                    if (user.getName() != null) {
                        nameCache.put(user.getName().toLowerCase(), user.getUniqueId());
                    }
                }
                
                Logger.info("Batch saved " + users.size() + " users");
            } catch (Exception e) {
                transaction.discard();
                Logger.severe("Failed to batch save users: " + e.getMessage());
                throw new RuntimeException("Batch user save failed", e);
            }
        });
    }

    /**
     * Remove user with atomic transaction
     */
    public void removeUser(UUID uuid) {
        redisManager.executeWithJedis(jedis -> {
            User user = userCache.get(uuid);
            
            Transaction transaction = jedis.multi();
            try {
                // Remove user data
                transaction.del(USER_PREFIX + uuid.toString());
                
                // Remove name mapping if available
                if (user != null && user.getName() != null) {
                    transaction.del(USER_NAME_PREFIX + user.getName().toLowerCase());
                }
                
                // Remove from user index
                transaction.srem(USER_INDEX, uuid.toString());
                
                // Execute transaction
                transaction.exec();
                
                // Remove from local caches
                userCache.remove(uuid);
                if (user != null && user.getName() != null) {
                    nameCache.remove(user.getName().toLowerCase());
                }
                
                Logger.info("Successfully removed user: " + uuid);
            } catch (Exception e) {
                transaction.discard();
                Logger.severe("Failed to remove user: " + e.getMessage());
                throw new RuntimeException("User removal failed", e);
            }
        });
    }

    public Collection<User> getUsers() {
        return userCache.values();
    }

    public List<String> getUsersName() {
        return userCache.values().stream()
                .map(User::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all user UUIDs from Redis
     */
    public Set<String> getAllUserUUIDs() {
        return redisManager.executeWithJedis(jedis -> {
            return jedis.smembers(USER_INDEX);
        });
    }

    /**
     * Load all users from Redis into cache
     */
    public void loadAllUsers() {
        redisManager.executeWithJedis(jedis -> {
            Set<String> userUUIDs = jedis.smembers(USER_INDEX);
            
            for (String uuidStr : userUUIDs) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String userJson = jedis.get(USER_PREFIX + uuidStr);
                    
                    if (userJson != null) {
                        User user = gson.fromJson(userJson, User.class);
                        userCache.put(uuid, user);
                        
                        if (user.getName() != null) {
                            nameCache.put(user.getName().toLowerCase(), uuid);
                        }
                    }
                } catch (Exception e) {
                    Logger.warning("Failed to load user " + uuidStr + ": " + e.getMessage());
                }
            }
            
            Logger.info("Loaded " + userCache.size() + " users into cache");
        });
    }

    public void clearCache() {
        userCache.clear();
        nameCache.clear();
    }

    public int getMaxSlot(Player player) {
        int max = 0;
        var config = NextGens.DEFAULT_CONFIG.getConfig();
        if (config.getBoolean("default-max-generator.enabled")) {
            max += config.getInt("default-max-generator.amount");
        }
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String permission = pai.getPermission();
            if (!permission.startsWith("nextgens.max.")) {
                continue;
            }
            int current = Integer.parseInt(permission.split("\\.")[2]);
            if (current > max) {
                max = current;
            }
        }

        int bonusMax = max + this.getUser(player).getBonus();
        int limit = config.getInt("player-generator-limit.limit");
        if (config.getBoolean("player-generator-limit.enabled") && bonusMax > limit) {
            return limit;
        }
        return bonusMax;
    }

    /**
     * Preload users for online players
     */
    public void preloadOnlineUsers() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            getUser(player.getUniqueId()); // This will cache the user
        }
    }

    /**
     * Get detailed statistics about user data
     */
    public Map<String, Object> getUserStats() {
        return redisManager.executeWithJedis(jedis -> {
            Map<String, Object> stats = new HashMap<>();
            
            // Count total users in Redis
            stats.put("total_users_redis", jedis.scard(USER_INDEX));
            
            // Count cached users
            stats.put("cached_users", userCache.size());
            stats.put("cached_names", nameCache.size());
            
            // Count user data keys
            Set<String> userKeys = jedis.keys(USER_PREFIX + "*");
            stats.put("user_data_keys", userKeys.size());
            
            // Count name mapping keys
            Set<String> nameKeys = jedis.keys(USER_NAME_PREFIX + "*");
            stats.put("name_mapping_keys", nameKeys.size());
            
            return stats;
        });
    }

} 