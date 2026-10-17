package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

/** Literal original fixed/prefixed long-array methods; source-pinned by verification. */
final class JavaLongArray {
	public static void writeLongArray(ByteBuf byteBuf, long[] ls) {
		VarInt.write(byteBuf, ls.length);
		writeFixedSizeLongArray(byteBuf, ls);
	}

	public static void writeFixedSizeLongArray(ByteBuf byteBuf, long[] ls) {
		for (long l : ls) {
			byteBuf.writeLong(l);
		}
	}

	public static long[] readLongArray(ByteBuf byteBuf) {
		int i = VarInt.read(byteBuf);
		int j = byteBuf.readableBytes() / 8;
		if (i > j) {
			throw new DecoderException("LongArray with size " + i + " is bigger than allowed " + j);
		} else {
			return readFixedSizeLongArray(byteBuf, new long[i]);
		}
	}

	public static long[] readFixedSizeLongArray(ByteBuf byteBuf, long[] ls) {
		for (int i = 0; i < ls.length; i++) {
			ls[i] = byteBuf.readLong();
		}

		return ls;
	}
}
