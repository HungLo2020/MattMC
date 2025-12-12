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

package net.fabricmc.fabric.mixin.networking;

import java.util.List;
import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;

import net.fabricmc.fabric.impl.networking.FabricCustomPayloadPacketCodec;
import net.fabricmc.fabric.impl.networking.GenericPayloadAccessor;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.splitter.FabricPacketSplitter;
import net.fabricmc.fabric.impl.networking.splitter.SplittablePacket;

@Mixin(CustomPayloadC2SPacket.class)
public class CustomPayloadC2SPacketMixin implements SplittablePacket, GenericPayloadAccessor {
	@Shadow
	@Final
	private CustomPayload payload;

	@WrapOperation(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/packet/CustomPayload;createCodec(Lnet/minecraft/network/packet/CustomPayload$CodecFactory;Ljava/util/List;)Lnet/minecraft/network/codec/PacketCodec;"
			)
	)
	private static PacketCodec<PacketByteBuf, CustomPayload> wrapCodec(CustomPayload.CodecFactory<PacketByteBuf> unknownCodecFactory, List<CustomPayload.Type<PacketByteBuf, ?>> types, Operation<PacketCodec<PacketByteBuf, CustomPayload>> original) {
		PacketCodec<PacketByteBuf, CustomPayload> codec = original.call(unknownCodecFactory, types);
		FabricCustomPayloadPacketCodec<PacketByteBuf> fabricCodec = (FabricCustomPayloadPacketCodec<PacketByteBuf>) codec;
		fabricCodec.fabric_setPacketCodecProvider((packetByteBuf, identifier) -> {
			// CustomPayloadC2SPacket does not have a separate codec for play/configuration. We know if the packetByteBuf is a PacketByteBuf we are in the play phase.
			if (packetByteBuf instanceof RegistryByteBuf) {
				return (CustomPayload.Type<PacketByteBuf, ? extends CustomPayload>) (Object) PayloadTypeRegistryImpl.PLAY_C2S.get(identifier);
			}

			return PayloadTypeRegistryImpl.CONFIGURATION_C2S.get(identifier);
		});
		return codec;
	}

	@Override
	public void fabric_split(PayloadTypeRegistryImpl<?> payloadTypeRegistry, ChannelHandlerContext channelHandlerContext, EncoderHandler<?> encoder, Packet<?> packet, Consumer<Packet<?>> consumer) throws Exception {
		int size = payloadTypeRegistry.getMaxPacketSize(this.payload.getId().id());

		if (size == -1) {
			consumer.accept((Packet<?>) this);
			return;
		}

		FabricPacketSplitter.genericPacketSplitter(this.payload.getId().id(), channelHandlerContext, encoder, packet, CustomPayloadC2SPacket::new, consumer, FabricPacketSplitter.SAFE_C2S_SPLIT_SIZE, size);
	}

	@Override
	public CustomPayload fabric_payload() {
		return this.payload;
	}
}
