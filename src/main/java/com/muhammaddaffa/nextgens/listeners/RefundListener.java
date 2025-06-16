package com.muhammaddaffa.nextgens.listeners;

import com.muhammaddaffa.nextgens.managers.RefundManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class RefundListener implements Listener {

    private final RefundManager refundManager;

    public RefundListener(RefundManager refundManager) {
        this.refundManager = refundManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refundManager.giveItemOnJoin(event.getPlayer());
    }

}
