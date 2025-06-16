package com.muhammaddaffa.nextgens.managers;

import com.google.gson.Gson;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RedisUserManager {

    private static final String USER_PREFIX = "nextgens:user:";
    private static final String USER_NAME_PREFIX = "nextgens:user:name:";
    
    private final RedisManager redisManager;
    private final Gson gson;
    
    // Local cache for frequently accessed users
    private final Map<UUID, User> userCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameCache = new ConcurrentHashMap<>();

    public RedisUserManager(RedisManager redisManager) {
        this.redisManager = redisManager;
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
        return redisManager.executeWithJedis(jedis -> {
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

    public void saveUser(User user) {
        redisManager.executeWithJedis(jedis -> {
            String userJson = gson.toJson(user);
            String userKey = USER_PREFIX + user.getUniqueId().toString();
            
            // Save user data
            jedis.set(userKey, userJson);
            
            // Also save by name if available
            if (user.getName() != null) {
                String nameKey = USER_NAME_PREFIX + user.getName().toLowerCase();
                jedis.set(nameKey, userJson);
                nameCache.put(user.getName().toLowerCase(), user.getUniqueId());
            }
            
            // Update local cache
            userCache.put(user.getUniqueId(), user);
        });
    }

    public void removeUser(UUID uuid) {
        redisManager.executeWithJedis(jedis -> {
            User user = userCache.get(uuid);
            if (user != null && user.getName() != null) {
                jedis.del(USER_NAME_PREFIX + user.getName().toLowerCase());
                nameCache.remove(user.getName().toLowerCase());
            }
            jedis.del(USER_PREFIX + uuid.toString());
            userCache.remove(uuid);
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
        redisManager.executeWithJedis(jedis -> {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                getUser(player.getUniqueId()); // This will cache the user
            }
        });
    }

} 