package com.muhammaddaffa.nextgens.api.events.sell;

import com.muhammaddaffa.nextgens.objects.User;
import com.muhammaddaffa.nextgens.objects.SellData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SellEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean isCancelled;

    // --------------------------------------------------------

    private final Player player;
    private final User user;
    private SellData sellData;

    public SellEvent(Player player, User user, SellData sellData) {
        this.player = player;
        this.user = user;
        this.sellData = sellData;
    }

    public Player getPlayer() {
        return player;
    }

    public User getUser() {
        return user;
    }

    public SellData getSellData() {
        return sellData;
    }

    public void setSellData(SellData sellData) {
        this.sellData = sellData;
    }

    // --------------------------------------------------------

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return this.isCancelled;
    }

    @Override
    public void setCancelled(boolean isCancelled) {
        this.isCancelled = isCancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

}
