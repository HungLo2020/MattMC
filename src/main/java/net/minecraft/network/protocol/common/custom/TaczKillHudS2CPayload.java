package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record TaczKillHudS2CPayload(int amount) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TaczKillHudS2CPayload> TYPE = new CustomPacketPayload.Type<>(
		ResourceLocation.withDefaultNamespace("kill_hud")
	);
	public static final StreamCodec<FriendlyByteBuf, TaczKillHudS2CPayload> STREAM_CODEC = CustomPacketPayload.codec(
		TaczKillHudS2CPayload::write,
		TaczKillHudS2CPayload::new
	);

	private TaczKillHudS2CPayload(FriendlyByteBuf friendlyByteBuf) {
		this(friendlyByteBuf.readVarInt());
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeVarInt(this.amount);
	}

	@Override
	public CustomPacketPayload.Type<TaczKillHudS2CPayload> type() {
		return TYPE;
	}
}
