package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendLoot;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class OceanRuinPieces {
	static final StructureProcessor WARM_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(
		Blocks.SAND, Blocks.SUSPICIOUS_SAND, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY
	);
	static final StructureProcessor COLD_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(
		Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY
	);
	private static final ResourceLocation[] WARM_RUINS = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_4"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_5"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_6"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_7"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/warm_8")
	};
	private static final ResourceLocation[] RUINS_BRICK = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_4"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_5"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_6"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_7"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/brick_8")
	};
	private static final ResourceLocation[] RUINS_CRACKED = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_4"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_5"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_6"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_7"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_8")
	};
	private static final ResourceLocation[] RUINS_MOSSY = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_4"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_5"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_6"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_7"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_8")
	};
	private static final ResourceLocation[] BIG_RUINS_BRICK = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_8")
	};
	private static final ResourceLocation[] BIG_RUINS_MOSSY = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_8")
	};
	private static final ResourceLocation[] BIG_RUINS_CRACKED = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_1"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_2"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_3"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_8")
	};
	private static final ResourceLocation[] BIG_WARM_RUINS = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_4"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_5"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_6"),
		ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_7")
	};

	private static StructureProcessor archyRuleProcessor(Block block, Block block2, ResourceKey<LootTable> resourceKey) {
		return new CappedProcessor(
			new RuleProcessor(
				List.of(
					new ProcessorRule(new BlockMatchTest(block), AlwaysTrueTest.INSTANCE, PosAlwaysTrueTest.INSTANCE, block2.defaultBlockState(), new AppendLoot(resourceKey))
				)
			),
			ConstantInt.of(5)
		);
	}

	private static ResourceLocation getSmallWarmRuin(RandomSource randomSource) {
		return Util.getRandom(WARM_RUINS, randomSource);
	}

	private static ResourceLocation getBigWarmRuin(RandomSource randomSource) {
		return Util.getRandom(BIG_WARM_RUINS, randomSource);
	}

	/** {@code small_plan.rs}'s template lists: warm {@code [small 8, big 4]}, cold {@code [brick, cracked, mossy, big brick, big cracked, big mossy]}. */
	private static ResourceLocation[] nativeTemplates(OceanRuinStructure.Type type) {
		ResourceLocation[][] lists = type == OceanRuinStructure.Type.COLD
			? new ResourceLocation[][]{RUINS_BRICK, RUINS_CRACKED, RUINS_MOSSY, BIG_RUINS_BRICK, BIG_RUINS_CRACKED, BIG_RUINS_MOSSY}
			: new ResourceLocation[][]{WARM_RUINS, BIG_WARM_RUINS};
		return java.util.Arrays.stream(lists).flatMap(java.util.Arrays::stream).toArray(ResourceLocation[]::new);
	}

	/**
	 * {@code small_plan.rs}'s params: {@code [family 6, cold, Float.floatToIntBits(largeProbability),
	 * Float.floatToIntBits(clusterProbability), template count, sizes (x, y, z) in nativeTemplates' order]}.
	 */
	public static int[] nativePlanParams(StructureTemplateManager structureTemplateManager, OceanRuinStructure oceanRuinStructure) {
		ResourceLocation[] templates = nativeTemplates(oceanRuinStructure.biomeTemp);
		int[] params = new int[5 + 3 * templates.length];
		params[0] = 6;
		params[1] = oceanRuinStructure.biomeTemp == OceanRuinStructure.Type.COLD ? 1 : 0;
		params[2] = Float.floatToIntBits(oceanRuinStructure.largeProbability);
		params[3] = Float.floatToIntBits(oceanRuinStructure.clusterProbability);
		params[4] = templates.length;
		for (int i = 0; i < templates.length; i++) {
			net.minecraft.core.Vec3i size = structureTemplateManager.getOrCreate(templates[i]).getSize();
			params[5 + 3 * i] = size.getX();
			params[6 + 3 * i] = size.getY();
			params[7 + 3 * i] = size.getZ();
		}
		return params;
	}

	/**
	 * Rebuilds {@code small_plan.rs}'s pieces, no draws. Each: {@code [62, 0, box (6), 2 (north), 9, template index
	 * (nativeTemplates), rotation ordinal, mirror 0, template x, y, z, Float.floatToIntBits(integrity), cold, isLarge]}.
	 */
	public static void addNativePieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, StructureTemplateManager structureTemplateManager, int[] words) {
		int at = 0;
		while (at < words.length) {
			if (at + 16 + 3 > words.length || words[at] != 62 || words[at + 1] != 0 || words[at + 8] != 2 || words[at + 9] != 6 + 3
				|| words[at + 12] != 0) {
				throw new IllegalArgumentException("Bad native ocean ruin words at " + at);
			}
			BoundingBox box = new BoundingBox(words[at + 2], words[at + 3], words[at + 4], words[at + 5], words[at + 6], words[at + 7]);
			int template = words[at + 10];
			Rotation rotation = Rotation.values()[words[at + 11]];
			BlockPos position = new BlockPos(words[at + 13], words[at + 14], words[at + 15]);
			int f = at + 16;
			at = f + 3;
			float integrity = Float.intBitsToFloat(words[f]);
			OceanRuinStructure.Type type = words[f + 1] != 0 ? OceanRuinStructure.Type.COLD : OceanRuinStructure.Type.WARM;
			OceanRuinPieces.OceanRuinPiece piece = new OceanRuinPieces.OceanRuinPiece(
				structureTemplateManager, nativeTemplates(type)[template], position, rotation, integrity, type, words[f + 2] != 0
			);
			if (!piece.getBoundingBox().equals(box)) {
				throw new IllegalStateException("Native ocean ruin box " + box + " differs from " + piece.getBoundingBox());
			}
			builder.addPiece(piece);
		}
	}

	public static void addPieces(
		StructureTemplateManager structureTemplateManager,
		BlockPos blockPos,
		Rotation rotation,
		StructurePieceAccessor structurePieceAccessor,
		RandomSource randomSource,
		OceanRuinStructure oceanRuinStructure
	) {
		boolean bl = randomSource.nextFloat() <= oceanRuinStructure.largeProbability;
		float f = bl ? 0.9F : 0.8F;
		addPiece(structureTemplateManager, blockPos, rotation, structurePieceAccessor, randomSource, oceanRuinStructure, bl, f);
		if (bl && randomSource.nextFloat() <= oceanRuinStructure.clusterProbability) {
			addClusterRuins(structureTemplateManager, randomSource, rotation, blockPos, oceanRuinStructure, structurePieceAccessor);
		}
	}

	private static void addClusterRuins(
		StructureTemplateManager structureTemplateManager,
		RandomSource randomSource,
		Rotation rotation,
		BlockPos blockPos,
		OceanRuinStructure oceanRuinStructure,
		StructurePieceAccessor structurePieceAccessor
	) {
		BlockPos blockPos2 = new BlockPos(blockPos.getX(), 90, blockPos.getZ());
		BlockPos blockPos3 = StructureTemplate.transform(new BlockPos(15, 0, 15), Mirror.NONE, rotation, BlockPos.ZERO).offset(blockPos2);
		BoundingBox boundingBox = BoundingBox.fromCorners(blockPos2, blockPos3);
		BlockPos blockPos4 = new BlockPos(Math.min(blockPos2.getX(), blockPos3.getX()), blockPos2.getY(), Math.min(blockPos2.getZ(), blockPos3.getZ()));
		List<BlockPos> list = allPositions(randomSource, blockPos4);
		int i = Mth.nextInt(randomSource, 4, 8);

		for (int j = 0; j < i; j++) {
			if (!list.isEmpty()) {
				int k = randomSource.nextInt(list.size());
				BlockPos blockPos5 = (BlockPos)list.remove(k);
				Rotation rotation2 = Rotation.getRandom(randomSource);
				BlockPos blockPos6 = StructureTemplate.transform(new BlockPos(5, 0, 6), Mirror.NONE, rotation2, BlockPos.ZERO).offset(blockPos5);
				BoundingBox boundingBox2 = BoundingBox.fromCorners(blockPos5, blockPos6);
				if (!boundingBox2.intersects(boundingBox)) {
					addPiece(structureTemplateManager, blockPos5, rotation2, structurePieceAccessor, randomSource, oceanRuinStructure, false, 0.8F);
				}
			}
		}
	}

	private static List<BlockPos> allPositions(RandomSource randomSource, BlockPos blockPos) {
		List<BlockPos> list = Lists.<BlockPos>newArrayList();
		list.add(blockPos.offset(-16 + Mth.nextInt(randomSource, 1, 8), 0, 16 + Mth.nextInt(randomSource, 1, 7)));
		list.add(blockPos.offset(-16 + Mth.nextInt(randomSource, 1, 8), 0, Mth.nextInt(randomSource, 1, 7)));
		list.add(blockPos.offset(-16 + Mth.nextInt(randomSource, 1, 8), 0, -16 + Mth.nextInt(randomSource, 4, 8)));
		list.add(blockPos.offset(Mth.nextInt(randomSource, 1, 7), 0, 16 + Mth.nextInt(randomSource, 1, 7)));
		list.add(blockPos.offset(Mth.nextInt(randomSource, 1, 7), 0, -16 + Mth.nextInt(randomSource, 4, 6)));
		list.add(blockPos.offset(16 + Mth.nextInt(randomSource, 1, 7), 0, 16 + Mth.nextInt(randomSource, 3, 8)));
		list.add(blockPos.offset(16 + Mth.nextInt(randomSource, 1, 7), 0, Mth.nextInt(randomSource, 1, 7)));
		list.add(blockPos.offset(16 + Mth.nextInt(randomSource, 1, 7), 0, -16 + Mth.nextInt(randomSource, 4, 8)));
		return list;
	}

	private static void addPiece(
		StructureTemplateManager structureTemplateManager,
		BlockPos blockPos,
		Rotation rotation,
		StructurePieceAccessor structurePieceAccessor,
		RandomSource randomSource,
		OceanRuinStructure oceanRuinStructure,
		boolean bl,
		float f
	) {
		switch (oceanRuinStructure.biomeTemp) {
			case WARM:
			default:
				ResourceLocation resourceLocation = bl ? getBigWarmRuin(randomSource) : getSmallWarmRuin(randomSource);
				structurePieceAccessor.addPiece(
					new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, resourceLocation, blockPos, rotation, f, oceanRuinStructure.biomeTemp, bl)
				);
				break;
			case COLD:
				ResourceLocation[] resourceLocations = bl ? BIG_RUINS_BRICK : RUINS_BRICK;
				ResourceLocation[] resourceLocations2 = bl ? BIG_RUINS_CRACKED : RUINS_CRACKED;
				ResourceLocation[] resourceLocations3 = bl ? BIG_RUINS_MOSSY : RUINS_MOSSY;
				int i = randomSource.nextInt(resourceLocations.length);
				structurePieceAccessor.addPiece(
					new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, resourceLocations[i], blockPos, rotation, f, oceanRuinStructure.biomeTemp, bl)
				);
				structurePieceAccessor.addPiece(
					new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, resourceLocations2[i], blockPos, rotation, 0.7F, oceanRuinStructure.biomeTemp, bl)
				);
				structurePieceAccessor.addPiece(
					new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, resourceLocations3[i], blockPos, rotation, 0.5F, oceanRuinStructure.biomeTemp, bl)
				);
		}
	}

	public static class OceanRuinPiece extends TemplateStructurePiece {
		private final OceanRuinStructure.Type biomeType;
		private final float integrity;
		private final boolean isLarge;

		public OceanRuinPiece(
			StructureTemplateManager structureTemplateManager,
			ResourceLocation resourceLocation,
			BlockPos blockPos,
			Rotation rotation,
			float f,
			OceanRuinStructure.Type type,
			boolean bl
		) {
			super(StructurePieceType.OCEAN_RUIN, 0, structureTemplateManager, resourceLocation, resourceLocation.toString(), makeSettings(rotation, f, type), blockPos);
			this.integrity = f;
			this.biomeType = type;
			this.isLarge = bl;
		}

		private OceanRuinPiece(
			StructureTemplateManager structureTemplateManager, CompoundTag compoundTag, Rotation rotation, float f, OceanRuinStructure.Type type, boolean bl
		) {
			super(StructurePieceType.OCEAN_RUIN, compoundTag, structureTemplateManager, resourceLocation -> makeSettings(rotation, f, type));
			this.integrity = f;
			this.biomeType = type;
			this.isLarge = bl;
		}

		private static StructurePlaceSettings makeSettings(Rotation rotation, float f, OceanRuinStructure.Type type) {
			StructureProcessor structureProcessor = type == OceanRuinStructure.Type.COLD
				? OceanRuinPieces.COLD_SUSPICIOUS_BLOCK_PROCESSOR
				: OceanRuinPieces.WARM_SUSPICIOUS_BLOCK_PROCESSOR;
			return new StructurePlaceSettings()
				.setRotation(rotation)
				.setMirror(Mirror.NONE)
				.addProcessor(new BlockRotProcessor(f))
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
				.addProcessor(structureProcessor);
		}

		public static OceanRuinPieces.OceanRuinPiece create(StructureTemplateManager structureTemplateManager, CompoundTag compoundTag) {
			Rotation rotation = (Rotation)compoundTag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow();
			float f = compoundTag.getFloatOr("Integrity", 0.0F);
			OceanRuinStructure.Type type = (OceanRuinStructure.Type)compoundTag.read("BiomeType", OceanRuinStructure.Type.LEGACY_CODEC).orElseThrow();
			boolean bl = compoundTag.getBooleanOr("IsLarge", false);
			return new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, compoundTag, rotation, f, type, bl);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext structurePieceSerializationContext, CompoundTag compoundTag) {
			super.addAdditionalSaveData(structurePieceSerializationContext, compoundTag);
			compoundTag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
			compoundTag.putFloat("Integrity", this.integrity);
			compoundTag.store("BiomeType", OceanRuinStructure.Type.LEGACY_CODEC, this.biomeType);
			compoundTag.putBoolean("IsLarge", this.isLarge);
		}

		@Override
		protected long[] nativeTailWords() {
			long[] words = this.nativeTailWords(4);
			words[1] = this.isLarge ? 1 : 0;
			return words;
		}

		@Override
		protected void handleDataMarker(String string, BlockPos blockPos, ServerLevelAccessor serverLevelAccessor, RandomSource randomSource, BoundingBox boundingBox) {
			if ("chest".equals(string)) {
				serverLevelAccessor.setBlock(
					blockPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, serverLevelAccessor.getFluidState(blockPos).is(FluidTags.WATER)), 2
				);
				BlockEntity blockEntity = serverLevelAccessor.getBlockEntity(blockPos);
				if (blockEntity instanceof ChestBlockEntity) {
					((ChestBlockEntity)blockEntity)
						.setLootTable(this.isLarge ? BuiltInLootTables.UNDERWATER_RUIN_BIG : BuiltInLootTables.UNDERWATER_RUIN_SMALL, randomSource.nextLong());
				}
			} else if ("drowned".equals(string)) {
				Drowned drowned = EntityType.DROWNED.create(serverLevelAccessor.getLevel(), EntitySpawnReason.STRUCTURE);
				if (drowned != null) {
					drowned.setPersistenceRequired();
					drowned.snapTo(blockPos, 0.0F, 0.0F);
					drowned.finalizeSpawn(serverLevelAccessor, serverLevelAccessor.getCurrentDifficultyAt(blockPos), EntitySpawnReason.STRUCTURE, null);
					serverLevelAccessor.addFreshEntityWithPassengers(drowned);
					if (blockPos.getY() > serverLevelAccessor.getSeaLevel()) {
						serverLevelAccessor.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 2);
					} else {
						serverLevelAccessor.setBlock(blockPos, Blocks.WATER.defaultBlockState(), 2);
					}
				}
			}
		}

		@Override
		public void postProcess(
			WorldGenLevel worldGenLevel,
			StructureManager structureManager,
			ChunkGenerator chunkGenerator,
			RandomSource randomSource,
			BoundingBox boundingBox,
			ChunkPos chunkPos,
			BlockPos blockPos
		) {
			int i = worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.templatePosition.getX(), this.templatePosition.getZ());
			this.templatePosition = new BlockPos(this.templatePosition.getX(), i, this.templatePosition.getZ());
			BlockPos blockPos2 = StructureTemplate.transform(
					new BlockPos(this.template.getSize().getX() - 1, 0, this.template.getSize().getZ() - 1), Mirror.NONE, this.placeSettings.getRotation(), BlockPos.ZERO
				)
				.offset(this.templatePosition);
			this.templatePosition = new BlockPos(
				this.templatePosition.getX(), this.getHeight(this.templatePosition, worldGenLevel, blockPos2), this.templatePosition.getZ()
			);
			super.postProcess(worldGenLevel, structureManager, chunkGenerator, randomSource, boundingBox, chunkPos, blockPos);
		}

		private int getHeight(BlockPos blockPos, BlockGetter blockGetter, BlockPos blockPos2) {
			int i = blockPos.getY();
			int j = 512;
			int k = i - 1;
			int l = 0;

			for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos, blockPos2)) {
				int m = blockPos3.getX();
				int n = blockPos3.getZ();
				int o = blockPos.getY() - 1;
				BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(m, o, n);
				BlockState blockState = blockGetter.getBlockState(mutableBlockPos);

				for (FluidState fluidState = blockGetter.getFluidState(mutableBlockPos);
					(blockState.isAir() || fluidState.is(FluidTags.WATER) || blockState.is(BlockTags.ICE)) && o > blockGetter.getMinY() + 1;
					fluidState = blockGetter.getFluidState(mutableBlockPos)
				) {
					mutableBlockPos.set(m, --o, n);
					blockState = blockGetter.getBlockState(mutableBlockPos);
				}

				j = Math.min(j, o);
				if (o < k - 2) {
					l++;
				}
			}

			int p = Math.abs(blockPos.getX() - blockPos2.getX());
			if (k - j > 2 && l > p - 2) {
				i = j + 1;
			}

			return i;
		}
	}
}
