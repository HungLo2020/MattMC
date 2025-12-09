package net.minecraft.network.protocol.game;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

import java.util.UUID;

/**
 * Packet sent from server to client containing another player's skin data.
 */
public record ClientboundPlayerSkinPacket(UUID playerId, String skinName, byte[] skinData, boolean isSlimModel) implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundPlayerSkinPacket> STREAM_CODEC = Packet.codec(
		ClientboundPlayerSkinPacket::write, ClientboundPlayerSkinPacket::new
	);
	
	private static final int MAX_SKIN_SIZE = 32768;

	private ClientboundPlayerSkinPacket(FriendlyByteBuf buf) {
		this(
			UUIDUtil.STREAM_CODEC.decode(buf), // player UUID
			buf.readUtf(256), // skin name
			buf.readByteArray(MAX_SKIN_SIZE), // skin data
			buf.readBoolean() // is slim model
		);
	}

	private void write(FriendlyByteBuf buf) {
		UUIDUtil.STREAM_CODEC.encode(buf, this.playerId);
		buf.writeUtf(this.skinName, 256);
		buf.writeByteArray(this.skinData);
		buf.writeBoolean(this.isSlimModel);
	}

	@Override
	public PacketType<ClientboundPlayerSkinPacket> type() {
		return GamePacketTypes.CLIENTBOUND_PLAYER_SKIN;
	}

	@Override
	public void handle(ClientGamePacketListener listener) {
		listener.handlePlayerSkin(this);
	}
}
