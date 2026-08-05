package net.sodium.fabric;

import net.sodium.client.render.chunk.map.ChunkStatus;
import net.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.sodium.client.render.StaticTerrainParityDiagnostics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.hooks.ClientPacketListenerHooks;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

/**
 * Sodium implementation of ClientPacketListenerHooks.
 * Updates chunk tracker when chunks are loaded/unloaded.
 */
public class SodiumClientPacketListenerHook implements ClientPacketListenerHooks {
    @Override
    public void onLightDataApplied(ClientLevel level, int x, int z, ClientboundLightUpdatePacketData lightData, boolean bl) {
        // Mark chunk as having light data for Sodium's chunk tracker
        ChunkTrackerHolder.get(level)
                .onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_LIGHT_DATA);
        StaticTerrainParityDiagnostics.recordAppearanceLightLifecycle(
                level, "client-light-applied", x, z, ChunkStatus.FLAG_HAS_LIGHT_DATA);
    }

    @Override
    public void onChunkUnload(ClientLevel level, ClientboundForgetLevelChunkPacket packet) {
        // Remove all chunk status flags when chunk is unloaded
        ChunkTrackerHolder.get(level)
                .onChunkStatusRemoved(packet.pos().x, packet.pos().z, ChunkStatus.FLAG_ALL);
    }
}
