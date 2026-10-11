package net.minecraft.server.packs.resources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.NativePackResources;
import net.minecraft.server.packs.NativePacks;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The resource manager over a pack stack, resolved by Rust
 * (`assets/resources`): per-namespace pack stacks, filter sections, sidecar
 * metadata, and lookups and listings in `ResourceLocation` order. Pack filter
 * patterns (Java regular expressions) are matched here when Rust asks.
 * Listing predicates only test locations, so they apply to Rust's results.
 */
public class MultiPackResourceManager implements CloseableResourceManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Cleaner CLEANER = Cleaner.create();
	private final List<PackResources> packs;
	private final NativePackResources[] nativePacks;
	private final long handle;
	private final Set<String> namespaces;

	public MultiPackResourceManager(PackType packType, List<PackResources> list) {
		this.packs = List.copyOf(list);
		int count = this.packs.size();
		this.nativePacks = new NativePackResources[count];
		long[] handles = new long[count];
		boolean[] hasFilter = new boolean[count];
		ResourceFilterSection[] filters = new ResourceFilterSection[count];
		boolean anyFilter = false;
		for (int index = 0; index < count; index++) {
			PackResources packResources = this.packs.get(index);
			this.nativePacks[index] = (NativePackResources)packResources;
			handles[index] = NativePackResources.handleOf(packResources);
			filters[index] = this.getPackFilterSection(packResources);
			hasFilter[index] = filters[index] != null;
			anyFilter |= hasFilter[index];
		}
		long token = anyFilter ? NativePacks.registerFilters(new NativePacks.FilterCheck() {
			@Override
			public boolean namespaceFiltered(int pack, String namespace) {
				return filters[pack] != null && filters[pack].isNamespaceFiltered(namespace);
			}

			@Override
			public boolean pathFiltered(int pack, String path) {
				return filters[pack] != null && filters[pack].isPathFiltered(path);
			}
		}) : 0L;
		long manager = NativePacks.createManager(packType.ordinal(), handles, hasFilter, token);
		this.handle = manager;
		CLEANER.register(this, () -> {
			NativePacks.releaseManager(manager);
			NativePacks.unregisterFilters(token);
		});
		this.namespaces = Collections.unmodifiableSet(new HashSet<>(NativePacks.managerNamespaces(manager)));
	}

	@Nullable
	private ResourceFilterSection getPackFilterSection(PackResources packResources) {
		try {
			return packResources.getMetadataSection(ResourceFilterSection.TYPE);
		} catch (IOException var3) {
			LOGGER.error("Failed to get filter section from pack {}", packResources.packId());
			return null;
		}
	}

	private IoSupplier<ResourceMetadata> sidecar(int pack, long entry, String namespace, String path) {
		if (pack < 0) {
			return ResourceMetadata.EMPTY_SUPPLIER;
		}
		NativePackResources source = this.nativePacks[pack];
		String relative = NativePackResources.resourcePath(namespace, path + ".mcmeta");
		return () -> ResourceMetadata.fromJsonStream(new ByteArrayInputStream(source.read(entry, relative)));
	}

	private Resource resource(NativePacks.Source source, String namespace, String path) {
		NativePackResources pack = this.nativePacks[source.pack()];
		return new Resource(pack, pack.supplier(source.entry(), NativePackResources.resourcePath(namespace, path)),
			this.sidecar(source.metadataPack(), source.metadataEntry(), namespace, path));
	}

	@Override
	public Set<String> getNamespaces() {
		return this.namespaces;
	}

	@Override
	public Optional<Resource> getResource(ResourceLocation resourceLocation) {
		String namespace = resourceLocation.getNamespace();
		String path = resourceLocation.getPath();
		NativePacks.Located located = NativePacks.getResource(this.handle, namespace, path);
		if (located == null) {
			return Optional.empty();
		}
		NativePackResources pack = this.nativePacks[located.pack()];
		IoSupplier<ResourceMetadata> metadata = () -> {
			NativePacks.Located sidecar = NativePacks.stackMetadata(this.handle, namespace, path, located.position());
			return sidecar == null ? ResourceMetadata.EMPTY : this.sidecar(sidecar.pack(), sidecar.entry(), namespace, path).get();
		};
		return Optional.of(new Resource(pack, pack.supplier(located.entry(), NativePackResources.resourcePath(namespace, path)), metadata));
	}

	@Override
	public List<Resource> getResourceStack(ResourceLocation resourceLocation) {
		List<Resource> list = new ArrayList<>();
		NativePacks.resourceStack(this.handle, resourceLocation.getNamespace(), resourceLocation.getPath(), (namespace, path, sources) -> {
			for (NativePacks.Source source : sources) {
				list.add(this.resource(source, namespace, path));
			}
		});
		return list;
	}

	@Override
	public Map<ResourceLocation, Resource> listResources(String string, Predicate<ResourceLocation> predicate) {
		checkTrailingDirectoryPath(string);
		Map<ResourceLocation, Resource> map = new TreeMap<>();
		NativePacks.listResources(this.handle, string, false, (namespace, path, sources) -> {
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
			if (predicate.test(location)) {
				map.put(location, this.resource(sources.getFirst(), namespace, path));
			}
		});
		return map;
	}

	@Override
	public Map<ResourceLocation, List<Resource>> listResourceStacks(String string, Predicate<ResourceLocation> predicate) {
		checkTrailingDirectoryPath(string);
		Map<ResourceLocation, List<Resource>> map = new TreeMap<>();
		NativePacks.listResources(this.handle, string, true, (namespace, path, sources) -> {
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
			if (predicate.test(location)) {
				List<Resource> list = new ArrayList<>(sources.size());
				for (NativePacks.Source source : sources) {
					list.add(this.resource(source, namespace, path));
				}
				map.put(location, list);
			}
		});
		return map;
	}

	private static void checkTrailingDirectoryPath(String string) {
		if (string.endsWith("/")) {
			throw new IllegalArgumentException("Trailing slash in path " + string);
		}
	}

	/**
	 * This manager's native handle, for Rust stages that read resources
	 * directly (`assets/textures`, `assets/models`). Valid while this
	 * manager is reachable.
	 */
	public long nativeHandle() {
		return this.handle;
	}

	private static final java.util.Map<net.minecraft.server.packs.PackType, MultiPackResourceManager> EMPTY = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The native manager behind `resourceManager`: itself, a reloadable
	 * manager's current resources, or an empty manager for
	 * `ResourceManager.Empty`.
	 */
	public static MultiPackResourceManager nativeManager(ResourceManager resourceManager) {
		if (resourceManager instanceof MultiPackResourceManager manager) {
			return manager;
		}
		if (resourceManager instanceof ReloadableResourceManager reloadable) {
			return nativeManager(reloadable.resources());
		}
		if (resourceManager == ResourceManager.Empty.INSTANCE) {
			return EMPTY.computeIfAbsent(PackType.CLIENT_RESOURCES, type -> new MultiPackResourceManager(type, List.of()));
		}
		throw new IllegalArgumentException("Resource manager has no native resources: " + resourceManager);
	}

	@Override
	public Stream<PackResources> listPacks() {
		return this.packs.stream();
	}

	@Override
	public void close() {
		this.packs.forEach(PackResources::close);
	}
}
