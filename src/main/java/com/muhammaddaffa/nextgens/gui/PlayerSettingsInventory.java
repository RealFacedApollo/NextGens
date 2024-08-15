package com.muhammaddaffa.nextgens.gui;

import com.muhammaddaffa.mdlib.gui.SimpleInventory;
import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.ItemBuilder;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.autosell.Autosell;
import com.muhammaddaffa.nextgens.users.User;
import com.muhammaddaffa.nextgens.users.managers.UserManager;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PlayerSettingsInventory extends SimpleInventory {

    public static void openInventory(Player player, UserManager userManager) {
        // create the gui
        PlayerSettingsInventory gui = new PlayerSettingsInventory(player, userManager);
        // open the gui
        gui.open(player);
    }

    private final Player player;
    private final UserManager userManager;

    public PlayerSettingsInventory(Player player, UserManager userManager) {
        super(NextGens.SETTINGS_GUI_CONFIG.getInt("size"), NextGens.SETTINGS_GUI_CONFIG.getString("title"));
        this.player = player;
        this.userManager = userManager;

        this.setAllItems();
    }

    private void setAllItems() {
        FileConfiguration config = NextGens.SETTINGS_GUI_CONFIG.getConfig();
        User user = this.userManager.getUser(this.player);
        String on = config.getString("placeholder-on");
        String off = config.getString("placeholder-off");
        // loop through all items
        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            // retrieve the data
            String type = config.getString("items." + key + ".type");
            List<Integer> slots = config.getIntegerList("items." + key + ".slots");
            ItemStack stack = ItemBuilder.fromConfig(config, "items." + key, new Placeholder()
                            .add("{status_cashback}", user.isToggleCashback() ? on : off)
                            .add("{status_inventory_autosell}", user.isToggleInventoryAutoSell() ? on : off)
                            .add("{status_gens_autosell}", user.isToggleGensAutoSell() ? on : off))
                    .build();
            // set the items
            this.setItems(slots, stack, event -> {
                if (type == null || type.equalsIgnoreCase("dummy")) return;
                // check if the type is toggle cashback
                if (type.equalsIgnoreCase("toggle_cashback")) {
                    user.setToggleCashback(!user.isToggleCashback());
                    this.playSuccessSound();
                }
                // check if the type is toggle inventory autosell
                if (type.equalsIgnoreCase("toggle_inventory_autosell")) {
                    if (Autosell.hasAutosellInventoryPermission(this.player)) {
                        user.setToggleInventoryAutoSell(!user.isToggleInventoryAutoSell());
                        this.playSuccessSound();
                    } else {
                        user.setToggleInventoryAutoSell(false);
                        this.playFailedSound();
                    }
                }
                // check if the type is toggle gens autosell
                if (type.equalsIgnoreCase("toggle_gens_autosell")) {
                    if (Autosell.hasAutosellGensPermission(this.player)) {
                        user.setToggleGensAutoSell(!user.isToggleGensAutoSell());
                        this.playSuccessSound();
                    } else {
                        user.setToggleGensAutoSell(false);
                        this.playFailedSound();
                    }
                }
                // update the item
                this.setAllItems();
            });
        }
    }

    private void playSuccessSound() {
        this.player.playSound(this.player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void playFailedSound() {
        this.player.playSound(this.player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

}
