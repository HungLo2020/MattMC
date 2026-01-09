package com.seibel.distanthorizons.common;


import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
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
		// Lazy initialization to avoid dependency injection issues during class loading
		private static AbstractPluginPacketSender getPacketSender() {
			return (AbstractPluginPacketSender) SingletonInjector.INSTANCE.get(IPluginPacketSender.class);
		}
		
		@NotNull
		@Override
		public CommonPacketPayload decode(@NotNull FriendlyByteBuf in)
		{ return new CommonPacketPayload(getPacketSender().decodeMessage(in)); }
		
		@Override
		public void encode(@NotNull FriendlyByteBuf out, CommonPacketPayload payload)
		{ getPacketSender().encodeMessage(out, payload.message()); }
		
	}
	
}

