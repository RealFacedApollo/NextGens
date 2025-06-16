package com.muhammaddaffa.nextgens.objects;

public class SellData {

    private final User user;
    private final double totalValue;
    private final int totalItems;
    private double multiplier;
    private final SellwandData sellwand;

    public SellData(User user, double totalValue, int totalItems, double multiplier, SellwandData sellwand) {
        this.user = user;
        this.totalValue = totalValue;
        this.totalItems = totalItems;
        this.multiplier = multiplier;
        this.sellwand = sellwand;
    }

    // Getters and setters

    public User getUser() {
        return user;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public SellwandData getSellwand() {
        return sellwand;
    }

}
