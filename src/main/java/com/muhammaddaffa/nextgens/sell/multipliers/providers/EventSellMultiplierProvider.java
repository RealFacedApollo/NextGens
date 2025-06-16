package com.muhammaddaffa.nextgens.sell.multipliers.providers;

import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.Event;
import com.muhammaddaffa.nextgens.sell.multipliers.SellMultiplierProvider;
import com.muhammaddaffa.nextgens.objects.SellwandData;
import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;

public class EventSellMultiplierProvider implements SellMultiplierProvider {

    @Override
    public double getMultiplier(Player player, User user, SellwandData sellwand) {
        Event event = NextGens.getInstance().getEventManager().getActiveEvent();
        // If there is no active event or event is not valid, return 0
        if (event == null || event.getType() != Event.Type.SELL_MULTIPLIER || event.getSellMultiplier() == null) {
            return 0;
        }
        return event.getSellMultiplier();
    }

}
