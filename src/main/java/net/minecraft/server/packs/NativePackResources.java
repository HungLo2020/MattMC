package net.minecraft.server.packs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.FileUtil;
import java.util.Optional;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.logging.LogUtils;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A pack opened by Rust (`assets/packs`): a directory or ZIP pack with its
 * overlays, or the vanilla pack's roots. Lookups, listings, namespaces and
 * reads run natively; `pack.mcmeta` sections decode here with their Java
 * codecs (the vanilla pack falls back to its built-in sections). Closing only
 * releases archive files, as Java's packs did; a later read reopens them.
 */
public class NativePackResources implements PackResources {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Cleaner CLEANER = Cleaner.create();
	private final PackLocationInfo location;
	private final long handle;
	@Nullable
	private final BuiltInMetadata builtInMetadata;
	/** The vanilla pack's per-type roots, for built-in pack discovery. */
	private final Map<PackType, List<Path>> rawPaths;

	protected NativePackResources(PackLocationInfo location, long handle, @Nullable BuiltInMetadata builtInMetadata, Map<PackType, List<Path>> rawPaths) {
		this.location = location;
		this.handle = handle;
		this.builtInMetadata = builtInMetadata;
		this.rawPaths = rawPaths;
		CLEANER.register(this, () -> NativePacks.releasePack(handle));
	}

	static int flags() {
		return (SharedConstants.IS_RUNNING_IN_IDE ? NativePacks.FLAG_RUNNING_IN_IDE : 0)
			| (SharedConstants.DEBUG_VALIDATE_RESOURCE_PATH_CASE ? NativePacks.FLAG_VALIDATE_PATH_CASE : 0);
	}

	/**
	 * A directory pack and its overlay directories (`PathPackResources`); a
	 * directory inside the game jar (a built-in data pack) reads its entries.
	 */
	public static NativePackResources directory(PackLocationInfo location, Path root, List<String> overlays) {
		long handle;
		if (root.getFileSystem() == FileSystems.getDefault()) {
			handle = NativePacks.openDirectory(location.id(), root.toString(), overlays, flags());
		} else {
			NativePacks.VanillaRoot archived = NativePackResources.root('D', root);
			handle = NativePacks.openArchiveDirectory(location.id(), archived.path(), archived.prefix(), overlays, flags());
		}
		return new NativePackResources(location, handle, null, Map.of());
	}

	/** A ZIP pack and its overlay prefixes (`FilePackResources`). */
	public static NativePackResources zip(PackLocationInfo location, Path file, List<String> overlays) {
		long handle = NativePacks.openZip(location.id(), file.toString(), overlays, flags());
		return new NativePackResources(location, handle, null, Map.of());
	}

	/**
	 * Opens the vanilla pack's roots: root resource roots and per-type roots,
	 * each highest priority first, and its exposed namespaces.
	 */
	static long openVanilla(PackLocationInfo location, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> pathsForType) {
		List<NativePacks.VanillaRoot> roots = new ArrayList<>();
		for (Path path : rootPaths) {
			roots.add(root('R', path));
		}
		for (PackType packType : PackType.values()) {
			for (Path path : pathsForType.getOrDefault(packType, List.of())) {
				roots.add(root((char)('0' + packType.ordinal()), path));
			}
		}
		return NativePacks.openVanilla(location.id(), roots, List.copyOf(namespaces), flags());
	}

	/** A root on the default file system, or inside a `jar:` archive. */
	private static NativePacks.VanillaRoot root(char role, Path path) {
		if (path.getFileSystem() == FileSystems.getDefault()) {
			return new NativePacks.VanillaRoot(role, false, path.toString(), "");
		}
		URI uri = path.toUri();
		String text = uri.toString();
		int separator = text.indexOf("!/");
		if (!"jar".equals(uri.getScheme()) || separator < 0) {
			throw new IllegalArgumentException("Unsupported vanilla pack root " + uri);
		}
		Path archive = Paths.get(URI.create(text.substring("jar:".length(), separator)));
		String inner = text.substring(separator + 2);
		String prefix = inner.isEmpty() || inner.endsWith("/") ? inner : inner + "/";
		return new NativePacks.VanillaRoot(role, true, archive.toString(), prefix);
	}

	long handle() {
		return this.handle;
	}

	public static long handleOf(PackResources packResources) {
		if (packResources instanceof NativePackResources nativePack) {
			return nativePack.handle;
		}
		throw new IllegalArgumentException("Pack " + packResources.packId() + " is not a native pack");
	}

	/** Reads one located entry. */
	public byte[] read(long entry, String relative) throws IOException {
		return NativePacks.read(this.handle, entry, relative);
	}

	public IoSupplier<InputStream> supplier(long entry, String relative) {
		return () -> new ByteArrayInputStream(NativePacks.read(this.handle, entry, relative));
	}

	public static String resourcePath(String namespace, String path) {
		return namespace + "/" + path;
	}

	@Nullable
	@Override
	public IoSupplier<InputStream> getRootResource(String... strings) {
		Long entry = NativePacks.rootResource(this.handle, String.join("\0", strings));
		return entry == null ? null : this.supplier(entry, String.join("/", strings));
	}

	@Nullable
	@Override
	public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation resourceLocation) {
		String namespace = resourceLocation.getNamespace();
		String path = resourceLocation.getPath();
		Long entry = NativePacks.resource(this.handle, packType.ordinal(), namespace, path);
		return entry == null ? null : this.supplier(entry, resourcePath(namespace, path));
	}

	@Override
	public void listResources(PackType packType, String string, String string2, PackResources.ResourceOutput resourceOutput) {
		NativePacks.listPack(this.handle, packType.ordinal(), string, string2, (path, entry) -> resourceOutput.accept(
			ResourceLocation.fromNamespaceAndPath(string, path), this.supplier(entry, resourcePath(string, path))
		));
	}

	@Override
	public Set<String> getNamespaces(PackType packType) {
		return Collections.unmodifiableSet(new HashSet<>(NativePacks.packNamespaces(this.handle, packType.ordinal())));
	}

	@Nullable
	@Override
	public <T> T getMetadataSection(MetadataSectionType<T> metadataSectionType) throws IOException {
		IoSupplier<InputStream> ioSupplier = this.getRootResource("pack.mcmeta");
		if (ioSupplier == null) {
			return this.builtInMetadata == null ? null : this.builtInMetadata.get(metadataSectionType);
		}
		if (this.builtInMetadata == null) {
			try (InputStream inputStream = ioSupplier.get()) {
				return getMetadataFromStream(metadataSectionType, inputStream, this.location);
			}
		}
		try (InputStream inputStream = ioSupplier.get()) {
			T object = getMetadataFromStream(metadataSectionType, inputStream, this.location);
			if (object != null) {
				return object;
			}
		} catch (IOException ignored) {
		}
		return this.builtInMetadata.get(metadataSectionType);
	}

	/**
	 * Decodes one `pack.mcmeta` section with its codec; a parse or decode
	 * failure logs and yields null.
	 */
	@Nullable
	public static <T> T getMetadataFromStream(MetadataSectionType<T> metadataSectionType, InputStream inputStream, PackLocationInfo packLocationInfo) {
		JsonObject jsonObject;
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			jsonObject = GsonHelper.parse(bufferedReader);
		} catch (Exception exception) {
			LOGGER.error("Couldn't load {} {} metadata: {}", packLocationInfo.id(), metadataSectionType.name(), exception.getMessage());
			return null;
		}

		return !jsonObject.has(metadataSectionType.name())
			? null
			: metadataSectionType.codec()
				.parse(JsonOps.INSTANCE, jsonObject.get(metadataSectionType.name()))
				.ifError(error -> LOGGER.error("Couldn't load {} {} metadata: {}", packLocationInfo.id(), metadataSectionType.name(), error.message()))
				.result()
				.orElse(null);
	}

	@Override
	public PackLocationInfo location() {
		return this.location;
	}

	@Override
	public void close() {
		NativePacks.closePack(this.handle);
	}

	/** `VanillaPackResources.listRawPaths`: each type root's location for a path. */
	public void listRawPaths(PackType packType, ResourceLocation resourceLocation, Consumer<Path> consumer) {
		FileUtil.decomposePath(resourceLocation.getPath()).ifSuccess(list -> {
			for (Path path : this.rawPaths.getOrDefault(packType, List.of())) {
				consumer.accept(FileUtil.resolvePath(path.resolve(resourceLocation.getNamespace()), list));
			}
		});
	}

	/** `VanillaPackResources.asProvider`. */
	public ResourceProvider asProvider() {
		return resourceLocation -> Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, resourceLocation))
			.map(ioSupplier -> new Resource(this, ioSupplier));
	}

	/** A directory pack source (`PathPackResources.PathResourcesSupplier`). */
	public static class DirectorySupplier implements Pack.ResourcesSupplier {
		private final Path content;

		public DirectorySupplier(Path path) {
			this.content = path;
		}

		@Override
		public PackResources openPrimary(PackLocationInfo packLocationInfo) {
			return directory(packLocationInfo, this.content, List.of());
		}

		@Override
		public PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata) {
			return directory(packLocationInfo, this.content, metadata.overlays());
		}
	}

	/** A ZIP pack source (`FilePackResources.FileResourcesSupplier`). */
	public static class ZipSupplier implements Pack.ResourcesSupplier {
		private final Path content;

		public ZipSupplier(Path path) {
			this.content = path;
		}

		public ZipSupplier(java.io.File file) {
			this(file.toPath());
		}

		@Override
		public PackResources openPrimary(PackLocationInfo packLocationInfo) {
			return zip(packLocationInfo, this.content, List.of());
		}

		@Override
		public PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata) {
			return zip(packLocationInfo, this.content, metadata.overlays());
		}
	}
}
