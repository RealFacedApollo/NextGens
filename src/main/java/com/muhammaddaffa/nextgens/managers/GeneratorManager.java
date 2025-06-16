package com.muhammaddaffa.nextgens.managers;

import com.muhammaddaffa.mdlib.utils.*;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.api.events.generators.GeneratorLoadEvent;
import com.muhammaddaffa.nextgens.managers.DatabaseManager;
import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.objects.Drop;
import com.muhammaddaffa.nextgens.objects.Generator;
import com.muhammaddaffa.nextgens.generators.runnables.GeneratorTask;
import com.muhammaddaffa.nextgens.requirements.GensRequirement;
import com.muhammaddaffa.nextgens.requirements.impl.PermissionRequirement;
import com.muhammaddaffa.nextgens.requirements.impl.PlaceholderRequirement;
import com.muhammaddaffa.nextgens.utils.*;
import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.core.BConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class GeneratorManager {

    private final Map<String, Generator> generatorMap = new HashMap<>();
    private final ConcurrentMap<String, ActiveGenerator> activeGenerators = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> generatorCount = new HashMap<>();

    private final DatabaseManager dbm;
    public GeneratorManager(DatabaseManager dbm) {
        this.dbm = dbm;
    }

    @Nullable
    public Generator getGenerator(String id) {
        return this.generatorMap.get(id);
    }

    @Nullable
    public Generator getGenerator(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null) {
            return null;
        }
        // scrap the id from the item
        String id = stack.getItemMeta().getPersistentDataContainer().get(NextGens.generator_id, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        return this.getGenerator(id);
    }

    @NotNull
    public Generator getRandomGenerator() {
        // turn the key into list
        List<String> generators = this.generatorMap.keySet().stream().toList();
        return this.generatorMap.get(generators.get(ThreadLocalRandom.current().nextInt(generators.size())));
    }

    public Set<String> getGeneratorIDs() {
        return this.generatorMap.keySet();
    }

    public boolean isGeneratorItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(NextGens.generator_id, PersistentDataType.STRING);
    }

    public boolean isDropItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(NextGens.drop_value, PersistentDataType.DOUBLE);
    }

    public Collection<Generator> getGenerators() {
        return this.generatorMap.values();
    }

    public int getGeneratorCount(Player player) {
        return this.getGeneratorCount(player.getUniqueId());
    }

    public int getGeneratorCount(UUID uuid) {
        return this.generatorCount.getOrDefault(uuid, 0);
    }

    public void addGeneratorCount(Player player, int amount) {
        this.addGeneratorCount(player.getUniqueId(), amount);
    }

    public void addGeneratorCount(UUID uuid, int amount) {
        this.generatorCount.put(uuid, this.generatorCount.getOrDefault(uuid, 0) + amount);
    }

    public void removeGeneratorCount(Player player, int amount) {
        this.removeGeneratorCount(player.getUniqueId(), amount);
    }

    public void removeGeneratorCount(UUID uuid, int amount) {
        this.generatorCount.put(uuid, this.generatorCount.getOrDefault(uuid, 0) - amount);
    }

    public Collection<ActiveGenerator> getActiveGenerator() {
        return this.activeGenerators.values();
    }

    @NotNull
    public List<ActiveGenerator> getActiveGenerator(Player player) {
        return this.getActiveGenerator(player.getUniqueId());
    }

    @NotNull
    public List<ActiveGenerator> getActiveGenerator(UUID uuid) {
        return this.getActiveGenerator().stream()
                .filter(active -> active.getOwner().equals(uuid))
                .collect(Collectors.toList());
    }

    @Nullable
    public ActiveGenerator getActiveGenerator(@Nullable Block block) {
        if (block == null) return null;
        return this.getActiveGenerator(block.getLocation());
    }

    @Nullable
    public ActiveGenerator getActiveGenerator(@NotNull Location location) {
        return this.getActiveGenerator(LocationUtils.serialize(location));
    }

    @Nullable
    public ActiveGenerator getActiveGenerator(String serialized) {
        return this.activeGenerators.get(serialized);
    }

    public ActiveGenerator registerGenerator(Player owner, @NotNull Generator generator, @NotNull Block block) {
        return this.registerGenerator(owner.getUniqueId(), generator, block);
    }

    public ActiveGenerator registerGenerator(UUID owner, @NotNull Generator generator, @NotNull Block block) {
        ActiveGenerator active = this.getActiveGenerator(block);
        // check if block is already active generator or not
        if (active == null) {
            // register the new one
            String serialized = LocationUtils.serialize(block.getLocation());
            active = new ActiveGenerator(owner, block.getLocation(), generator);
            this.activeGenerators.put(serialized, active);
            // add generator count
            this.addGeneratorCount(owner, 1);
        } else {
            // change the generator id
            active.setGenerator(generator);
            // set the block
            Executor.syncLater(2L, () -> block.setType(generator.item().getType()));
        }
        ActiveGenerator finalActive = active;
        // save the generator on the database
        Executor.async(() -> this.dbm.saveGenerator(finalActive));
        return active;
    }

    public void unregisterGenerator(@Nullable Block block) {
        if (block == null) return;
        this.unregisterGenerator(block.getLocation());
    }

    public void unregisterGenerator(Location location) {
        this.unregisterGenerator(LocationUtils.serialize(location));
    }

    public void unregisterGenerator(String serialized) {
        ActiveGenerator removed = this.activeGenerators.remove(serialized);
        // check if the remove is successful
        if (removed != null) {
            // remove the corrupt status
            removed.setCorrupted(false);
            // force remove
            GeneratorTask.destroy(removed);
            // remove the generator count
            this.removeGeneratorCount(removed.getOwner(), 1);
            // remove the generator from the database
            Executor.async(() -> this.dbm.deleteGenerator(removed));
        }
    }

    public void removeAllGenerator(Player player) {
        this.removeAllGenerator(player.getUniqueId());
    }

    public void removeAllGenerator(UUID uuid) {
        this.getActiveGenerator(uuid).forEach(active -> {
            // Get the block
            Block block = active.getLocation().getBlock();
            // Unregister the generator and set it to air
            this.unregisterGenerator(block);
            block.setType(Material.AIR);
        });
    }

    public void loadActiveGenerator() {
        String query = "SELECT * FROM " + DatabaseManager.GENERATOR_TABLE;
        this.dbm.executeQuery(query, result -> {
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

                Generator generator = this.getGenerator(generatorId);
                if (generatorId == null || generator == null || location.getWorld() == null) continue;

                // store it on the map
                this.activeGenerators.put(serialized, new ActiveGenerator(owner, location, generator, timer, isCorrupted));
                // add generator count
                this.addGeneratorCount(owner, 1);
            }
            // send log message
            Logger.info("Successfully loaded " + this.activeGenerators.size() + " active generators!");
        });
    }

    public void saveActiveGenerator(ActiveGenerator active) {
        this.dbm.saveGenerator(active);
    }

    public void saveActiveGenerator() {
        this.dbm.saveGenerator(this.activeGenerators.values());
    }

    public void loadGenerators() {
        // Clear the generators map
        this.generatorMap.clear();
        // log message
        Logger.info("Starting to load all generators...");
        // load all generators files inside the 'generators' directory
        File directory = this.getMainDirectory();
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    this.loadGenerators(YamlConfiguration.loadConfiguration(file));
                }
            }
            // load generator from 'generators.yml'
            this.loadGenerators(NextGens.GENERATORS_CONFIG.getConfig());
            // send log message
            Logger.info("Successfully loaded " + this.generatorMap.size() + " generators!");
        } else {
            directory.mkdirs();
            // generate default files
            Logger.info("Creating default files for per-file generators system...");
            NextGens.getInstance().saveResource("generators/elemental_generators.yml", true);
            Logger.info("Successfully created 'generators/elemental_generators.yml' file");
            NextGens.getInstance().saveResource("generators/orion_generators.yml", true);
            Logger.info("Successfully created 'generators/orion_generators.yml' file");
            // load back the generator
            this.loadGenerators();
        }
    }

    private void loadGenerators(FileConfiguration config) {
        // get all sections on the config
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            // load the generator
            this.loadGenerators(id, section);
        }
    }

    private void loadGenerators(String id, ConfigurationSection section) {
        // get all data
        String displayName = section.getString("display-name");
        int interval = section.getInt("interval");
        boolean corrupted = section.getBoolean("corrupted.enabled");
        double fixCost = section.getDouble("corrupted.cost");
        double corruptChance = section.getDouble("corrupted.chance");
        String nextTier = section.getString("upgrade.next-generator");
        double upgradeCost = section.getDouble("upgrade.upgrade-cost");
        // online only options
        Boolean onlineOnly;
        if (section.get("online-only") == null) {
            onlineOnly = null;
        } else {
            onlineOnly = section.getBoolean("online-only");
        }

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection == null) {
            Logger.warning("Failed to load generator '" + id + "'");
            Logger.warning("Reason: There is no generator item configuration!");
            return;
        }
        ItemBuilder builder = ItemBuilder.fromConfig(itemSection);
        if (builder == null) {
            Logger.warning("Failed to load generator '" + id + "'");
            Logger.warning("Reason: Failed to load the generator item configuration!");
            return;
        }
        ItemStack item = builder.build();

        List<Drop> drops = new ArrayList<>();

        if (section.isConfigurationSection("drops")) {
            for (String key : section.getConfigurationSection("drops").getKeys(false)) {
                ConfigurationSection dropSection = section.getConfigurationSection("drops." + key);
                if (dropSection == null) {
                    continue;
                }
                drops.add(Drop.fromConfig(id, key, dropSection));
            }
        }

        List<GensRequirement> placeRequirements = this.loadRequirement(section, "place-requirements");
        List<GensRequirement> upgradeRequirements = this.loadRequirement(section, "upgrade-requirements");

        Generator generator = new Generator(id, displayName, interval, item, drops, nextTier, upgradeCost,
                corrupted, fixCost, corruptChance, onlineOnly, placeRequirements, upgradeRequirements);

        // call the custom event
        GeneratorLoadEvent loadEvent = new GeneratorLoadEvent(generator);
        Bukkit.getPluginManager().callEvent(loadEvent);
        if (loadEvent.isCancelled()) {
            return;
        }
        // store it on the map
        this.generatorMap.put(id, generator);
        // send log message
        Logger.info("Loaded generator '" + id + "'");
    }

    private List<GensRequirement> loadRequirement(ConfigurationSection section, String path) {
        List<GensRequirement> requirements = new ArrayList<>();
        if (section.isConfigurationSection(path)) {
            for (String key : section.getConfigurationSection(path).getKeys(false)) {
                String type = section.getString(path + "." + key + ".type", "DUMMY");
                String message = section.getString(path + "." + key + ".message", "&cYou don't have the requirement to do this!");
                switch (type.toUpperCase()) {
                    case "PERMISSION" -> {
                        String permission = section.getString(path + "." + key + ".permission");
                        requirements.add(new PermissionRequirement(message, permission));
                    }
                    case "PLACEHOLDER" -> {
                        String placeholder = section.getString(path + "." + key + ".placeholder");
                        String value = section.getString(path + "." + key + ".value");
                        requirements.add(new PlaceholderRequirement(message, placeholder, value));
                    }
                }
            }
        }
        return requirements;
    }

    public void refreshActiveGenerator() {
        for (ActiveGenerator active : this.activeGenerators.values()) {
            if (active.getGenerator() == null) {
                this.fixGenerator(active);
                continue;
            }
            // get the new generator
            Generator refreshed = this.getGenerator(active.getGenerator().id());
            // if the refreshed is not null
            if (refreshed != null) {
                // refresh it
                active.setGenerator(refreshed);
            }
        }
    }

    private void fixGenerator(ActiveGenerator active) {
        if (active.getGenerator() != null) {
            return;
        }
        for (Generator generator : this.generatorMap.values()) {
            if (active.getLocation().getBlock().getType() == generator.item().getType()) {
                active.setGenerator(generator);
                break;
            }
        }
    }

    private File getMainDirectory() {
        return new File(NextGens.getInstance().getDataFolder() + File.separator + "generators");
    }

}
