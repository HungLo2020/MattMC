package com.github.alexthe666.citadel.fabric;

import com.github.alexthe666.citadel.Citadel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point for Citadel mod.
 * Handles initialization on both client and server.
 */
public class CitadelFabricMod implements ModInitializer, ClientModInitializer, DedicatedServerModInitializer {
    
    @Override
    public void onInitialize() {
        // Common initialization
        Citadel.commonInit();
    }
    
    @Override
    public void onInitializeClient() {
        // Client-only initialization
        Citadel.clientInit();
    }
    
    @Override
    public void onInitializeServer() {
        // Server-only initialization
        Citadel.serverInit();
    }
}
