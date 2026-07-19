package net.minecraft.nbt;

public final class NativeNbtTestAccess {
	private NativeNbtTestAccess() {
	}

	public static long fingerprintRaw(byte[] bytes) {
		NativeNbt.Result result = NativeNbt.fingerprint(bytes);
		if (result.status() != NativeNbt.OK) {
			throw new AssertionError("Rust NBT fingerprint failed with status " + result.status() + " error " + result.errorKind());
		}
		return result.fingerprint();
	}
}
