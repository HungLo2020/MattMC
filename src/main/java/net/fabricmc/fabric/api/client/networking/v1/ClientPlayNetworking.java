/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.api.client.networking.v1;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Offers access to play stage client-side networking functionalities.
 *
 * <p>Client-side networking functionalities include receiving clientbound packets,
 * sending serverbound packets, and events related to client-side network handlers.
 * Packets <strong>received</strong> by this class must be registered to {@link PayloadTypeRegistry#playS2C()} on both ends.
 * Packets <strong>sent</strong> by this class must be registered to {@link PayloadTypeRegistry#playC2S()} on both ends.
 * Packets must be registered before registering any receivers.
 *
 * <p>This class should be only used on the physical client and for the logical client.
 *
 * @see ServerPlayNetworking
 */
public final class ClientPlayNetworking {
	private static final Set<ResourceLocation> globalReceivers = new HashSet<>();

	/**
	 * Registers a handler for a payload type.
	 * A global receiver is registered to all connections, in the present and future.
	 *
	 * @param type the payload type
	 * @param handler the handler
	 * @return false if a handler is already registered to the channel
	 */
	public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.TypeAndCodec<?, T> type, PlayPayloadHandler<T> handler) {
		synchronized (globalReceivers) {
			return globalReceivers.add(type.type().id());
		}
	}

	/**
	 * Removes the handler for a payload type.
	 *
	 * @param id the payload id
	 * @return the previous handler, or {@code null} if no handler was bound to the channel
	 */
	@Nullable
	public static ClientPlayNetworking.PlayPayloadHandler<?> unregisterGlobalReceiver(ResourceLocation id) {
		synchronized (globalReceivers) {
			globalReceivers.remove(id);
		}
		return null;
	}

	/**
	 * Gets all channel names which global receivers are registered for.
	 *
	 * @return all channel names which global receivers are registered for.
	 */
	public static Set<ResourceLocation> getGlobalReceivers() {
		synchronized (globalReceivers) {
			return new HashSet<>(globalReceivers);
		}
	}

	/**
	 * Registers a handler for a payload type.
	 *
	 * @param type the payload type
	 * @param handler the handler
	 * @return {@code false} if a handler is already registered for the type
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static <T extends CustomPacketPayload> boolean registerReceiver(CustomPacketPayload.TypeAndCodec<?, T> type, PlayPayloadHandler<T> handler) {
		// Stub implementation
		return true;
	}

	/**
	 * Removes the handler for a payload id.
	 *
	 * @param id the payload id
	 * @return the previous handler, or {@code null} if no handler was bound to the channel
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	@Nullable
	public static ClientPlayNetworking.PlayPayloadHandler<?> unregisterReceiver(ResourceLocation id) {
		// Stub implementation
		return null;
	}

	/**
	 * Gets all the channel names that the client can receive packets on.
	 *
	 * @return All the channel names that the client can receive packets on
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static Set<ResourceLocation> getReceived() throws IllegalStateException {
		return getGlobalReceivers();
	}

	/**
	 * Gets all channel names that the connected server declared the ability to receive a packets on.
	 *
	 * @return All the channel names the connected server declared the ability to receive a packets on
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static Set<ResourceLocation> getSendable() throws IllegalStateException {
		return new HashSet<>();
	}

	/**
	 * Checks if the connected server declared the ability to receive a packet on a specified channel name.
	 *
	 * @param channelName the channel name
	 * @return {@code true} if the connected server has declared the ability to receive a packet on the specified channel
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static boolean canSend(ResourceLocation channelName) throws IllegalStateException {
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		return false;
	}

	/**
	 * Checks if the connected server declared the ability to receive a specific type of packet.
	 *
	 * @param type the packet type
	 * @return {@code true} if the connected server has declared the ability to receive a specific type of packet
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static boolean canSend(CustomPacketPayload.TypeAndCodec<?, ?> type) throws IllegalStateException {
		Objects.requireNonNull(type, "Packet type cannot be null");
		return false;
	}

	/**
	 * Creates a packet which may be sent to the connected server.
	 *
	 * @param payload the payload
	 * @return a new packet
	 */
	public static <T extends CustomPacketPayload> Packet<ServerCommonPacketListener> createC2SPacket(T payload) {
		// Stub implementation
		return null;
	}

	/**
	 * Gets the packet sender which sends packets to the connected server.
	 *
	 * @return the packet sender
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static PacketSender getSender() throws IllegalStateException {
		// Stub implementation
		return null;
	}

	/**
	 * Sends a packet to the connected server.
	 *
	 * @param payload the payload to send
	 * @throws IllegalStateException if the client is not connected to a server
	 */
	public static void send(CustomPacketPayload payload) {
		Objects.requireNonNull(payload, "Payload cannot be null");
		// Stub implementation
	}

	private ClientPlayNetworking() {
	}

	@FunctionalInterface
	public interface PlayPayloadHandler<T extends CustomPacketPayload> {
		/**
		 * Handles an incoming packet.
		 *
		 * <p>This method is executed on the render thread, and should not be used to manipulate the world.
		 *
		 * @param payload the packet payload
		 * @param context the play networking context
		 */
		void receive(T payload, Context context);
	}

	@ApiStatus.NonExtendable
	public interface Context {
		/**
		 * @return The Minecraft client instance
		 */
		Minecraft client();

		/**
		 * @return The player that received the packet
		 */
		LocalPlayer player();

		/**
		 * @return The packet sender
		 */
		PacketSender responseSender();
	}
}
