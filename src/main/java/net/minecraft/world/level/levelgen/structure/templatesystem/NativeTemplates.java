package net.minecraft.world.level.levelgen.structure.templatesystem;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
final class NativeTemplates {
	private static final Logger LOGGER = net.logging.LogUtils.getLogger();
	private static final Object UNSUPPORTED = new Object();
	/** By (template, palette, processors): compiled natives or {@link #UNSUPPORTED}. */
	private static final Map<List<Object>, Object> CACHE = new ConcurrentHashMap<>();

	private NativeTemplates() {
	}

	/** A compiled template palette with its processors and the Java tables their words index. */
	private static final class Compiled {
		MemorySegment template = MemorySegment.NULL, processors = MemorySegment.NULL;
		final List<CompoundTag> nbts = new ArrayList<>();
		final List<ResourceKey<LootTable>> lootTables = new ArrayList<>();
		final List<CompoundTag> statics = new ArrayList<>();
	}

	/**
	 * Places the palette natively; false when Java's path must place it (nothing was written).
	 * Called by {@code placeInWorld} after its palette choice and emptiness checks.
	 */
	static boolean place(StructureTemplate template, StructureTemplate.Palette palette, List<StructureTemplate.StructureBlockInfo> blocks,
						 ServerLevelAccessor level, BlockPos origin, BlockPos pivot, StructurePlaceSettings settings, RandomSource random, int flags) {
		var region = NativeDecoration.activeRegion(level);
		// A settings random is supported when it is the placement random itself (fossils):
		// block rot then draws from that stream in processing order.
		boolean shared = settings.nativeHasRandom();
		if (region == null || shared && !settings.nativeRandomIs(random)) return false;
		// Processors read at every template position before the bounding box applies, and
		// Java's generation region serves chunks beyond the bound 3x3: those stay in Java.
		var extent = template.getBoundingBox(settings, origin);
		if (!region.covers(extent.minX() >> 4, extent.minZ() >> 4, extent.maxX() >> 4, extent.maxZ() >> 4)) return false;
		// Loot seeds draw nextLong from the placement random: a WorldgenRandom over a Xoroshiro source
		// (a bare Xoroshiro source derives nextLong and nextInt differently).
		XoroshiroRandomSource source = random instanceof WorldgenRandom worldgen && worldgen.nativeSource() instanceof XoroshiroRandomSource x ? x : null;
		if (source == null) return false;
		List<Object> key = List.of(template, palette, List.copyOf(settings.getProcessors()));
		Object entry = CACHE.computeIfAbsent(key, k -> compile(blocks, settings.getProcessors()));
		if (!(entry instanceof Compiled compiled)) return false;
		var generator = source.nativeGenerator();
		var box = settings.getBoundingBox();
		long[] args = {origin.getX(), origin.getY(), origin.getZ(), pivot.getX(), pivot.getY(), pivot.getZ(), settings.getMirror().ordinal(),
			settings.getRotation().ordinal(), box == null ? 0 : 1, box == null ? 0 : box.minX(), box == null ? 0 : box.minY(), box == null ? 0 : box.minZ(),
			box == null ? 0 : box.maxX(), box == null ? 0 : box.maxY(), box == null ? 0 : box.maxZ(), flags, settings.shouldApplyWaterlogging() ? 1 : 0,
			shared ? 1 : 0, 0, generator.nativeSeedLo(), generator.nativeSeedHi(), settings.getRotationPivot().getX(), settings.getRotationPivot().getY(),
			settings.getRotationPivot().getZ()};
		try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(LOGGER)) {
			long[] positions = NativeDecoration.placeTemplate(region, compiled.template, compiled.processors, args,
				(pos, loads, at) -> loadBlockEntity(compiled, level, collector, pos, loads, at));
			if (positions == null) return false;
			generator.nativeSetState(args[19], args[20]);
			if (!settings.getKnownShape() && positions.length > 0) updateShapes(level, flags, positions);
			if (!settings.isIgnoreEntities()) {
				template.placeEntities(level, origin, settings.getMirror(), settings.getRotation(), settings.getRotationPivot(), box,
					settings.shouldFinalizeEntities(), collector);
			}
		}
		return true;
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
		try {
			var compiled = new Compiled();
			List<Integer> processorWords = new ArrayList<>();
			var states = new LinkedHashMap<BlockState, Integer>();
			if (!encodeProcessors(processors, processorWords, compiled, states)) return UNSUPPORTED;
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
					if (info.nbt() == null) return UNSUPPORTED;
					BlockState replacement;
					try {
						replacement = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, info.nbt().getStringOr("final_state", "minecraft:air"), true).blockState();
					} catch (Exception unparsable) {
						return UNSUPPORTED;
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
			// Facts for the closure of states under transforms and waterlogging.
			List<BlockState> pending = new ArrayList<>(states.keySet());
			for (int i = 0; i < pending.size(); i++) {
				BlockState state = pending.get(i);
				List<BlockState> next = new ArrayList<>();
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) next.add(state.mirror(mirror).rotate(rotation));
				}
				if (state.hasProperty(BlockStateProperties.WATERLOGGED)) next.add(state.setValue(BlockStateProperties.WATERLOGGED, true));
				for (BlockState s : next) {
					if (states.putIfAbsent(s, 0) == null) pending.add(s);
				}
			}
			words.add(states.size());
			for (BlockState state : states.keySet()) {
				int flags = facts(state);
				if (flags < 0) return UNSUPPORTED;
				words.add(stateId(state));
				words.add(flags);
				words.add(stateId(state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, true) : state));
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) words.add(stateId(state.mirror(mirror).rotate(rotation)));
				}
			}
			words.add(stateId(Blocks.BARRIER.defaultBlockState()));
			words.add(BuiltInRegistries.FLUID.getId(Fluids.WATER));
			compiled.template = NativeDecoration.createTemplateObject(false, toArray(words), compiled);
			compiled.processors = NativeDecoration.createTemplateObject(true, toArray(processorWords), compiled);
			return compiled.template.address() == 0 || compiled.processors.address() == 0 ? UNSUPPORTED : compiled;
		} catch (ReflectiveOperationException | ClassCastException failure) {
			return UNSUPPORTED;
		}
	}

	/** {@code template.rs}'s state flags, or -1 for a liquid container Rust cannot place into. */
	private static int facts(BlockState state) {
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
			if (block instanceof SimpleWaterloggedBlock && state.hasProperty(BlockStateProperties.WATERLOGGED) && !overridesPlaceLiquid(block.getClass())) {
				flags |= 32;
				if (state.getValue(BlockStateProperties.WATERLOGGED)) flags |= 64;
			} else if (block instanceof net.minecraft.world.level.block.KelpBlock || block instanceof net.minecraft.world.level.block.KelpPlantBlock
				|| block instanceof net.minecraft.world.level.block.SeagrassBlock || block instanceof net.minecraft.world.level.block.TallSeagrassBlock) {
				// Their placeLiquid returns false and changes nothing.
				flags |= 128;
			} else return -1;
		}
		if (state.is(Blocks.JIGSAW)) flags |= 256;
		return flags;
	}

	private static boolean overridesPlaceLiquid(Class<?> type) {
		for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
			try {
				at.getDeclaredMethod("placeLiquid", net.minecraft.world.level.LevelAccessor.class, BlockPos.class, BlockState.class, net.minecraft.world.level.material.FluidState.class);
				return true;
			} catch (NoSuchMethodException ignored) {
			}
		}
		return false;
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

	/** {@code template.rs}'s processor words; false declines the list. */
	private static boolean encodeProcessors(List<StructureProcessor> processors, List<Integer> words, Compiled compiled, Map<BlockState, Integer> outputs)
		throws ReflectiveOperationException {
		List<Integer> body = new ArrayList<>();
		int count = 0;
		boolean ruleSeen = false;
		for (StructureProcessor processor : processors) {
			Class<?> type = processor.getClass();
			if (type == NopProcessor.class) continue;
			count++;
			if (type == BlockIgnoreProcessor.class) {
				@SuppressWarnings("unchecked")
				var ignored = (List<Block>)field(processor, type, "toIgnore");
				body.add(1);
				set(body, s -> ignored.contains(s.getBlock()));
			} else if (type == JigsawReplacementProcessor.class) {
				// Jigsaw replacement reads the template's own NBT: rules must not edit it first.
				if (ruleSeen) return false;
				body.add(2);
			} else if (type == RuleProcessor.class) {
				ruleSeen = true;
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
			} else return false;
		}
		words.add(count);
		words.addAll(body);
		return true;
	}

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
