package net.minecraft.world.level.levelgen.structure;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NativeDecoration;
import net.minecraft.world.level.levelgen.NativeDecorationEncoder;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * Structure pieces placed by Rust ({@code levelgen/structure/}). A piece that
 * supports native placement encodes its fields as words; Rust places it over
 * the bound decoration region with the placement random and hands back the
 * fields it changed. Entities and block-entity contents run as Java steps from
 * a fixed table: chest, dispenser and brushable loot per built-in table, then
 * the entity steps below.
 */
public final class NativeStructurePieces {
	/** A piece whose {@code postProcess} Rust can run. */
	public interface Native {
		/** {@code [kind, box (6), orientation, gen depth, fields…]}, or null to keep Java's path. */
		long @Nullable [] nativeWords();

		/** The fields after placement: box, then the piece's own. */
		void nativeUpdate(long[] words);
	}

	private static final boolean ENABLED = !Boolean.getBoolean("mattmc.worldgen.javaStructurePieces");
	private static final MethodHandle INIT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_init",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final int SETS = 20;
	/** Biome tags Rust asks about at a position ({@code OP_BIOME_TAG}), by index. */
	public static final List<net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>> BIOME_TAGS = List.of(
		net.minecraft.tags.BiomeTags.MINESHAFT_BLOCKING);
	private static final MethodHandle PLACE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_piece_place",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle AFTER_PLACE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_after_place",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle TEMPLATE_TAIL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_template_tail",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final int PLACED = 1, JAVA_ROUTE = -4, UNAVAILABLE_CHUNK = -2;
	private static final java.util.concurrent.atomic.LongAdder NATIVE = new java.util.concurrent.atomic.LongAdder();

	private static volatile boolean initialized;
	private static volatile List<NativeDecorationEncoder.JavaStep> steps = List.of();

	private NativeStructurePieces() {
	}

	public static long placed() {
		return NATIVE.sum();
	}

	/** Exports the global tables once, after the block and loot registries exist. */
	/** Exports the shared tables now (decoration setup calls this once). */
	public static void prepare() {
		if (ENABLED) init();
	}

	private static synchronized boolean init() {
		if (initialized) return !steps.isEmpty();
		long setupStart = System.nanoTime();
		try {
			return initLocked();
		} finally {
			net.minecraft.world.level.levelgen.NativeDecoration.setupTime("pieces", setupStart);
		}
	}

	private static boolean initLocked() {
		initialized = true;
		try (var arena = Arena.ofConfined()) {
			int count = Block.BLOCK_STATE_REGISTRY.size();
			int words = (count + 63) >>> 6;
			var transforms = arena.allocate(ValueLayout.JAVA_INT, count * 4L);
			var fluids = arena.allocate(ValueLayout.JAVA_INT, count);
			var sets = arena.allocate(ValueLayout.JAVA_LONG, words * (long)SETS);
			var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
			var origin = BlockPos.ZERO;
			for (int id = 0; id < count; id++) {
				BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
				BlockState[] oriented = {state, state.mirror(Mirror.LEFT_RIGHT), state.mirror(Mirror.LEFT_RIGHT).rotate(Rotation.CLOCKWISE_90),
					state.rotate(Rotation.CLOCKWISE_90)};
				for (int t = 0; t < 4; t++) transforms.setAtIndex(ValueLayout.JAVA_INT, id * 4L + t, Block.BLOCK_STATE_REGISTRY.getId(oriented[t]));
				var fluid = state.getFluidState();
				fluids.setAtIndex(ValueLayout.JAVA_INT, id, fluid.isEmpty() ? -1 : net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(fluid.getType()));
				boolean dynamic = state.getBlock().hasDynamicShape();
				boolean[] bits = new boolean[SETS];
				bits[0] = state.isSolidRender();
				bits[1] = state.isAir() || state.liquid() || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
				bits[2] = SHAPE_CHECK.contains(state.getBlock());
				bits[3] = state.liquid();
				for (Direction direction : Direction.values()) bits[4 + direction.ordinal()] = !dynamic && state.isFaceSturdy(empty, origin, direction);
				// Block.canSupportCenter(level, pos, DOWN), whose shape test is world-independent without a dynamic shape.
				bits[10] = !dynamic && !state.is(net.minecraft.tags.BlockTags.UNSTABLE_BOTTOM_CENTER)
					&& state.isFaceSturdy(empty, origin, Direction.DOWN, net.minecraft.world.level.block.SupportType.CENTER);
				bits[11] = state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock;
				bits[12] = dynamic;
				for (Direction direction : Direction.values()) {
					bits[13 + direction.ordinal()] = !dynamic && Block.isFaceFull(state.getCollisionShape(empty, origin), direction);
				}
				bits[19] = state.is(net.minecraft.tags.BlockTags.FEATURES_CANNOT_REPLACE);
				for (int s = 0; s < SETS; s++) {
					if (bits[s]) {
						long at = (long)s * words + (id >>> 6);
						sets.setAtIndex(ValueLayout.JAVA_LONG, at, sets.getAtIndex(ValueLayout.JAVA_LONG, at) | 1L << (id & 63));
					}
				}
			}
			List<ResourceKey<LootTable>> loot = new ArrayList<>(BuiltInLootTables.all());
			loot.sort(Comparator.comparing(key -> key.location().toString()));
			byte[] names = String.join("\n", loot.stream().map(key -> key.location().toString()).toList()).getBytes(StandardCharsets.UTF_8);
			var nameSegment = arena.allocate(Math.max(1, names.length));
			MemorySegment.copy(names, 0, nameSegment, ValueLayout.JAVA_BYTE, 0, names.length);
			if ((int)INIT.invokeExact(transforms, fluids, count, sets, SETS, nameSegment, names.length) != 1) return false;
			List<NativeDecorationEncoder.JavaStep> table = new ArrayList<>();
			for (var key : loot) table.add((level, random, pos) -> {
				if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.setLootTable(key, random.nextLong());
			});
			for (var key : loot) table.add((level, random, pos) -> {
				if (level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser) dispenser.setLootTable(key, random.nextLong());
			});
			for (var key : loot) table.add((level, random, pos) ->
				level.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK).ifPresent(brushable -> brushable.setLootTable(key, pos.asLong())));
			for (var key : loot) table.add((level, random, pos) -> net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, pos, key));
			table.add(NativeStructurePieces::spawnWitch);
			table.add(NativeStructurePieces::spawnCat);
			var mineshaft = BuiltInLootTables.ABANDONED_MINESHAFT;
			table.add((level, random, pos) -> {
				// MineShaftCorridor.createChest's minecart.
				var minecart = EntityType.CHEST_MINECART.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
				if (minecart != null) {
					minecart.setInitialPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
					minecart.setLootTable(mineshaft, random.nextLong());
					level.addFreshEntity(minecart);
				}
			});
			table.add((level, random, pos) -> {
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) spawner.setEntityId(EntityType.CAVE_SPIDER, random);
			});
			table.add((level, random, pos) -> {
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) spawner.setEntityId(EntityType.SILVERFISH, random);
			});
			table.add((level, random, pos) -> {
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) spawner.setEntityId(EntityType.BLAZE, random);
			});
			table.add((level, random, pos) -> {
				// OceanMonumentPiece.spawnElder.
				var elder = EntityType.ELDER_GUARDIAN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (elder != null) {
					elder.heal(elder.getMaxHealth());
					elder.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
					elder.finalizeSpawn(level, level.getCurrentDifficultyAt(elder.blockPosition()), EntitySpawnReason.STRUCTURE, null);
					level.addFreshEntityWithPassengers(elder);
				}
			});
			table.add((level, random, pos) -> {
				// EndCityPiece's sentry.
				var shulker = EntityType.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (shulker != null) {
					shulker.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
					level.addFreshEntity(shulker);
				}
			});
			for (Rotation rotation : Rotation.values()) table.add((level, random, pos) -> {
				// EndCityPiece's elytra item frame.
				var frame = new net.minecraft.world.entity.decoration.ItemFrame(level.getLevel(), pos, rotation.rotate(Direction.SOUTH));
				frame.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA), false);
				level.addFreshEntity(frame);
			});
			table.add((level, random, pos) -> {
				// OceanRuinPiece's drowned; Rust then writes air or water there.
				var drowned = EntityType.DROWNED.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (drowned != null) {
					drowned.setPersistenceRequired();
					drowned.snapTo(pos, 0.0F, 0.0F);
					drowned.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
					level.addFreshEntityWithPassengers(drowned);
				}
			});
			table.add((level, random, pos) -> spawnMansionMobs(level, pos, List.of(EntityType.EVOKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE))));
			table.add((level, random, pos) -> spawnMansionMobs(level, pos, List.of(EntityType.VINDICATOR.create(level.getLevel(), EntitySpawnReason.STRUCTURE))));
			table.add((level, random, pos) -> {
				List<net.minecraft.world.entity.Mob> allays = new ArrayList<>();
				int n = level.getRandom().nextInt(3) + 1;
				for (int i = 0; i < n; i++) allays.add(EntityType.ALLAY.create(level.getLevel(), EntitySpawnReason.STRUCTURE));
				spawnMansionMobs(level, pos, allays);
			});
			steps = List.copyOf(table);
			return true;
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Cannot export structure tables", failure);
		}
	}

	/** {@code StructurePiece.SHAPE_CHECK_BLOCKS}. */
	private static final java.util.Set<Block> SHAPE_CHECK = java.util.Set.of(Blocks.NETHER_BRICK_FENCE, Blocks.TORCH, Blocks.WALL_TORCH,
		Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.DARK_OAK_FENCE, Blocks.PALE_OAK_FENCE, Blocks.ACACIA_FENCE, Blocks.BIRCH_FENCE,
		Blocks.JUNGLE_FENCE, Blocks.LADDER, Blocks.IRON_BARS);

	private static void spawnWitch(WorldGenLevel level, RandomSource random, BlockPos pos) {
		var witch = EntityType.WITCH.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (witch != null) {
			witch.setPersistenceRequired();
			witch.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
			witch.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
			level.addFreshEntityWithPassengers(witch);
		}
	}

	/** WoodlandMansionPiece's mobs at a data marker; Rust then writes air there. */
	private static void spawnMansionMobs(WorldGenLevel level, BlockPos pos, List<? extends net.minecraft.world.entity.Mob> mobs) {
		for (var mob : mobs) {
			if (mob != null) {
				mob.setPersistenceRequired();
				mob.snapTo(pos, 0.0F, 0.0F);
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.STRUCTURE, null);
				level.addFreshEntityWithPassengers(mob);
			}
		}
	}

	private static void spawnCat(WorldGenLevel level, RandomSource random, BlockPos pos) {
		var cat = EntityType.CAT.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (cat != null) {
			cat.setPersistenceRequired();
			cat.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
			cat.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
			level.addFreshEntityWithPassengers(cat);
		}
	}

	/**
	 * Runs {@code structure.afterPlace} natively, or recognises Java's no-op default;
	 * false when Java's must run.
	 */
	public static boolean afterPlace(Structure structure, WorldGenLevel level, RandomSource random, BoundingBox box, ChunkPos chunkPos, PiecesContainer pieces) {
		if (!ENABLED) return false;
		long[] words;
		if (structure.getClass() == net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure.class) {
			var list = new ArrayList<Long>();
			int count = 0;
			for (StructurePiece piece : pieces.pieces()) {
				if (piece instanceof net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece pyramid) {
					count++;
					long[] header = header(pyramid, 0, 0);
					for (int i = 1; i <= 7; i++) list.add(header[i]);
					var roof = pyramid.getRandomCollapsedRoofPos();
					list.add(pyramid.nativeCellarPlaced() || !pyramid.getPotentialSuspiciousSandWorldPositions().isEmpty() ? 1L : 0L);
					list.addAll(List.of((long)roof.getX(), (long)roof.getY(), (long)roof.getZ()));
				}
			}
			words = new long[2 + list.size()];
			words[0] = 1;
			words[1] = count;
			for (int i = 0; i < list.size(); i++) words[2 + i] = list.get(i);
		} else if (structure.getClass() == net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure.class) {
			var all = pieces.pieces();
			words = new long[2 + all.size() * 6];
			words[0] = 2;
			words[1] = all.size();
			for (int i = 0; i < all.size(); i++) {
				var b = all.get(i).getBoundingBox();
				long[] w = {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
				System.arraycopy(w, 0, words, 2 + i * 6, 6);
			}
		} else {
			try {
				// Structure's afterPlace does nothing: nothing to run.
				return structure.getClass().getMethod("afterPlace", WorldGenLevel.class, net.minecraft.world.level.StructureManager.class,
					net.minecraft.world.level.chunk.ChunkGenerator.class, RandomSource.class, BoundingBox.class, ChunkPos.class, PiecesContainer.class)
					.getDeclaringClass() == Structure.class;
			} catch (NoSuchMethodException e) {
				return false;
			}
		}
		var region = NativeDecoration.activeRegion(level);
		if (region == null || !(random instanceof WorldgenRandom worldgen) || !(worldgen.nativeSource() instanceof XoroshiroRandomSource source)) return false;
		if (!init()) return false;
		var generator = source.nativeGenerator();
		var center = pieces.calculateBoundingBox().getCenter();
		long[] args = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), center.getX(), center.getY(), center.getZ(), level.getSeed(),
			generator.nativeSeedLo(), generator.nativeSeedHi(), 0, 0};
		int status = NativeDecoration.placeAfter(region, level, worldgen, steps, AFTER_PLACE, words, args);
		if (status == JAVA_ROUTE) return false;
		generator.nativeSetState(args[10], args[11]);
		return true;
	}

	/** The common header of a piece's words. */
	public static long[] header(StructurePiece piece, int kind, int fields) {
		long[] words = new long[9 + fields];
		var box = piece.getBoundingBox();
		Direction orientation = piece.getOrientation();
		words[0] = kind;
		words[1] = box.minX();
		words[2] = box.minY();
		words[3] = box.minZ();
		words[4] = box.maxX();
		words[5] = box.maxY();
		words[6] = box.maxZ();
		words[7] = orientation == null ? -1 : orientation.get2DDataValue();
		words[8] = piece.getGenDepth();
		return words;
	}

	/** The box Rust handed back. */
	public static BoundingBox box(long[] words) {
		return new BoundingBox((int)words[1], (int)words[2], (int)words[3], (int)words[4], (int)words[5], (int)words[6]);
	}

	/**
	 * Runs {@code piece.postProcess} natively; false when Java's path must run it
	 * (nothing was written).
	 */
	public static boolean place(StructurePiece piece, WorldGenLevel level, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
		if (!ENABLED || !(piece instanceof Native nativePiece)) return false;
		var region = NativeDecoration.activeRegion(level);
		if (region == null || !(random instanceof WorldgenRandom worldgen) || !(worldgen.nativeSource() instanceof XoroshiroRandomSource source)) return false;
		if (!init()) return false;
		long[] words = nativePiece.nativeWords();
		if (words == null) return false;
		var generator = source.nativeGenerator();
		// WorldGenRegion.getRandom(), which some pieces draw from besides the placement random.
		var regionGenerator = level.getRandom() instanceof XoroshiroRandomSource regionSource ? regionSource.nativeGenerator() : null;
		long[] args = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), chunkPos.x, chunkPos.z, pivot.getX(), pivot.getY(),
			pivot.getZ(), generator.nativeSeedLo(), generator.nativeSeedHi(), 0, 0, regionGenerator == null ? 0 : 1,
			regionGenerator == null ? 0 : regionGenerator.nativeSeedLo(), regionGenerator == null ? 0 : regionGenerator.nativeSeedHi(), level.getSeed(), level.getSeaLevel()};
		int status = NativeDecoration.placePiece(region, level, worldgen, steps, PLACE, words, args);
		if (status == JAVA_ROUTE) return false;
		generator.nativeSetState(args[11], args[12]);
		if (regionGenerator != null && args[15] != 0) regionGenerator.nativeSetState(args[16], args[17]);
		nativePiece.nativeUpdate(words);
		NATIVE.increment();
		return true;
	}

	/**
	 * {@code TemplateStructurePiece.postProcess} after a native {@code placeInWorld}: the
	 * piece family's data markers, the jigsaw blocks' final states and the ruined portal's
	 * decoration, in Rust. False when Java's loops must run (nothing was written).
	 */
	public static boolean templateTail(TemplateStructurePiece piece, WorldGenLevel level, RandomSource random, BoundingBox box, long[] words) {
		if (!ENABLED) return false;
		var placed = net.minecraft.world.level.levelgen.structure.templatesystem.NativeTemplates.takePlaced(piece.template(), piece.templatePosition());
		if (placed == null) return false;
		var region = NativeDecoration.activeRegion(level);
		if (region == null || !(random instanceof WorldgenRandom worldgen) || !(worldgen.nativeSource() instanceof XoroshiroRandomSource source)) return false;
		if (!init()) return false;
		var generator = source.nativeGenerator();
		var settings = piece.placeSettings();
		var origin = piece.templatePosition();
		var pivot = settings.getRotationPivot();
		long[] args = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), origin.getX(), origin.getY(), origin.getZ(),
			settings.getMirror().ordinal(), settings.getRotation().ordinal(), pivot.getX(), pivot.getY(), pivot.getZ(), generator.nativeSeedLo(),
			generator.nativeSeedHi(), 0, 0, level.getSeed(), level.getSeaLevel(), net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(
			net.minecraft.world.level.material.Fluids.WATER), net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(net.minecraft.world.level.material.Fluids.FLOWING_WATER)};
		int status = NativeDecoration.placeTail(region, level, worldgen, steps, TEMPLATE_TAIL, placed, words, args);
		if (status == JAVA_ROUTE) return false;
		generator.nativeSetState(args[14], args[15]);
		return true;
	}
}
