package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.blaze3d.platform.NativeImage;
import net.logging.LogUtils;
import net.math.MatrixUtil;
import net.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.block.model.multipart.MultiPartModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.NativeLog;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * One model reload in Rust (`assets/models`): model and blockstate files,
 * discovery, and baking. Java supplies state definitions, item roots, the
 * block atlas's sprites and JOML matrices, and builds the model objects its
 * renderers and gameplay use from the baked records.
 */
public final class NativeModels implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Cleaner CLEANER = Cleaner.create();
	private static final MethodHandle LOAD = handle("models_load", ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle GROUPS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_models_groups",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle DISCOVER = handle("models_discover", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle BAKE = handle("models_bake", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_models_release",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
	private static final MethodHandle BUFFER_FREE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_buffer_free",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final Direction[] DIRECTIONS = Direction.values();

	static {
		NativeLog.ensureRegistered();
	}

	private static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.of(result, arguments));
	}

	// -- records -------------------------------------------------------------

	/** A growable little-endian record buffer. */
	private static final class Writer {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
		private final ByteBuffer scratch = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

		void u8(int value) {
			this.out.write(value);
		}

		void u16(int value) {
			this.out.write(value);
			this.out.write(value >>> 8);
		}

		void u32(int value) {
			this.out.write(value);
			this.out.write(value >>> 8);
			this.out.write(value >>> 16);
			this.out.write(value >>> 24);
		}

		void u64(long value) {
			this.scratch.clear();
			this.scratch.putLong(value);
			this.out.write(this.scratch.array(), 0, 8);
		}

		void f32(float value) {
			this.u32(Float.floatToRawIntBits(value));
		}

		void text(String text) {
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			this.u32(bytes.length);
			this.out.write(bytes, 0, bytes.length);
		}

		void location(ResourceLocation location) {
			this.text(location.getNamespace());
			this.text(location.getPath());
		}

		void matrix(Matrix4fc matrix) {
			float[] values = matrix.get(new float[16]);
			for (float value : values) {
				this.f32(value);
			}
		}

		byte[] bytes() {
			return this.out.toByteArray();
		}
	}

	/** A little-endian record reader over a native buffer. */
	private static final class Reader {
		private final MemorySegment buffer;
		private long at;

		Reader(MemorySegment buffer) {
			this.buffer = buffer;
		}

		boolean remaining() {
			return this.at < this.buffer.byteSize();
		}

		int u8() {
			return this.buffer.get(ValueLayout.JAVA_BYTE, this.at++) & 0xFF;
		}

		int u32() {
			int value = this.buffer.get(ValueLayout.JAVA_INT_UNALIGNED, this.at);
			this.at += 4;
			return value;
		}

		float f32() {
			return Float.intBitsToFloat(this.u32());
		}

		long u64() {
			long value = this.buffer.get(ValueLayout.JAVA_LONG_UNALIGNED, this.at);
			this.at += 8;
			return value;
		}

		/** A reader of the same buffer from `offset`. */
		Reader at(long offset) {
			Reader reader = new Reader(this.buffer);
			reader.at = offset;
			return reader;
		}

		int[] ints(int count) {
			int[] values = new int[count];
			MemorySegment.copy(this.buffer, ValueLayout.JAVA_INT_UNALIGNED, this.at, values, 0, count);
			this.at += 4L * count;
			return values;
		}

		String text() {
			int length = this.u32();
			byte[] bytes = new byte[length];
			MemorySegment.copy(this.buffer, ValueLayout.JAVA_BYTE, this.at, bytes, 0, length);
			this.at += length;
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private static MemorySegment input(Arena arena, byte[] bytes) {
		return bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
	}

	/** Reads a native result buffer with `read`, then frees it. */
	private static <T> T take(MemorySegment pointer, long length, java.util.function.Function<Reader, T> read) throws Throwable {
		if (length == 0L) {
			return read.apply(new Reader(MemorySegment.ofArray(new byte[0])));
		}
		MemorySegment buffer = pointer.get(ValueLayout.ADDRESS, 0).reinterpret(length);
		try {
			return read.apply(new Reader(buffer));
		} finally {
			BUFFER_FREE.invokeExact(buffer, length);
		}
	}

	// -- state definitions -----------------------------------------------------

	/** Java's blockstate definitions in Rust's record form, with every state in record order. */
	private record Definitions(byte[] records, List<BlockState> states) {
	}

	@Nullable
	private static volatile Definitions definitions;

	private static <T extends Comparable<T>> String valueName(Property<T> property, Comparable<?> value) {
		@SuppressWarnings("unchecked")
		T typed = (T)value;
		return property.getName(typed);
	}

	/** Block registries are frozen before resources load, so the export is made once. */
	private static Definitions definitions() {
		Definitions cached = definitions;
		if (cached != null) {
			return cached;
		}
		Map<ResourceLocation, StateDefinition<Block, BlockState>> map = BlockStateDefinitions.definitions();
		Writer writer = new Writer();
		List<BlockState> states = new ArrayList<>();
		writer.u32(map.size());
		map.forEach((id, definition) -> {
			writer.location(id);
			writer.text(String.valueOf(definition.getOwner()));
			List<Property<?>> properties = List.copyOf(definition.getProperties());
			writer.u32(properties.size());
			List<List<?>> values = new ArrayList<>(properties.size());
			for (Property<?> property : properties) {
				writer.text(property.getName());
				writer.u8(property instanceof BooleanProperty ? 0 : property instanceof IntegerProperty ? 1 : 2);
				writer.text(property.getPossibleValues().toString());
				writer.text(property.toString());
				List<?> possible = property.getPossibleValues();
				values.add(possible);
				writer.u32(possible.size());
				for (Object value : possible) {
					writer.text(valueName(property, (Comparable<?>)value));
				}
			}
			List<BlockState> possibleStates = definition.getPossibleStates();
			writer.u32(possibleStates.size());
			for (BlockState state : possibleStates) {
				for (int index = 0; index < properties.size(); index++) {
					writer.u16(values.get(index).indexOf(state.getValue(properties.get(index))));
				}
				states.add(state);
			}
		});
		cached = new Definitions(writer.bytes(), List.copyOf(states));
		definitions = cached;
		return cached;
	}

	// -- session ---------------------------------------------------------------

	private final long[] handle;
	private final List<BlockState> states;

	private NativeModels(long handle, List<BlockState> states) {
		this.handle = new long[]{handle};
		this.states = states;
		long[] cell = this.handle;
		CLEANER.register(this, () -> release(cell));
	}

	private static void release(long[] cell) {
		synchronized (cell) {
			if (cell[0] != 0L) {
				try {
					RELEASE.invokeExact(cell[0]);
				} catch (Throwable throwable) {
					throw new IllegalStateException("Release model reload failed", throwable);
				} finally {
					cell[0] = 0L;
				}
			}
		}
	}

	private long handle() {
		synchronized (this.handle) {
			if (this.handle[0] == 0L) {
				throw new IllegalStateException("Model reload is closed");
			}
			return this.handle[0];
		}
	}

	@Override
	public void close() {
		release(this.handle);
	}

	/**
	 * Loads the model files (top pack per id) and blockstate stacks of
	 * `manager` and instantiates blockstates for every known state
	 * (`ModelManager.loadBlockModels`, `BlockStateModelLoader`).
	 */
	public static NativeModels load(MultiPackResourceManager manager) {
		Definitions definitions = definitions();
		try (Arena arena = Arena.ofConfined()) {
			long handle = (long)LOAD.invokeExact(manager.nativeHandle(), input(arena, definitions.records()), (long)definitions.records().length);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected the blockstate definitions");
			}
			return new NativeModels(handle, definitions.states());
		} catch (Throwable throwable) {
			throw throwable instanceof RuntimeException runtime ? runtime : new IllegalStateException("Load models failed", throwable);
		} finally {
			Reference.reachabilityFence(manager);
		}
	}

	/** Every known state, in record order. */
	public List<BlockState> states() {
		return this.states;
	}

	/**
	 * Each state's model equality group (`ModelGroupCollector`'s
	 * `visualEqualityGroup`), or -1 for a state no blockstate file covers.
	 */
	public int[] groups() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			GROUPS.invokeExact(this.handle(), pointer, length);
			return take(pointer, length.get(ValueLayout.JAVA_LONG, 0), reader -> reader.ints((int)(reader.buffer.byteSize() / 4)));
		} catch (Throwable throwable) {
			throw new IllegalStateException("Model groups failed", throwable);
		}
	}

	/** An element rotation baking needs: axis ordinal and angle. */
	public record ElementRotation(int axis, float angle) {
	}

	/** Item roots and the rotations their bake needs (`ModelDiscovery`). */
	public record Discovered(List<ResourceLocation> itemRoots, List<ElementRotation> rotations) {
	}

	/**
	 * Discovers the models blockstates and `itemRoots` (model ids item
	 * definitions reference, in request order) reach.
	 */
	public Discovered discover(List<ResourceLocation> itemRoots) {
		Writer writer = new Writer();
		writer.u32(itemRoots.size());
		itemRoots.forEach(writer::location);
		byte[] bytes = writer.bytes();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)DISCOVER.invokeExact(this.handle(), input(arena, bytes), (long)bytes.length, pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Model discovery failed: " + status);
			}
			List<ElementRotation> rotations = take(pointer, length.get(ValueLayout.JAVA_LONG, 0), reader -> {
				int count = reader.u32();
				List<ElementRotation> list = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					list.add(new ElementRotation(reader.u32(), Float.intBitsToFloat(reader.u32())));
				}
				return list;
			});
			return new Discovered(List.copyOf(itemRoots), rotations);
		} catch (Throwable throwable) {
			throw throwable instanceof RuntimeException runtime ? runtime : new IllegalStateException("Model discovery failed", throwable);
		}
	}

	// -- baking ----------------------------------------------------------------

	/** The block atlas's sprites in record order (the missing sprite included). */
	private static List<TextureAtlasSprite> sprites(SpriteLoader.Preparations preparations, Writer writer) {
		List<TextureAtlasSprite> sprites = List.copyOf(preparations.regions().values());
		writer.u32(sprites.size());
		int missing = -1;
		for (int index = 0; index < sprites.size(); index++) {
			TextureAtlasSprite sprite = sprites.get(index);
			if (sprite == preparations.missing()) {
				missing = index;
			}
			SpriteContents contents = sprite.contents();
			writer.location(contents.name());
			writer.f32(sprite.getU0());
			writer.f32(sprite.getU1());
			writer.f32(sprite.getV0());
			writer.f32(sprite.getV1());
			writer.f32(sprite.uvShrinkRatio());
			writer.u32(contents.width());
			writer.u32(contents.height());
			writer.u32(contents.animatedTexture != null ? contents.animatedTexture.frameRowSize : 0);
			int[] frames = contents.getUniqueFrames().toArray();
			writer.u32(frames.length);
			for (int frame : frames) {
				writer.u32(frame);
			}
			NativeImage sheet = contents.originalImage;
			writer.u64(sheet.getPointer());
			writer.u32(sheet.getWidth());
			writer.u32(sheet.getHeight());
		}
		if (missing < 0) {
			throw new IllegalStateException("Block atlas has no missing sprite");
		}
		writer.u32(missing);
		return sprites;
	}

	/** `BlockModelRotation`s' JOML matrices and the element rotations' (`FaceBakery`). */
	private static void matrices(Writer writer, List<ElementRotation> rotations) {
		for (BlockModelRotation rotation : BlockModelRotation.values()) {
			Transformation transformation = rotation.transformation();
			writer.u8(transformation != Transformation.identity() ? 1 : 0);
			writer.matrix(transformation.getMatrix());
			ModelState uvLock = rotation.withUvLock();
			for (Direction direction : DIRECTIONS) {
				Matrix4fc inverse = uvLock.inverseFaceTransformation(direction);
				writer.matrix(inverse);
				writer.u8(MatrixUtil.isIdentity(inverse) ? 1 : 0);
			}
		}
		writer.u32(rotations.size());
		for (ElementRotation rotation : rotations) {
			Direction.Axis axis = Direction.Axis.values()[rotation.axis()];
			writer.u32(rotation.axis());
			writer.u32(Float.floatToRawIntBits(rotation.angle()));
			writer.matrix(new Matrix4f().rotation(rotation.angle() * (float)(Math.PI / 180.0), axis.getPositive().getUnitVec3f()));
			float angle = Math.abs(rotation.angle());
			writer.f32(1.0F / Mth.cos(angle * (float)(Math.PI / 180.0)));
		}
	}

	/** One model's top properties (`ModelRenderProperties` inputs). */
	private record Properties(QuadCollection geometry, boolean ambientOcclusion, boolean blockLight, TextureAtlasSprite particle, ItemTransforms transforms) {
	}

	/**
	 * The bake's Java objects: block state models (states whose bake failed
	 * are absent, as Java's were), the missing models, the baker item models
	 * use, and the missing sprites reported so far (item bakes add theirs).
	 */
	public record Baked(
		Map<BlockState, BlockStateModel> blockStateModels, ModelBakery.MissingModels missingModels, ModelBaker itemBaker, MissingSprites missingSprites
	) {
	}

	/** Missing sprites one bake step reported, by model debug name. */
	private record Reports(Map<String, List<Material>> textures, Map<String, List<String>> references) {
		static final Reports EMPTY = new Reports(Map.of(), Map.of());

		static Reports read(Reader reader) {
			int textureModels = reader.u32();
			Map<String, List<Material>> textures = textureModels == 0 ? Map.of() : new HashMap<>();
			for (int model = 0; model < textureModels; model++) {
				String name = reader.text();
				int count = reader.u32();
				List<Material> materials = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					materials.add(new Material(ResourceLocation.parse(reader.text()), ResourceLocation.parse(reader.text())));
				}
				textures.put(name, materials);
			}
			int referenceModels = reader.u32();
			Map<String, List<String>> references = referenceModels == 0 ? Map.of() : new HashMap<>();
			for (int model = 0; model < referenceModels; model++) {
				String name = reader.text();
				int count = reader.u32();
				List<String> names = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					names.add(reader.text());
				}
				references.put(name, names);
			}
			return textures.isEmpty() && references.isEmpty() ? EMPTY : new Reports(textures, references);
		}
	}

	/**
	 * `ModelManager`'s missing-sprite multimaps: what the block bake reported
	 * and what each item bake step adds when an item model takes it.
	 */
	public static final class MissingSprites {
		private final Multimap<String, Material> textures = Multimaps.synchronizedMultimap(HashMultimap.create());
		private final Multimap<String, String> references = Multimaps.synchronizedMultimap(HashMultimap.create());

		void add(Reports reports) {
			if (reports != Reports.EMPTY) {
				reports.textures().forEach(this.textures::putAll);
				reports.references().forEach(this.references::putAll);
			}
		}

		/** Logs as `ModelManager.loadModels` did once baking finished. */
		public void log() {
			this.textures.asMap().forEach((model, materials) -> LOGGER.warn("Missing textures in model {}:\n{}", model,
				materials.stream().sorted(Material.COMPARATOR).map(material -> "    " + material.atlasLocation() + ":" + material.texture()).collect(Collectors.joining("\n"))));
			this.references.asMap().forEach((model, names) -> LOGGER.warn("Missing texture references in model {}:\n{}", model,
				names.stream().sorted().map(name -> "    " + name).collect(Collectors.joining("\n"))));
		}
	}

	/**
	 * Bakes every geometry blockstates and item roots need against the
	 * block atlas `preparations`, and builds the model objects.
	 */
	public Baked bake(SpriteLoader.Preparations preparations, Discovered discovered) {
		Writer writer = new Writer();
		List<TextureAtlasSprite> sprites = sprites(preparations, writer);
		matrices(writer, discovered.rotations());
		byte[] bytes = writer.bytes();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)BAKE.invokeExact(this.handle(), input(arena, bytes), (long)bytes.length, pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Model bake failed: " + status);
			}
			return take(pointer, length.get(ValueLayout.JAVA_LONG, 0), reader -> this.build(reader, sprites, discovered));
		} catch (Throwable throwable) {
			throw throwable instanceof RuntimeException runtime ? runtime : new IllegalStateException("Model bake failed", throwable);
		} finally {
			// The sheets Rust read stay alive with their sprites.
			Reference.reachabilityFence(preparations);
		}
	}

	private static QuadCollection geometry(Reader reader, List<TextureAtlasSprite> sprites) {
		int[] counts = reader.ints(7);
		int total = 0;
		for (int count : counts) {
			total += count;
		}
		if (total == 0) {
			return QuadCollection.EMPTY;
		}
		ImmutableList.Builder<BakedQuad> builder = ImmutableList.builderWithExpectedSize(total);
		for (int index = 0; index < total; index++) {
			int[] vertices = reader.ints(32);
			int tint = reader.u32();
			Direction direction = DIRECTIONS[reader.u8()];
			TextureAtlasSprite sprite = sprites.get(reader.u32());
			boolean shade = reader.u8() != 0;
			int lightEmission = reader.u32();
			builder.add(new BakedQuad(vertices, tint, direction, sprite, shade, lightEmission));
		}
		List<BakedQuad> all = builder.build();
		if (total == counts[0]) {
			return new QuadCollection(all, all, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		}
		int at = 0;
		List<List<BakedQuad>> buckets = new ArrayList<>(7);
		for (int count : counts) {
			buckets.add(all.subList(at, at + count));
			at += count;
		}
		return new QuadCollection(all, buckets.get(0), buckets.get(1), buckets.get(2), buckets.get(3), buckets.get(4), buckets.get(5), buckets.get(6));
	}

	private static BlockStateModel node(Reader reader, List<SimpleModelWrapper> parts) {
		if (reader.u8() == 0) {
			return new SingleVariant(parts.get(reader.u32()));
		}
		int count = reader.u32();
		List<Weighted<BlockStateModel>> entries = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			BlockStateModel model = new SingleVariant(parts.get(reader.u32()));
			entries.add(new Weighted<>(model, reader.u32()));
		}
		return new WeightedVariants(WeightedList.of(entries));
	}

	private static Properties properties(Reader reader, QuadCollection[] geometries, List<TextureAtlasSprite> sprites, ItemTransform[] transforms) {
		QuadCollection geometry = geometries[reader.u32()];
		boolean ambientOcclusion = reader.u8() != 0;
		boolean blockLight = reader.u8() == 1;
		TextureAtlasSprite particle = sprites.get(reader.u32());
		ItemTransform[] slots = new ItemTransform[9];
		for (int context = 0; context < 9; context++) {
			slots[context] = switch (reader.u8()) {
				case 0 -> ItemTransform.NO_TRANSFORM;
				case 1 -> null;
				default -> transforms[reader.u32()];
			};
		}
		ItemTransforms itemTransforms = new ItemTransforms(slots[0], slots[1], slots[2], slots[3], slots[4], slots[5], slots[6], slots[7], slots[8]);
		return new Properties(geometry, ambientOcclusion, blockLight, particle, itemTransforms);
	}

	private record Failure(String message) {
	}

	private Baked build(Reader reader, List<TextureAtlasSprite> sprites, Discovered discovered) {
		QuadCollection[] geometries = new QuadCollection[reader.u32()];
		long[] offsets = new long[geometries.length];
		for (int index = 0; index < offsets.length; index++) {
			offsets[index] = reader.u64();
		}
		long section = reader.u64();
		long base = reader.at;
		// Geometries are independent records: build their quads in parallel.
		IntStream.range(0, geometries.length).parallel().forEach(index -> geometries[index] = geometry(reader.at(base + offsets[index]), sprites));
		reader.at = base + section;
		int partCount = reader.u32();
		List<SimpleModelWrapper> parts = new ArrayList<>(partCount);
		for (int index = 0; index < partCount; index++) {
			QuadCollection geometry = geometries[reader.u32()];
			boolean ambientOcclusion = reader.u8() != 0;
			parts.add(new SimpleModelWrapper(geometry, ambientOcclusion, sprites.get(reader.u32())));
		}
		// Each root bakes once: its states share the model (`SimpleCachedUnbakedRoot`, `SharedBakedState`).
		int rootCount = reader.u32();
		Object[] roots = new Object[rootCount];
		for (int index = 0; index < rootCount; index++) {
			switch (reader.u8()) {
				case 1 -> roots[index] = node(reader, parts);
				case 2 -> {
					int count = reader.u32();
					List<BlockStateModel> selectors = new ArrayList<>(count);
					for (int selector = 0; selector < count; selector++) {
						selectors.add(node(reader, parts));
					}
					roots[index] = MultiPartModel.shared(selectors);
				}
				case 3 -> roots[index] = new Failure(reader.text());
				default -> {
				}
			}
		}
		Map<BlockState, BlockStateModel> models = new IdentityHashMap<>();
		int stateCount = reader.u32();
		if (stateCount != this.states.size()) {
			throw new IllegalStateException("Baked " + stateCount + " states for " + this.states.size());
		}
		for (BlockState state : this.states) {
			int root = reader.u32();
			if (root < 0) {
				continue;
			}
			int count = reader.u32();
			BitSet selected = new BitSet();
			for (int selector = 0; selector < count; selector++) {
				selected.set(reader.u32());
			}
			switch (roots[root]) {
				case MultiPartModel.SharedBakedState shared -> models.put(state, new MultiPartModel(shared, selected));
				case BlockStateModel model -> models.put(state, model);
				case Failure failure -> LOGGER.warn("Unable to bake model: '{}': {}", state, failure.message());
				default -> throw new IllegalStateException("State " + state + " names an unbaked root");
			}
		}
		ItemTransform[] transforms = new ItemTransform[reader.u32()];
		for (int index = 0; index < transforms.length; index++) {
			Vector3f rotation = new Vector3f(reader.f32(), reader.f32(), reader.f32());
			Vector3f translation = new Vector3f(reader.f32(), reader.f32(), reader.f32());
			Vector3f scale = new Vector3f(reader.f32(), reader.f32(), reader.f32());
			transforms[index] = new ItemTransform(rotation, translation, scale);
		}
		Properties missing = properties(reader, geometries, sprites, transforms);
		ModelBakery.MissingModels missingModels = new ModelBakery.MissingModels(
			new SingleVariant(new SimpleModelWrapper(missing.geometry(), missing.ambientOcclusion(), missing.particle())),
			new MissingItemModel(missing.geometry().getAll(), new ModelRenderProperties(missing.blockLight(), missing.particle(), missing.transforms()))
		);
		MissingSprites missingSprites = new MissingSprites();
		NativeResolvedModel missingModel = new NativeResolvedModel("minecraft:builtin/missing", missing, null, Reports.EMPTY, Reports.EMPTY, missingSprites);
		Map<ResourceLocation, NativeResolvedModel> items = new HashMap<>();
		int itemCount = reader.u32();
		if (itemCount != discovered.itemRoots().size()) {
			throw new IllegalStateException("Baked " + itemCount + " item roots for " + discovered.itemRoots().size());
		}
		for (ResourceLocation id : discovered.itemRoots()) {
			if (reader.u8() == 0) {
				Properties properties = properties(reader, geometries, sprites, transforms);
				String failure = reader.u8() == 1 ? reader.text() : null;
				Reports geometryReports = Reports.read(reader);
				Reports particleReports = Reports.read(reader);
				items.putIfAbsent(id, new NativeResolvedModel(id.toString(), properties, failure, geometryReports, particleReports, missingSprites));
			}
		}
		missingSprites.add(Reports.read(reader));
		return new Baked(models, missingModels, new ItemBaker(items, missingModel), missingSprites);
	}

	/**
	 * A resolved model for item bakes (`ModelBakery.ModelBakerImpl.getModel`):
	 * properties Rust resolved and the X0_Y0 geometry it baked, or the bake
	 * failure item baking reports. Baking the geometry and resolving the
	 * particle add the missing sprites those steps reported, as Java's did.
	 */
	static final class NativeResolvedModel implements ResolvedModel {
		private final String debugName;
		private final Properties properties;
		@Nullable
		private final String failure;
		private final Reports geometryReports;
		private final Reports particleReports;
		private final MissingSprites missingSprites;

		NativeResolvedModel(
			String debugName, Properties properties, @Nullable String failure, Reports geometryReports, Reports particleReports, MissingSprites missingSprites
		) {
			this.debugName = debugName;
			this.properties = properties;
			this.failure = failure;
			this.geometryReports = geometryReports;
			this.particleReports = particleReports;
			this.missingSprites = missingSprites;
		}

		@Override
		public String debugName() {
			return this.debugName;
		}

		@Override
		public TextureSlots getTopTextureSlots() {
			return TextureSlots.EMPTY;
		}

		@Override
		public boolean getTopAmbientOcclusion() {
			return this.properties.ambientOcclusion();
		}

		@Override
		public UnbakedModel.GuiLight getTopGuiLight() {
			return this.properties.blockLight() ? UnbakedModel.GuiLight.SIDE : UnbakedModel.GuiLight.FRONT;
		}

		@Override
		public ItemTransforms getTopTransforms() {
			return this.properties.transforms();
		}

		@Override
		public TextureAtlasSprite resolveParticleSprite(TextureSlots textureSlots, ModelBaker modelBaker) {
			this.missingSprites.add(this.particleReports);
			return this.properties.particle();
		}

		@Override
		public QuadCollection bakeTopGeometry(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState) {
			this.missingSprites.add(this.geometryReports);
			if (this.failure != null) {
				throw new IllegalStateException(this.failure);
			}
			if (modelState != BlockModelRotation.X0_Y0) {
				throw new IllegalArgumentException("Item models bake only the default model state, not " + modelState);
			}
			return this.properties.geometry();
		}
	}

	/** `ModelBakery.ModelBakerImpl` for item bakes. */
	private static final class ItemBaker implements ModelBaker {
		private final Map<ResourceLocation, NativeResolvedModel> models;
		private final NativeResolvedModel missing;
		private final Map<ModelBaker.SharedOperationKey<Object>, Object> operations = new ConcurrentHashMap<>();

		ItemBaker(Map<ResourceLocation, NativeResolvedModel> models, NativeResolvedModel missing) {
			this.models = Map.copyOf(models);
			this.missing = missing;
		}

		@Override
		public ResolvedModel getModel(ResourceLocation resourceLocation) {
			NativeResolvedModel model = this.models.get(resourceLocation);
			if (model == null) {
				LOGGER.warn("Requested a model that was not discovered previously: {}", resourceLocation);
				return this.missing;
			}
			return model;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T compute(ModelBaker.SharedOperationKey<T> sharedOperationKey) {
			return (T)this.operations.computeIfAbsent((ModelBaker.SharedOperationKey<Object>)(ModelBaker.SharedOperationKey<?>)sharedOperationKey,
				key -> key.compute(this));
		}
	}
}
