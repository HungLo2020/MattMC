package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side networking functionality for play stage.
 * Simplified stub for Distant Horizons compatibility.
 */
public final class ClientPlayNetworking {
private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> GLOBAL_RECEIVERS = new HashMap<>();

private ClientPlayNetworking() {}

/**
 * Registers a global receiver for a custom payload type.
 */
public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
CustomPacketPayload.Type<T> type, 
PlayPayloadHandler<T> handler) {
GLOBAL_RECEIVERS.put(type, handler);
return true;
}

/**
 * Sends a custom payload to the server.
 */
public static void send(CustomPacketPayload payload) {
// Send via vanilla networking
Minecraft client = Minecraft.getInstance();
if (client != null && client.getConnection() != null) {
client.getConnection().send(new ServerboundCustomPayloadPacket(payload));
}
}

/**
 * Checks if a packet type can be sent to the server.
 */
public static boolean canSend(CustomPacketPayload.Type<?> type) {
// Stub: always return true for simplicity
Minecraft client = Minecraft.getInstance();
return client != null && client.getConnection() != null;
}

/**
 * Sends a packet to the server via a channel.
 */
public static void send(ResourceLocation channel, FriendlyByteBuf buf) {
// For compatibility - DH uses the CustomPayload version
}

@FunctionalInterface
public interface PlayPayloadHandler<T extends CustomPacketPayload> {
void receive(T payload, Context context);
}

public interface Context {
Minecraft client();
ClientPacketListener listener();
void execute(Runnable task);
}
}
