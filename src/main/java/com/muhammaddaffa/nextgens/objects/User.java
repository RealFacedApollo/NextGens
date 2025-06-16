package com.muhammaddaffa.nextgens.objects;

import org.bukkit.Bukkit;

import java.util.UUID;

public class User {

    private final UUID uuid;
    private int bonus;
    // multiplier system
    private double multiplier;
    // statistics
    private double earnings;
    private int itemsSold;
    private int normalSell, sellwandSell;
    // settings
    private boolean toggleCashback = true;
    private boolean toggleInventoryAutoSell = false;
    private boolean toggleGensAutoSell = false;

    private int interval;

    public User(UUID uuid) {
        this.uuid = uuid;
    }

    public User(UUID uuid, int bonus, double multiplier, double earnings, int itemsSold, int normalSell, int sellwandSell,
                boolean toggleCashback, boolean toggleInventoryAutoSell, boolean toggleGensAutoSell) {
        this.uuid = uuid;
        this.bonus = bonus;
        this.multiplier = multiplier;
        this.earnings = earnings;
        this.itemsSold = itemsSold;
        this.normalSell = normalSell;
        this.sellwandSell = sellwandSell;
        this.toggleCashback = toggleCashback;
        this.toggleInventoryAutoSell = toggleInventoryAutoSell;
        this.toggleGensAutoSell = toggleGensAutoSell;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public String getName() {
        return Bukkit.getOfflinePlayer(this.uuid).getName();
    }

    public int getBonus() {
        return bonus;
    }

    public void setBonus(int bonus) {
        this.bonus = bonus;
        if (this.bonus < 0) {
            this.bonus = 0;
        }
    }

    public void addBonus(int amount) {
        this.bonus += amount;
    }

    public void removeBonus(int amount) {
        this.bonus -= amount;
        if (this.bonus < 0) {
            this.bonus = 0;
        }
    }

    public double getMultiplier() {
        return multiplier;
    }

    public double getVisualMultiplier() {
        double visual = multiplier - 1;
        if (visual < 0) visual = 0;
        return visual;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public void addMultiplier(double amount) {
        this.multiplier += amount;
    }

    public void removeMultiplier(double amount) {
        this.multiplier -= amount;
    }

    public double getEarnings() {
        return earnings;
    }

    public void setEarnings(double earnings) {
        this.earnings = earnings;
    }

    public void addEarnings(double amount) {
        this.earnings += amount;
    }

    public void removeEarnings(double amount) {
        this.earnings -= amount;
    }

    public int getItemsSold() {
        return itemsSold;
    }

    public void setItemsSold(int itemsSold) {
        this.itemsSold = itemsSold;
    }

    public void addItemsSold(int amount) {
        this.itemsSold += amount;
    }

    public void removeItemsSold(int amount) {
        this.itemsSold -= amount;
    }

    public int getNormalSell() {
        return normalSell;
    }

    public void setNormalSell(int normalSell) {
        this.normalSell = normalSell;
    }

    public void addNormalSell(int amount) {
        this.normalSell += amount;
    }

    public void removeNormalSell(int amount) {
        this.normalSell -= amount;
    }

    public int getSellwandSell() {
        return sellwandSell;
    }

    public void setSellwandSell(int sellwandSell) {
        this.sellwandSell = sellwandSell;
    }

    public void addSellwandSell(int amount) {
        this.sellwandSell += amount;
    }

    public void removeSellwandSell(int amount) {
        this.sellwandSell -= amount;
    }

    public int getTotalSell() {
        return this.normalSell + this.sellwandSell;
    }

    public boolean isToggleCashback() {
        return toggleCashback;
    }

    public void setToggleCashback(boolean toggleCashback) {
        this.toggleCashback = toggleCashback;
    }

    public boolean isToggleInventoryAutoSell() {
        return toggleInventoryAutoSell;
    }

    public void setToggleInventoryAutoSell(boolean toggleInventoryAutoSell) {
        this.toggleInventoryAutoSell = toggleInventoryAutoSell;
    }

    public boolean isToggleGensAutoSell() {
        return toggleGensAutoSell;
    }

    public void setToggleGensAutoSell(boolean toggleGensAutoSell) {
        this.toggleGensAutoSell = toggleGensAutoSell;
    }

    public int getInterval() {
        return interval;
    }

    public void updateInterval(int amount) {
        this.interval += amount;
    }

    public void setInterval(int amount) {
        this.interval = interval;
    }

}
