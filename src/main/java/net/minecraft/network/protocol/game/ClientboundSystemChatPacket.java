package net.minecraft.network.protocol.game;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.slf4j.Logger;

public record ClientboundSystemChatPacket(Component content, boolean overlay) implements Packet<ClientGamePacketListener> {
	private static final Logger LOGGER = LogUtils.getLogger();
	
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSystemChatPacket> STREAM_CODEC = StreamCodec.composite(
		ComponentSerialization.TRUSTED_STREAM_CODEC,
		ClientboundSystemChatPacket::content,
		ByteBufCodecs.BOOL,
		ClientboundSystemChatPacket::overlay,
		ClientboundSystemChatPacket::new
	);

	public ClientboundSystemChatPacket(Component content, boolean overlay) {
		this.content = content;
		this.overlay = overlay;
		// DEBUG: Log packet creation
		LOGGER.info("=== SYSTEM CHAT PACKET CREATED ===");
		LOGGER.info("Content: {}", content);
		LOGGER.info("Content style: {}", content.getStyle());
		LOGGER.info("Content style color: {}", content.getStyle().getColor());
		LOGGER.info("Content style click event: {}", content.getStyle().getClickEvent());
	}

	@Override
	public PacketType<ClientboundSystemChatPacket> type() {
		return GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT;
	}

	public void handle(ClientGamePacketListener clientGamePacketListener) {
		// DEBUG: Log packet handling on client
		LOGGER.info("=== SYSTEM CHAT PACKET RECEIVED ON CLIENT ===");
		LOGGER.info("Content: {}", this.content);
		LOGGER.info("Content style: {}", this.content.getStyle());
		LOGGER.info("Content style color: {}", this.content.getStyle().getColor());
		LOGGER.info("Content style click event: {}", this.content.getStyle().getClickEvent());
		
		clientGamePacketListener.handleSystemChat(this);
	}

	@Override
	public boolean isSkippable() {
		return true;
	}
}
