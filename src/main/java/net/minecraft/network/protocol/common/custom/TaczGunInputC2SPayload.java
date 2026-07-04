package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record TaczGunInputC2SPayload(TaczGunInputC2SPayload.Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TaczGunInputC2SPayload> TYPE = new CustomPacketPayload.Type<>(
		ResourceLocation.withDefaultNamespace("gun_input")
	);
	public static final StreamCodec<FriendlyByteBuf, TaczGunInputC2SPayload> STREAM_CODEC = CustomPacketPayload.codec(
		TaczGunInputC2SPayload::write,
		TaczGunInputC2SPayload::new
	);

	private TaczGunInputC2SPayload(FriendlyByteBuf friendlyByteBuf) {
		this(Action.byId(friendlyByteBuf.readUnsignedByte()));
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeByte(this.action.ordinal());
	}

	@Override
	public CustomPacketPayload.Type<TaczGunInputC2SPayload> type() {
		return TYPE;
	}

	public enum Action {
		SHOOT,
		RELOAD,
		FIRE_SELECT;

		private static Action byId(int id) {
			Action[] actions = values();
			return id >= 0 && id < actions.length ? actions[id] : SHOOT;
		}
	}
}
