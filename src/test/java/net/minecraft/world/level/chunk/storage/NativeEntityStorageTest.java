package net.minecraft.world.level.chunk.storage;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtBenchmarkAccess;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeEntityStorageTest {
	private static final int CURRENT_DATA_VERSION = 4556;
	private static final RegionStorageInfo STORAGE_INFO = new RegionStorageInfo("test", null, "entity-test");

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
	}

	@Test
	void generatedEntityEnvelopeMatchesJavaNbtPath(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(2, 3);
		CompoundTag root = entityRoot(pos, entity("minecraft:pig", passenger("minecraft:chicken")), entity("minecraft:item"));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, pos, root);

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);

			assertEnvelope(root, rust);
			assertEquals(ResourceLocation.withDefaultNamespace("pig"), rust.entities().get(0).parsedId().orElseThrow());
			assertEquals(1, rust.entities().get(0).passengerCount());
			assertEquals(1, rust.entities().get(0).passengerDepth());
			assertEquals(0, rust.entities().get(1).passengerCount());
			assertEquals(new UUID(0x12345678FFFFFFFFL, 0x0102030400000005L), rust.entities().get(0).uuid().orElseThrow());
			assertEquals(1.25, rust.entities().get(0).position().orElseThrow().x());
		}
	}

	@Test
	void emptyAndAbsentEntityChunksDecode(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos present = new ChunkPos(0, 0);
		CompoundTag root = entityRoot(present);
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, present, root);

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.DecodeResult rust = storage.decodeChunk(present.x, present.z);
			assertEnvelope(root, rust);
			assertTrue(rust.entities().isEmpty());

			NativeEntityStorage.DecodeResult absent = storage.decodeChunk(1, 0);
			assertEquals(NativeEntityStorage.OK, absent.result().status());
			assertFalse(absent.result().present());
			assertTrue(absent.entities().isEmpty());
		}
	}

	@Test
	void unknownAndMalformedEntityIdsAreReportedWithoutFailingChunk(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag malformed = entity("minecraft:pig");
		malformed.putInt("id", 7);
		CompoundTag unknown = entity("mattmc:not_registered");
		CompoundTag root = entityRoot(pos, malformed, unknown);
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, pos, root);

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeEntityStorage.OK, rust.result().status());
			assertTrue(rust.entities().get(0).id().isEmpty());
			assertTrue(rust.entities().get(0).idMalformed());
			assertEquals("mattmc:not_registered", rust.entities().get(1).id().orElseThrow());
			assertEquals(ResourceLocation.fromNamespaceAndPath("mattmc", "not_registered"), rust.entities().get(1).parsedId().orElseThrow());
		}
	}

	@Test
	void incorrectRootChunkPositionIsReturnedForJavaParityComparison(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos storedAt = new ChunkPos(4, 5);
		CompoundTag root = entityRoot(new ChunkPos(40, 50), entity("minecraft:pig"));
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, storedAt, root);

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.DecodeResult rust = storage.decodeChunk(storedAt.x, storedAt.z);

			assertEquals(NativeEntityStorage.OK, rust.result().status());
			assertEquals(40, rust.result().chunkX());
			assertEquals(50, rust.result().chunkZ());
			assertEquals(1, rust.result().entityCount());
		}
	}

	@Test
	void oldEntityChunksReportRequiresDfu(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag root = entityRoot(pos, entity("minecraft:pig"));
		root.putInt("DataVersion", CURRENT_DATA_VERSION - 1);
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, pos, root);

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);

			assertEquals(NativeEntityStorage.OK, rust.result().status());
			assertTrue(rust.result().requiresDfu());
			assertEquals(CURRENT_DATA_VERSION - 1, rust.result().dataVersion());
			assertTrue(rust.entities().isEmpty());
		}
	}

	@Test
	void malformedEnvelopeFailsSafely(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(0, 0);
		CompoundTag root = entityRoot(pos, entity("minecraft:pig"));
		root.putString("Position", "not a chunk position");
		writeJavaRegion(regionPath, tempDir, RegionFileVersion.VERSION_NONE, pos, root);

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			assertThrows(NativeEntityStorage.DecodeException.class, () -> storage.decodeChunk(pos.x, pos.z));
		}
	}

	@Test
	void copiedRealWorldEntityRegionMatchesJavaNbtEnvelopeWhenAvailable(@TempDir Path tempDir) throws IOException {
		Path entitiesDir = Path.of("run", "saves", "Origin", "entities");
		assumeTrue(Files.isDirectory(entitiesDir), "Origin entity directory is not available");
		RegionChunk sourceChunk = firstPresentOriginEntityChunk(entitiesDir);
		assumeTrue(sourceChunk != null, "Origin entity regions have no populated chunks");
		Path copy = tempDir.resolve("r.0.0.mca");
		Files.copy(sourceChunk.regionPath(), copy);
		ChunkPos pos = new ChunkPos(sourceChunk.regionX() * 32 + sourceChunk.localX(), sourceChunk.regionZ() * 32 + sourceChunk.localZ());
		CompoundTag javaRoot = readJavaRegion(copy, pos);
		assumeTrue(javaRoot.getInt("DataVersion").orElse(0) == CURRENT_DATA_VERSION, "Origin entity chunk remains Java DFU-owned");

		try (NativeEntityStorage storage = NativeEntityStorage.open(copy)) {
			NativeEntityStorage.DecodeResult rust = storage.decodeChunk(pos.x, pos.z);

			assertEnvelope(javaRoot, rust);
		}
	}

	@Test
	void nativeWriterPreservesOpaqueCustomEntityTapes(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(4, 6);
		CompoundTag modded = entity("alexsmobs:tiger", entity("minecraft:armor_stand"));
		CompoundTag custom = new CompoundTag();
		custom.putString("owner", "native-entity-storage-test");
		custom.putLongArray("opaque", new long[]{1L, 2L, 3L, Long.MIN_VALUE});
		modded.put("MattMCOpaqueCustomData", custom);
		CompoundTag display = entity("minecraft:text_display");
		display.putString("text", "{\"text\":\"Native entity writer\"}");
		List<CompoundTag> expected = List.of(modded, display);
		List<byte[]> tapes = expected.stream().map(NativeEntityStorageTest::writeEntityTapeUnchecked).toList();

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			NativeEntityStorage.WriteResult write = storage.writeChunk(pos.x, pos.z, RegionFileVersion.VERSION_DEFLATE.getId(), tapes);
			assertEquals(NativeEntityStorage.OK, write.status());
			assertEquals(expected.size(), write.entityCount());

			NativeEntityStorage.DecodeResult decoded = storage.decodeChunk(pos.x, pos.z);
			assertTrue(decoded.result().present());
			assertEquals(expected.size(), decoded.entities().size());
			assertEquals(ResourceLocation.fromNamespaceAndPath("alexsmobs", "tiger"), decoded.entities().get(0).parsedId().orElseThrow());
			assertEquals(1, decoded.entities().get(0).passengerCount());
			for (int i = 0; i < expected.size(); i++) {
				assertEquals(rootlessObjectFingerprint(expected.get(i)), rootlessObjectFingerprint(decoded.entities().get(i).readTapeAsTag()), "entity index " + i);
			}
		}
	}

	@Test
	void emptyEntityChunkDeletionClearsNativeWrittenChunk(@TempDir Path tempDir) throws IOException {
		Path regionPath = tempDir.resolve("r.0.0.mca");
		ChunkPos pos = new ChunkPos(8, 9);
		List<byte[]> tapes = List.of(NbtBenchmarkAccess.writeTapeObject(entity("minecraft:item")));

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			assertEquals(NativeEntityStorage.OK, storage.writeChunk(pos.x, pos.z, RegionFileVersion.VERSION_DEFLATE.getId(), tapes).status());
			assertTrue(storage.decodeChunk(pos.x, pos.z).result().present());
		}

		try (RegionFile regionFile = new RegionFile(STORAGE_INFO, regionPath, tempDir, RegionFileVersion.VERSION_DEFLATE, false)) {
			assertNotNull(regionFile.getChunkDataInputStream(pos));
			regionFile.clear(pos);
			assertNull(regionFile.getChunkDataInputStream(pos));
		}

		try (NativeEntityStorage storage = NativeEntityStorage.open(regionPath)) {
			assertFalse(storage.decodeChunk(pos.x, pos.z).result().present());
		}
	}

	private static void assertEnvelope(CompoundTag root, NativeEntityStorage.DecodeResult rust) throws IOException {
		assertEquals(NativeEntityStorage.OK, rust.result().status());
		assertTrue(rust.result().present());
		assertFalse(rust.result().requiresDfu());
		assertEquals(root.getInt("DataVersion").orElseThrow(), rust.result().dataVersion());
		assertArrayEquals(root.getIntArray("Position").orElseThrow(), new int[]{rust.result().chunkX(), rust.result().chunkZ()});
		List<CompoundTag> javaEntities = root.getList("Entities").stream().flatMap(ListTag::compoundStream).toList();
		assertEquals(javaEntities.size(), rust.result().entityCount());
		assertEquals(javaEntities.size(), rust.entities().size());
		for (int i = 0; i < javaEntities.size(); i++) {
			CompoundTag rustEntity = rust.entities().get(i).readTapeAsTag();
			assertEquals(0L, rust.entities().get(i).fingerprint());
			assertEquals(passengerCount(javaEntities.get(i)), rust.entities().get(i).passengerCount());
			assertEquals(passengerDepth(javaEntities.get(i)), rust.entities().get(i).passengerDepth());
			assertEquals(rootlessObjectFingerprint(javaEntities.get(i)), rootlessObjectFingerprint(rustEntity));
		}
	}

	private static long rootlessObjectFingerprint(CompoundTag tag) throws IOException {
		return NbtBenchmarkAccess.implementationFingerprint(NbtBenchmarkAccess.writeObject(tag, NbtBenchmarkAccess.FORMAT_RAW), NbtBenchmarkAccess.FORMAT_RAW);
	}

	private static int passengerCount(CompoundTag entity) {
		return entity.getList("Passengers").stream().flatMap(ListTag::compoundStream).mapToInt(passenger -> 1 + passengerCount(passenger)).sum();
	}

	private static int passengerDepth(CompoundTag entity) {
		return entity.getList("Passengers").stream().flatMap(ListTag::compoundStream).mapToInt(passenger -> 1 + passengerDepth(passenger)).max().orElse(0);
	}

	private static void writeJavaRegion(Path regionPath, Path dir, RegionFileVersion version, ChunkPos pos, CompoundTag tag) throws IOException {
		try (RegionFile regionFile = new RegionFile(STORAGE_INFO, regionPath, dir, version, false);
			DataOutputStream output = regionFile.getChunkDataOutputStream(pos)) {
			NbtIo.write(tag, output);
		}
	}

	private static CompoundTag readJavaRegion(Path regionPath, ChunkPos pos) throws IOException {
		try (RegionFile regionFile = new RegionFile(STORAGE_INFO, regionPath, regionPath.getParent(), false);
			InputStream input = regionFile.getChunkDataInputStream(pos)) {
			assertNotNull(input);
			return NbtIo.read(new java.io.DataInputStream(input));
		}
	}

	private static CompoundTag entityRoot(ChunkPos pos, CompoundTag... entities) {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", CURRENT_DATA_VERSION);
		root.putIntArray("Position", new int[]{pos.x, pos.z});
		ListTag list = new ListTag();
		for (CompoundTag entity : entities) {
			list.add(entity);
		}
		root.put("Entities", list);
		return root;
	}

	private static CompoundTag entity(String id, CompoundTag... passengers) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(new UUID(0x12345678FFFFFFFFL, 0x0102030400000005L)));
		ListTag pos = new ListTag();
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(1.25));
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(64.0));
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(-2.5));
		tag.put("Pos", pos);
		if (passengers.length > 0) {
			ListTag list = new ListTag();
			for (CompoundTag passenger : passengers) {
				list.add(passenger);
			}
			tag.put("Passengers", list);
		}
		return tag;
	}

	private static CompoundTag passenger(String id) {
		return entity(id);
	}

	private static byte[] writeEntityTapeUnchecked(CompoundTag tag) {
		try {
			return NbtBenchmarkAccess.writeTapeObject(tag);
		} catch (IOException exception) {
			throw new AssertionError(exception);
		}
	}

	private static RegionChunk firstPresentOriginEntityChunk(Path entitiesDir) throws IOException {
		try (Stream<Path> paths = Files.list(entitiesDir)) {
			for (Path path : paths.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted().toList()) {
				ChunkPos pos = firstPresentChunk(path);
				if (pos != null) {
					String[] parts = path.getFileName().toString().split("\\.");
					int regionX = Integer.parseInt(parts[1]);
					int regionZ = Integer.parseInt(parts[2]);
					return new RegionChunk(path, regionX, regionZ, pos.x, pos.z);
				}
			}
		}
		return null;
	}

	private static ChunkPos firstPresentChunk(Path regionPath) throws IOException {
		byte[] bytes = Files.readAllBytes(regionPath);
		for (int i = 0; i < 1024 && i * 4 + 3 < bytes.length; i++) {
			if (readInt(bytes, i * 4) != 0) {
				int localX = i & 31;
				int localZ = i >> 5;
				return new ChunkPos(localX, localZ);
			}
		}
		return null;
	}

	private static int readInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xFF) << 24
			| (bytes[offset + 1] & 0xFF) << 16
			| (bytes[offset + 2] & 0xFF) << 8
			| bytes[offset + 3] & 0xFF;
	}

	private record RegionChunk(Path regionPath, int regionX, int regionZ, int localX, int localZ) {
	}
}
