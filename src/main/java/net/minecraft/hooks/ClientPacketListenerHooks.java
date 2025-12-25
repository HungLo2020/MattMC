package net.minecraft.hooks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

/**
 * Hook interface for client packet listener events.
 * Allows mods to track chunk loading and network events.
 */
public interface ClientPacketListenerHooks {
    /**
     * Called after light data is applied to a chunk.
     * 
     * @param level The client level
     * @param x Chunk X coordinate
     * @param z Chunk Z coordinate
     * @param lightData The light update data
     * @param bl Boolean parameter
     */
    default void onLightDataApplied(ClientLevel level, int x, int z, ClientboundLightUpdatePacketData lightData, boolean bl) {}
    
    /**
     * Called after a chunk unload packet is handled.
     * 
     * @param level The client level
     * @param packet The forget level chunk packet
     */
    default void onChunkUnload(ClientLevel level, ClientboundForgetLevelChunkPacket packet) {}
}
