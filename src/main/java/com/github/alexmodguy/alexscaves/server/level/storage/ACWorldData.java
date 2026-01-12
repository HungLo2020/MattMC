package com.github.alexmodguy.alexscaves.server.level.storage;

import net.minecraft.server.level.ServerLevel;

// Stub for world data
public class ACWorldData {
    public static ACWorldData get(ServerLevel level) {
        return new ACWorldData();
    }
    
    public boolean isPrimordialBossDefeatedOnce() {
        return true; // Always return true so spawning isn't blocked
    }
}
