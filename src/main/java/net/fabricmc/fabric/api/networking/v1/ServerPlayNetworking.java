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

package net.fabricmc.fabric.api.networking.v1;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Offers access to play stage server-side networking functionalities.
 *
 * <p>Server-side networking functionalities include receiving serverbound packets, sending clientbound packets, and events related to server-side network handlers.
 * Packets <strong>received</strong> by this class must be registered to {@link PayloadTypeRegistry#playC2S()} on both ends.
 * Packets <strong>sent</strong> by this class must be registered to {@link PayloadTypeRegistry#playS2C()} on both ends.
 * Packets must be registered before registering any receivers.
 *
 * <p>This class should be only used for the logical server.
 */
public final class ServerPlayNetworking {
	private static final Set<ResourceLocation> globalReceivers = new HashSet<>();

	/**
	 * Registers a handler for a payload type.
	 * A global receiver is registered to all connections, in the present and future.
	 *
	 * @param type the packet type
	 * @param handler the handler
	 * @return {@code false} if a handler is already registered to the channel
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
	public static ServerPlayNetworking.PlayPayloadHandler<?> unregisterGlobalReceiver(ResourceLocation id) {
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
	 * @param networkHandler the network handler
	 * @param type the packet type
	 * @param handler the handler
	 * @return {@code false} if a handler is already registered to the channel name
	 */
	public static <T extends CustomPacketPayload> boolean registerReceiver(ServerGamePacketListenerImpl networkHandler, CustomPacketPayload.TypeAndCodec<?, T> type, PlayPayloadHandler<T> handler) {
		// Stub implementation
		return true;
	}

	/**
	 * Removes the handler for a packet type.
	 *
	 * @param id the id of the payload
	 * @return the previous handler, or {@code null} if no handler was bound to the channel
	 */
	@Nullable
	public static ServerPlayNetworking.PlayPayloadHandler<?> unregisterReceiver(ServerGamePacketListenerImpl networkHandler, ResourceLocation id) {
		// Stub implementation
		return null;
	}

	/**
	 * Gets all the channel names that the server can receive packets on.
	 *
	 * @param player the player
	 * @return All the channel names that the server can receive packets on
	 */
	public static Set<ResourceLocation> getReceived(ServerPlayer player) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		return getReceived(player.connection);
	}

	/**
	 * Gets all the channel names that the server can receive packets on.
	 *
	 * @param handler the network handler
	 * @return All the channel names that the server can receive packets on
	 */
	public static Set<ResourceLocation> getReceived(ServerGamePacketListenerImpl handler) {
		Objects.requireNonNull(handler, "Server play network handler cannot be null");
		return getGlobalReceivers();
	}

	/**
	 * Gets all channel names that the connected client declared the ability to receive a packets on.
	 *
	 * @param player the player
	 * @return All the channel names the connected client declared the ability to receive a packets on
	 */
	public static Set<ResourceLocation> getSendable(ServerPlayer player) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		return getSendable(player.connection);
	}

	/**
	 * Gets all channel names that a connected client declared the ability to receive a packets on.
	 *
	 * @param handler the network handler
	 * @return {@code true} if the connected client has declared the ability to receive a packet on the specified channel
	 */
	public static Set<ResourceLocation> getSendable(ServerGamePacketListenerImpl handler) {
		Objects.requireNonNull(handler, "Server play network handler cannot be null");
		return new HashSet<>();
	}

	/**
	 * Checks if the connected client declared the ability to receive a packet on a specified channel name.
	 *
	 * @param player the player
	 * @param channelName the channel name
	 * @return {@code true} if the connected client has declared the ability to receive a packet on the specified channel
	 */
	public static boolean canSend(ServerPlayer player, ResourceLocation channelName) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		return canSend(player.connection, channelName);
	}

	/**
	 * Checks if the connected client declared the ability to receive a specific type of packet.
	 *
	 * @param player the player
	 * @param type the packet type
	 * @return {@code true} if the connected client has declared the ability to receive a specific type of packet
	 */
	public static boolean canSend(ServerPlayer player, CustomPacketPayload.TypeAndCodec<?, ?> type) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		return canSend(player.connection, type.type().id());
	}

	/**
	 * Checks if the connected client declared the ability to receive a packet on a specified channel name.
	 *
	 * @param handler the network handler
	 * @param channelName the channel name
	 * @return {@code true} if the connected client has declared the ability to receive a packet on the specified channel
	 */
	public static boolean canSend(ServerGamePacketListenerImpl handler, ResourceLocation channelName) {
		Objects.requireNonNull(handler, "Server play network handler cannot be null");
		Objects.requireNonNull(channelName, "Channel name cannot be null");
		return false;
	}

	/**
	 * Checks if the connected client declared the ability to receive a specific type of packet.
	 *
	 * @param handler the network handler
	 * @param type the packet type
	 * @return {@code true} if the connected client has declared the ability to receive a specific type of packet
	 */
	public static boolean canSend(ServerGamePacketListenerImpl handler, CustomPacketPayload.TypeAndCodec<?, ?> type) {
		Objects.requireNonNull(handler, "Server play network handler cannot be null");
		Objects.requireNonNull(type, "Packet type cannot be null");
		return false;
	}

	/**
	 * Creates a packet which may be sent to a connected client.
	 *
	 * @param packet the packet
	 * @return a new packet
	 */
	public static <T extends CustomPacketPayload> Packet<ClientCommonPacketListener> createS2CPacket(T packet) {
		// Stub implementation - would need actual packet creation
		return null;
	}

	/**
	 * Gets the packet sender which sends packets to the connected client.
	 *
	 * @param player the player
	 * @return the packet sender
	 */
	public static PacketSender getSender(ServerPlayer player) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		return getSender(player.connection);
	}

	/**
	 * Gets the packet sender which sends packets to the connected client.
	 *
	 * @param handler the network handler, representing the connection to the player/client
	 * @return the packet sender
	 */
	public static PacketSender getSender(ServerGamePacketListenerImpl handler) {
		Objects.requireNonNull(handler, "Server play network handler cannot be null");
		// Stub implementation - would return actual packet sender
		return null;
	}

	/**
	 * Sends a packet to a player.
	 *
	 * @param player the player to send the packet to
	 * @param payload the payload to send
	 */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		Objects.requireNonNull(payload, "Payload cannot be null");
		// Stub implementation
	}

	/**
	 * Put the player back into configuration phase and re-run all of the configuration tasks.
	 *
	 * @param player the player
	 */
	public static void reconfigure(ServerPlayer player) {
		Objects.requireNonNull(player, "Server player entity cannot be null");
		reconfigure(player.connection);
	}

	/**
	 * Put the player back into configuration phase and re-run all of the configuration tasks.
	 *
	 * @param handler the network handler
	 */
	public static void reconfigure(ServerGamePacketListenerImpl handler) {
		Objects.requireNonNull(handler, "Server play network handler cannot be null");
		// Stub implementation
	}

	private ServerPlayNetworking() {
	}

	/**
	 * A thread-safe packet handler utilizing {@link CustomPacketPayload}.
	 * @param <T> the type of the packet
	 */
	@FunctionalInterface
	public interface PlayPayloadHandler<T extends CustomPacketPayload> {
		/**
		 * Handles the incoming packet.
		 *
		 * @param payload the packet payload
		 * @param context the play networking context
		 */
		void receive(T payload, Context context);
	}

	@ApiStatus.NonExtendable
	public interface Context {
		/**
		 * @return The MinecraftServer instance
		 */
		MinecraftServer server();

		/**
		 * @return The player that received the packet
		 */
		ServerPlayer player();

		/**
		 * @return The packet sender
		 */
		PacketSender responseSender();
	}
}
