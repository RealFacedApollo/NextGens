package com.muhammaddaffa.nextgens.managers;

import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.managers.SellManager;
import com.muhammaddaffa.nextgens.objects.User;
import com.muhammaddaffa.nextgens.managers.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public record AutosellManager(
        UserManager userManager
) {

    public void startTask() {
        Executor.syncTimer(0L, 20L, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                User user = this.userManager.getUser(player);
                // check if player has autosell inventory permission
                if (!Autosell.hasAutosellInventoryPermission(player) ||
                        !user.isToggleInventoryAutoSell()) continue;
                // get the interval
                int sellInterval = Autosell.getAutosellInventoryInterval(player);
                // check if sell interval is equals or greater than user interval
                if (sellInterval >= user.getInterval()) {
                    // sell the inventory
                    NextGens.getInstance().getSellManager().performSell(player, null, true, player.getInventory());
                    // set interval back to 0
                    user.setInterval(0);
                    continue;
                }
                // update the player interval
                user.updateInterval(1);
            }
        });
    }

}
