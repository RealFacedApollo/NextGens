package com.muhammaddaffa.nextgens.managers;

import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.function.Consumer;
import java.util.function.Function;

public class RedisManager {

    private JedisPool jedisPool;
    private String serverId;

    public void connect() {
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        
        String host = config.getString("redis.host", "localhost");
        int port = config.getInt("redis.port", 6379);
        String password = config.getString("redis.password", "");
        int database = config.getInt("redis.database", 0);
        
        // Generate unique server ID
        this.serverId = config.getString("server-id", 
            System.getProperty("server.port", "default") + "-" + System.currentTimeMillis());
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        try {
            if (password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, null, database);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database);
            }
            
            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }
            
            Logger.info("Successfully connected to Redis at " + host + ":" + port);
            Logger.info("Server ID: " + serverId);
        } catch (Exception ex) {
            Logger.severe("Failed to connect to Redis!");
            ex.printStackTrace();
            throw new RuntimeException("Redis connection failed", ex);
        }
    }

    public void executeWithJedis(Consumer<Jedis> action) {
        executeWithJedis(jedis -> {
            action.accept(jedis);
            return null;
        });
    }

    public <T> T executeWithJedis(Function<Jedis, T> action) {
        if (jedisPool == null) {
            Logger.warning("Redis pool is not initialized!");
            return null;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            return action.apply(jedis);
        } catch (JedisException ex) {
            Logger.severe("Redis operation failed: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    public String getServerId() {
        return serverId;
    }

    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

} 