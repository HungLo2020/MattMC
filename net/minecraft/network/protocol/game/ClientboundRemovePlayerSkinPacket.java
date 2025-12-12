package net.minecraft.network.protocol.game;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

import java.util.UUID;

/**
 * Packet sent from server to client to remove a player's skin from cache when they disconnect.
 */
public record ClientboundRemovePlayerSkinPacket(UUID playerId) implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundRemovePlayerSkinPacket> STREAM_CODEC = Packet.codec(
		ClientboundRemovePlayerSkinPacket::write, ClientboundRemovePlayerSkinPacket::new
	);

	private ClientboundRemovePlayerSkinPacket(FriendlyByteBuf buf) {
		this(UUIDUtil.STREAM_CODEC.decode(buf));
	}

	private void write(FriendlyByteBuf buf) {
		UUIDUtil.STREAM_CODEC.encode(buf, this.playerId);
	}

	@Override
	public PacketType<ClientboundRemovePlayerSkinPacket> type() {
		return GamePacketTypes.CLIENTBOUND_REMOVE_PLAYER_SKIN;
	}

	@Override
	public void handle(ClientGamePacketListener listener) {
		listener.handleRemovePlayerSkin(this);
	}
}
