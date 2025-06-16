package com.muhammaddaffa.nextgens.objects;

import com.muhammaddaffa.mdlib.utils.ItemBuilder;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.nextgens.NextGens;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record Drop(
        String id,
        double chance,
        @Nullable ItemStack item,
        @Nullable Double dropValue,
        List<String> commands
) {

    public static Drop fromConfig(String id, String key, ConfigurationSection section) {
        double chance = section.getDouble("chance");
        Double sellValue = section.get("sell-value") == null ? null : section.getDouble("sell-value");

        ItemBuilder builder = ItemBuilder.fromConfig(section.getConfigurationSection("item"));
        ItemStack stack = builder == null ? null : builder.build();

        List<String> commands = section.getStringList("commands");

        return new Drop(id + "_" + key, chance, stack, sellValue, commands);
    }

    public boolean shouldUse() {
        return ThreadLocalRandom.current().nextDouble(101) <= this.chance();
    }

    public ItemStack getItem() {
        // create the proper item first
        ItemBuilder builder = new ItemBuilder(this.item().clone());
        // add the drop value
        if (this.dropValue() != null) {
            builder.pdc(NextGens.drop_value, this.dropValue());
        }
        return builder.build();
    }

    public void spawn(Block block, @Nullable OfflinePlayer player, boolean shouldItemDrop) {
        // get the drop location
        Location dropLocation = block.getLocation().add(0.5, 1, 0.5);
        // drop the item if it's exist
        if (this.item() != null && shouldItemDrop) {
            // finally, drop the item
            Item item = block.getWorld().dropItem(dropLocation, this.getItem());
            // remove the velocity
            item.setVelocity(new Vector(0, 0, 0));
        }
        // execute the commands with placeholder
        Placeholder placeholder = new Placeholder()
                .add("{x}", dropLocation.getBlockX())
                .add("{y}", dropLocation.getBlockY())
                .add("{z}", dropLocation.getBlockZ())
                .add("{world}", dropLocation.getWorld().getName())
                .add("{world_lower}", dropLocation.getWorld().getName().toLowerCase());
        if (player != null) {
            placeholder.add("{player}", player.getName());
        }
        this.commands.forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholder.translate(command)));
    }

}
