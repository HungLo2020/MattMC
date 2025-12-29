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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.netty.util.AsciiString;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.protocol.CustomPayload;
import net.minecraft.server.packss.ResourceLocation;
import net.minecraft.util.InvalidIdentifierException;

public record RegistrationPayload(Id<RegistrationPayload> id, List<ResourceLocation> channels) implements CustomPayload {
	public static final CustomPayload.Id<RegistrationPayload> REGISTER = new CustomPayload.Id<>(NetworkingImpl.REGISTER_CHANNEL);
	public static final CustomPayload.Id<RegistrationPayload> UNREGISTER = new CustomPayload.Id<>(NetworkingImpl.UNREGISTER_CHANNEL);
	public static final PacketCodec<FriendlyByteBuf, RegistrationPayload> REGISTER_CODEC = codec(REGISTER);
	public static final PacketCodec<FriendlyByteBuf, RegistrationPayload> UNREGISTER_CODEC = codec(UNREGISTER);

	private RegistrationPayload(Id<RegistrationPayload> id, FriendlyByteBuf buf) {
		this(id, read(buf));
	}

	private void write(FriendlyByteBuf buf) {
		boolean first = true;

		for (ResourceLocation channel : channels) {
			if (first) {
				first = false;
			} else {
				buf.writeByte(0);
			}

			buf.writeBytes(channel.toString().getBytes(StandardCharsets.US_ASCII));
		}
	}

	private static List<ResourceLocation> read(FriendlyByteBuf buf) {
		List<ResourceLocation> ids = new ArrayList<>();
		StringBuilder active = new StringBuilder();

		while (buf.isReadable()) {
			byte b = buf.readByte();

			if (b != 0) {
				active.append(AsciiString.b2c(b));
			} else {
				addId(ids, active);
				active = new StringBuilder();
			}
		}

		addId(ids, active);

		return Collections.unmodifiableList(ids);
	}

	private static void addId(List<ResourceLocation> ids, StringBuilder sb) {
		String literal = sb.toString();

		try {
			ids.add(ResourceLocation.of(literal));
		} catch (InvalidIdentifierException ex) {
			NetworkingImpl.LOGGER.warn("Received invalid channel identifier \"{}\"", literal);
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return id;
	}

	private static PacketCodec<FriendlyByteBuf, RegistrationPayload> codec(Id<RegistrationPayload> id) {
		return CustomPayload.codecOf(RegistrationPayload::write, buf -> new RegistrationPayload(id, buf));
	}
}
