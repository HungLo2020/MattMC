package net.minecraft.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.DeflaterOutputStream;

public final class NbtBenchmarkAccess {
	public static final int FORMAT_RAW = NativeNbt.FORMAT_RAW;
	public static final int FORMAT_GZIP = NativeNbt.FORMAT_GZIP;
	public static final int FORMAT_ZLIB = NativeNbt.FORMAT_ZLIB;

	private NbtBenchmarkAccess() {
	}

	public static boolean supportsNativeTape() {
		return true;
	}

	public static long implementationFingerprint(byte[] input, int compression) throws IOException {
		NativeNbt.Result result = NativeNbt.compressedFingerprint(input, compression, new NativeNbt.CompressionLimits(0, 0), new NativeNbt.Limits(0, 0, 0, 0));
		checkOk("Benchmark NBT fingerprint", result);
		return result.fingerprint();
	}

	public static byte[] implementationRecompress(byte[] input, int inputCompression, int outputCompression) throws IOException {
		NativeNbt.ReencodeResult result = NativeNbt.recompress(input, inputCompression, outputCompression);
		checkOk("Benchmark NBT recompress", result.result());
		return result.bytes();
	}

	public static byte[] decodeToTape(byte[] input, int compression) throws IOException {
		NativeNbt.ReencodeResult result = NativeNbt.decodeToTape(input, compression, new NativeNbt.CompressionLimits(0, 0), new NativeNbt.Limits(0, 0, 0, 0));
		checkOk("Benchmark NBT decode tape", result.result());
		return result.bytes();
	}

	public static byte[] encodeFromTape(byte[] tape, int compression) throws IOException {
		NativeNbt.ReencodeResult result = NativeNbt.encodeFromTape(tape, compression, new NativeNbt.CompressionLimits(0, 0), new NativeNbt.Limits(0, 0, 0, 0));
		checkOk("Benchmark NBT encode tape", result.result());
		return result.bytes();
	}

	public static CompoundTag readTapeObject(byte[] tape) throws IOException {
		return NativeNbt.readTape(tape);
	}

	public static byte[] writeTapeObject(CompoundTag tag) throws IOException {
		return NativeNbt.writeTape(tag);
	}

	public static CompoundTag readObject(byte[] input, int compression) throws IOException {
		return NativeNbt.read(input, compression, NbtAccounter.unlimitedHeap());
	}

	public static byte[] writeObject(CompoundTag tag, int compression) throws IOException {
		return NativeNbt.write(tag, compression);
	}

	public static String objectFingerprint(CompoundTag tag) throws IOException {
		return sha256Hex(writeObject(tag, FORMAT_RAW));
	}

	public static byte[] decodeToRawBytes(byte[] input, int compression) throws IOException {
		return switch (compression) {
			case FORMAT_RAW -> input.clone();
			case FORMAT_GZIP -> {
				try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(input))) {
					yield stream.readAllBytes();
				}
			}
			case FORMAT_ZLIB -> {
				try (InflaterInputStream stream = new InflaterInputStream(new ByteArrayInputStream(input))) {
					yield stream.readAllBytes();
				}
			}
			default -> throw new IOException("Unsupported NBT compression for benchmark: " + compression);
		};
	}

	public static byte[] encodeRawBytes(byte[] input, int compression) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		switch (compression) {
			case FORMAT_RAW -> output.write(input);
			case FORMAT_GZIP -> {
				try (GZIPOutputStream stream = new GZIPOutputStream(output)) {
					stream.write(input);
				}
			}
			case FORMAT_ZLIB -> {
				try (DeflaterOutputStream stream = new DeflaterOutputStream(output)) {
					stream.write(input);
				}
			}
			default -> throw new IOException("Unsupported NBT compression for benchmark: " + compression);
		}
		return output.toByteArray();
	}

	public static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static long hash64(byte[] bytes) {
		byte[] digest;
		try {
			digest = MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
		return ByteBuffer.wrap(digest).getLong();
	}

	private static void checkOk(String action, NativeNbt.Result result) throws IOException {
		if (result.status() != NativeNbt.OK) {
			throw new IOException(action + " failed with native status " + result.status() + " error " + result.errorKind() + " at offset " + result.offset());
		}
	}
}
