import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceFilterSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.world.level.validation.DirectoryValidator;

/**
 * Same-API observer for Frozen and Current resource stacks over the same
 * content: a directory pack of the checkout's resources, a vanilla-builder
 * pack over those roots and a jar-style archive subset, plus synthetic
 * fixtures (overlays, filters, sidecars, invalid names, archives with
 * directory entries, broken and metadata-less packs). Also records tag
 * loading per registry directory, and registry-data entries and update-tags
 * payloads from server registries loaded over the vanilla data and fixture
 * packs (`observeSync`).
 *
 * Writes every observable result to a record file and prints one receipt
 * with its hash and warm timings.
 *
 * Usage: ResourceStackReference content-dir fixture-dir record-file iterations
 */
public final class ResourceStackReference {
	private static final DirectoryValidator TRUST_ALL = new DirectoryValidator(path -> true);
	private static PrintWriter record;

	public static void main(String[] args) throws Exception {
		Path content = Path.of(args[0]);
		Path fixtures = Path.of(args[1]);
		Path recordFile = Path.of(args[2]);
		int iterations = Integer.parseInt(args[3]);
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		writeFixtures(fixtures);
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(recordFile, StandardCharsets.UTF_8))) {
			record = writer;
			Stacks client = stacks(PackType.CLIENT_RESOURCES, content, fixtures.resolve("client"));
			Stacks server = stacks(PackType.SERVER_DATA, content, fixtures.resolve("server"));
			observe("client", client, content);
			observe("server", server, content);
			observeSelection();
			long tagNs = observeTags(server, content, true);
			long[] syncTimes = observeSync(content, fixtures.resolve("sync"), iterations);
			long[] clientTimes = time(client, content, iterations);
			long[] serverTimes = time(server, content, iterations);
			writer.flush();
			String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(recordFile)));
			System.out.println("RESOURCE_STACK_REFERENCE sha256=" + sha + " client_list_read_ns=" + clientTimes[0]
				+ " client_lookup_ns=" + clientTimes[1] + " server_list_read_ns=" + serverTimes[0] + " server_lookup_ns=" + serverTimes[1]
				+ " client_manager_ns=" + clientTimes[2] + " server_manager_ns=" + serverTimes[2] + " tags_ns=" + timeTags(server, content, iterations)
				+ " sync_ns=" + syncTimes[0] + " retag_ns=" + syncTimes[1] + " first_retag_ns=" + syncTimes[2]);
			client.close();
			server.close();
		}
	}

	// -- fixtures --------------------------------------------------------------

	private static String format(PackType type) {
		var version = SharedConstants.getCurrentVersion().packVersion(type);
		return version.major() + "." + version.minor();
	}

	private static String mcmeta(PackType type, String extra) {
		var version = SharedConstants.getCurrentVersion().packVersion(type);
		int major = version.major();
		return "{\"pack\":{\"description\":\"fixture " + type + "\",\"min_format\":[" + major + "," + version.minor() + "],"
			+ "\"max_format\":" + (major + 5) + "}" + extra + "}";
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private static void writeFixtures(Path root) throws IOException {
		for (PackType type : PackType.values()) {
			String side = type == PackType.CLIENT_RESOURCES ? "client" : "server";
			String dir = type.getDirectory();
			var version = SharedConstants.getCurrentVersion().packVersion(type);
			int major = version.major();
			Path packs = root.resolve(side);
			String overlays = ",\"overlays\":{\"entries\":["
				+ "{\"min_format\":" + major + ",\"max_format\":" + (major + 2) + ",\"directory\":\"overlay_now\"},"
				+ "{\"min_format\":" + (major + 10) + ",\"max_format\":" + (major + 12) + ",\"directory\":\"overlay_future\"}]}";
			// A pattern without a path blocks every path of its namespaces, and
			// one without a namespace every namespace, so each filter pack keeps
			// one pattern to leave the lower packs (vanilla, the jar) visible.
			String filter = ",\"filter\":{\"block\":[{\"namespace\":\"minecraft\",\"path\":\"fixture/blocked/.*\"}]}";
			String namespaceFilter = ",\"filter\":{\"block\":[{\"namespace\":\"filtered_ns\"}]}";
			// The low pack: files a higher filter blocks, sidecars it overrides.
			Path low = packs.resolve("a_low");
			write(low.resolve("pack.mcmeta"), mcmeta(type, ""));
			write(low.resolve(dir + "/minecraft/fixture/blocked/hidden.json"), "{\"low\":1}");
			write(low.resolve(dir + "/minecraft/fixture/shared.json"), "{\"from\":\"low\"}");
			write(low.resolve(dir + "/minecraft/fixture/shared.json.mcmeta"), "{\"texture\":{\"blur\":true}}");
			write(low.resolve(dir + "/minecraft/fixture/meta_only_low.json"), "{}");
			write(low.resolve(dir + "/minecraft/fixture/meta_only_low.json.mcmeta"), "{\"texture\":{\"clamp\":true}}");
			write(low.resolve(dir + "/filtered_ns/fixture/any.json"), "{}");
			write(low.resolve(dir + "/othermod/fixture/low.json"), "{}");
			Path namespaceOnly = packs.resolve("a_namespace_filter");
			write(namespaceOnly.resolve("pack.mcmeta"), mcmeta(type, namespaceFilter));
			write(namespaceOnly.resolve(dir + "/minecraft/fixture/namespace_filter.json"), "{}");
			// The directory pack: overlays, a filter, odd names, nested files.
			Path high = packs.resolve("b_dir");
			write(high.resolve("pack.mcmeta"), mcmeta(type, overlays + filter));
			write(high.resolve(dir + "/minecraft/fixture/shared.json"), "{\"from\":\"dir\"}");
			write(high.resolve(dir + "/minecraft/fixture/meta_only_low.json"), "{\"dir\":1}");
			write(high.resolve(dir + "/minecraft/fixture/a.png"), "primary-a");
			write(high.resolve(dir + "/minecraft/fixture/a.png.mcmeta"), "{\"animation\":{\"frametime\":3}}");
			write(high.resolve(dir + "/minecraft/fixture/sub/deep/b.json"), "{\"deep\":true}");
			write(high.resolve(dir + "/minecraft/fixture/Upper/C.json"), "{\"upper\":true}");
			write(high.resolve(dir + "/minecraft/fixture/bad name.json"), "{}");
			write(high.resolve(dir + "/minecraft/fixture/lonely.json.mcmeta"), "{\"texture\":{}}");
			write(high.resolve(dir + "/othermod/fixture/c.json"), "{\"c\":1}");
			write(high.resolve(dir + "/Bad_NS/fixture/d.json"), "{}");
			write(high.resolve(dir + "/stray.txt"), "stray");
			write(high.resolve("overlay_now/" + dir + "/minecraft/fixture/a.png"), "overlay-now-a");
			write(high.resolve("overlay_now/" + dir + "/minecraft/fixture/only_overlay.json"), "{\"overlay\":1}");
			write(high.resolve("overlay_future/" + dir + "/minecraft/fixture/a.png"), "overlay-future-a");
			write(high.resolve("pack.png"), "not-a-png");
			// The archive pack: directory entries, stored and deflated entries,
			// an overlay prefix, sidecars, and names Java rejects.
			Path zip = packs.resolve("c_zip.zip");
			Files.createDirectories(zip.getParent());
			try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
				zipEntry(out, dir + "/", null, false);
				zipEntry(out, dir + "/minecraft/", null, false);
				zipEntry(out, "pack.mcmeta", mcmeta(type, ",\"overlays\":{\"entries\":[{\"min_format\":" + major + ",\"max_format\":"
					+ (major + 1) + ",\"directory\":\"zoverlay\"}]}"), true);
				zipEntry(out, dir + "/minecraft/fixture/zip_stored.json", "{\"stored\":true}", false);
				zipEntry(out, dir + "/minecraft/fixture/zip_deflated.json", "{\"deflated\":true,\"padding\":\"" + "x".repeat(4000) + "\"}", true);
				zipEntry(out, dir + "/minecraft/fixture/shared.json", "{\"from\":\"zip\"}", true);
				zipEntry(out, dir + "/minecraft/fixture/a.png.mcmeta", "{\"animation\":{\"frametime\":9}}", true);
				zipEntry(out, dir + "/minecraft/fixture/dir_only/", null, false);
				zipEntry(out, dir + "/minecraft/fixture/Upper Case/Bad.json", "{}", true);
				zipEntry(out, dir + "/zipmod/fixture/z.json", "{\"z\":1}", true);
				zipEntry(out, dir + "/zipmod.txt", "file-under-type", true);
				zipEntry(out, "zoverlay/" + dir + "/minecraft/fixture/zip_stored.json", "{\"overlaid\":true}", true);
			}
			// Packs Java skips: no metadata, broken metadata, a broken archive,
			// and one whose format falls back to the description only.
			write(packs.resolve("d_nometa/" + dir + "/minecraft/fixture/x.json"), "{}");
			write(packs.resolve("e_badjson/pack.mcmeta"), "{\"pack\":");
			Files.writeString(packs.resolve("f_broken.zip"), "this is not a zip");
			write(packs.resolve("g_fallback/pack.mcmeta"), "{\"pack\":{\"description\":\"fallback only\"}}");
			write(packs.resolve("g_fallback/" + dir + "/minecraft/fixture/fallback.json"), "{}");
			write(packs.resolve("h_trailing/pack.mcmeta"), mcmeta(type, "") + " trailing garbage");
			write(packs.resolve("not_a_pack.txt"), "ignored");
			if (type == PackType.SERVER_DATA) {
				writeTagFixtures(low, high);
			}
		}
	}

	/** Tag files across two packs: replace, references, optional entries, cycles and bad entries. */
	private static void writeTagFixtures(Path low, Path high) throws IOException {
		String base = "data/minecraft/tags/fixture/";
		write(low.resolve(base + "replaced.json"), "{\"values\":[\"minecraft:low_only\"]}");
		write(high.resolve(base + "replaced.json"), "{\"replace\":true,\"values\":[\"minecraft:high_only\",\"#minecraft:chain_a\"]}");
		write(low.resolve(base + "appended.json"), "{\"values\":[\"minecraft:first\"]}");
		write(high.resolve(base + "appended.json"), "{\"values\":[\"minecraft:second\",\"minecraft:first\"]}");
		write(high.resolve(base + "chain_a.json"), "{\"values\":[\"#minecraft:chain_b\",\"minecraft:a1\",{\"id\":\"minecraft:opt_x\",\"required\":false}]}");
		write(high.resolve(base + "chain_b.json"), "{\"values\":[\"#minecraft:chain_c\",\"minecraft:b1\",\"minecraft:opt_x\"]}");
		write(high.resolve(base + "chain_c.json"), "{\"values\":[\"minecraft:c1\",{\"id\":\"#minecraft:nowhere\",\"required\":false}]}");
		write(high.resolve(base + "missing_ref.json"), "{\"values\":[\"minecraft:missing_thing\",\"#minecraft:nowhere\",\"minecraft:fine\"]}");
		write(high.resolve(base + "cycle_a.json"), "{\"values\":[\"#minecraft:cycle_b\",\"minecraft:ca\"]}");
		write(high.resolve(base + "cycle_b.json"), "{\"values\":[\"#minecraft:cycle_c\",\"minecraft:cb\"]}");
		write(high.resolve(base + "cycle_c.json"), "{\"values\":[\"#minecraft:cycle_a\",\"minecraft:cc\"]}");
		write(high.resolve(base + "optional_cycle_a.json"), "{\"values\":[{\"id\":\"#minecraft:optional_cycle_b\",\"required\":false},\"minecraft:oa\"]}");
		write(high.resolve(base + "optional_cycle_b.json"), "{\"values\":[{\"id\":\"#minecraft:optional_cycle_a\",\"required\":false},\"minecraft:ob\"]}");
		write(high.resolve(base + "bad_entry.json"), "{\"values\":[\"minecraft:good\",\"Bad Entry\"]}");
		write(high.resolve(base + "bad_json.json"), "{\"values\":[");
		write(high.resolve(base + "empty.json"), "");
		write(high.resolve(base + "trailing.json"), "{\"values\":[]} trailing");
		write(high.resolve(base + "nested/deep.json"), "{\"values\":[\"#minecraft:appended\",\"othermod:deep\"]}");
		write(high.resolve("data/othermod/tags/fixture/foreign.json"), "{\"values\":[\"#minecraft:replaced\"]}");
		String overflow = "data/minecraft/tags/fixture_overflow/";
		write(high.resolve(overflow + "selfish.json"), "{\"values\":[\"#minecraft:selfish\"]}");
		for (int index = 0; index < 6; index++) {
			write(high.resolve(overflow + "dependent_" + index + ".json"), "{\"values\":[\"#minecraft:selfish\"]}");
		}
	}

	private static void zipEntry(ZipOutputStream out, String name, String text, boolean deflate) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0L);
		byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
		if (!deflate) {
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(bytes.length);
			entry.setCompressedSize(bytes.length);
			CRC32 crc = new CRC32();
			crc.update(bytes);
			entry.setCrc(crc.getValue());
		}
		out.putNextEntry(entry);
		out.write(bytes);
		out.closeEntry();
	}

	// -- stacks ----------------------------------------------------------------

	private record Stacks(PackType type, MultiPackResourceManager manager, List<PackResources> packs, PackRepository repository,
		FileSystem jar) {
		void close() throws IOException {
			this.manager.close();
			this.jar.close();
		}
	}

	private static Stacks stacks(PackType type, Path content, Path fixtures) throws IOException {
		FileSystem jar = FileSystems.newFileSystem(content.resolve("subset.jar"));
		PackRepository repository = new PackRepository(
			new FolderRepositorySource(content.resolve("packs"), type, PackSource.DEFAULT, TRUST_ALL),
			new FolderRepositorySource(fixtures, type, PackSource.WORLD, TRUST_ALL),
			// Directory packs inside an archive, as the game jar's built-in data packs are.
			new FolderRepositorySource(jar.getPath("/packs_in_jar"), type, PackSource.BUILT_IN, TRUST_ALL)
		);
		repository.reload();
		record.println("== repository " + type);
		for (Pack pack : repository.getAvailablePacks()) {
			record.println("available " + pack.getId() + " compat=" + pack.getCompatibility() + " features=" + pack.getRequestedFeatures()
				+ " description=" + pack.getDescription().getString() + " required=" + pack.isRequired() + " position=" + pack.getDefaultPosition());
		}
		List<String> order = new ArrayList<>(repository.getAvailableIds());
		repository.setSelected(order);
		record.println("selected " + String.join(",", repository.getSelectedIds()));
		List<PackResources> packs = new ArrayList<>();
		// The vanilla pack over the same roots, then an archive root inside a jar.
		packs.add(new VanillaPackResourcesBuilder()
			.setMetadata(BuiltInMetadata.of())
			.exposeNamespace("minecraft", "voxelmap", "sodium", "iris", "distanthorizons", "tacz")
			.pushAssetPath(type, jar.getPath("/" + type.getDirectory()))
			.pushUniversalPath(content.resolve("resources"))
			.build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.empty())));
		packs.addAll(repository.openAllSelected());
		MultiPackResourceManager manager = new MultiPackResourceManager(type, packs);
		return new Stacks(type, manager, packs, repository, jar);
	}

	// -- observations ----------------------------------------------------------

	private static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static String read(Resource resource) {
		try (InputStream stream = resource.open()) {
			return sha(stream.readAllBytes());
		} catch (Exception exception) {
			return "error:" + exception.getClass().getSimpleName();
		}
	}

	private static String metadata(Resource resource) {
		try {
			var meta = resource.metadata();
			return "anim=" + meta.getSection(AnimationMetadataSection.TYPE).map(section -> section.defaultFrameTime()).orElse(-1)
				+ ",tex=" + meta.getSection(TextureMetadataSection.TYPE).map(section -> section.blur() + "/" + section.clamp()).orElse("-");
		} catch (Exception exception) {
			return "meta-error:" + exception.getClass().getSimpleName();
		}
	}

	private static String describe(Resource resource) {
		return resource.sourcePackId() + "@" + read(resource) + "[" + metadata(resource) + "]";
	}

	private static Set<String> directories(Path content, PackType type) throws IOException {
		Set<String> directories = new TreeSet<>(List.of("fixture", "fixture/sub", "missing_dir", "", "fixture/blocked"));
		Path base = content.resolve("resources").resolve(type.getDirectory());
		try (Stream<Path> namespaces = Files.list(base)) {
			for (Path namespace : namespaces.toList()) {
				if (Files.isDirectory(namespace)) {
					try (Stream<Path> children = Files.list(namespace)) {
						children.filter(Files::isDirectory).forEach(child -> directories.add(child.getFileName().toString()));
					}
				}
			}
		}
		return directories;
	}

	private static void observe(String side, Stacks stacks, Path content) throws Exception {
		PackType type = stacks.type();
		record.println("== manager " + side);
		record.println("namespaces " + new TreeSet<>(stacks.manager().getNamespaces()));
		for (PackResources pack : stacks.packs()) {
			record.println("pack " + pack.packId() + " namespaces=" + new TreeSet<>(pack.getNamespaces(type)));
			var meta = pack.getRootResource("pack.mcmeta");
			var icon = pack.getRootResource("pack.png");
			record.println("  root mcmeta=" + (meta == null ? "-" : sha(meta.get().readAllBytes())) + " png=" + (icon == null ? "-" : sha(icon.get().readAllBytes())));
			PackMetadataSection section = pack.getMetadataSection(PackMetadataSection.forPackType(type));
			record.println("  section=" + (section == null ? "-" : section.description().getString() + " " + section.supportedFormats()));
			OverlayMetadataSection overlays = pack.getMetadataSection(OverlayMetadataSection.forPackType(type));
			record.println("  overlays=" + (overlays == null ? "-" : overlays.overlaysForVersion(SharedConstants.getCurrentVersion().packVersion(type))));
			record.println("  filter=" + (pack.getMetadataSection(ResourceFilterSection.TYPE) != null));
			for (String bad : new String[]{"../escape", ""}) {
				try {
					record.println("  root " + bad + "=" + (pack.getRootResource(bad) != null));
				} catch (IllegalArgumentException exception) {
					record.println("  root " + bad + "=IAE " + exception.getMessage());
				}
			}
		}
		Set<ResourceLocation> seen = new TreeSet<>();
		for (String directory : directories(content, type)) {
			record.println("-- list " + directory);
			try {
				for (Map.Entry<ResourceLocation, Resource> entry : stacks.manager().listResources(directory, location -> true).entrySet()) {
					seen.add(entry.getKey());
					record.println(entry.getKey() + " " + describe(entry.getValue()));
				}
				for (Map.Entry<ResourceLocation, List<Resource>> entry : stacks.manager().listResourceStacks(directory, location -> true).entrySet()) {
					StringBuilder line = new StringBuilder("stack " + entry.getKey());
					for (Resource resource : entry.getValue()) {
						line.append(' ').append(describe(resource));
					}
					record.println(line);
				}
			} catch (IllegalArgumentException exception) {
				record.println("IAE " + exception.getMessage());
			}
			// A predicate that keeps only JSON files.
			record.println("json " + stacks.manager().listResources(directory, location -> location.getPath().endsWith(".json")).keySet().size());
		}
		List<ResourceLocation> probes = new ArrayList<>(seen);
		for (String extra : new String[]{"minecraft:fixture/blocked/hidden.json", "filtered_ns:fixture/any.json", "minecraft:fixture/Upper/C.json",
			"minecraft:fixture/missing.json", "minecraft:fixture/dir_only", "minecraft:fixture/a.png.mcmeta", "minecraft:fixture/lonely.json",
			"zipmod:fixture/z.json", "minecraft:fixture/sub/../shared.json", "nosuchns:fixture/x.json", "minecraft:fixture//double.json"}) {
			ResourceLocation location = ResourceLocation.tryParse(extra);
			if (location != null) {
				probes.add(location);
			}
		}
		record.println("-- lookups " + probes.size());
		for (ResourceLocation location : probes) {
			var single = stacks.manager().getResource(location);
			StringBuilder line = new StringBuilder("get " + location + " " + single.map(ResourceStackReference::describe).orElse("-"));
			for (Resource resource : stacks.manager().getResourceStack(location)) {
				line.append(" | ").append(describe(resource));
			}
			record.println(line);
		}
	}

	// -- tags ------------------------------------------------------------------

	private static Set<String> tagDirectories(Path content) throws IOException {
		Set<String> directories = new TreeSet<>(List.of("tags/fixture", "tags/fixture_overflow"));
		try (Stream<Path> namespaces = Files.list(content.resolve("resources/data"))) {
			for (Path namespace : namespaces.toList()) {
				Path tags = namespace.resolve("tags");
				if (!Files.isDirectory(tags)) {
					continue;
				}
				try (Stream<Path> files = Files.walk(tags)) {
					files.filter(file -> file.toString().endsWith(".json")).forEach(file -> {
						// Every directory holding tag files (a loader lists its subtree).
						directories.add(namespace.relativize(file.getParent()).toString().replace('\\', '/'));
					});
				}
			}
		}
		return directories;
	}

	/** Runs a TagLoader by reflection (its load/build types differ between trees). */
	@SuppressWarnings("unchecked")
	private static Map<ResourceLocation, List<String>> runTags(net.minecraft.server.packs.resources.ResourceManager manager, String directory,
		List<String> calls) throws Exception {
		Set<ResourceLocation> created = new java.util.HashSet<>();
		net.minecraft.tags.TagLoader.ElementLookup<String> lookup = (id, required) -> {
			calls.add(id + (required ? "!" : "?"));
			boolean present = required ? !id.getPath().contains("missing") : created.contains(id) || Math.floorMod(id.getPath().hashCode(), 3) == 0;
			if (required && present) {
				created.add(id);
			}
			return present ? Optional.of(id.toString()) : Optional.empty();
		};
		var loader = new net.minecraft.tags.TagLoader<>(lookup, directory);
		var load = net.minecraft.tags.TagLoader.class.getMethod("load", net.minecraft.server.packs.resources.ResourceManager.class);
		Object loaded = load.invoke(loader, manager);
		var build = java.util.Arrays.stream(net.minecraft.tags.TagLoader.class.getMethods()).filter(method -> method.getName().equals("build")).findFirst().orElseThrow();
		return (Map<ResourceLocation, List<String>>)build.invoke(loader, loaded);
	}

	private static long observeTags(Stacks server, Path content, boolean record) throws Exception {
		if (record) {
			ResourceStackReference.record.println("== tags");
		}
		long total = 0L;
		for (String directory : tagDirectories(content)) {
			List<String> calls = new ArrayList<>();
			long started = System.nanoTime();
			String outcome;
			Map<ResourceLocation, List<String>> tags = Map.of();
			try {
				tags = runTags(server.manager(), directory, calls);
				outcome = "ok";
			} catch (java.lang.reflect.InvocationTargetException exception) {
				outcome = exception.getCause() instanceof StackOverflowError ? "stack-overflow" : "error:" + exception.getCause().getClass().getSimpleName();
			}
			total += System.nanoTime() - started;
			if (!record) {
				continue;
			}
			ResourceStackReference.record.println("-- tags " + directory + " " + outcome + " tags=" + tags.size() + " calls=" + calls.size()
				+ " callsha=" + sha(String.join("\n", calls).getBytes(StandardCharsets.UTF_8)));
			if (directory.startsWith("tags/fixture")) {
				ResourceStackReference.record.println("calls " + calls);
			}
			for (Map.Entry<ResourceLocation, List<String>> entry : new java.util.TreeMap<>(tags).entrySet()) {
				ResourceStackReference.record.println("tag " + entry.getKey() + " " + entry.getValue());
			}
			// The map's own iteration order (it decides later binding order).
			ResourceStackReference.record.println("order " + sha(tags.keySet().toString().getBytes(StandardCharsets.UTF_8)));
		}
		return total;
	}

	private static long timeTags(Stacks server, Path content, int iterations) throws Exception {
		List<Long> times = new ArrayList<>();
		for (int iteration = 0; iteration < iterations; iteration++) {
			times.add(observeTags(server, content, false));
		}
		return median(times);
	}

	// -- registry synchronization -----------------------------------------------

	/**
	 * Server registries as `WorldLoader` assembles them (static tags, then
	 * worldgen registries) from the checkout's data as the vanilla known pack
	 * plus an unknown fixture pack; records the encoded registry-data entries
	 * for three client known-pack sets and the update-tags payloads, then
	 * rebinds static tags from a second fixture (a reload) and records again.
	 */
	private static long[] observeSync(Path content, Path fixtures, int iterations) throws Exception {
		Path extra = fixtures.resolve("extra");
		write(extra.resolve("pack.mcmeta"), mcmeta(PackType.SERVER_DATA, ""));
		write(extra.resolve("data/fixture/damage_type/custom.json"), "{\"message_id\":\"fixture\",\"exhaustion\":0.1,\"scaling\":\"never\"}");
		write(extra.resolve("data/minecraft/tags/damage_type/is_fire.json"), "{\"values\":[\"fixture:custom\"]}");
		write(extra.resolve("data/minecraft/tags/item/fixture_sync.json"), "{\"values\":[\"minecraft:stone\",\"#minecraft:logs\",\"minecraft:dirt\"]}");
		Path reload = fixtures.resolve("reload");
		write(reload.resolve("pack.mcmeta"), mcmeta(PackType.SERVER_DATA, ""));
		write(reload.resolve("data/minecraft/tags/item/fixture_sync.json"), "{\"replace\":true,\"values\":[\"minecraft:diamond\"]}");
		write(reload.resolve("data/minecraft/tags/block/fixture_reload.json"), "{\"values\":[\"minecraft:stone\"]}");
		KnownPack core = KnownPack.vanilla("core");
		PackResources vanilla = new VanillaPackResourcesBuilder()
			.setMetadata(BuiltInMetadata.of())
			.exposeNamespace("minecraft")
			.pushUniversalPath(content.resolve("resources"))
			.build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.of(core)));
		PackResources fixture = openDirectory(extra);
		PackResources reloaded = openDirectory(reload);
		record.println("== sync");
		long[] times = new long[3];
		try (MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla, fixture));
			MultiPackResourceManager reloadManager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla, fixture, reloaded))) {
			LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
			List<Registry.PendingTags<?>> pending = TagLoader.loadTagsForExistingRegistries(manager, layers.getLayer(RegistryLayer.STATIC));
			List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), pending);
			RegistryAccess.Frozen worldgen = RegistryDataLoader.load(manager, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
			LayeredRegistryAccess<RegistryLayer> loaded = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen);
			pending.forEach(Registry.PendingTags::apply);
			List<Set<KnownPack>> clients = List.of(Set.of(core), Set.of(), Set.of(new KnownPack("fixture", "other", "1")));
			recordSync(loaded, clients);
			// Warm-up rounds run untimed, so both stages report warm medians like the other timings.
			List<Long> sync = new ArrayList<>();
			for (int iteration = 0; iteration < 2 * iterations; iteration++) {
				long started = System.nanoTime();
				sync(loaded, clients, null);
				if (iteration >= iterations) {
					sync.add(System.nanoTime() - started);
				}
			}
			record.println("-- reload");
			List<Long> retag = new ArrayList<>();
			for (int iteration = 0; iteration < 2 * iterations; iteration++) {
				MultiPackResourceManager source = iteration % 2 == 0 ? reloadManager : manager;
				List<Registry.PendingTags<?>> again = TagLoader.loadTagsForExistingRegistries(source, layers.getLayer(RegistryLayer.STATIC));
				long started = System.nanoTime();
				again.forEach(Registry.PendingTags::apply);
				sync(loaded, clients, null);
				long elapsed = System.nanoTime() - started;
				if (iteration == 0) {
					times[2] = elapsed;
				} else if (iteration >= iterations) {
					retag.add(elapsed);
				}
				if (iteration < 2) {
					recordSync(loaded, clients);
				}
			}
			times[0] = median(sync);
			times[1] = median(retag);
		}
		vanilla.close();
		fixture.close();
		reloaded.close();
		return times;
	}

	/** A directory data pack, opened through the same repository API in both trees. */
	private static PackResources openDirectory(Path path) {
		PackRepository repository = new PackRepository(new FolderRepositorySource(path.getParent(), PackType.SERVER_DATA, PackSource.WORLD, TRUST_ALL));
		repository.reload();
		Pack pack = repository.getPack("file/" + path.getFileName());
		if (pack == null) {
			throw new IllegalStateException("No fixture pack " + path);
		}
		return pack.open();
	}

	private static void recordSync(LayeredRegistryAccess<RegistryLayer> layers, List<Set<KnownPack>> clients) throws Exception {
		sync(layers, clients, record);
	}

	/** One configuration-phase sync per client, with every encoded byte hashed when recording. */
	private static void sync(LayeredRegistryAccess<RegistryLayer> layers, List<Set<KnownPack>> clients, PrintWriter out) throws Exception {
		DynamicOps<Tag> ops = layers.compositeAccess().createSerializationContext(NbtOps.INSTANCE);
		for (Set<KnownPack> client : clients) {
			List<String> lines = new ArrayList<>();
			BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> consumer = (key, entries) -> {
				if (out == null) {
					return;
				}
				ByteBuf buffer = Unpooled.buffer();
				int withData = 0;
				for (RegistrySynchronization.PackedRegistryEntry entry : entries) {
					RegistrySynchronization.PackedRegistryEntry.STREAM_CODEC.encode(buffer, entry);
					withData += entry.data().isPresent() ? 1 : 0;
				}
				byte[] bytes = new byte[buffer.readableBytes()];
				buffer.readBytes(bytes);
				lines.add("registry " + key.location() + " entries=" + entries.size() + " data=" + withData + " sha=" + shaUnchecked(bytes));
			};
			packRegistries(ops, layers, client, consumer);
			if (out != null) {
				out.println("-- client " + client);
				lines.forEach(out::println);
			}
		}
		Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags = TagNetworkSerialization.serializeTagsToNetwork(layers);
		if (out == null) {
			return;
		}
		// The packet map is keyed by identity-hashed `ResourceKey`s, so its order varies per JVM; record by key.
		Map<String, TagNetworkSerialization.NetworkPayload> sorted = new java.util.TreeMap<>();
		tags.forEach((key, payload) -> sorted.put(key.location().toString(), payload));
		for (Map.Entry<String, TagNetworkSerialization.NetworkPayload> entry : sorted.entrySet()) {
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			entry.getValue().write(buffer);
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.getBytes(0, bytes);
			out.println("tags " + entry.getKey() + " size=" + entry.getValue().size());
			// Wire order follows `getTags()`, whose `TagKey` hashes are identity-based and so vary per JVM:
			// compare it with Frozen's algorithm in this JVM, and the contents across trees.
			List<String> wire = new ArrayList<>();
			Map<String, String> contents = new java.util.TreeMap<>();
			for (int count = buffer.readVarInt(); count > 0; count--) {
				ResourceLocation tag = buffer.readResourceLocation();
				wire.add(tag.toString());
				contents.put(tag.toString(), buffer.readIntIdList().toString());
			}
			Registry<?> registry = layers.compositeAccess().lookupOrThrow(ResourceKey.createRegistryKey(ResourceLocation.parse(entry.getKey())));
			List<String> reference = referenceTags(registry).keySet().stream().map(ResourceLocation::toString).toList();
			out.println("wire-order " + (wire.equals(reference) ? "reference" : "DIFFERENT " + wire + " vs " + reference));
			contents.forEach((tag, ids) -> out.println("tag " + tag + " " + ids));
		}
	}

	/** Frozen's `TagNetworkSerialization.serializeToNetwork` map. */
	private static <T> Map<ResourceLocation, it.unimi.dsi.fastutil.ints.IntList> referenceTags(Registry<T> registry) {
		Map<ResourceLocation, it.unimi.dsi.fastutil.ints.IntList> map = new java.util.HashMap<>();
		registry.getTags().forEach(named -> {
			it.unimi.dsi.fastutil.ints.IntList ids = new it.unimi.dsi.fastutil.ints.IntArrayList(named.size());
			for (net.minecraft.core.Holder<T> holder : named) {
				ids.add(registry.getId(holder.value()));
			}
			map.put(named.key().location(), ids);
		});
		return map;
	}

	/** Frozen packs from a `RegistryAccess` layer; Current from the layered access. */
	private static void packRegistries(DynamicOps<Tag> ops, LayeredRegistryAccess<RegistryLayer> layers, Set<KnownPack> client,
		BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> consumer) throws Exception {
		for (java.lang.reflect.Method method : RegistrySynchronization.class.getMethods()) {
			if (method.getName().equals("packRegistries")) {
				Object access = method.getParameterTypes()[1] == LayeredRegistryAccess.class ? layers : layers.getAccessFrom(RegistryLayer.WORLDGEN);
				method.invoke(null, ops, access, client, consumer);
				return;
			}
		}
		throw new IllegalStateException("No packRegistries");
	}

	private static String shaUnchecked(byte[] bytes) {
		try {
			return sha(bytes);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	// -- selection -------------------------------------------------------------

	/** `PackRepository.rebuildSelected` and `Pack.Position.insert` over synthetic packs. */
	private static void observeSelection() {
		record.println("== selection");
		Pack.Position[] positions = Pack.Position.values();
		List<Pack> packs = new ArrayList<>();
		int id = 0;
		for (boolean required : new boolean[]{false, true}) {
			for (Pack.Position position : positions) {
				for (boolean fixed : new boolean[]{false, true}) {
					String name = "p" + id++ + (required ? "r" : "") + position.name().charAt(0) + (fixed ? "f" : "");
					PackLocationInfo info = new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty());
					packs.add(new Pack(info, null, new Pack.Metadata(Component.literal(name),
						net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE, net.minecraft.world.flag.FeatureFlagSet.of(), List.of()),
						new net.minecraft.server.packs.PackSelectionConfig(required, position, fixed)));
				}
			}
		}
		PackRepository repository = new PackRepository(consumer -> packs.forEach(consumer));
		repository.reload();
		java.util.Random random = new java.util.Random(7L);
		for (int trial = 0; trial < 200; trial++) {
			List<String> requested = new ArrayList<>();
			int count = random.nextInt(9);
			for (int index = 0; index < count; index++) {
				requested.add(random.nextInt(10) == 0 ? "missing" : packs.get(random.nextInt(packs.size())).getId());
			}
			repository.setSelected(requested);
			record.println("select " + requested + " -> " + repository.getSelectedPacks().stream().map(Pack::getId).toList());
			List<Pack> list = new ArrayList<>(repository.getSelectedPacks());
			Pack pack = packs.get(random.nextInt(packs.size()));
			boolean opposite = random.nextBoolean();
			int at = pack.getDefaultPosition().insert(list, pack, Pack::selectionConfig, opposite);
			record.println("insert " + pack.getId() + " opposite=" + opposite + " at=" + at + " -> " + list.stream().map(Pack::getId).toList());
		}
	}

	// -- timings ---------------------------------------------------------------

	private static long median(List<Long> values) {
		List<Long> sorted = new ArrayList<>(values);
		sorted.sort(null);
		return sorted.get(sorted.size() / 2);
	}

	private static long[] time(Stacks stacks, Path content, int iterations) throws Exception {
		Set<String> directories = directories(content, stacks.type());
		List<Long> listRead = new ArrayList<>();
		List<Long> lookup = new ArrayList<>();
		List<Long> manager = new ArrayList<>();
		long sink = 0L;
		for (int iteration = 0; iteration < iterations; iteration++) {
			long started = System.nanoTime();
			MultiPackResourceManager fresh = new MultiPackResourceManager(stacks.type(), stacks.packs());
			manager.add(System.nanoTime() - started);
			List<ResourceLocation> locations = new ArrayList<>();
			started = System.nanoTime();
			for (String directory : directories) {
				if (directory.isEmpty()) {
					continue;
				}
				for (Map.Entry<ResourceLocation, List<Resource>> entry : fresh.listResourceStacks(directory, location -> true).entrySet()) {
					locations.add(entry.getKey());
					for (Resource resource : entry.getValue()) {
						try (InputStream stream = resource.open()) {
							sink += stream.readAllBytes().length;
						}
					}
				}
			}
			listRead.add(System.nanoTime() - started);
			started = System.nanoTime();
			for (ResourceLocation location : locations) {
				sink += fresh.getResource(location).isPresent() ? 1 : 0;
			}
			lookup.add(System.nanoTime() - started);
		}
		if (sink == 42L) {
			System.out.println("unlikely");
		}
		return new long[]{median(listRead), median(lookup), median(manager)};
	}
}
