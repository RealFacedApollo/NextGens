package com.muhammaddaffa.nextgens.api.events;

import com.muhammaddaffa.nextgens.objects.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerCashbackEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean isCancelled;

    // --------------------------------------------------------

    private final Player player;
    private final User user;
    private double percentage;

    public PlayerCashbackEvent(Player player, User user, double percentage) {
        this.player = player;
        this.user = user;
        this.percentage = percentage;
    }

    public Player getPlayer() {
        return player;
    }

    public User getUser() {
        return user;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
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
