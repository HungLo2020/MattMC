import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.validation.DirectoryValidator;
import sun.misc.Unsafe;

/**
 * Same-API observer for Reference and Current model reloads: every known
 * atlas stitched by `SpriteLoader.loadAndStitch` (never uploaded), then
 * `ModelManager.reload` over the checkout's resources, alone and with a
 * fixture pack of edge cases. Records per block state its model's class,
 * particle, render group and the parts it collects for several seeds (each
 * part's AO flag, particle and quads per face), and per item model id the
 * baked item model tree and properties, plus the missing models; then times
 * the model reload (blocks atlas precomputed) and a full atlas + model reload.
 *
 * Generated items order their front faces from `Map.of`, whose iteration
 * order differs per JVM, so adjacent SOUTH/NORTH quads in item models are
 * recorded in NORTH/SOUTH order.
 *
 * Usage: ModelReference content-dir fixture-packs-dir record-file iterations [detail-file [label-regex]]
 */
public final class ModelReference {
	private static final int MIP_LEVELS = 4;
	private static final int MAX_SIZE = 16384;
	private static final int SEEDS = 4;
	private static final Direction[] FACES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	private static PrintWriter record;
	private static PrintWriter detail;
	private static java.util.regex.Pattern detailLabels = java.util.regex.Pattern.compile("");
	private static Unsafe unsafe;

	record Atlas(ResourceLocation texture, ResourceLocation definition, boolean mipmaps, java.util.Set<MetadataSectionType<?>> metadata) {
	}

	public static void main(String[] args) throws Exception {
		Path content = Path.of(args[0]);
		Path fixtures = Path.of(args[1]);
		Path recordFile = Path.of(args[2]);
		int iterations = Integer.parseInt(args[3]);
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ClientBootstrap.bootstrap();
		Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
		theUnsafe.setAccessible(true);
		unsafe = (Unsafe)theUnsafe.get(null);
		List<Atlas> atlases = knownAtlases();
		PackResources vanilla = new VanillaPackResourcesBuilder()
			.setMetadata(BuiltInMetadata.of())
			.exposeNamespace("minecraft", "voxelmap", "sodium", "iris", "distanthorizons", "tacz")
			.pushUniversalPath(content.resolve("resources"))
			.build(new PackLocationInfo("vanilla", Component.literal("vanilla"), PackSource.BUILT_IN, Optional.empty()));
		List<PackResources> withFixtures = new ArrayList<>(List.of(vanilla));
		withFixtures.addAll(openPacks(fixtures));
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(recordFile, StandardCharsets.UTF_8));
			PrintWriter details = args.length > 4 ? new PrintWriter(Files.newBufferedWriter(Path.of(args[4]), StandardCharsets.UTF_8)) : null) {
			record = writer;
			detail = details;
			if (args.length > 5) {
				detailLabels = java.util.regex.Pattern.compile(args[5]);
			}
			MultiPackResourceManager plain = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(vanilla));
			observe("vanilla", plain, atlases, executor);
			observe("fixtures", new MultiPackResourceManager(PackType.CLIENT_RESOURCES, withFixtures), atlases, executor);
			Map<ResourceLocation, SpriteLoader.Preparations> stitched = stitchAll(plain, atlases, executor);
			List<Long> models = new ArrayList<>();
			List<Long> full = new ArrayList<>();
			for (int iteration = 0; iteration < iterations; iteration++) {
				long started = System.nanoTime();
				reload(plain, atlases, stitched, executor);
				models.add(System.nanoTime() - started);
				started = System.nanoTime();
				Map<ResourceLocation, SpriteLoader.Preparations> fresh = stitchAll(plain, atlases, executor);
				reload(plain, atlases, fresh, executor);
				full.add(System.nanoTime() - started);
				close(fresh);
			}
			close(stitched);
			writer.flush();
			models.sort(null);
			full.sort(null);
			String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(recordFile)));
			System.out.println("MODEL_REFERENCE sha256=" + sha + " models_ns=" + models.get(models.size() / 2) + " reload_ns=" + full.get(full.size() / 2));
		} finally {
			executor.shutdownNow();
		}
	}

	private static List<PackResources> openPacks(Path folder) {
		PackRepository repository = new PackRepository(
			new FolderRepositorySource(folder, PackType.CLIENT_RESOURCES, PackSource.DEFAULT, new DirectoryValidator(path -> true)));
		repository.reload();
		List<PackResources> packs = new ArrayList<>();
		for (Pack pack : repository.getAvailablePacks()) {
			packs.add(pack.open());
		}
		return packs;
	}

	@SuppressWarnings("unchecked")
	private static List<Atlas> knownAtlases() throws Exception {
		Field field = AtlasManager.class.getDeclaredField("KNOWN_ATLASES");
		field.setAccessible(true);
		List<Atlas> atlases = new ArrayList<>();
		for (Object config : (List<Object>)field.get(null)) {
			Class<?> type = config.getClass();
			atlases.add(new Atlas((ResourceLocation)component(type, config, "textureId"), (ResourceLocation)component(type, config, "definitionLocation"),
				(boolean)component(type, config, "createMipmaps"), (java.util.Set<MetadataSectionType<?>>)component(type, config, "additionalMetadata")));
		}
		return atlases;
	}

	private static Object component(Class<?> type, Object instance, String name) throws Exception {
		var accessor = type.getDeclaredMethod(name);
		accessor.setAccessible(true);
		return accessor.invoke(instance);
	}

	/** Every atlas's preparations by definition id. */
	private static Map<ResourceLocation, SpriteLoader.Preparations> stitchAll(MultiPackResourceManager manager, List<Atlas> atlases, ExecutorService executor) {
		Map<ResourceLocation, CompletableFuture<SpriteLoader.Preparations>> futures = new TreeMap<>();
		for (Atlas atlas : atlases) {
			futures.put(atlas.definition(), new SpriteLoader(atlas.texture(), MAX_SIZE, 0, 0)
				.loadAndStitch(manager, atlas.definition(), atlas.mipmaps() ? MIP_LEVELS : 0, executor, atlas.metadata())
				.thenCompose(preparations -> preparations.readyForUpload().thenApply(ignored -> preparations)));
		}
		Map<ResourceLocation, SpriteLoader.Preparations> stitched = new TreeMap<>();
		futures.forEach((id, future) -> stitched.put(id, future.join()));
		return stitched;
	}

	private static void close(Map<ResourceLocation, SpriteLoader.Preparations> stitched) {
		stitched.values().forEach(preparations -> preparations.regions().values().forEach(sprite -> sprite.contents().close()));
	}

	private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static Object get(Class<?> type, Object instance, String name) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	/**
	 * An `AtlasManager` whose atlases hold the stitched sprites without a
	 * texture manager or upload, and the shared state its stitch results
	 * would publish.
	 */
	@SuppressWarnings("unchecked")
	private static AtlasManager atlasManager(List<Atlas> atlases, Map<ResourceLocation, SpriteLoader.Preparations> stitched,
		PreparableReloadListener.SharedState sharedState) throws Exception {
		AtlasManager manager = (AtlasManager)unsafe.allocateInstance(AtlasManager.class);
		Map<ResourceLocation, Object> byTexture = new java.util.HashMap<>();
		Map<ResourceLocation, Object> byId = new java.util.HashMap<>();
		Map<Material, TextureAtlasSprite> lookup = new java.util.HashMap<>();
		Class<?> entryType = Class.forName("net.minecraft.client.resources.model.AtlasManager$AtlasEntry");
		var entryConstructor = entryType.getDeclaredConstructors()[0];
		entryConstructor.setAccessible(true);
		List<Field> configFields = new ArrayList<>();
		var configConstructor = AtlasManager.AtlasConfig.class.getConstructor(ResourceLocation.class, ResourceLocation.class, boolean.class, java.util.Set.class);
		for (Atlas atlas : atlases) {
			SpriteLoader.Preparations preparations = stitched.get(atlas.definition());
			TextureAtlas textureAtlas = (TextureAtlas)unsafe.allocateInstance(TextureAtlas.class);
			set(TextureAtlas.class, textureAtlas, "location", atlas.texture());
			set(TextureAtlas.class, textureAtlas, "texturesByName", Map.copyOf(preparations.regions()));
			set(TextureAtlas.class, textureAtlas, "missingSprite", preparations.missing());
			Object entry = entryConstructor.newInstance(textureAtlas,
				configConstructor.newInstance(atlas.texture(), atlas.definition(), atlas.mipmaps(), atlas.metadata()));
			byTexture.put(atlas.texture(), entry);
			byId.put(atlas.definition(), entry);
			preparations.regions().forEach((name, sprite) -> lookup.put(new Material(atlas.texture(), name), sprite));
		}
		set(AtlasManager.class, manager, "atlasByTexture", byTexture);
		set(AtlasManager.class, manager, "atlasById", byId);
		set(AtlasManager.class, manager, "materialLookup", lookup);
		manager.prepareSharedState(sharedState);
		Object pending = sharedState.get(AtlasManager.PENDING_STITCH);
		Map<ResourceLocation, CompletableFuture<SpriteLoader.Preparations>> futures =
			(Map<ResourceLocation, CompletableFuture<SpriteLoader.Preparations>>)get(AtlasManager.PendingStitchResults.class, pending, "stitchFuturesById");
		futures.forEach((id, future) -> future.complete(stitched.get(id)));
		return manager;
	}

	/**
	 * `ModelManager.reload`'s prepared state, taken at the preparation
	 * barrier (applying it reaches into the running client).
	 */
	private static Object reload(MultiPackResourceManager resources, List<Atlas> atlases, Map<ResourceLocation, SpriteLoader.Preparations> stitched,
		ExecutorService executor) throws Exception {
		PreparableReloadListener.SharedState sharedState = new PreparableReloadListener.SharedState(resources);
		AtlasManager atlasManager = atlasManager(atlases, stitched, sharedState);
		ModelManager modelManager = new ModelManager(BlockColors.createDefault(), atlasManager, null);
		CompletableFuture<Object> prepared = new CompletableFuture<>();
		CompletableFuture<Void> reloaded = modelManager.reload(sharedState, executor, new PreparableReloadListener.PreparationBarrier() {
			@Override
			public <T> CompletableFuture<T> wait(T object) {
				prepared.complete(object);
				return new CompletableFuture<>();
			}
		}, executor);
		reloaded.whenComplete((ignored, failure) -> {
			if (failure != null) {
				prepared.completeExceptionally(failure);
			}
		});
		return prepared.join();
	}

	private static Object component(Object instance, String name) throws Exception {
		return component(instance.getClass(), instance, name);
	}

	@SuppressWarnings("unchecked")
	private static void observe(String stack, MultiPackResourceManager manager, List<Atlas> atlases, ExecutorService executor) throws Exception {
		Map<ResourceLocation, SpriteLoader.Preparations> stitched = stitchAll(manager, atlases, executor);
		Object state = reload(manager, atlases, stitched, executor);
		Object baked = component(state, "bakedModels");
		Object missing = component(baked, "missingModels");
		BlockStateModel missingBlock = (BlockStateModel)component(missing, "block");
		Map<BlockState, BlockStateModel> cache = (Map<BlockState, BlockStateModel>)component(state, "modelCache");
		it.unimi.dsi.fastutil.objects.Object2IntMap<BlockState> groups = (it.unimi.dsi.fastutil.objects.Object2IntMap<BlockState>)component(state, "modelGroups");
		record.println("== " + stack + " groups=" + groups.size() + " states=" + cache.size());
		emit("missing block", stateModel(missingBlock));
		// Group ids are arbitrary (`HashMap` order of identity-hashed keys);
		// what counts is which states share one, and the invisible group 0.
		Map<Integer, Integer> canonical = new java.util.HashMap<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			for (BlockState blockState : block.getStateDefinition().getPossibleStates()) {
				// `BlockModelShaper.getBlockModel`.
				BlockStateModel model = cache.getOrDefault(blockState, missingBlock);
				String group = !groups.containsKey(blockState) ? "none"
					: groups.getInt(blockState) <= 0 ? String.valueOf(groups.getInt(blockState))
					: "g" + canonical.computeIfAbsent(groups.getInt(blockState), id -> canonical.size() + 1);
				emit("state " + blockState + " group=" + group + (model == missingBlock ? " missing" : ""), stateModel(model));
			}
		}
		Map<ResourceLocation, Object> items = (Map<ResourceLocation, Object>)component(baked, "itemStackModels");
		Map<ResourceLocation, Object> properties = (Map<ResourceLocation, Object>)component(baked, "itemProperties");
		record.println("items=" + items.size() + " properties=" + properties.size());
		emit("missing item", dump(component(missing, "item")));
		for (Map.Entry<ResourceLocation, Object> entry : new TreeMap<>(items).entrySet()) {
			emit("item " + entry.getKey() + " properties=" + dump(properties.get(entry.getKey())), dump(entry.getValue()));
		}
		close(stitched);
	}

	/** A record line: label and the detail's hash (the detail itself goes to the detail file). */
	private static void emit(String label, String text) throws Exception {
		record.println(label + " " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 20));
		if (detail != null && detailLabels.matcher(label).find()) {
			detail.println(label);
			detail.println(text);
		}
	}

	private static String stateModel(BlockStateModel model) {
		StringBuilder text = new StringBuilder(model.getClass().getSimpleName()).append(" particle=").append(sprite(model.particleIcon()));
		for (int seed = 0; seed < SEEDS; seed++) {
			List<BlockModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(seed), parts);
			text.append("\n seed ").append(seed).append(" parts=").append(parts.size());
			for (BlockModelPart part : parts) {
				text.append("\n  part ao=").append(part.useAmbientOcclusion()).append(" particle=").append(sprite(part.particleIcon()));
				for (Direction face : FACES) {
					List<BakedQuad> quads = part.getQuads(face);
					if (!quads.isEmpty()) {
						text.append("\n   ").append(face).append(':');
						for (BakedQuad quad : quads) {
							text.append("\n    ").append(dump(quad));
						}
					}
				}
			}
		}
		return text.toString();
	}

	private static String sprite(TextureAtlasSprite sprite) {
		return sprite.atlasLocation() + "/" + sprite.contents().name();
	}

	private static String dump(Object value) {
		StringBuilder text = new StringBuilder();
		dump(value, text, new IdentityHashMap<>(), 0);
		return text.toString();
	}

	/**
	 * A structural dump: model and item renderer classes by their fields,
	 * collections by their elements, sprites by name, floats by bits; other
	 * objects (special renderers' entity models, codecs, lambdas) by class.
	 */
	private static void dump(Object value, StringBuilder text, IdentityHashMap<Object, Integer> seen, int depth) {
		if (value == null) {
			text.append("null");
			return;
		}
		Class<?> type = value.getClass();
		if (value instanceof Float number) {
			text.append("f").append(Integer.toHexString(Float.floatToRawIntBits(number)));
		} else if (value instanceof Double number) {
			text.append("d").append(Long.toHexString(Double.doubleToRawLongBits(number)));
		} else if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value instanceof Character
			|| value instanceof ResourceLocation || value instanceof Enum<?>) {
			text.append(value);
		} else if (value instanceof org.joml.Vector3fc vector) {
			// Values: Java shares its default vectors between transforms.
			text.append("v(").append(Integer.toHexString(Float.floatToRawIntBits(vector.x()))).append(',')
				.append(Integer.toHexString(Float.floatToRawIntBits(vector.y()))).append(',')
				.append(Integer.toHexString(Float.floatToRawIntBits(vector.z()))).append(')');
		} else if (value instanceof TextureAtlasSprite sprite) {
			text.append("sprite:").append(sprite(sprite));
		} else if (value instanceof BlockState state) {
			text.append("state:").append(state);
		} else if (value instanceof Optional<?> optional) {
			text.append("Optional[");
			dump(optional.orElse(null), text, seen, depth);
			text.append(']');
		} else if (type.isArray()) {
			text.append('[');
			for (int index = 0; index < Array.getLength(value); index++) {
				if (index > 0) {
					text.append(',');
				}
				dump(Array.get(value, index), text, seen, depth + 1);
			}
			text.append(']');
		} else if (value instanceof Map<?, ?> map) {
			text.append('{');
			Map<String, Object> sorted = new TreeMap<>();
			map.forEach((key, entry) -> sorted.put(String.valueOf(key), entry));
			sorted.forEach((key, entry) -> {
				text.append(key).append('=');
				dump(entry, text, seen, depth + 1);
				text.append(';');
			});
			text.append('}');
		} else if (value instanceof Collection<?> collection) {
			List<Object> elements = new ArrayList<>(collection);
			if (value instanceof java.util.Set<?>) {
				elements.sort(java.util.Comparator.comparing(String::valueOf));
			}
			canonicalFrontFaces(elements);
			text.append('[');
			for (Object element : elements) {
				text.append("\n").append(" ".repeat(depth + 1));
				dump(element, text, seen, depth + 1);
			}
			text.append(']');
		} else if (type.isHidden() || type.isSynthetic()) {
			text.append("lambda");
		} else if (!walked(type)) {
			text.append('<').append(type.getName()).append('>');
		} else if (seen.containsKey(value)) {
			text.append("@").append(seen.get(value));
		} else {
			seen.put(value, seen.size());
			text.append(type.getSimpleName()).append('(');
			for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
				for (Field field : at.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())) {
						continue;
					}
					try {
						field.setAccessible(true);
						text.append(field.getName()).append('=');
						dump(field.get(value), text, seen, depth + 1);
						text.append(' ');
					} catch (ReflectiveOperationException | RuntimeException inaccessible) {
						text.append("?").append(' ');
					}
				}
			}
			text.append(')');
		}
	}

	private static boolean walked(Class<?> type) {
		String name = type.getName();
		return name.startsWith("net.minecraft.client.renderer.item.") || name.startsWith("net.minecraft.client.renderer.block.model.")
			|| name.startsWith("net.minecraft.client.resources.model.") || name.startsWith("org.joml.")
			|| name.startsWith("net.minecraft.client.renderer.special.") && type.isRecord();
	}

	/** Adjacent SOUTH then NORTH quads (a generated front element from `Map.of`) as NORTH then SOUTH. */
	private static void canonicalFrontFaces(List<Object> elements) {
		for (int index = 0; index + 1 < elements.size(); index++) {
			if (elements.get(index) instanceof BakedQuad first && elements.get(index + 1) instanceof BakedQuad second
				&& first.direction() == Direction.SOUTH && second.direction() == Direction.NORTH) {
				elements.set(index, second);
				elements.set(index + 1, first);
				index++;
			}
		}
	}
}
