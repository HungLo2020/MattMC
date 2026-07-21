package net.minecraft.nbt;

import net.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.util.profiling.storage.StoragePerfDiagnostics;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Dev-only NBT diagnostics and shadow comparison.
 *
 * <p>{@link NbtIo} owns the production API surface. This helper owns validation
 * counters, status JSON, and Java/Rust shadow comparison so normal whole-buffer
 * NBT paths only make a single gated observer call at operation boundaries.
 */
final class NbtIoDiagnostics {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean RUST_SHADOW_COMPARE = Boolean.getBoolean("mattmc.dev.nbtShadowCompare");

	private NbtIoDiagnostics() {
	}

	static long start() {
		return StoragePerfDiagnostics.start();
	}

	static long elapsed(long started) {
		return StoragePerfDiagnostics.elapsed(started);
	}

	static void recordRustRead(byte[] input, int compression, CompoundTag rust, long elapsedNanos, @Nullable Path path) {
		StoragePerfDiagnostics.recordNbtRead("rust", compression, input.length, rust.sizeInBytes(), elapsedNanos);
		shadowCompareRead(input, compression, rust);
	}

	static void recordRustWrite(CompoundTag tag, int compression, byte[] rustBytes, long elapsedNanos, @Nullable Path path) {
		StoragePerfDiagnostics.recordNbtWrite("rust", compression, tag.sizeInBytes(), rustBytes.length, elapsedNanos);
		shadowCompareWrite(tag, compression, rustBytes);
	}

	static void recordJavaRead(CompoundTag tag, long elapsedNanos) {
		StoragePerfDiagnostics.recordNbtRead("java", -1, -1L, tag.sizeInBytes(), elapsedNanos);
	}

	static void recordJavaWrite(CompoundTag tag, long elapsedNanos) {
		StoragePerfDiagnostics.recordNbtWrite("java", -1, tag.sizeInBytes(), -1L, elapsedNanos);
	}

	private static void shadowCompareRead(byte[] input, int compression, CompoundTag rust) {
		if (!RUST_SHADOW_COMPARE) {
			return;
		}
		try {
			CompoundTag java = shadowRead(input, compression);
			if (!java.equals(rust)) {
				LOGGER.error("Rust NBT whole-buffer read shadow mismatch for compression {}", compression);
			}
		} catch (Throwable throwable) {
			LOGGER.error("Java NBT shadow read failed for compression {}", compression, throwable);
		}
	}

	private static void shadowCompareWrite(CompoundTag tag, int compression, byte[] rustBytes) {
		if (!RUST_SHADOW_COMPARE) {
			return;
		}
		try {
			ByteArrayOutputStream javaOutput = new ByteArrayOutputStream();
			if (compression == NativeNbt.FORMAT_GZIP) {
				try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(javaOutput))) {
					NbtIo.write(output, tag);
				}
			} else {
				try (DataOutputStream output = new DataOutputStream(javaOutput)) {
					NbtIo.write(output, tag);
				}
			}
			CompoundTag javaTag = shadowRead(javaOutput.toByteArray(), compression);
			CompoundTag rustTag = shadowRead(rustBytes, compression);
			if (!javaTag.equals(rustTag)) {
				LOGGER.error("Rust NBT whole-buffer write shadow mismatch for compression {}", compression);
			}
		} catch (Throwable throwable) {
			LOGGER.error("NBT shadow write failed for compression {}", compression, throwable);
		}
	}

	private static CompoundTag shadowRead(byte[] input, int compression) throws IOException {
		return compression == NativeNbt.FORMAT_GZIP
			? NbtIo.read(new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(input))), NbtAccounter.unlimitedHeap())
			: NbtIo.read(new DataInputStream(new ByteArrayInputStream(input)), NbtAccounter.unlimitedHeap());
	}
}
