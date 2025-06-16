package com.muhammaddaffa.nextgens.sell.multipliers.providers;

import com.muhammaddaffa.nextgens.sell.multipliers.SellMultiplierProvider;
import com.muhammaddaffa.nextgens.objects.SellwandData;
import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;

public class UserSellMultiplierProvider implements SellMultiplierProvider {

    @Override
    public double getMultiplier(Player player, User user, SellwandData sellwand) {
        return user.getMultiplier();
    }

}
