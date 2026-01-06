package com.mamiyaotaru.voxelmap.fabric;

import com.mamiyaotaru.voxelmap.Events;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class FabricEvents implements Events {
    public FabricEvents() {
        ResourceLocation voxelMapMinimapLayer = ResourceLocation.parse("voxelmap:minimap");
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, voxelMapMinimapLayer, new HudElement() {
            @Override
            public void render(GuiGraphics context, DeltaTracker tickCounter) {
                VoxelConstants.renderOverlay(context);
            }
        });
        
        // Register world save handler to persist VoxelMap data
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            try {
                VoxelConstants.getLogger().info("World saving - flushing VoxelMap data");
                ThreadManager.flushSaveQueue();
            } catch (Exception e) {
                VoxelConstants.getLogger().error("Error flushing VoxelMap data on world save", e);
            }
        });
    }

    @Override
    public void initEvents(VoxelMap map) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> map.onDisconnect());
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> map.onConfigurationInit());
        ClientPlayConnectionEvents.INIT.register((handler, client) -> map.onPlayInit());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> map.onJoinServer());
        ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> map.onClientStopping());
    }
}
