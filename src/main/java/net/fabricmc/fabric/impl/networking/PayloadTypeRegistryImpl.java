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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class PayloadTypeRegistryImpl<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_C2S = new PayloadTypeRegistryImpl<>();
	public static final PayloadTypeRegistryImpl<FriendlyByteBuf> CONFIGURATION_S2C = new PayloadTypeRegistryImpl<>();
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> PLAY_C2S = new PayloadTypeRegistryImpl<>();
	public static final PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> PLAY_S2C = new PayloadTypeRegistryImpl<>();

	private final Map<CustomPacketPayload.TypeAndCodec<?, ?>, StreamCodec<?, ?>> codecs = new HashMap<>();

	private PayloadTypeRegistryImpl() {
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> register(CustomPacketPayload.TypeAndCodec<? super B, T> id, StreamCodec<? super B, T> codec) {
		codecs.put(id, codec);
		return id.type();
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> registerLarge(CustomPacketPayload.TypeAndCodec<? super B, T> id, StreamCodec<? super B, T> codec, int maxPacketSize) {
		// For now, treat large packets the same as regular packets
		codecs.put(id, codec);
		return id.type();
	}
}
