package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Registry for custom payload types.
 * Simplified stub for Distant Horizons and VoxelMap compatibility.
 */
public final class PayloadTypeRegistry<B> {
	
	private PayloadTypeRegistry() {}
	
	/**
	 * Gets the registry for client-to-server packets.
	 */
	public static PayloadTypeRegistry<CustomPacketPayload> playC2S() {
		return new PayloadTypeRegistry<>();
	}
	
	/**
	 * Gets the registry for server-to-client packets.
	 */
	public static PayloadTypeRegistry<CustomPacketPayload> playS2C() {
		return new PayloadTypeRegistry<>();
	}
	
	/**
	 * Gets the registry for client-to-server configuration packets.
	 */
	public static PayloadTypeRegistry<CustomPacketPayload> configurationC2S() {
		return new PayloadTypeRegistry<>();
	}
	
	/**
	 * Gets the registry for server-to-client configuration packets.
	 */
	public static PayloadTypeRegistry<CustomPacketPayload> configurationS2C() {
		return new PayloadTypeRegistry<>();
	}
	
	/**
	 * Registers a custom payload type.
	 */
	public <T extends CustomPacketPayload> PayloadTypeRegistry<B> register(CustomPacketPayload.Type<T> type, Object codecOrStreamCodec) {
		// Stub - registration is handled elsewhere
		return this;
	}
}
