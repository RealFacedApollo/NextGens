package com.muhammaddaffa.nextgens.sell.multipliers;

import com.muhammaddaffa.nextgens.objects.SellwandData;
import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;

public interface SellMultiplierProvider {

    /**
     * Returns the additive multiplier value for a sale.
     * Should return the multiplier amount to be added to the base multiplier.
     * For example, return 2.0 for a 2x multiplier.
     */
    double getMultiplier(Player player, User user, SellwandData sellwand);

}
