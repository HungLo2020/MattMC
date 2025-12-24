package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

/**
 * Packet sent from client to server containing the player's skin data.
 */
public record ServerboundPlayerSkinPacket(String skinName, byte[] skinData, boolean isSlimModel) implements Packet<ServerGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ServerboundPlayerSkinPacket> STREAM_CODEC = Packet.codec(
		ServerboundPlayerSkinPacket::write, ServerboundPlayerSkinPacket::new
	);
	
	// Maximum skin size: 64x64 RGBA = 16KB, we allow up to 32KB for safety
	private static final int MAX_SKIN_SIZE = 32768;

	private ServerboundPlayerSkinPacket(FriendlyByteBuf buf) {
		this(
			buf.readUtf(256), // skin name, max 256 chars
			buf.readByteArray(MAX_SKIN_SIZE), // skin data
			buf.readBoolean() // is slim model
		);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(this.skinName, 256);
		buf.writeByteArray(this.skinData);
		buf.writeBoolean(this.isSlimModel);
	}

	@Override
	public PacketType<ServerboundPlayerSkinPacket> type() {
		return GamePacketTypes.SERVERBOUND_PLAYER_SKIN;
	}

	@Override
	public void handle(ServerGamePacketListener listener) {
		listener.handlePlayerSkin(this);
	}
}
