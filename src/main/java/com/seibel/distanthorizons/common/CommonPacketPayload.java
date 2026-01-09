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
		private static AbstractPluginPacketSender getPacketSender() {
			try {
				AbstractPluginPacketSender sender = (AbstractPluginPacketSender) SingletonInjector.INSTANCE.get(IPluginPacketSender.class);
				if (sender == null) {
					LOGGER.error("CommonPacketPayload.Codec: PacketSender is null from SingletonInjector!");
					throw new IllegalStateException("PacketSender is null - SingletonInjector not initialized");
				}
				return sender;
			} catch (Exception e) {
				LOGGER.error("CommonPacketPayload.Codec: Failed to get PacketSender from SingletonInjector", e);
				throw new RuntimeException("Failed to get PacketSender: " + e.getMessage(), e);
			}
		}
		
		@NotNull
		@Override
		public CommonPacketPayload decode(@NotNull FriendlyByteBuf in)
		{ 
			try {
				LOGGER.info("CommonPacketPayload.Codec: Attempting to decode packet, buffer readable bytes: " + in.readableBytes());
				AbstractPluginPacketSender sender = getPacketSender();
				AbstractNetworkMessage message = sender.decodeMessage(in);
				LOGGER.info("CommonPacketPayload.Codec: Successfully decoded message: " + (message != null ? message.getClass().getSimpleName() : "null"));
				return new CommonPacketPayload(message);
			} catch (Exception e) {
				LOGGER.error("CommonPacketPayload.Codec: Failed to decode packet", e);
				throw new RuntimeException("Failed to decode CommonPacketPayload: " + e.getMessage(), e);
			}
		}
		
		@Override
		public void encode(@NotNull FriendlyByteBuf out, CommonPacketPayload payload)
		{ 
			try {
				LOGGER.info("CommonPacketPayload.Codec: Attempting to encode payload with message: " + (payload.message() != null ? payload.message().getClass().getSimpleName() : "null"));
				AbstractPluginPacketSender sender = getPacketSender();
				sender.encodeMessage(out, payload.message());
				LOGGER.info("CommonPacketPayload.Codec: Successfully encoded message");
			} catch (Exception e) {
				LOGGER.error("CommonPacketPayload.Codec: Failed to encode packet", e);
				throw new RuntimeException("Failed to encode CommonPacketPayload: " + e.getMessage(), e);
			}
		}
		
	}
	
}

