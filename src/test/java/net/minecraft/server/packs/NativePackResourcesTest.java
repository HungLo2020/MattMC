package net.minecraft.server.packs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.jetbrains.annotations.Nullable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePackResourcesTest {
	private static final PackLocationInfo LOCATION = new PackLocationInfo(
		"native-pack-test",
		Component.literal("Native Pack Test"),
		PackSource.DEFAULT,
		java.util.Optional.empty()
	);

	@Test
	void directoryPackMatchesJavaForNamespacesListingsAndBytes(@TempDir Path tempDir) throws IOException {
		Path root = createPackTree(tempDir.resolve("dir-pack"));

		try (
			PackResources javaPack = new PathPackResources(LOCATION, root);
			NativePackResources rustPack = NativePackResources.openDirectory(root)
		) {
			assertNamespaceParity(javaPack, rustPack, PackType.CLIENT_RESOURCES);
			assertNamespaceParity(javaPack, rustPack, PackType.SERVER_DATA);
			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "minecraft", "textures");
			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "nested");
			assertListParity(javaPack, rustPack, PackType.SERVER_DATA, "minecraft", "tags");
			assertRootBytes(javaPack, rustPack, "pack.mcmeta");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "minecraft", "textures/block/stone.txt");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "binary/blob.bin");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "empty.dat");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "large.bin");
			assertFalse(rustPack.exists(PackType.CLIENT_RESOURCES, "missing", "textures/block/stone.txt"));

			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "case");
			assertThrows(IOException.class, () -> rustPack.exists(PackType.CLIENT_RESOURCES, "example", "bad/../escape.txt"));

			NativePackBridge.Counters counters = rustPack.counters();
			assertTrue(counters.listOps() >= 4);
			assertTrue(counters.existsOps() >= 5);
			assertTrue(counters.readOps() >= 5);
			assertTrue(counters.bytesReturned() > 100_000);
			assertEquals(rustPack.openStats().entriesIndexed(), counters.entriesIndexed());
		}
	}

	@Test
	void zipPreservesResourcesThatDifferOnlyByCase(@TempDir Path tempDir) throws IOException {
		Path zipPath = tempDir.resolve("case.zip");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
			putZip(zip, "assets/example/case/lower.txt", new byte[]{1});
			putZip(zip, "assets/example/case/Lower.txt", new byte[]{2});
		}

		try (
			PackResources javaPack = new FilePackResources.FileResourcesSupplier(zipPath).openPrimary(LOCATION);
			NativePackResources rustPack = NativePackResources.openZip(zipPath, "")
		) {
			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "case");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "case/lower.txt");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "case/Lower.txt");
		}
	}

	@Test
	void zipPackMatchesJavaForNamespacesListingsPrefixesAndRepeatedReads(@TempDir Path tempDir) throws IOException {
		Path root = createPackTree(tempDir.resolve("zip-source"));
		Path zipPath = tempDir.resolve("pack.zip");
		writeZipFromDirectory(root, zipPath, "");

		try (
			PackResources javaPack = new FilePackResources.FileResourcesSupplier(zipPath).openPrimary(LOCATION);
			NativePackResources rustPack = NativePackResources.openZip(zipPath, "")
		) {
			assertNamespaceParity(javaPack, rustPack, PackType.CLIENT_RESOURCES);
			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "minecraft", "textures");
			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "nested");
			assertRootBytes(javaPack, rustPack, "pack.mcmeta");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "minecraft", "textures/block/stone.txt");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "example", "binary/blob.bin");
			assertArrayEquals(
				rustPack.readResource(PackType.CLIENT_RESOURCES, "example", "binary/blob.bin"),
				rustPack.readResource(PackType.CLIENT_RESOURCES, "example", "binary/blob.bin")
			);
		}
	}

	@Test
	void zipPrefixMatchesFilePackOverlaySemantics(@TempDir Path tempDir) throws IOException {
		Path root = createPackTree(tempDir.resolve("zip-source"));
		Path zipPath = tempDir.resolve("prefixed.zip");
		writeZipFromDirectory(root, zipPath, "overlay");

		try (
			PackResources javaPack = new FilePackResources(LOCATION, zipPath.toFile(), "overlay");
			NativePackResources rustPack = NativePackResources.openZip(zipPath, "overlay")
		) {
			assertNamespaceParity(javaPack, rustPack, PackType.CLIENT_RESOURCES);
			assertListParity(javaPack, rustPack, PackType.CLIENT_RESOURCES, "minecraft", "textures");
			assertResourceBytes(javaPack, rustPack, PackType.CLIENT_RESOURCES, "minecraft", "textures/block/stone.txt");
		}
	}

	@Test
	void malformedZipTraversalEntryIsRejectedByRustIndex(@TempDir Path tempDir) throws IOException {
		Path zipPath = tempDir.resolve("bad.zip");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
			putZip(zip, "assets/minecraft/safe.txt", new byte[]{1});
			putZip(zip, "assets/minecraft/bad/../escape.txt", new byte[]{2});
		}

		try (NativePackResources rustPack = NativePackResources.openZip(zipPath, "")) {
			assertEquals(List.of("safe.txt"), rustPack.listResources(PackType.CLIENT_RESOURCES, "minecraft", "safe"));
			assertThrows(IOException.class, () -> rustPack.exists(PackType.CLIENT_RESOURCES, "minecraft", "bad/../escape.txt"));
		}
	}

	@Test
	void closeIsIdempotentAtFacadeAndStaleNativeHandleFails(@TempDir Path tempDir) throws IOException {
		Path root = createPackTree(tempDir.resolve("dir-pack"));
		NativePackResources rustPack = NativePackResources.openDirectory(root);
		rustPack.close();
		rustPack.close();

		NativePackResources second = NativePackResources.openDirectory(root);
		second.closeRawForTest();
		assertThrows(IOException.class, () -> second.closeRawForTest());
	}

	@Test
	void productionDirectoryPackUsesNativeReadsAndRefreshesLiveDirectoryChanges(@TempDir Path tempDir) throws Exception {
		Path root = createPackTree(tempDir.resolve("dir-pack"));
		try (PackResources pack = new PathPackResources(LOCATION, root)) {
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath("example", "nested/path/value.json");
			assertResourceBytes(pack, PackType.CLIENT_RESOURCES, location, "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

			write(root.resolve("assets/example/nested/path/added.json"), "{\"added\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			ResourceLocation added = ResourceLocation.fromNamespaceAndPath("example", "nested/path/added.json");
			assertResourceBytes(pack, PackType.CLIENT_RESOURCES, added, "{\"added\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

			List<String> listed = javaList(pack, PackType.CLIENT_RESOURCES, "example", "nested");
			assertTrue(listed.contains("nested/path/added.json"));
		}
	}

	@Test
	void productionSupplierAfterCloseFailsWithoutClosingTheWholeTestProcess(@TempDir Path tempDir) throws Exception {
		Path root = createPackTree(tempDir.resolve("dir-pack"));
		IoSupplier<InputStream> supplier;
		try (PackResources pack = new PathPackResources(LOCATION, root)) {
			supplier = pack.getResource(PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.txt"));
			assertNotNull(supplier);
		}
		assertThrows(IOException.class, supplier::get);
	}

	@Test
	void productionZipPackAllowsConcurrentResourceReads(@TempDir Path tempDir) throws Exception {
		Path root = createPackTree(tempDir.resolve("zip-source"));
		Path zipPath = tempDir.resolve("pack.zip");
		writeZipFromDirectory(root, zipPath, "");
		try (PackResources pack = new FilePackResources.FileResourcesSupplier(zipPath).openPrimary(LOCATION)) {
			ExecutorService executor = Executors.newFixedThreadPool(4);
			try {
				List<Callable<byte[]>> tasks = List.of(
					() -> readResource(pack, PackType.CLIENT_RESOURCES, "minecraft", "textures/block/stone.txt"),
					() -> readResource(pack, PackType.CLIENT_RESOURCES, "example", "binary/blob.bin"),
					() -> readResource(pack, PackType.CLIENT_RESOURCES, "example", "large.bin"),
					() -> readResource(pack, PackType.CLIENT_RESOURCES, "example", "nested/path/value.json")
				);
				List<Future<byte[]>> futures = executor.invokeAll(tasks);
				assertArrayEquals("stone".getBytes(java.nio.charset.StandardCharsets.UTF_8), futures.get(0).get());
				assertArrayEquals(new byte[]{0, 1, 2, -1, 42}, futures.get(1).get());
				assertArrayEquals(largeBytes(), futures.get(2).get());
				assertArrayEquals("{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8), futures.get(3).get());
			} finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void productionDirectoryPackAllowsConcurrentReadsAndListings(@TempDir Path tempDir) throws Exception {
		Path root = createPackTree(tempDir.resolve("dir-pack"));
		try (PackResources pack = new PathPackResources(LOCATION, root)) {
			ExecutorService executor = Executors.newFixedThreadPool(4);
			try {
				List<Callable<Integer>> tasks = List.of(
					() -> javaList(pack, PackType.CLIENT_RESOURCES, "example", "nested").size(),
					() -> readResource(pack, PackType.CLIENT_RESOURCES, "example", "large.bin").length,
					() -> javaList(pack, PackType.CLIENT_RESOURCES, "minecraft", "textures").size(),
					() -> readResource(pack, PackType.CLIENT_RESOURCES, "minecraft", "textures/block/stone.txt").length
				);
				List<Future<Integer>> futures = executor.invokeAll(tasks);
				assertTrue(futures.get(0).get() > 0);
				assertEquals(largeBytes().length, futures.get(1).get());
				assertTrue(futures.get(2).get() > 0);
				assertEquals("stone".length(), futures.get(3).get());
			} finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void productionZipReplacementUsesFreshIndexAfterClose(@TempDir Path tempDir) throws Exception {
		Path root = createPackTree(tempDir.resolve("zip-source"));
		Path zipPath = tempDir.resolve("pack.zip");
		writeZipFromDirectory(root, zipPath, "");
		try (PackResources pack = new FilePackResources.FileResourcesSupplier(zipPath).openPrimary(LOCATION)) {
			assertResourceBytes(pack, PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.txt"), "stone".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}

		Path replacementRoot = tempDir.resolve("replacement-source");
		createPackTree(replacementRoot);
		write(replacementRoot.resolve("assets/minecraft/textures/block/stone.txt"), "replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Path replacement = tempDir.resolve("replacement.zip");
		writeZipFromDirectory(replacementRoot, replacement, "");
		Files.move(replacement, zipPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

		try (PackResources pack = new FilePackResources.FileResourcesSupplier(zipPath).openPrimary(LOCATION)) {
			assertResourceBytes(pack, PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.txt"), "replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	@Test
	void productionRepeatedOpenCloseRejectsOldSuppliersButKeepsNewHandlesValid(@TempDir Path tempDir) throws Exception {
		Path root = createPackTree(tempDir.resolve("dir-pack"));
		IoSupplier<InputStream> staleSupplier;
		try (PackResources pack = new PathPackResources(LOCATION, root)) {
			staleSupplier = pack.getResource(PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.txt"));
			assertNotNull(staleSupplier);
		}
		assertThrows(IOException.class, staleSupplier::get);

		for (int i = 0; i < 5; i++) {
			try (PackResources pack = new PathPackResources(LOCATION, root)) {
				assertResourceBytes(pack, PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.txt"), "stone".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
		}
	}

	@Test
	void nonDefaultFilesystemDirectoryPackUsesJavaCompatibilityPath(@TempDir Path tempDir) throws Exception {
		Path zipPath = tempDir.resolve("zipfs-pack.zip");
		try (FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + zipPath.toUri()), Map.of("create", "true"))) {
			Path root = createPackTree(fileSystem.getPath("/"));
			try (PathPackResources pack = new PathPackResources(LOCATION, root)) {
				assertFalse(nativeBackendAttached(pack));
				assertResourceBytes(pack, PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.txt"), "stone".getBytes(java.nio.charset.StandardCharsets.UTF_8));
				assertTrue(javaList(pack, PackType.CLIENT_RESOURCES, "example", "nested").contains("nested/path/value.json"));
			}
		}
	}

	@Test
	void customPackResourcesRemainJavaOwnedCompatibilityPath() throws Exception {
		try (MultiPackResourceManager manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new CustomPackResources()))) {
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath("custom", "validation/value.txt");
			assertTrue(manager.getResource(location).isPresent());
			try (InputStream input = manager.getResource(location).orElseThrow().open()) {
				assertArrayEquals("custom".getBytes(java.nio.charset.StandardCharsets.UTF_8), input.readAllBytes());
			}
			assertTrue(manager.listResources("validation", resource -> true).containsKey(location));
		}
	}

	private static Path createPackTree(Path root) throws IOException {
		write(root.resolve("pack.mcmeta"), """
			{"pack":{"pack_format":1,"description":"native pack test"}}
			""".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		write(root.resolve("assets/minecraft/textures/block/stone.txt"), "stone".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		write(root.resolve("assets/minecraft/textures/block/stone.txt.mcmeta"), "meta".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		write(root.resolve("assets/example/nested/path/value.json"), "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		write(root.resolve("assets/example/binary/blob.bin"), new byte[]{0, 1, 2, -1, 42});
		write(root.resolve("assets/example/empty.dat"), new byte[0]);
		write(root.resolve("assets/example/case/lower.txt"), new byte[]{10});
		write(root.resolve("assets/example/case/Lower.txt"), new byte[]{11});
		write(root.resolve("assets/example/large.bin"), largeBytes());
		write(root.resolve("assets/BAD/ignored.txt"), new byte[]{9});
		write(root.resolve("data/minecraft/tags/blocks/test.json"), "{\"values\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Files.createDirectories(root.resolve("assets/example/emptydir"));
		return root;
	}

	private static void assertNamespaceParity(PackResources javaPack, NativePackResources rustPack, PackType type) throws IOException {
		assertEquals(new TreeSet<>(javaPack.getNamespaces(type)), new TreeSet<>(rustPack.listNamespaces(type)));
	}

	private static void assertListParity(PackResources javaPack, NativePackResources rustPack, PackType type, String namespace, String prefix) throws IOException {
		assertEquals(javaList(javaPack, type, namespace, prefix), rustPack.listResources(type, namespace, prefix));
	}

	private static List<String> javaList(PackResources pack, PackType type, String namespace, String prefix) {
		List<String> paths = new ArrayList<>();
		pack.listResources(type, namespace, prefix, (location, supplier) -> paths.add(location.getPath()));
		paths.sort(Comparator.naturalOrder());
		return paths;
	}

	private static void assertRootBytes(PackResources javaPack, NativePackResources rustPack, String path) throws IOException {
		IoSupplier<InputStream> supplier = javaPack.getRootResource(path);
		assertNotNull(supplier);
		try (InputStream input = supplier.get()) {
			assertArrayEquals(input.readAllBytes(), rustPack.readRootResource(path));
		}
	}

	private static void assertResourceBytes(
		PackResources javaPack,
		NativePackResources rustPack,
		PackType type,
		String namespace,
		String path
	) throws IOException {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
		IoSupplier<InputStream> supplier = javaPack.getResource(type, location);
		assertNotNull(supplier, location.toString());
		assertTrue(rustPack.exists(type, namespace, path), location.toString());
		try (InputStream input = supplier.get()) {
			assertArrayEquals(input.readAllBytes(), rustPack.readResource(type, namespace, path), location.toString());
		}
	}

	private static void assertResourceBytes(PackResources pack, PackType type, ResourceLocation location, byte[] expected) throws IOException {
		IoSupplier<InputStream> supplier = pack.getResource(type, location);
		assertNotNull(supplier, location.toString());
		try (InputStream input = supplier.get()) {
			assertArrayEquals(expected, input.readAllBytes(), location.toString());
		}
	}

	private static byte[] readResource(PackResources pack, PackType type, String namespace, String path) throws IOException {
		IoSupplier<InputStream> supplier = pack.getResource(type, ResourceLocation.fromNamespaceAndPath(namespace, path));
		assertNotNull(supplier);
		try (InputStream input = supplier.get()) {
			return input.readAllBytes();
		}
	}

	private static boolean nativeBackendAttached(PathPackResources pack) throws ReflectiveOperationException {
		Field field = PathPackResources.class.getDeclaredField("nativeBackend");
		field.setAccessible(true);
		return field.get(pack) != null;
	}

	private static final class CustomPackResources implements PackResources {
		@Nullable
		@Override
		public IoSupplier<InputStream> getRootResource(String... strings) {
			return null;
		}

		@Nullable
		@Override
		public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
			return resourceLocation.equals(ResourceLocation.fromNamespaceAndPath("custom", "validation/value.txt"))
				? () -> new ByteArrayInputStream("custom".getBytes(java.nio.charset.StandardCharsets.UTF_8))
				: null;
		}

		@Override
		public void listResources(PackType packType, String string, String string2, PackResources.ResourceOutput resourceOutput) {
			if ("custom".equals(string) && "validation".equals(string2)) {
				resourceOutput.accept(
					ResourceLocation.fromNamespaceAndPath("custom", "validation/value.txt"),
					() -> new ByteArrayInputStream("custom".getBytes(java.nio.charset.StandardCharsets.UTF_8))
				);
			}
		}

		@Override
		public Set<String> getNamespaces(PackType packType) {
			return Set.of("custom");
		}

		@Nullable
		@Override
		public <T> T getMetadataSection(MetadataSectionType<T> metadataSectionType) {
			return null;
		}

		@Override
		public PackLocationInfo location() {
			return LOCATION;
		}

		@Override
		public void close() {
		}
	}

	private static void write(Path path, byte[] bytes) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private static byte[] largeBytes() {
		byte[] bytes = new byte[128 * 1024];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte)(i * 31);
		}
		return bytes;
	}

	private static void writeZipFromDirectory(Path root, Path zipPath, String prefix) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
			try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
				for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
					String name = root.relativize(path).toString().replace('\\', '/');
					if (!prefix.isEmpty()) {
						name = prefix + "/" + name;
					}
					putZip(zip, name, Files.readAllBytes(path));
				}
			}
		}
	}

	private static void putZip(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(bytes);
		zip.closeEntry();
	}
}
