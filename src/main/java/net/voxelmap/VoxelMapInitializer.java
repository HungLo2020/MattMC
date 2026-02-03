package net.voxelmap;

import net.voxelmap.fabric.*;
import net.voxelmap.fabric.*;

/**
 * Initializer for VoxelMap direct integration into MattMC.
 * Replaces VoxelmapFabricMod.onInitializeClient()
 */
public class VoxelMapInitializer {
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) {
            VoxelConstants.getLogger().warn("VoxelMap already initialized!");
            return;
        }
        
        VoxelConstants.getLogger().info("Initializing VoxelMap...");
        
        // Initialize packet handlers (original FabricMod code)
        new VoxelmapSettingsChannelHandler();
        new VoxelmapWorldIdChannelHandler();
        
        // Set up abstraction layer
        VoxelConstants.setEvents(new FabricEvents());
        VoxelConstants.setPacketBridge(new FabricPacketBridge());
        VoxelConstants.setModApiBride(new FabricModApiBridge());  // Note: typo in VoxelConstants method name
        
        initialized = true;
        VoxelConstants.getLogger().info("VoxelMap initialization complete");
    }
    
    public static void lateInit() {
        VoxelConstants.getLogger().info("VoxelMap late initialization...");
        
        // Call VoxelConstants.lateInit() which properly initializes the VoxelMap instance
        // with showUnderMenus=true and isFair=false
        VoxelConstants.lateInit();
        
        VoxelConstants.getLogger().info("VoxelMap fully initialized");
    }
}
