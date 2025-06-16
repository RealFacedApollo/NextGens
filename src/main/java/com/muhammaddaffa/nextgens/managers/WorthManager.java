package com.muhammaddaffa.nextgens.managers;

import com.muhammaddaffa.mdlib.utils.ItemBuilder;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.utils.Utils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class WorthManager {

    private final Map<Material, Double> materialWorth = new HashMap<>();
    private final Map<ItemStack, Double> itemWorth = new HashMap<>();

    @Nullable
    public Double getMaterialWorth(ItemStack stack) {
        Double price = materialWorth.get(stack.getType());
        return price != null ? price * stack.getAmount() : null;
    }

    @Nullable
    public Double getItemWorth(ItemStack stack) {
        for (Map.Entry<ItemStack, Double> entry : itemWorth.entrySet()) {
            if (Utils.isSimilar(stack, entry.getKey())) {
                return entry.getValue() * stack.getAmount();
            }
        }
        return null;
    }

    public void load() {
        materialWorth.clear();
        itemWorth.clear();

        FileConfiguration config = NextGens.WORTH_CONFIG.getConfig();

        Logger.info("Starting to load material and custom items worth...");

        loadMaterialWorth(config.getConfigurationSection("material-worth"));
        loadCustomItemWorth(config.getConfigurationSection("custom-item-worth"), config);

        Logger.info("Successfully loaded all material and custom items worth");
    }

    private void loadMaterialWorth(@Nullable ConfigurationSection materialSection) {
        if (materialSection == null) return;

        for (String key : materialSection.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase());
            if (material == null) {
                Logger.warning("There is no material named '" + key + "'");
                continue;
            }
            double worth = materialSection.getDouble(key);
            materialWorth.put(material, worth);
        }
    }

    private void loadCustomItemWorth(@Nullable ConfigurationSection itemSection, FileConfiguration config) {
        if (itemSection == null) return;

        for (String key : itemSection.getKeys(false)) {
            ItemBuilder builder = ItemBuilder.fromConfig(config, "custom-item-worth." + key);
            if (builder == null) {
                Logger.warning("Failed to load custom item worth with id '" + key + "'");
                continue;
            }
            double price = itemSection.getDouble(key + ".price");
            itemWorth.put(builder.build(), price);
        }
    }

}
