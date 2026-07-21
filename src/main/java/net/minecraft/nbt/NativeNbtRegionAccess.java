package net.minecraft.nbt;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class NativeNbtRegionAccess {
	private NativeNbtRegionAccess() {
	}

	public static CompoundTag readTape(byte[] tape) throws IOException {
		return NativeNbt.readTape(tape);
	}

	public static byte[] writeTape(CompoundTag tag) throws IOException {
		return NativeNbt.writeTape(tag);
	}

	public static long rawFingerprint(CompoundTag tag) throws IOException {
		byte[] raw = NativeNbt.write(tag, NativeNbt.FORMAT_RAW);
		NativeNbt.Result result = NativeNbt.compressedFingerprint(
			raw,
			NativeNbt.FORMAT_RAW,
			new NativeNbt.CompressionLimits(0, 0),
			new NativeNbt.Limits(0, 0, 0, 0)
		);
		if (result.status() != NativeNbt.OK) {
			throw new IOException(
				"NBT fingerprint failed with native status "
					+ result.status()
					+ " error "
					+ result.errorKind()
					+ " at offset "
					+ result.offset()
			);
		}
		return result.fingerprint();
	}

	public static String objectFingerprint(CompoundTag tag) throws IOException {
		return sha256Hex(NativeNbt.write(tag, NativeNbt.FORMAT_RAW));
	}

	private static String sha256Hex(byte[] bytes) throws IOException {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder builder = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				builder.append(Character.forDigit(value >>> 4 & 0xF, 16));
				builder.append(Character.forDigit(value & 0xF, 16));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IOException("SHA-256 digest is unavailable", exception);
		}
	}
}
