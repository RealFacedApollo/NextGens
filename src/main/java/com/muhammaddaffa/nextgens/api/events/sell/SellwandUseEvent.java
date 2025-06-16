package com.muhammaddaffa.nextgens.api.events.sell;

import com.muhammaddaffa.nextgens.objects.User;
import com.muhammaddaffa.nextgens.objects.SellData;
import org.bukkit.entity.Player;

public class SellwandUseEvent extends SellEvent{

    public SellwandUseEvent(Player player, User user, SellData sellData) {
        super(player, user, sellData);
    }

}
