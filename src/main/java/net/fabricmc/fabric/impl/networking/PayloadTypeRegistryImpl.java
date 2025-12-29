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

package net.fabricmc.fabric.impl.networking;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.netty.buffer.ByteBufUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.protocol.CustomPacketPayload;
import net.minecraft.network.state.NetworkState;
import net.minecraft.server.packss.ResourceLocation;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.impl.networking.splitter.FabricPacketSplitter;

public class PayloadTypeRegistryImpl<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_C2S = new PayloadTypeRegistryImpl<>(NetworkPhase.CONFIGURATION, NetworkSide.SERVERBOUND);
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_S2C = new PayloadTypeRegistryImpl<>(NetworkPhase.CONFIGURATION, NetworkSide.CLIENTBOUND);
	public static final PayloadTypeRegistryImpl<RegistryByteBuf> PLAY_C2S = new PayloadTypeRegistryImpl<>(NetworkPhase.PLAY, NetworkSide.SERVERBOUND);
	public static final PayloadTypeRegistryImpl<RegistryByteBuf> PLAY_S2C = new PayloadTypeRegistryImpl<>(NetworkPhase.PLAY, NetworkSide.CLIENTBOUND);
	private final Map<ResourceLocation, CustomPacketPayload.Type<B, ? extends CustomPacketPayload>> packetTypes = new HashMap<>();
	private final Object2IntMap<ResourceLocation> maxPacketSize = new Object2IntOpenHashMap<>();
	private final NetworkPhase state;
	private final NetworkSide side;
	private final int minimalSplittableSize;

	private PayloadTypeRegistryImpl(NetworkPhase state, NetworkSide side) {
		this.state = state;
		this.side = side;
		this.minimalSplittableSize = side == NetworkSide.CLIENTBOUND ? FabricPacketSplitter.SAFE_S2C_SPLIT_SIZE : FabricPacketSplitter.SAFE_C2S_SPLIT_SIZE;
	}

	@Nullable
	public static PayloadTypeRegistryImpl<?> get(NetworkState<?> state) {
		return switch (state.id()) {
		case CONFIGURATION -> state.side() == NetworkSide.CLIENTBOUND ? CONFIGURATION_S2C : CONFIGURATION_C2S;
		case PLAY -> state.side() == NetworkSide.CLIENTBOUND ? PLAY_S2C : PLAY_C2S;
		default -> null;
		};
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<? super B, T> register(CustomPacketPayload.Id<T> id, PacketCodec<? super B, T> codec) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(codec, "codec");

		final CustomPacketPayload.Type<B, T> payloadType = new CustomPacketPayload.Type<>(id, codec.cast());

		if (packetTypes.containsKey(id.id())) {
			throw new IllegalArgumentException("Packet type " + id + " is already registered!");
		}

		packetTypes.put(id.id(), payloadType);
		return payloadType;
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<? super B, T> registerLarge(CustomPacketPayload.Id<T> id, PacketCodec<? super B, T> codec, int maxPayloadSize) {
		if (maxPayloadSize < 0) {
			throw new IllegalArgumentException("Provided maxPayloadSize needs to be positive!");
		}

		CustomPacketPayload.Type<? super B, T> type = register(id, codec);
		// Defines max packet size, increased by length of packet's ResourceLocation to cover full size of CustomPayloadX2YPackets.
		int identifierSize = ByteBufUtil.utf8MaxBytes(id.id().toString());
		int maxPacketSize = maxPayloadSize + VarInts.getSizeInBytes(identifierSize) + identifierSize + 5 * 2;

		// Prevent overflow
		if (maxPacketSize < 0) {
			maxPacketSize = Integer.MAX_VALUE;
		}

		// No need to enable splitting, if packet's max size is smaller than chunk
		if (maxPacketSize > this.minimalSplittableSize) {
			this.maxPacketSize.put(id.id(), maxPacketSize);
		}

		return type;
	}

	@Nullable
	public CustomPacketPayload.Type<B, ? extends CustomPacketPayload> get(ResourceLocation id) {
		return packetTypes.get(id);
	}

	@Nullable
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<B, T> get(CustomPacketPayload.Id<T> id) {
		//noinspection unchecked
		return (CustomPacketPayload.Type<B, T>) packetTypes.get(id.id());
	}

	public int getMaxPacketSize(ResourceLocation id) {
		return this.maxPacketSize.getOrDefault(id, -1);
	}

	public NetworkPhase getPhase() {
		return state;
	}

	public NetworkSide getSide() {
		return side;
	}
}
