package com.seibel.distanthorizons.common;


import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record CommonPacketPayload(@Nullable AbstractNetworkMessage message) implements CustomPacketPayload
{
	public static final Type<CommonPacketPayload> TYPE = new Type<>(AbstractPluginPacketSender.WRAPPER_PACKET_RESOURCE);
	public static final StreamCodec<FriendlyByteBuf, CommonPacketPayload> STREAM_CODEC = new Codec();
	
	@NotNull
	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
	
	
	public static class Codec implements StreamCodec<FriendlyByteBuf, CommonPacketPayload>
	{
		private static final DhLogger LOGGER = new DhLoggerBuilder().build();
		
		// Lazy initialization to avoid dependency injection issues during class loading
		@Nullable
		private static AbstractPluginPacketSender getPacketSender() {
			try {
				AbstractPluginPacketSender sender = (AbstractPluginPacketSender) SingletonInjector.INSTANCE.get(IPluginPacketSender.class);
				if (sender == null) {
					LOGGER.warn("CommonPacketPayload.Codec: PacketSender is null - Distant Horizons not fully initialized yet. Packet will be skipped.");
				}
				return sender;
			} catch (Exception e) {
				LOGGER.error("CommonPacketPayload.Codec: Failed to get PacketSender from SingletonInjector", e);
				return null;
			}
		}
		
		@NotNull
		@Override
		public CommonPacketPayload decode(@NotNull FriendlyByteBuf in)
		{ 
			try {
				LOGGER.debug("CommonPacketPayload.Codec: Attempting to decode packet, buffer readable bytes: " + in.readableBytes());
				AbstractPluginPacketSender sender = getPacketSender();
				
				// If DI system isn't ready yet, skip the packet data and return empty payload
				if (sender == null) {
					LOGGER.warn("CommonPacketPayload.Codec: Skipping packet decode - PacketSender not available yet. Discarding " + in.readableBytes() + " bytes.");
					in.skipBytes(in.readableBytes()); // Discard the packet data
					return new CommonPacketPayload(null); // Return empty payload
				}
				
				AbstractNetworkMessage message = sender.decodeMessage(in);
				LOGGER.debug("CommonPacketPayload.Codec: Successfully decoded message: " + (message != null ? message.getClass().getSimpleName() : "null"));
				return new CommonPacketPayload(message);
			} catch (Exception e) {
				LOGGER.error("CommonPacketPayload.Codec: Failed to decode packet", e);
				// Skip remaining bytes to prevent further decode errors
				if (in.readableBytes() > 0) {
					in.skipBytes(in.readableBytes());
				}
				return new CommonPacketPayload(null); // Return empty payload instead of crashing
			}
		}
		
		@Override
		public void encode(@NotNull FriendlyByteBuf out, CommonPacketPayload payload)
		{ 
			try {
				LOGGER.debug("CommonPacketPayload.Codec: Attempting to encode payload with message: " + (payload.message() != null ? payload.message().getClass().getSimpleName() : "null"));
				
				// If message is null, write empty packet
				if (payload.message() == null) {
					LOGGER.debug("CommonPacketPayload.Codec: Message is null, writing empty packet");
					return;
				}
				
				AbstractPluginPacketSender sender = getPacketSender();
				
				// If DI system isn't ready yet, skip encoding
				if (sender == null) {
					LOGGER.warn("CommonPacketPayload.Codec: Skipping packet encode - PacketSender not available yet");
					return;
				}
				
				sender.encodeMessage(out, payload.message());
				LOGGER.debug("CommonPacketPayload.Codec: Successfully encoded message");
			} catch (Exception e) {
				LOGGER.error("CommonPacketPayload.Codec: Failed to encode packet", e);
				// Don't throw - this would crash the connection
			}
		}
		
	}
	
}

