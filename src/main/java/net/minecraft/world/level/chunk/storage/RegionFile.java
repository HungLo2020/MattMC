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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.util.profiling.jfr.JvmProfiler;
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
		RegionFileDiagnostics.opened(path);
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

	@Nullable
	public synchronized CompoundTag readChunk(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.TapeResult tape;
		long started = RegionFileDiagnostics.start();
		try {
			tape = NativeRegionFileBridge.readNbtTape(this.nativeRegionHandle(), chunkPos.x, chunkPos.z, 0L, 0L, 0, 0, 0L, 0L);
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-read-nbt-rust", "read-nbt", exception);
			throw exception;
		}
		NativeRegionFileBridge.TapeMetadata result = tape.result();
		RegionFileDiagnostics.recordRead(
			this.path,
			chunkPos,
			result.present(),
			result.present() ? result.compressionId() : -1,
			result.present() && result.external(),
			result.compressedLength(),
			RegionFileDiagnostics.elapsed(started)
		);
		if (!result.present()) {
			return null;
		}

		RegionFileVersion regionFileVersion = RegionFileVersion.fromId(result.compressionId());
		if (regionFileVersion == null || regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
			RegionFileDiagnostics.recordUnreadable(
				this.path,
				chunkPos,
				"Rust region NBT reader returned unsupported compression id " + result.compressionId()
			);
			throw new IOException("Rust region NBT reader returned unsupported compression id " + result.compressionId() + " for chunk " + chunkPos);
		}

		RegionFileDiagnostics.recordNbtRead(this.path, this.nativeRegionHandle(), chunkPos, result);
		JvmProfiler.INSTANCE.onRegionFileRead(this.info, chunkPos, regionFileVersion, Math.toIntExact(result.compressedLength()));
		return NativeNbtRegionAccess.readTape(tape.bytes());
	}

	@Nullable
	private DataInputStream getChunkDataInputStreamNative(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.PayloadResult payload;
		long started = RegionFileDiagnostics.start();
		try {
			payload = NativeRegionFileBridge.readPayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-read-rust", "read", exception);
			throw exception;
		}
		NativeRegionFileBridge.Result result = payload.result();
		RegionFileDiagnostics.recordRead(
			this.path,
			chunkPos,
			result.present(),
			result.present() ? result.compressionId() : -1,
			result.present() && result.external(),
			payload.bytes().length,
			RegionFileDiagnostics.elapsed(started)
		);
		if (!result.present()) {
			return null;
		} else {
			RegionFileVersion regionFileVersion = RegionFileVersion.fromId(result.compressionId());
			if (regionFileVersion == null || regionFileVersion == RegionFileVersion.VERSION_CUSTOM) {
				RegionFileDiagnostics.recordUnreadable(
					this.path,
					chunkPos,
					"Rust region reader returned unsupported compression id " + result.compressionId()
				);
				throw new IOException("Rust region reader returned unsupported compression id " + result.compressionId() + " for chunk " + chunkPos);
			}

			RegionFileDiagnostics.recordPayloadRead(this.path, this.nativeRegionHandle(), chunkPos, result, payload.bytes());
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
		long started = RegionFileDiagnostics.start();
		try {
			NativeRegionFileBridge.flush(this.nativeRegionHandle());
			RegionFileDiagnostics.recordFlush(this.path, RegionFileDiagnostics.elapsed(started));
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, null, "region-flush-rust", "flush", exception);
			throw exception;
		}
	}

	public synchronized void clear(ChunkPos chunkPos) throws IOException {
		NativeRegionFileBridge.WriteResult result;
		long started = RegionFileDiagnostics.start();
		try {
			result = NativeRegionFileBridge.deleteChunk(this.nativeRegionHandle(), chunkPos.x, chunkPos.z);
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-delete-rust", "delete", exception);
			throw exception;
		}
		RegionFileDiagnostics.recordDelete(this.path, chunkPos, RegionFileDiagnostics.elapsed(started), result.timestamp());
	}

	public synchronized void writeChunk(ChunkPos chunkPos, CompoundTag compoundTag) throws IOException {
		byte[] tape = NativeNbtRegionAccess.writeTape(compoundTag);
		NativeRegionFileBridge.WriteResult result;
		long started = RegionFileDiagnostics.start();
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
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-write-nbt-rust", "write-nbt", exception);
			throw exception;
		}
		RegionFileDiagnostics.recordWrite(
			this.path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.payloadLength(),
			RegionFileDiagnostics.elapsed(started)
		);
		JvmProfiler.INSTANCE.onRegionFileWrite(this.info, chunkPos, this.version, Math.toIntExact(result.payloadLength() + 1));
		RegionFileDiagnostics.recordNbtWrite(this.path, this.nativeRegionHandle(), chunkPos, result, "write-nbt");
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
		long started = RegionFileDiagnostics.start();
		try {
			NativeChunkSectionStorage.WriteResult result = NativeChunkSectionStorage.writeChunk(
				this.nativeRegionHandle(),
				chunkPos.x,
				chunkPos.z,
				this.version.getId(),
				data
			);
			RegionFileDiagnostics.recordWrite(
				this.path,
				chunkPos,
				result.compressionId(),
				result.external(),
				result.compressedLength(),
				RegionFileDiagnostics.elapsed(started)
			);
			JvmProfiler.INSTANCE.onRegionFileWrite(this.info, chunkPos, this.version, Math.toIntExact(result.compressedLength() + 1));
			if (RegionFileDiagnostics.chunkSectionWriteShadowEnabled()) {
				RegionFileDiagnostics.validateChunkSectionWriteShadow(this, chunkPos, data);
			}
			RegionFileDiagnostics.recordChunkSectionWrite(this.path, this.nativeRegionHandle(), chunkPos, result);
			return result;
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-write-chunk-sections-rust", "write-chunk-sections", exception);
			throw exception;
		}
	}

	public synchronized NativeEntityStorage.WriteResult writeEntityChunk(ChunkPos chunkPos, List<byte[]> entityTapes) throws IOException {
		long started = RegionFileDiagnostics.start();
		try {
			NativeEntityStorage.WriteResult result = NativeEntityStorage.writeChunk(
				this.nativeRegionHandle(),
				chunkPos.x,
				chunkPos.z,
				this.version.getId(),
				entityTapes
			);
			RegionFileDiagnostics.recordWrite(
				this.path,
				chunkPos,
				result.compressionId(),
				result.external(),
				result.compressedLength(),
				RegionFileDiagnostics.elapsed(started)
			);
			JvmProfiler.INSTANCE.onRegionFileWrite(this.info, chunkPos, this.version, Math.toIntExact(result.compressedLength() + 1));
			RegionFileDiagnostics.recordEntityWrite(this.path, chunkPos, result);
			return result;
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-write-entity-rust", "write-entity", exception);
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
		long started = RegionFileDiagnostics.start();
		try {
			result = NativeRegionFileBridge.writePayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z, compressionId, payload);
		} catch (IOException exception) {
			RegionFileDiagnostics.recordError(this.path, chunkPos, "region-write-rust", "write", exception);
			throw exception;
		}
		RegionFileDiagnostics.recordWrite(
			this.path,
			chunkPos,
			result.compressionId(),
			result.external(),
			result.payloadLength(),
			RegionFileDiagnostics.elapsed(started)
		);
		RegionFileDiagnostics.recordPayloadWrite(this.path, this.nativeRegionHandle(), chunkPos, result, payload);
	}

	public synchronized boolean hasChunk(ChunkPos chunkPos) {
		try {
			return NativeRegionFileBridge.readPayload(this.nativeRegionHandle(), chunkPos.x, chunkPos.z).result().present();
		} catch (IOException exception) {
			return false;
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
		RegionFileDiagnostics.recordClose(this.path);
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
}
