package com.muhammaddaffa.nextgens.objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class ActiveGenerator {

    private final UUID owner;
    private final Location location;
    private Generator generator;
    private double timer;
    private boolean corrupted;

    public ActiveGenerator(UUID owner, Location location, Generator generator) {
        this.owner = owner;
        this.location = location;
        this.generator = generator;
    }

    public ActiveGenerator(UUID owner, Location location, Generator generator, double timer, boolean corrupted) {
        this.owner = owner;
        this.location = location;
        this.generator = generator;
        this.timer = timer;
        this.corrupted = corrupted;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return Bukkit.getOfflinePlayer(this.owner).getName();
    }

    public Location getLocation() {
        return location;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }

    public double getTimer() {
        return timer;
    }

    public void setTimer(double timer) {
        this.timer = timer;
    }

    public void addTimer(double amount) {
        this.timer += amount;
    }

    public boolean isCorrupted() {
        return corrupted;
    }

    public void setCorrupted(boolean corrupted) {
        this.corrupted = corrupted;
    }

    public boolean isChunkLoaded() {
        int x = this.location.getBlockX() >> 4;
        int z = this.location.getBlockZ() >> 4;
        World world = this.location.getWorld();
        if (world == null) {
            return false;
        }
        return world.isChunkLoaded(x, z);
    }

}
