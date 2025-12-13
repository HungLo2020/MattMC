package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric API stub for PacketSender
 */
public interface PacketSender {
    void sendPacket(CustomPacketPayload payload);
}
