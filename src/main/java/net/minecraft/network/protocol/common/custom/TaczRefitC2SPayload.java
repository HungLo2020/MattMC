package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TaczAttachmentType;

public record TaczRefitC2SPayload(TaczRefitC2SPayload.Action action, int attachmentSlot, TaczAttachmentType attachmentType) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TaczRefitC2SPayload> TYPE = new CustomPacketPayload.Type<>(
		ResourceLocation.withDefaultNamespace("refit")
	);
	public static final StreamCodec<FriendlyByteBuf, TaczRefitC2SPayload> STREAM_CODEC = CustomPacketPayload.codec(
		TaczRefitC2SPayload::write,
		TaczRefitC2SPayload::new
	);

	private TaczRefitC2SPayload(FriendlyByteBuf friendlyByteBuf) {
		this(Action.byId(friendlyByteBuf.readUnsignedByte()), friendlyByteBuf.readVarInt(), TaczAttachmentType.byId(friendlyByteBuf.readUnsignedByte()));
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeByte(this.action.ordinal());
		friendlyByteBuf.writeVarInt(this.attachmentSlot);
		friendlyByteBuf.writeByte(this.attachmentType.ordinal());
	}

	@Override
	public CustomPacketPayload.Type<TaczRefitC2SPayload> type() {
		return TYPE;
	}

	public enum Action {
		INSTALL,
		UNINSTALL;

		private static Action byId(int id) {
			Action[] actions = values();
			return id >= 0 && id < actions.length ? actions[id] : INSTALL;
		}
	}
}
