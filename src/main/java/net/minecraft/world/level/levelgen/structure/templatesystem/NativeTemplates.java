package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.NativeDecoration;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendLoot;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendStatic;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.Clear;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.Passthrough;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifier;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Native {@code StructureTemplate.placeInWorld} for one palette under supported
 * processors ({@code decoration/template.rs}): Rust processes and writes the
 * blocks and waterlogging over the bound decoration region; Java keeps the NBT,
 * loading recorded block entities in order, shape updates and entities.
 * Unsupported processors, liquid containers or randoms keep Java's path.
 */
public final class NativeTemplates {
	private static final Logger LOGGER = net.logging.LogUtils.getLogger();
	/** A palette Java places, and why. */
	private record Unsupported(String reason) {
	}
	/** By (template, palette, processors): compiled natives or {@link Unsupported}. */
	private static final Map<List<Object>, Object> CACHE = new ConcurrentHashMap<>();

	/** The last native placement on this thread: (template, origin, compiled template object). */
	private static final ThreadLocal<Object[]> PLACED = new ThreadLocal<>();

	private NativeTemplates() {
	}

	/**
	 * The compiled template object of this thread's last native {@code placeInWorld}, if it
	 * placed {@code template} at {@code origin}; cleared either way.
	 */
	@Nullable
	public static MemorySegment takePlaced(StructureTemplate template, BlockPos origin) {
		Object[] placed = PLACED.get();
		PLACED.remove();
		return placed != null && placed[0] == template && origin.equals(placed[1]) ? (MemorySegment)placed[2] : null;
	}

	/** A compiled template palette with its processors and the Java tables their words index. */
	private static final class Compiled {
		MemorySegment template = MemorySegment.NULL, processors = MemorySegment.NULL;
		final List<CompoundTag> nbts = new ArrayList<>();
		final List<ResourceKey<LootTable>> lootTables = new ArrayList<>();
		final List<CompoundTag> statics = new ArrayList<>();
		/** A lava-submerged processor tests template states' shapes: dynamic shapes stay in Java. */
		boolean testsShapes;
	}

	/**
	 * Places the palette natively; false when Java's path must place it (nothing was written).
	 * Called by {@code placeInWorld} after its palette choice and emptiness checks.
	 */
	static boolean place(StructureTemplate template, StructureTemplate.Palette palette, List<StructureTemplate.StructureBlockInfo> blocks,
						 ServerLevelAccessor level, BlockPos origin, BlockPos pivot, StructurePlaceSettings settings, RandomSource random, int flags) {
		PLACED.remove();
		var region = NativeDecoration.activeRegion(level);
		// A settings random is supported when it is the placement random itself (fossils):
		// block rot then draws from that stream in processing order.
		boolean shared = settings.nativeHasRandom();
		if (region == null) return false;
		if (shared && !settings.nativeRandomIs(random)) return decline(level, "settings random");
		// Writes stay inside the settings box (or the whole extent without one), which must lie in
		// the bound 3x3; processors' reads beyond it go to Java's generation region.
		var writes = settings.getBoundingBox() != null ? settings.getBoundingBox() : template.getBoundingBox(settings, origin);
		if (!region.covers(writes.minX() >> 4, writes.minZ() >> 4, writes.maxX() >> 4, writes.maxZ() >> 4)) return decline(level, "extent");
		if (!(level instanceof net.minecraft.world.level.WorldGenLevel worldGen)) return decline(level, "level");
		// Loot seeds draw nextLong from the placement random: a WorldgenRandom over a Xoroshiro source
		// (a bare Xoroshiro source derives nextLong and nextInt differently).
		XoroshiroRandomSource source = random instanceof WorldgenRandom worldgen && worldgen.nativeSource() instanceof XoroshiroRandomSource x ? x : null;
		if (source == null) return decline(level, "random");
		List<Object> key = List.of(template, palette, List.copyOf(settings.getProcessors()));
		Object entry = CACHE.computeIfAbsent(key, k -> compile(blocks, settings.getProcessors()));
		if (!(entry instanceof Compiled compiled)) return decline(level, ((Unsupported)entry).reason());
		var generator = source.nativeGenerator();
		var box = settings.getBoundingBox();
		long[] args = {origin.getX(), origin.getY(), origin.getZ(), pivot.getX(), pivot.getY(), pivot.getZ(), settings.getMirror().ordinal(),
			settings.getRotation().ordinal(), box == null ? 0 : 1, box == null ? 0 : box.minX(), box == null ? 0 : box.minY(), box == null ? 0 : box.minZ(),
			box == null ? 0 : box.maxX(), box == null ? 0 : box.maxY(), box == null ? 0 : box.maxZ(), flags, settings.shouldApplyWaterlogging() ? 1 : 0,
			shared ? 1 : 0, level.getLevel().getSeed(), generator.nativeSeedLo(), generator.nativeSeedHi(), settings.getRotationPivot().getX(), settings.getRotationPivot().getY(),
			settings.getRotationPivot().getZ()};
		try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(LOGGER)) {
			long[] positions = NativeDecoration.placeTemplate(region, worldGen, compiled.template, compiled.processors, args,
				(pos, loads, at) -> loadBlockEntity(compiled, level, collector, pos, loads, at));
			if (positions == null) return decline(level, "write path");
			generator.nativeSetState(args[19], args[20]);
			PLACED.set(new Object[]{template, origin.immutable(), compiled.template});
			if (!settings.getKnownShape() && positions.length > 0) updateShapes(level, flags, positions);
			if (!settings.isIgnoreEntities()) {
				template.placeEntities(level, origin, settings.getMirror(), settings.getRotation(), settings.getRotationPivot(), box,
					settings.shouldFinalizeEntities(), collector);
			}
		}
		return true;
	}

	private static boolean decline(ServerLevelAccessor level, String reason) {
		NativeDecoration.javaTemplate(level, reason);
		return false;
	}

	/** The tail of {@code placeInWorld} for templates without a known shape. */
	private static void updateShapes(ServerLevelAccessor level, int flags, long[] positions) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < positions.length; i += 3) {
			minX = Math.min(minX, (int)positions[i]);
			minY = Math.min(minY, (int)positions[i + 1]);
			minZ = Math.min(minZ, (int)positions[i + 2]);
			maxX = Math.max(maxX, (int)positions[i]);
			maxY = Math.max(maxY, (int)positions[i + 1]);
			maxZ = Math.max(maxZ, (int)positions[i + 2]);
		}
		var shape = new BitSetDiscreteVoxelShape(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
		for (int i = 0; i < positions.length; i += 3) shape.fill((int)positions[i] - minX, (int)positions[i + 1] - minY, (int)positions[i + 2] - minZ);
		StructureTemplate.updateShapeAtEdge(level, flags, shape, minX, minY, minZ);
		for (int i = 0; i < positions.length; i += 3) {
			BlockPos pos = new BlockPos((int)positions[i], (int)positions[i + 1], (int)positions[i + 2]);
			BlockState state = level.getBlockState(pos);
			BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
			if (state != updated) level.setBlock(pos, updated, flags & -2 | 16);
			level.updateNeighborsAt(pos, updated.getBlock());
		}
	}

	/** A recorded block-entity load: the template's NBT, rule edits, then the loot seed. */
	private static void loadBlockEntity(Compiled compiled, ServerLevelAccessor level, ProblemReporter.ScopedCollector collector, BlockPos pos, long[] loads, int at) {
		int source = (int)loads[at + 3];
		CompoundTag nbt = source >= 0 ? compiled.nbts.get(source).copy() : null;
		int ops = (int)loads[at + 6];
		for (int op = 0; op < ops; op++) {
			long kind = loads[at + 7 + op * 3], argument = loads[at + 8 + op * 3], seed = loads[at + 9 + op * 3];
			if (kind == 1) {
				nbt = nbt == null ? new CompoundTag() : nbt.copy();
				nbt.store("LootTable", LootTable.KEY_CODEC, compiled.lootTables.get((int)argument));
				nbt.putLong("LootTableSeed", seed);
			} else if (kind == 2) {
				nbt = nbt == null ? compiled.statics.get((int)argument).copy() : nbt.merge(compiled.statics.get((int)argument));
			} else {
				nbt = new CompoundTag();
			}
		}
		var blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) throw new IllegalStateException("Native template expected a block entity at " + pos);
		boolean randomizable = loads[at + 4] != 0;
		if (randomizable != blockEntity instanceof RandomizableContainer) throw new IllegalStateException("Native template container facts differ at " + pos);
		if (randomizable) nbt.putLong("LootTableSeed", loads[at + 5]);
		blockEntity.loadWithComponents(TagValueInput.create(collector.forChild(blockEntity.problemPath()), level.registryAccess(), nbt));
	}

	@Nullable
	private static Object compile(List<StructureTemplate.StructureBlockInfo> blocks, List<StructureProcessor> processors) {
		long setupStart = System.nanoTime();
		try {
			return compileTimed(blocks, processors);
		} finally {
			net.minecraft.world.level.levelgen.NativeDecoration.setupTime("templates", setupStart);
		}
	}

	@Nullable
	private static Object compileTimed(List<StructureTemplate.StructureBlockInfo> blocks, List<StructureProcessor> processors) {
		try {
			var compiled = new Compiled();
			var states = new LinkedHashMap<BlockState, Integer>();
			// A processor list encodes (and scans the state registry) once, shared by every template it processes.
			Encoded encoded = PROCESSORS.computeIfAbsent(List.copyOf(processors), NativeTemplates::encode);
			if (encoded.failure() != null) return new Unsupported(encoded.failure());
			compiled.lootTables.addAll(encoded.lootTables());
			compiled.statics.addAll(encoded.statics());
			compiled.testsShapes = encoded.testsShapes();
			for (BlockState output : encoded.outputs()) states.put(output, 0);
			List<Integer> words = new ArrayList<>();
			words.add(blocks.size());
			for (var info : blocks) {
				words.addAll(List.of(info.pos().getX(), info.pos().getY(), info.pos().getZ(), stateId(info.state())));
				int nbt = -1;
				if (info.nbt() != null) {
					nbt = compiled.nbts.size();
					compiled.nbts.add(info.nbt());
				}
				words.add(nbt);
				states.put(info.state(), 0);
				if (info.state().is(Blocks.JIGSAW)) {
					// JigsawReplacementProcessor's final_state, parsed as Java parses it.
					if (info.nbt() == null) return new Unsupported("jigsaw without NBT");
					BlockState replacement;
					try {
						replacement = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, info.nbt().getStringOr("final_state", "minecraft:air"), true).blockState();
					} catch (Exception unparsable) {
						return new Unsupported("unparsable jigsaw final state");
					}
					if (replacement.is(Blocks.STRUCTURE_VOID)) {
						words.add(-1);
					} else {
						words.add(stateId(replacement));
						states.put(replacement, 0);
					}
				} else {
					words.add(-2);
				}
			}
			if (compiled.testsShapes) {
				for (BlockState state : states.keySet()) {
					if (state.getBlock().hasDynamicShape()) return new Unsupported("dynamic shape under lava submersion");
				}
			}
			// TemplateStructurePiece.postProcess's tail: DATA markers (an unreadable mode keeps
			// Java's loops) and every jigsaw's final state, in palette order.
			List<Integer> tail = new ArrayList<>();
			List<Integer> jigsaws = new ArrayList<>();
			int markers = 0;
			for (var info : blocks) {
				if (info.state().is(Blocks.STRUCTURE_BLOCK) && info.nbt() != null) {
					var mode = info.nbt().read("mode", net.minecraft.world.level.block.state.properties.StructureMode.LEGACY_CODEC);
					if (mode.isEmpty()) {
						markers = -1;
						break;
					}
					if (mode.get() == net.minecraft.world.level.block.state.properties.StructureMode.DATA) {
						markers++;
						int[] text = info.nbt().getStringOr("metadata", "").codePoints().toArray();
						tail.addAll(List.of(info.pos().getX(), info.pos().getY(), info.pos().getZ(), text.length));
						for (int c : text) tail.add(c);
					}
				} else if (info.state().is(Blocks.JIGSAW) && info.nbt() != null) {
					BlockState finalState;
					try {
						finalState = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, info.nbt().getStringOr("final_state", "minecraft:air"), true).blockState();
					} catch (Exception unparsable) {
						return new Unsupported("unparsable jigsaw final state");
					}
					jigsaws.addAll(List.of(info.pos().getX(), info.pos().getY(), info.pos().getZ(), stateId(finalState)));
					states.put(finalState, 0);
				}
			}
			// Facts for the closure of states under transforms and waterlogging.
			List<BlockState> pending = new ArrayList<>(states.keySet());
			for (int i = 0; i < pending.size(); i++) {
				BlockState state = pending.get(i);
				List<BlockState> next = new ArrayList<>();
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) next.add(state.mirror(mirror).rotate(rotation));
				}
				if (state.hasProperty(BlockStateProperties.WATERLOGGED)) next.add(liquidTarget(state));
				for (BlockState s : next) {
					if (states.putIfAbsent(s, 0) == null) pending.add(s);
				}
			}
			words.add(states.size());
			for (BlockState state : states.keySet()) {
				int flags = facts(state);
				if (flags < 0) return new Unsupported("liquid container " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
				words.add(stateId(state));
				words.add(flags);
				words.add(stateId(state.hasProperty(BlockStateProperties.WATERLOGGED) ? liquidTarget(state) : state));
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) words.add(stateId(state.mirror(mirror).rotate(rotation)));
				}
			}
			words.add(stateId(Blocks.BARRIER.defaultBlockState()));
			words.add(BuiltInRegistries.FLUID.getId(Fluids.WATER));
			words.add(markers);
			if (markers >= 0) {
				words.addAll(tail);
				words.add(jigsaws.size() / 4);
				words.addAll(jigsaws);
			}
			compiled.template = NativeDecoration.createTemplateObject(false, toArray(words), compiled);
			compiled.processors = encoded.segment();
			return compiled.template.address() == 0 || compiled.processors.address() == 0 ? new Unsupported("native rejected the words") : compiled;
		} catch (ClassCastException failure) {
			return new Unsupported("custom state or unreadable processor");
		}
	}

	// Pure per state (and per class below): shared by every template that holds the state.
	private static final java.util.concurrent.ConcurrentHashMap<BlockState, Integer> FACTS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.Optional<Class<?>>> LIQUID_OWNERS = new java.util.concurrent.ConcurrentHashMap<>();

	/** {@code template.rs}'s state flags, or -1 for a liquid container Rust cannot place into. */
	private static int facts(BlockState state) {
		return FACTS.computeIfAbsent(state, NativeTemplates::computeFacts);
	}

	private static int computeFacts(BlockState state) {
		int flags = 0;
		Block block = state.getBlock();
		if (state.hasBlockEntity() && block instanceof EntityBlock entityBlock) {
			var blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
			if (blockEntity != null) {
				flags |= 1;
				if (blockEntity instanceof RandomizableContainer) flags |= 2;
			}
		}
		var fluid = state.getFluidState();
		if (fluid.isSource()) flags |= 4;
		if (fluid.getType() == Fluids.WATER) flags |= 8;
		if (fluid.isEmpty()) flags |= 16;
		if (block instanceof LiquidBlockContainer) {
			Class<?> owner = placeLiquidOwner(block.getClass());
			boolean waterloggable = block instanceof SimpleWaterloggedBlock && state.hasProperty(BlockStateProperties.WATERLOGGED);
			if (waterloggable && owner == net.minecraft.world.level.block.SlabBlock.class
				&& state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) {
				// A double slab's placeLiquid does nothing.
				flags |= 128;
			} else if (waterloggable && (owner == null || owner == net.minecraft.world.level.block.SlabBlock.class
				|| owner == net.minecraft.world.level.block.DriedGhastBlock.class || owner == net.minecraft.world.level.block.CampfireBlock.class
				|| owner == net.minecraft.world.level.block.CandleBlock.class)) {
				// SimpleWaterloggedBlock.placeLiquid's write (the overrides' sounds, particles and
				// game events do nothing in a generation region): liquidTarget with flags 3, or 11
				// for a lit candle's extinguish.
				flags |= 32;
				if (state.getValue(BlockStateProperties.WATERLOGGED)) flags |= 64;
				if (owner == net.minecraft.world.level.block.CandleBlock.class && state.getValue(net.minecraft.world.level.block.CandleBlock.LIT)) flags |= 512;
			} else if (block instanceof net.minecraft.world.level.block.KelpBlock || block instanceof net.minecraft.world.level.block.KelpPlantBlock
				|| block instanceof net.minecraft.world.level.block.SeagrassBlock || block instanceof net.minecraft.world.level.block.TallSeagrassBlock) {
				// Their placeLiquid returns false and changes nothing.
				flags |= 128;
			} else return -1;
		}
		if (state.is(Blocks.JIGSAW)) flags |= 256;
		return flags;
	}

	/** The class declaring {@code placeLiquid} above {@link SimpleWaterloggedBlock}'s default, or null. */
	@Nullable
	private static Class<?> placeLiquidOwner(Class<?> type) {
		return LIQUID_OWNERS.computeIfAbsent(type, t -> java.util.Optional.ofNullable(findPlaceLiquidOwner(t))).orElse(null);
	}

	@Nullable
	private static Class<?> findPlaceLiquidOwner(Class<?> type) {
		for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
			try {
				at.getDeclaredMethod("placeLiquid", net.minecraft.world.level.LevelAccessor.class, BlockPos.class, BlockState.class, net.minecraft.world.level.material.FluidState.class);
				return at;
			} catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}

	/** The state {@code placeLiquid} writes for a waterloggable state: waterlogged, and unlit for campfires and candles. */
	private static BlockState liquidTarget(BlockState state) {
		BlockState target = state.setValue(BlockStateProperties.WATERLOGGED, true);
		Class<?> owner = placeLiquidOwner(state.getBlock().getClass());
		if (owner == net.minecraft.world.level.block.CampfireBlock.class) target = target.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false);
		if (owner == net.minecraft.world.level.block.CandleBlock.class) target = target.setValue(net.minecraft.world.level.block.CandleBlock.LIT, false);
		return target;
	}

	private static Object field(Object owner, Class<?> type, String name) throws ReflectiveOperationException {
		var field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static int stateId(BlockState state) {
		if (state.getClass() != BlockState.class) throw new ClassCastException("custom state");
		return Block.BLOCK_STATE_REGISTRY.getId(state);
	}

	private static int[] toArray(List<Integer> words) {
		return words.stream().mapToInt(Integer::intValue).toArray();
	}

	private static void set(List<Integer> words, java.util.function.Predicate<BlockState> test) {
		int count = Block.BLOCK_STATE_REGISTRY.size();
		int[] set = new int[(count + 31) >>> 5];
		for (int id = 0; id < count; id++) {
			if (test.test(Block.BLOCK_STATE_REGISTRY.byId(id))) set[id >>> 5] |= 1 << (id & 31);
		}
		words.add(set.length);
		for (int word : set) words.add(word);
	}

	/** A processor list's words as a native object, the tables they index and the states they write; or why it is unsupported. */
	private record Encoded(@Nullable String failure, MemorySegment segment, List<ResourceKey<LootTable>> lootTables, List<CompoundTag> statics,
						   List<BlockState> outputs, boolean testsShapes) {
	}

	/** By processor list (processors compare by identity). */
	private static final Map<List<StructureProcessor>, Encoded> PROCESSORS = new ConcurrentHashMap<>();

	private static Encoded encode(List<StructureProcessor> processors) {
		var scratch = new Compiled();
		var outputs = new LinkedHashMap<BlockState, Integer>();
		List<Integer> words = new ArrayList<>();
		String failure;
		try {
			failure = encodeProcessors(processors, words, scratch, outputs);
		} catch (ReflectiveOperationException | ClassCastException error) {
			failure = "unreadable processor";
		}
		if (failure != null) return new Encoded(failure, MemorySegment.NULL, List.of(), List.of(), List.of(), false);
		// The native object lives as long as the cache key (the entry's own list).
		MemorySegment segment = NativeDecoration.createTemplateObject(true, toArray(words), processors);
		if (segment.address() == 0) return new Encoded("native rejected the processors", MemorySegment.NULL, List.of(), List.of(), List.of(), false);
		return new Encoded(null, segment, List.copyOf(scratch.lootTables), List.copyOf(scratch.statics), List.copyOf(outputs.keySet()), scratch.testsShapes);
	}

	/** {@code template.rs}'s processor words; false declines the list. */
	@Nullable
	private static String encodeProcessors(List<StructureProcessor> processors, List<Integer> words, Compiled compiled, Map<BlockState, Integer> outputs)
		throws ReflectiveOperationException {
		List<Integer> body = new ArrayList<>();
		int count = 0;
		boolean[] ruleSeen = {false};
		for (StructureProcessor processor : processors) {
			if (processor.getClass() == NopProcessor.class) continue;
			count++;
			if (!encodeProcessor(processor, body, compiled, outputs, ruleSeen, 0)) return "processor " + processor.getClass().getSimpleName();
		}
		words.add(count);
		words.addAll(body);
		return null;
	}

	/** One processor's words (a capped processor's delegate at depth 1); false declines. */
	private static boolean encodeProcessor(StructureProcessor processor, List<Integer> body, Compiled compiled, Map<BlockState, Integer> outputs,
		boolean[] ruleSeen, int depth) throws ReflectiveOperationException {
		Class<?> type = processor.getClass();
		if (type == BlockIgnoreProcessor.class) {
			@SuppressWarnings("unchecked")
			var ignored = (List<Block>)field(processor, type, "toIgnore");
			body.add(1);
			set(body, s -> ignored.contains(s.getBlock()));
		} else if (type == JigsawReplacementProcessor.class) {
			// Jigsaw replacement reads the template's own NBT: rules must not edit it first.
			if (ruleSeen[0]) return false;
			body.add(2);
		} else if (type == RuleProcessor.class) {
			ruleSeen[0] = true;
			@SuppressWarnings("unchecked")
			var rules = (List<ProcessorRule>)field(processor, type, "rules");
			body.addAll(List.of(3, rules.size()));
			for (ProcessorRule rule : rules) {
				if (!ruleTest((RuleTest)field(rule, ProcessorRule.class, "inputPredicate"), body)
					|| !ruleTest((RuleTest)field(rule, ProcessorRule.class, "locPredicate"), body)
					|| !posTest((PosRuleTest)field(rule, ProcessorRule.class, "posPredicate"), body)) return false;
				BlockState output = (BlockState)field(rule, ProcessorRule.class, "outputState");
				body.add(stateId(output));
				outputs.put(output, 0);
				var modifier = (RuleBlockEntityModifier)field(rule, ProcessorRule.class, "blockEntityModifier");
				if (modifier.getClass() == Passthrough.class) {
					body.addAll(List.of(0, 0));
				} else if (modifier.getClass() == AppendLoot.class) {
					@SuppressWarnings("unchecked")
					var table = (ResourceKey<LootTable>)field(modifier, AppendLoot.class, "lootTable");
					body.addAll(List.of(1, compiled.lootTables.size()));
					compiled.lootTables.add(table);
				} else if (modifier.getClass() == AppendStatic.class) {
					body.addAll(List.of(2, compiled.statics.size()));
					compiled.statics.add((CompoundTag)field(modifier, AppendStatic.class, "tag"));
				} else if (modifier.getClass() == Clear.class) {
					body.addAll(List.of(3, 0));
				} else return false;
			}
		} else if (type == BlockRotProcessor.class) {
			@SuppressWarnings("unchecked")
			var rottable = (java.util.Optional<HolderSet<Block>>)field(processor, type, "rottableBlocks");
			body.addAll(List.of(4, Float.floatToRawIntBits((float)field(processor, type, "integrity")), rottable.isPresent() ? 1 : 0));
			if (rottable.isPresent()) set(body, s -> s.is(rottable.get()));
		} else if (type == GravityProcessor.class) {
			var heightmap = (net.minecraft.world.level.levelgen.Heightmap.Types)field(processor, type, "heightmap");
			body.addAll(List.of(5, heightmap.ordinal(), (int)field(processor, type, "offset")));
		} else if (type == ProtectedBlockProcessor.class) {
			TagKey<Block> tag = ((ProtectedBlockProcessor)processor).cannotReplace;
			body.add(6);
			set(body, s -> !s.is(tag));
		} else if (type == BlackstoneReplaceProcessor.class) {
			int[] map = BLACKSTONE.get();
			body.add(7);
			body.add(map.length / 2);
			for (int i = 0; i < map.length; i += 2) {
				body.add(map[i]);
				body.add(map[i + 1]);
				outputs.put(Block.BLOCK_STATE_REGISTRY.byId(map[i + 1]), 0);
			}
		} else if (type == LavaSubmergedBlockProcessor.class) {
			compiled.testsShapes = true;
			body.add(8);
			set(body, s -> s.is(Blocks.LAVA));
			set(body, s -> !s.getBlock().hasDynamicShape()
				&& !Block.isShapeFullBlock(s.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
			body.add(stateId(Blocks.LAVA.defaultBlockState()));
			outputs.put(Blocks.LAVA.defaultBlockState(), 0);
		} else if (type == BlockAgeProcessor.class) {
			body.addAll(List.of(9, Float.floatToRawIntBits((float)field(processor, type, "mossiness"))));
			int[] table = BLOCK_AGE.get();
			body.add(table.length / 3);
			for (int i = 0; i < table.length; i += 3) {
				body.addAll(List.of(table[i], table[i + 1], table[i + 2]));
				if (table[i + 2] >= 0) outputs.put(Block.BLOCK_STATE_REGISTRY.byId(table[i + 2]), 0);
			}
			List<BlockState> fixed = new ArrayList<>(List.of(Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				Blocks.STONE_SLAB.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState(),
				Blocks.CRYING_OBSIDIAN.defaultBlockState()));
			for (Block stairs : List.of(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS)) {
				// BlockAgeProcessor.getRandomFacingStairs: Plane.HORIZONTAL's facing order, then Half.values().
				for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
					for (Half half : Half.values()) {
						fixed.add(stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half));
					}
				}
			}
			for (BlockState state : fixed) {
				body.add(stateId(state));
				outputs.put(state, 0);
			}
		} else if (type == CappedProcessor.class && depth == 0) {
			var limit = (net.minecraft.util.valueproviders.IntProvider)field(processor, type, "limit");
			if (limit.getClass() == net.minecraft.util.valueproviders.ConstantInt.class) {
				body.addAll(List.of(10, 0, limit.getMaxValue(), 0));
			} else if (limit.getClass() == net.minecraft.util.valueproviders.UniformInt.class) {
				body.addAll(List.of(10, 1, limit.getMinValue(), limit.getMaxValue()));
			} else return false;
			var delegate = (StructureProcessor)field(processor, type, "delegate");
			if (delegate.getClass() == NopProcessor.class || !encodeProcessor(delegate, body, compiled, outputs, new boolean[]{true}, depth + 1)) return false;
		} else return false;
		return true;
	}

	/** {@code BlackstoneReplaceProcessor}'s replacement per state, as (state, replacement) pairs. */
	private static final com.google.common.base.Supplier<int[]> BLACKSTONE = com.google.common.base.Suppliers.memoize(() -> {
		List<Integer> pairs = new ArrayList<>();
		for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
			var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state, null);
			BlockState replaced = BlackstoneReplaceProcessor.INSTANCE.processBlock(null, BlockPos.ZERO, BlockPos.ZERO, info, info, null).state();
			if (replaced != state) {
				pairs.add(Block.BLOCK_STATE_REGISTRY.getId(state));
				pairs.add(Block.BLOCK_STATE_REGISTRY.getId(replaced));
			}
		}
		return toArray(pairs);
	});

	/**
	 * {@code BlockAgeProcessor}'s categories per state, as (state, category, mossy counterpart or -1):
	 * 1 stone bricks, stone or chiseled stone bricks; 2 stairs; 3 slabs; 4 walls; 5 obsidian.
	 */
	private static final com.google.common.base.Supplier<int[]> BLOCK_AGE = com.google.common.base.Suppliers.memoize(() -> {
		List<Integer> table = new ArrayList<>();
		for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
			int category;
			BlockState mapped = null;
			if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.STONE) || state.is(Blocks.CHISELED_STONE_BRICKS)) {
				category = 1;
			} else if (state.is(net.minecraft.tags.BlockTags.STAIRS)) {
				category = 2;
				mapped = Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(state);
			} else if (state.is(net.minecraft.tags.BlockTags.SLABS)) {
				category = 3;
				mapped = Blocks.MOSSY_STONE_BRICK_SLAB.withPropertiesOf(state);
			} else if (state.is(net.minecraft.tags.BlockTags.WALLS)) {
				category = 4;
				mapped = Blocks.MOSSY_STONE_BRICK_WALL.withPropertiesOf(state);
			} else if (state.is(Blocks.OBSIDIAN)) {
				category = 5;
			} else continue;
			table.addAll(List.of(Block.BLOCK_STATE_REGISTRY.getId(state), category, mapped == null ? -1 : Block.BLOCK_STATE_REGISTRY.getId(mapped)));
		}
		return toArray(table);
	});

	private static boolean ruleTest(RuleTest test, List<Integer> words) throws ReflectiveOperationException {
		Class<?> type = test.getClass();
		if (type == AlwaysTrueTest.class) {
			words.add(0);
		} else if (type == BlockMatchTest.class || type == TagMatchTest.class || type == BlockStateMatchTest.class) {
			words.add(1);
			set(words, s -> test.test(s, null));
		} else if (type == RandomBlockMatchTest.class) {
			Block block = (Block)field(test, type, "block");
			words.addAll(List.of(2, Float.floatToRawIntBits((float)field(test, type, "probability"))));
			set(words, s -> s.is(block));
		} else if (type == RandomBlockStateMatchTest.class) {
			BlockState match = (BlockState)field(test, type, "blockState");
			words.addAll(List.of(2, Float.floatToRawIntBits((float)field(test, type, "probability"))));
			set(words, s -> s == match);
		} else return false;
		return true;
	}

	private static boolean posTest(PosRuleTest test, List<Integer> words) throws ReflectiveOperationException {
		Class<?> type = test.getClass();
		if (type == PosAlwaysTrueTest.class) {
			words.add(0);
		} else if (type == AxisAlignedLinearPosTest.class || type == LinearPosTest.class) {
			boolean axis = type == AxisAlignedLinearPosTest.class;
			words.addAll(List.of(axis ? 1 : 2, axis ? ((net.minecraft.core.Direction.Axis)field(test, type, "axis")).ordinal() : 0,
				Float.floatToRawIntBits((float)field(test, type, "minChance")), Float.floatToRawIntBits((float)field(test, type, "maxChance")),
				(int)field(test, type, "minDist"), (int)field(test, type, "maxDist")));
		} else return false;
		return true;
	}
}
