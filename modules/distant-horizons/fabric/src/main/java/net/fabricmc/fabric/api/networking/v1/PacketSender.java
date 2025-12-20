package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents something that supports sending packets to channels.
 * Fabric API stub for Distant Horizons compatibility.
 */
public interface PacketSender {
/**
 * Sends a packet.
 *
 * @param payload the payload to send
 */
void sendPacket(CustomPacketPayload payload);

/**
 * Sends a packet to a channel.
 *
 * @param channel the id of the channel
 * @param buf     the content of the packet
 */
void sendPacket(ResourceLocation channel, FriendlyByteBuf buf);
}
