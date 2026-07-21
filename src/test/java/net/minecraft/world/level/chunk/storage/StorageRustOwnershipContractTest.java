package net.minecraft.world.level.chunk.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRustOwnershipContractTest {
	private static final Path ROOT = Path.of(System.getProperty("user.dir"));

	@Test
	void productionRegionStorageUsesRustNbtTapePath() throws IOException {
		String storage = source("src/main/java/net/minecraft/world/level/chunk/storage/RegionFileStorage.java");
		String region = source("src/main/java/net/minecraft/world/level/chunk/storage/RegionFile.java");

		assertTrue(storage.contains("return regionFile.readChunk(chunkPos);"));
		assertTrue(storage.contains("regionFile.writeChunk(chunkPos, compoundTag);"));
		assertTrue(region.contains("NativeRegionFileBridge.open(path, bl)"));
		assertTrue(region.contains("NativeRegionFileBridge.readNbtTape("));
		assertTrue(region.contains("NativeRegionFileBridge.writeNbtTape("));
		assertTrue(region.contains("NativeRegionFileBridge.deleteChunk("));
		assertTrue(region.contains("NativeRegionFileBridge.flush("));
		assertTrue(region.contains("NativeRegionFileBridge.close(handle)"));
		assertFalse(region.contains("mattmc.dev.rustRegionFiles"));
		assertFalse(region.contains("mattmc.dev.rustRegionFilesShadow"));
		assertFalse(region.contains("FileChannel"));
		assertFalse(region.contains("RegionBitmap"));
	}

	@Test
	void wholeBufferNbtUsesNativeImplementationWhileStreamsRemainCompatibilityOnly() throws IOException {
		String nbtIo = source("src/main/java/net/minecraft/nbt/NbtIo.java");

		assertTrue(nbtIo.contains("CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_GZIP, nbtAccounter);"));
		assertTrue(nbtIo.contains("CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_RAW, NbtAccounter.unlimitedHeap());"));
		assertTrue(nbtIo.contains("byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_GZIP);"));
		assertTrue(nbtIo.contains("byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_RAW);"));
		assertTrue(nbtIo.contains("public static void parse(DataInput dataInput, StreamTagVisitor streamTagVisitor"));
		assertTrue(nbtIo.contains("public static void write(CompoundTag compoundTag, DataOutput dataOutput)"));
		assertFalse(nbtIo.contains("mattmc.dev.rustRegionFiles"));
	}

	@Test
	void deterministicBenchmarkDoesNotKeepJavaObjectFallbackHelpers() throws IOException {
		Path productionBenchmarkAccess = ROOT.resolve("src/main/java/net/minecraft/nbt/NbtBenchmarkAccess.java");
		String benchmarkAccess = source("src/test/java/net/minecraft/nbt/NbtBenchmarkAccess.java");

		assertFalse(Files.exists(productionBenchmarkAccess));
		assertTrue(benchmarkAccess.contains("return NativeNbt.read(input, compression, NbtAccounter.unlimitedHeap());"));
		assertTrue(benchmarkAccess.contains("return NativeNbt.write(tag, compression);"));
		assertFalse(benchmarkAccess.contains("readObjectJava("));
		assertFalse(benchmarkAccess.contains("writeObjectJava("));
	}

	private static String source(String relativePath) throws IOException {
		return Files.readString(ROOT.resolve(relativePath));
	}
}
