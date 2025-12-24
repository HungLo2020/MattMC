package net.minecraft.util;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public enum Unit {
	INSTANCE;

	public static final Codec<Unit> CODEC = Codec.unit(INSTANCE);
	public static final StreamCodec<ByteBuf, Unit> STREAM_CODEC = StreamCodec.unit(INSTANCE);
}
