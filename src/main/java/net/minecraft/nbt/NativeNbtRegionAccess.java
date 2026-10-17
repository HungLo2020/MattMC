package net.minecraft.nbt;

import java.io.IOException;

public final class NativeNbtRegionAccess {
	private NativeNbtRegionAccess() {
	}

	public static CompoundTag readTape(byte[] tape) throws IOException {
		return NativeNbt.readTape(tape);
	}

	public static byte[] writeTape(CompoundTag tag) throws IOException {
		return NativeNbt.writeTape(tag);
	}

	public static byte[] writeTape(CompoundTag tag, Tag placeholder, byte[] splice) throws IOException {
		return NativeNbt.writeTape(tag, placeholder, splice);
	}

	public static byte[] elementTape(Tag tag) throws IOException {
		return NativeNbt.elementTape(tag);
	}
}
