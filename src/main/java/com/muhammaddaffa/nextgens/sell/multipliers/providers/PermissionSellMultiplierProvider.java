package com.muhammaddaffa.nextgens.sell.multipliers.providers;

import com.muhammaddaffa.nextgens.sell.multipliers.SellMultiplierProvider;
import com.muhammaddaffa.nextgens.objects.SellwandData;
import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class PermissionSellMultiplierProvider implements SellMultiplierProvider {

    @Override
    public double getMultiplier(Player player, User user, SellwandData sellwand) {
        return this.getSellMultiplier(player);
    }

    private double getSellMultiplier(Player player) {
        if (player == null) return 0;

        int multiplier = 0;
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String permission = pai.getPermission();
            if (!permission.startsWith("nextgens.multiplier.sell")) {
                continue;
            }
            int current = Integer.parseInt(permission.split("\\.")[3]);
            if (current > multiplier) {
                multiplier = current;
            }
        }
        // get the multiplier in decimals
        return multiplier;
    }

}
