package net.minecraft.world.level.chunk.storage;

import net.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.NbtBenchmarkAccess;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.storage.StoragePerfDiagnostics;
import net.minecraft.world.entity.ai.village.poi.NativePoiStorage;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Java facade for a Rust-owned Anvil region file.
 *
 * <p>Rust owns the persistent {@code .mca}/{@code .mcc} file lifecycle,
 * location and timestamp headers, sector allocation, payload compression,
 * binary NBT parsing/writing, and chunk delete/flush behavior. Java keeps this
 * facade so {@link RegionFileStorage}, tooling, and direct callers retain the
 * Minecraft API shape while normal chunk storage uses coarse NBT tape methods.
 *
 * <p>The stream methods below are compatibility surfaces for direct callers
 * such as stream visitors, region-editor tooling, and Distant Horizons wrappers.
 * They still delegate payload storage to Rust, but Java performs the requested
 * stream compression/decompression because those callers explicitly ask for
 * stream-shaped access.
 */
public class RegionFile implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int CHUNK_HEADER_SIZE = 5;
	final RegionStorageInfo info;
	private final Path path;
	final RegionFileVersion version;
	private long nativeRegionHandle;

	public RegionFile(RegionStorageInfo regionStorageInfo, Path path, Path path2, boolean bl) throws IOException {
		this(regionStorageInfo, path, path2, RegionFileVersion.getSelected(), bl);
	}

	public RegionFile(RegionStorageInfo regionStorageInfo, Path path, Path path2, RegionFileVersion regionFileVersion, boolean bl) throws IOException {
		this.info = regionStorageInfo;
		this.path = path;
		this.version = regionFileVersion;
		RegionFileRustValidation.regionOpened(path);
		StoragePerfDiagnostics.recordRegionOpen(path);
		if (!Files.isDirectory(path2, new LinkOption[0])) {
			throw new IllegalArgumentException("Expected directory, got " + path2.toAbsolutePath());
		} else {
			this.nativeRegionHandle = NativeRegionFileBridge.open(path, bl);
		}
	}

	private long nativeRegionHandle() throws IOException {
		if (this.nativeRegionHandle == 0L) {
			throw new IOException("Rust region file handle is not open for " + this.path);
		}

		return this.nativeRegionHandle;
	}

	public Path getPath() {
		return this.path;
	}

	@Nullable
	public synchronized DataInputStream getChunkDataInputStream(ChunkPos chunkPos) throws IOException {
		return this.getChunkDataInputStreamNative(chunkPos);
	}

	/**
	 * Deterministic storage replay hook. Not used by production chunk storage.
	 */
	public synchronized BenchmarkPayload readBenchmarkPayload(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.PayloadResult payload = NativeRegionFileBridge.readPayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
		NativeRegionFileBridge.Result result = payload.result();
		if (!result.present()) {
			return BenchmarkPayload.missing();
		}

		ByteBuffer encoded = ByteBuffer.allocate(CHUNK_HEADER_SIZE + payload.bytes().length);
		encoded.putInt(payload.bytes().length + 1);
		encoded.put((byte)result.compressionId());
		encoded.put(payload.bytes());
		return new BenchmarkPayload(true, result.compressionId(), result.external(), result.timestamp(), encoded.array());
	}

	@Nullable
	public synchronized CompoundTag readChunk(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.TapeResult tape;
		long started = StoragePerfDiagnostics.start();
		try {
			tape = NativeRegionFileBridge.readNbtTape(this.nativeRegionHandle(), chunkPos.x, chunkPos.z, 0L, 0L, 0, 0, 0L, 0L);
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-read-nbt-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "read-nbt", exception.getMessage());
			throw exception;
		}
		NativeRegionFileBridge.TapeMetadata result = tape.result();
		StoragePerfDiagnostics.recordRegionRead(
			this.path,
			chunkPos.x,
			chunkPos.z,
			result.present(),
			result.present() ? result.compressionId() : -1,
			result.present() && result.external(),
			result.compressedLength(),
			StoragePerfDiagnostics.elapsed(started)
		);
		if (!result.present()) {
			return null;
		}

		RegionFileVersion regionFileVersion = RegionFileVersion.fromId(result.compressionId());
		if (regionFileVersion == null || regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
			RegionFileRustValidation.recordUnreadableChunk(
				this.path,
				chunkPos,
				"Rust region NBT reader returned unsupported compression id " + result.compressionId()
			);
			throw new IOException("Rust region NBT reader returned unsupported compression id " + result.compressionId() + " for chunk " + chunkPos);
		}

		if (RegionFileRustValidation.enabled()) {
			RegionFileRustValidation.recordRead(
				this.path,
				chunkPos,
				result.compressionId(),
				result.external(),
				result.timestamp(),
				Math.toIntExact(result.compressedLength()),
				"",
				result.fingerprint()
			);
		}
		JvmProfiler.INSTANCE.onRegionFileRead(this.info, chunkPos, regionFileVersion, Math.toIntExact(result.compressedLength()));
		return NativeNbtRegionAccess.readTape(tape.bytes());
	}

	public synchronized void writeBenchmarkPayload(ChunkPos chunkPos, byte[] encodedPayload) throws IOException {
		this.write(chunkPos, ByteBuffer.wrap(encodedPayload));
	}

	@Nullable
	private DataInputStream getChunkDataInputStreamNative(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.PayloadResult payload;
		long started = StoragePerfDiagnostics.start();
		try {
			payload = NativeRegionFileBridge.readPayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-read-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "read", exception.getMessage());
			throw exception;
		}
		NativeRegionFileBridge.Result result = payload.result();
		StoragePerfDiagnostics.recordRegionRead(
			this.path,
			chunkPos.x,
			chunkPos.z,
			result.present(),
			result.present() ? result.compressionId() : -1,
			result.present() && result.external(),
			payload.bytes().length,
			StoragePerfDiagnostics.elapsed(started)
		);
		if (!result.present()) {
			return null;
		} else {
			RegionFileVersion regionFileVersion = RegionFileVersion.fromId(result.compressionId());
			if (regionFileVersion == null || regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
				RegionFileRustValidation.recordUnreadableChunk(
					this.path,
					chunkPos,
					"Rust region reader returned unsupported compression id " + result.compressionId()
				);
				throw new IOException("Rust region reader returned unsupported compression id " + result.compressionId() + " for chunk " + chunkPos);
			}

			if (RegionFileRustValidation.enabled()) {
				long fingerprint = this.readNativeNbtFingerprint(chunkPos, "read");
				RegionFileRustValidation.recordRead(
					this.path,
					chunkPos,
					result.compressionId(),
					result.external(),
					result.timestamp(),
					payload.bytes().length,
					sha256(payload.bytes()),
					fingerprint
				);
			}
			JvmProfiler.INSTANCE.onRegionFileRead(this.info, chunkPos, regionFileVersion, payload.bytes().length);
			return this.createChunkInputStream(chunkPos, result.compressionId(), new ByteArrayInputStream(payload.bytes()));
		}
	}

	@Nullable
	private DataInputStream createChunkInputStream(ChunkPos chunkPos, int compressionId, InputStream inputStream) throws IOException {
		RegionFileVersion regionFileVersion = RegionFileVersion.fromId(compressionId);
		if (regionFileVersion == null || regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
			LOGGER.error("Chunk {} has invalid chunk stream version {}", chunkPos, compressionId);
			return null;
		} else {
			return new DataInputStream(regionFileVersion.wrap(inputStream));
		}
	}

	public boolean doesChunkExist(ChunkPos chunkPos) {
		return this.hasChunk(chunkPos);
	}

	public DataOutputStream getChunkDataOutputStream(ChunkPos chunkPos) throws IOException {
		return new DataOutputStream(this.version.wrap(new RegionFile.ChunkBuffer(chunkPos)));
	}

	public synchronized void flush() throws IOException {
		long started = StoragePerfDiagnostics.start();
		try {
			NativeRegionFileBridge.flush(this.nativeRegionHandle());
			StoragePerfDiagnostics.recordRegionFlush(this.path, StoragePerfDiagnostics.elapsed(started));
			RegionFileRustValidation.recordFlush(this.path);
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-flush-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, null, "flush", exception.getMessage());
			throw exception;
		}
	}

	public synchronized void clear(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.WriteResult result;
		long started = StoragePerfDiagnostics.start();
		try {
			result = NativeRegionFileBridge.deleteChunk(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-delete-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "delete", exception.getMessage());
			throw exception;
		}
		StoragePerfDiagnostics.recordRegionDelete(this.path, chunkPos.x, chunkPos.z, StoragePerfDiagnostics.elapsed(started));
		RegionFileRustValidation.recordDelete(this.path, chunkPos, result.timestamp());
	}

	public synchronized void writeChunk(ChunkPos chunkPos, CompoundTag compoundTag) throws IOException {
		byte[] tape = NativeNbtRegionAccess.writeTape(compoundTag);
		NativeRegionFileBridge.WriteResult result;
		long started = StoragePerfDiagnostics.start();
		try {
			result = NativeRegionFileBridge.writeNbtTape(
				this.nativeRegionHandle(),
				chunkPos.x,
				chunkPos.z,
				this.version.getId(),
				tape,
				0L,
				0L,
				0,
				0,
				0L,
				0L
			);
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-write-nbt-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "write-nbt", exception.getMessage());
			throw exception;
		}
		StoragePerfDiagnostics.recordRegionWrite(
			this.path,
			chunkPos.x,
			chunkPos.z,
			result.compressionId(),
			result.external(),
			result.payloadLength(),
			StoragePerfDiagnostics.elapsed(started)
		);
		JvmProfiler.INSTANCE.onRegionFileWrite(this.info, chunkPos, this.version, Math.toIntExact(result.payloadLength() + 1));
		if (RegionFileRustValidation.enabled()) {
			long fingerprint = this.readNativeNbtFingerprint(chunkPos, "write-nbt");
			RegionFileRustValidation.recordWrite(
				this.path,
				chunkPos,
				result.compressionId(),
				result.external(),
				result.timestamp(),
				result.payloadLength(),
				"",
				fingerprint
			);
		}
	}

	public synchronized NativePoiStorage.WriteResult writePoiChunk(ChunkPos chunkPos, byte[] tape) throws IOException {
		return NativePoiStorage.writeChunk(this.nativeRegionHandle(), chunkPos.x, chunkPos.z, this.version.getId(), tape);
	}

	public synchronized NativePoiStorage.DecodeResult readPoiChunk(ChunkPos chunkPos) throws IOException {
		return NativePoiStorage.decodeChunk(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
	}

	public synchronized NativeEntityStorage.DecodeResult readEntityChunk(ChunkPos chunkPos) throws IOException {
		return NativeEntityStorage.decodeChunk(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
	}

	public synchronized NativeChunkSectionStorage.DecodeResult readChunkSections(ChunkPos chunkPos) throws IOException {
		return NativeChunkSectionStorage.decodeChunk(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
	}

	public synchronized NativeChunkSectionStorage.WriteResult writeChunkSections(ChunkPos chunkPos, SerializableChunkData data) throws IOException {
		long started = StoragePerfDiagnostics.start();
		try {
			NativeChunkSectionStorage.WriteResult result = NativeChunkSectionStorage.writeChunk(
				this.nativeRegionHandle(),
				chunkPos.x,
				chunkPos.z,
				this.version.getId(),
				data
			);
			StoragePerfDiagnostics.recordRegionWrite(
				this.path,
				chunkPos.x,
				chunkPos.z,
				result.compressionId(),
				result.external(),
				result.compressedLength(),
				StoragePerfDiagnostics.elapsed(started)
			);
			JvmProfiler.INSTANCE.onRegionFileWrite(this.info, chunkPos, this.version, Math.toIntExact(result.compressedLength() + 1));
			if (ChunkSectionReadDiagnostics.writeShadowValidationEnabled()) {
				this.validateChunkSectionWriteShadow(chunkPos, data);
			}
			if (RegionFileRustValidation.enabled()) {
				RegionFileRustValidation.recordWrite(
					this.path,
					chunkPos,
					result.compressionId(),
					result.external(),
					result.timestamp(),
					result.compressedLength(),
					"",
					this.readNativeNbtFingerprint(chunkPos, "write-chunk-sections")
				);
			}
			return result;
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-write-chunk-sections-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "write-chunk-sections", exception.getMessage());
			throw exception;
		}
	}

	private void validateChunkSectionWriteShadow(ChunkPos chunkPos, SerializableChunkData data) throws IOException {
		long started = ChunkSectionReadDiagnostics.now();
		CompoundTag javaRoot = data.writeWithRustSectionResidual();
		CompoundTag rustRoot = this.readChunk(chunkPos);
		long compareStarted = ChunkSectionReadDiagnostics.now();
		String javaFingerprint = NbtBenchmarkAccess.objectFingerprint(javaRoot);
		String rustFingerprint = rustRoot == null ? "missing" : NbtBenchmarkAccess.objectFingerprint(rustRoot);
		if (rustRoot == null || !javaRoot.equals(rustRoot)) {
			ChunkSectionReadDiagnostics.writeShadowMismatch(
				chunkPos,
				javaFingerprint,
				rustFingerprint,
				rustRoot == null ? "missing rust chunk" : firstDifference("$", javaRoot, rustRoot),
				ChunkSectionReadDiagnostics.elapsed(started),
				ChunkSectionReadDiagnostics.elapsed(compareStarted)
			);
		} else {
			ChunkSectionReadDiagnostics.writeShadowMatch(
				chunkPos,
				javaFingerprint,
				ChunkSectionReadDiagnostics.elapsed(started),
				ChunkSectionReadDiagnostics.elapsed(compareStarted)
			);
		}
	}

	private static String firstDifference(String path, Tag expected, Tag actual) {
		if (expected.getId() != actual.getId()) {
			return path + " type expected=" + expected.getType().getName() + " actual=" + actual.getType().getName();
		}
		if (expected instanceof CompoundTag expectedCompound && actual instanceof CompoundTag actualCompound) {
			for (String key : expectedCompound.keySet()) {
				Tag expectedChild = expectedCompound.get(key);
				Tag actualChild = actualCompound.get(key);
				if (actualChild == null) {
					return path + "." + key + " missing in actual";
				}
				if (!expectedChild.equals(actualChild)) {
					return firstDifference(path + "." + key, expectedChild, actualChild);
				}
			}
			for (String key : actualCompound.keySet()) {
				if (!expectedCompound.contains(key)) {
					return path + "." + key + " extra in actual";
				}
			}
			return path + " compound differs";
		}
		if (expected instanceof CollectionTag expectedCollection && actual instanceof CollectionTag actualCollection) {
			if (expectedCollection.size() != actualCollection.size()) {
				return path + " size expected=" + expectedCollection.size() + " actual=" + actualCollection.size();
			}
			for (int i = 0; i < expectedCollection.size(); i++) {
				Tag expectedChild = expectedCollection.get(i);
				Tag actualChild = actualCollection.get(i);
				if (!expectedChild.equals(actualChild)) {
					return firstDifference(path + "[" + i + "]", expectedChild, actualChild);
				}
			}
			return path + " collection differs";
		}
		return truncateDifference(path + " expected=" + expected + " actual=" + actual);
	}

	private static String truncateDifference(String difference) {
		int limit = 512;
		return difference.length() <= limit ? difference : difference.substring(0, limit) + "...";
	}

	public synchronized NativeEntityStorage.WriteResult writeEntityChunk(ChunkPos chunkPos, List<byte[]> entityTapes) throws IOException {
		long started = StoragePerfDiagnostics.start();
		try {
			NativeEntityStorage.WriteResult result = NativeEntityStorage.writeChunk(
				this.nativeRegionHandle(),
				chunkPos.x,
				chunkPos.z,
				this.version.getId(),
				entityTapes
			);
			StoragePerfDiagnostics.recordRegionWrite(
				this.path,
				chunkPos.x,
				chunkPos.z,
				result.compressionId(),
				result.external(),
				result.compressedLength(),
				StoragePerfDiagnostics.elapsed(started)
			);
			JvmProfiler.INSTANCE.onRegionFileWrite(this.info, chunkPos, this.version, Math.toIntExact(result.compressedLength() + 1));
			if (RegionFileRustValidation.enabled()) {
				RegionFileRustValidation.recordWrite(
					this.path,
					chunkPos,
					result.compressionId(),
					result.external(),
					result.timestamp(),
					result.compressedLength(),
					"",
					result.fingerprint()
				);
			}
			return result;
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-write-entity-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "write-entity", exception.getMessage());
			throw exception;
		}
	}

	protected synchronized void write(ChunkPos chunkPos, ByteBuffer byteBuffer) throws IOException {
		ByteBuffer duplicate = byteBuffer.duplicate();
		int size = duplicate.remaining();
		if (size < CHUNK_HEADER_SIZE) {
			throw new IOException("Chunk " + chunkPos + " encoded payload is too small: " + size);
		}

		int declaredLength = duplicate.getInt(duplicate.position());
		int payloadLength = declaredLength - 1;
		if (payloadLength < 0 || payloadLength != size - CHUNK_HEADER_SIZE) {
			throw new IOException(
				"Chunk " + chunkPos + " encoded payload length " + declaredLength + " does not match buffer size " + size
			);
		}

		int compressionId = Byte.toUnsignedInt(duplicate.get(duplicate.position() + 4));
		byte[] payload = new byte[payloadLength];
		duplicate.position(duplicate.position() + CHUNK_HEADER_SIZE);
		duplicate.get(payload);
		NativeRegionFileBridge.WriteResult result;
		long started = StoragePerfDiagnostics.start();
		try {
			result = NativeRegionFileBridge.writePayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z, compressionId, payload);
		} catch (IOException exception) {
			StoragePerfDiagnostics.recordError("region-write-rust", exception);
			RegionFileRustValidation.recordRustError(this.path, chunkPos, "write", exception.getMessage());
			throw exception;
		}
		StoragePerfDiagnostics.recordRegionWrite(
			this.path,
			chunkPos.x,
			chunkPos.z,
			result.compressionId(),
			result.external(),
			result.payloadLength(),
			StoragePerfDiagnostics.elapsed(started)
		);
		if (RegionFileRustValidation.enabled()) {
			long fingerprint = this.readNativeNbtFingerprint(chunkPos, "write");
			RegionFileRustValidation.recordWrite(
				this.path,
				chunkPos,
				result.compressionId(),
				result.external(),
				result.timestamp(),
				result.payloadLength(),
				sha256(payload),
				fingerprint
			);
		}
	}

	public synchronized boolean hasChunk(ChunkPos chunkPos) {
		try {
			return NativeRegionFileBridge.readPayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z).result().present();
		} catch (IOException exception) {
			return false;
		}
	}

	private static String sha256(byte[] bytes) throws IOException {
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

	private long readNativeNbtFingerprint(ChunkPos chunkPos, String operation) throws IOException {
		try {
			NativeRegionFileBridge.NbtResult result = NativeRegionFileBridge.readNbtFingerprint(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
			if (!result.present()) {
				RegionFileRustValidation.recordUnreadableChunk(this.path, chunkPos, "NBT fingerprint missing after " + operation);
				throw new IOException("Rust region NBT fingerprint missing for " + chunkPos + " after " + operation);
			}

			return result.fingerprint();
		} catch (IOException exception) {
			RegionFileRustValidation.recordMalformedNbt(this.path, chunkPos, exception.getMessage());
			throw exception;
		}
	}

	public synchronized void close() throws IOException {
		IOException failure = null;
		long handle = this.nativeRegionHandle;
		if (handle != 0L) {
			this.nativeRegionHandle = 0L;
			try {
				NativeRegionFileBridge.close(handle);
			} catch (IOException exception) {
				failure = exception;
			}
		}
		StoragePerfDiagnostics.recordRegionClose(this.path);
		if (failure != null) {
			throw failure;
		}
	}

	class ChunkBuffer extends ByteArrayOutputStream {
		private final ChunkPos pos;

		public ChunkBuffer(final ChunkPos chunkPos) {
			super(8096);
			super.write(0);
			super.write(0);
			super.write(0);
			super.write(0);
			super.write(RegionFile.this.version.getId());
			this.pos = chunkPos;
		}

		public void close() throws IOException {
			ByteBuffer byteBuffer = ByteBuffer.wrap(this.buf, 0, this.count);
			int i = this.count - CHUNK_HEADER_SIZE + 1;
			JvmProfiler.INSTANCE.onRegionFileWrite(RegionFile.this.info, this.pos, RegionFile.this.version, i);
			byteBuffer.putInt(0, i);
			RegionFile.this.write(this.pos, byteBuffer);
		}
	}

	public record BenchmarkPayload(boolean present, int compressionId, boolean external, long timestamp, byte[] encodedPayload) {
		static BenchmarkPayload missing() {
			return new BenchmarkPayload(false, -1, false, 0L, new byte[0]);
		}
	}
}
