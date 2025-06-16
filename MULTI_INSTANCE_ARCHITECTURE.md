# NextGens Multi-Instance Architecture

This document explains the new Redis-based architecture that enables NextGens to run across multiple server instances safely.

## Architecture Overview

The new architecture moves from local memory + database storage to a distributed Redis-based system with the following key components:

### Core Components

1. **RedisManager** - Manages Redis connection pool with try-with-resources pattern
2. **RedisUserManager** - Handles user data storage and caching in Redis
3. **RedisGeneratorManager** - Manages active generators with distributed locking
4. **GeneratorLockAcquirer** - Acquires locks for generators every 30 seconds
5. **GeneratorRunner** - Processes generators that this instance holds locks for
6. **ChunkGeneratorListener** - Links/unlinks generators when chunks load/unload

### Key Features

#### Distributed Locking System
- Each generator can only be processed by one server instance at a time
- Locks have a 30-second TTL to handle server failures
- Lock acquisition happens every 30 seconds
- Only locked generators are processed by each instance

#### Chunk-Based Generator Linking
- Generators exist in Redis even when chunks are unloaded
- When chunks load, the system scans for generator blocks asynchronously
- Generators are linked (made interactive) only when their chunks are loaded
- This prevents memory leaks and improves performance

#### Multi-Instance Safe Storage
- All data is stored in Redis with proper indexing
- Users and generators are accessible across all instances
- Server-specific caching for performance
- Automatic failover when servers go down

## Redis Data Structure

### User Data
```
nextgens:user:{uuid}                    - User JSON data
nextgens:user:name:{name}              - User lookup by name
```

### Generator Data
```
nextgens:generator:{location_id}                    - Generator JSON data
nextgens:generators:owner:{uuid}:{world}           - Set of generator IDs by owner/world
nextgens:generators:world:{world}                  - Set of generator IDs by world
nextgens:generator:count:{uuid}                    - Generator count per player
nextgens:generator:lock:{location_id}              - Distributed lock (TTL: 30s)
```

## Process Flow

### 1. Lock Acquisition (Every 30 seconds)
```
GeneratorLockAcquirer:
1. Get all online players
2. Get all loaded worlds  
3. Query Redis for generators by owner+world and by world
4. Deduplicate generator list
5. Attempt to acquire locks with 30s TTL
6. Log success rate
```

### 2. Generator Processing (Every 5 ticks)
```
GeneratorRunner:
1. Get same generator list as lock acquirer
2. For each generator ID:
   - Check if this server holds the lock
   - If yes, process generator logic (corruption, timers, drops)
   - Update generator state in Redis
   - Handle events and multipliers
3. Save state periodically
```

### 3. Chunk Management
```
ChunkGeneratorListener:
On Chunk Load:
1. Get all block locations in chunk
2. Async: Check each block for generator data in Redis
3. Sync: Link found generators (add to local cache, setup holograms)

On Chunk Unload:
1. Unlink all generators in chunk
2. Remove from local cache
3. Clean up holograms and interactions
```

## Migration from Old System

### Database Migration
The old system used local memory + database. To migrate:

1. **Export existing data**: Run migration script to export all users and generators
2. **Import to Redis**: Convert data format and import into Redis
3. **Update configuration**: Add Redis connection details and server-id
4. **Deploy new version**: Replace old code with Redis-based version

### Configuration Changes

Add to `config.yml`:
```yaml
# Server ID for multi-instance support
server-id: 'server-1'  # Unique per instance

# Redis configuration
redis:
  host: localhost
  port: 6379
  password: ''
  database: 0
```

## Performance Considerations

### Improvements
- **Memory Usage**: Generators not loaded in memory unless chunk is loaded
- **Scalability**: Can run multiple instances without conflicts
- **Failover**: Automatic failover when servers go down (30s TTL)
- **Load Distribution**: Processing distributed across instances

### Potential Issues
- **Network Latency**: Redis operations have network overhead
- **Redis Dependency**: Single point of failure if Redis goes down
- **Lock Contention**: Many servers competing for same generators

### Optimization Strategies
- **Local Caching**: Cache frequently accessed data locally
- **Batch Operations**: Use Redis pipelines for bulk operations
- **Connection Pooling**: Proper connection pool configuration
- **Monitoring**: Track Redis performance and lock success rates

## Deployment Guide

### Prerequisites
- Redis server (recommended: Redis 6+)
- Multiple Minecraft server instances
- Shared Redis access from all instances

### Step-by-Step Deployment

1. **Set up Redis Server**
   ```bash
   # Install Redis
   sudo apt-get install redis-server
   
   # Configure Redis (redis.conf)
   bind 0.0.0.0
   port 6379
   # Set password if needed
   requirepass your_password_here
   ```

2. **Configure Each Server Instance**
   ```yaml
   # config.yml for each server
   server-id: 'survival-1'  # Unique per instance
   redis:
     host: redis.yourdomain.com
     port: 6379
     password: 'your_password'
     database: 0
   ```

3. **Deploy Plugin**
   - Replace old NextGens.jar with new version
   - Restart all server instances
   - Monitor logs for successful Redis connection

4. **Verify Operation**
   - Check that generators work across all servers
   - Verify user data is shared
   - Test failover by stopping one server

## Monitoring and Troubleshooting

### Key Metrics to Monitor
- Redis connection pool usage
- Lock acquisition success rate  
- Generator processing performance
- Memory usage per instance

### Common Issues

**Issue**: Generators not processing
- **Cause**: Redis connection issues or lock contention
- **Solution**: Check Redis connectivity and increase lock TTL

**Issue**: Data inconsistency between servers
- **Cause**: Network partitions or Redis failover
- **Solution**: Implement data reconciliation scripts

**Issue**: High memory usage
- **Cause**: Too many generators cached locally
- **Solution**: Implement cache eviction policies

## Future Enhancements

1. **Redis Clustering**: Support for Redis cluster deployment
2. **Data Replication**: Cross-region Redis replication
3. **Performance Metrics**: Built-in monitoring dashboard
4. **Auto-Scaling**: Dynamic server instance management
5. **Conflict Resolution**: Handle edge cases in distributed locking

## Conclusion

This new architecture provides a robust foundation for running NextGens across multiple server instances. The distributed locking system ensures data consistency while the chunk-based linking system optimizes performance and memory usage.

The system is designed to be resilient to server failures and scales horizontally by adding more server instances as needed. 