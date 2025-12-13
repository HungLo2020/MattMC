package net.fabricmc.api;

import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Fabric API stub for DedicatedServerModInitializer
 */
@FunctionalInterface
public interface DedicatedServerModInitializer {
    void onInitializeServer();
}
