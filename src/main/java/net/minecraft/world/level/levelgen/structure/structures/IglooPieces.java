package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class IglooPieces {
	public static final int GENERATION_HEIGHT = 90;
	static final ResourceLocation STRUCTURE_LOCATION_IGLOO = ResourceLocation.withDefaultNamespace("igloo/top");
	private static final ResourceLocation STRUCTURE_LOCATION_LADDER = ResourceLocation.withDefaultNamespace("igloo/middle");
	private static final ResourceLocation STRUCTURE_LOCATION_LABORATORY = ResourceLocation.withDefaultNamespace("igloo/bottom");
	static final Map<ResourceLocation, BlockPos> PIVOTS = ImmutableMap.of(
		STRUCTURE_LOCATION_IGLOO, new BlockPos(3, 5, 5), STRUCTURE_LOCATION_LADDER, new BlockPos(1, 3, 1), STRUCTURE_LOCATION_LABORATORY, new BlockPos(3, 6, 7)
	);
	static final Map<ResourceLocation, BlockPos> OFFSETS = ImmutableMap.of(
		STRUCTURE_LOCATION_IGLOO, BlockPos.ZERO, STRUCTURE_LOCATION_LADDER, new BlockPos(2, -3, 4), STRUCTURE_LOCATION_LABORATORY, new BlockPos(0, -3, -2)
	);

	/** {@code small_plan.rs}'s template order. */
	private static final ResourceLocation[] NATIVE_TEMPLATES = new ResourceLocation[]{
		STRUCTURE_LOCATION_IGLOO, STRUCTURE_LOCATION_LADDER, STRUCTURE_LOCATION_LABORATORY
	};

	/** {@code small_plan.rs}'s params: {@code [family 4, sizes (x, y, z) of igloo/top, igloo/middle, igloo/bottom]}. */
	public static int[] nativePlanParams(StructureTemplateManager structureTemplateManager) {
		int[] params = new int[1 + 3 * NATIVE_TEMPLATES.length];
		params[0] = 4;
		for (int i = 0; i < NATIVE_TEMPLATES.length; i++) {
			Vec3i size = structureTemplateManager.getOrCreate(NATIVE_TEMPLATES[i]).getSize();
			params[1 + 3 * i] = size.getX();
			params[2 + 3 * i] = size.getY();
			params[3 + 3 * i] = size.getZ();
		}
		return params;
	}

	/**
	 * Rebuilds {@code small_plan.rs}'s pieces, no draws. Each: {@code [60, 0, box (6), 2 (north), 6, template index
	 * (top, middle, bottom), rotation ordinal, mirror 0, template x, y, z]}.
	 */
	public static void addNativePieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, StructureTemplateManager structureTemplateManager, int[] words) {
		int at = 0;
		while (at < words.length) {
			if (at + 16 + 0 > words.length || words[at] != 60 || words[at + 1] != 0 || words[at + 8] != 2 || words[at + 9] != 6 + 0
				|| words[at + 12] != 0) {
				throw new IllegalArgumentException("Bad native igloo words at " + at);
			}
			BoundingBox box = new BoundingBox(words[at + 2], words[at + 3], words[at + 4], words[at + 5], words[at + 6], words[at + 7]);
			int template = words[at + 10];
			Rotation rotation = Rotation.values()[words[at + 11]];
			BlockPos position = new BlockPos(words[at + 13], words[at + 14], words[at + 15]);
			int f = at + 16;
			at = f + 0;
			IglooPieces.IglooPiece piece = new IglooPieces.IglooPiece(structureTemplateManager, NATIVE_TEMPLATES[template], rotation, position);
			if (!piece.getBoundingBox().equals(box)) {
				throw new IllegalStateException("Native igloo box " + box + " differs from " + piece.getBoundingBox());
			}
			builder.addPiece(piece);
		}
	}

	public static void addPieces(
		StructureTemplateManager structureTemplateManager,
		BlockPos blockPos,
		Rotation rotation,
		StructurePieceAccessor structurePieceAccessor,
		RandomSource randomSource
	) {
		if (randomSource.nextDouble() < 0.5) {
			int i = randomSource.nextInt(8) + 4;
			structurePieceAccessor.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, STRUCTURE_LOCATION_LABORATORY, blockPos, rotation, i * 3));

			for (int j = 0; j < i - 1; j++) {
				structurePieceAccessor.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, STRUCTURE_LOCATION_LADDER, blockPos, rotation, j * 3));
			}
		}

		structurePieceAccessor.addPiece(new IglooPieces.IglooPiece(structureTemplateManager, STRUCTURE_LOCATION_IGLOO, blockPos, rotation, 0));
	}

	public static class IglooPiece extends TemplateStructurePiece {
		public IglooPiece(StructureTemplateManager structureTemplateManager, ResourceLocation resourceLocation, BlockPos blockPos, Rotation rotation, int i) {
			super(
				StructurePieceType.IGLOO,
				0,
				structureTemplateManager,
				resourceLocation,
				resourceLocation.toString(),
				makeSettings(rotation, resourceLocation),
				makePosition(resourceLocation, blockPos, i)
			);
		}

		/** A piece Rust planned ({@code small_plan.rs}) at its template position, no draws. */
		IglooPiece(StructureTemplateManager structureTemplateManager, ResourceLocation resourceLocation, Rotation rotation, BlockPos templatePosition) {
			super(
				StructurePieceType.IGLOO,
				0,
				structureTemplateManager,
				resourceLocation,
				resourceLocation.toString(),
				makeSettings(rotation, resourceLocation),
				templatePosition
			);
		}

		public IglooPiece(StructureTemplateManager structureTemplateManager, CompoundTag compoundTag) {
			super(
				StructurePieceType.IGLOO,
				compoundTag,
				structureTemplateManager,
				resourceLocation -> makeSettings((Rotation)compoundTag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow(), resourceLocation)
			);
		}

		private static StructurePlaceSettings makeSettings(Rotation rotation, ResourceLocation resourceLocation) {
			return new StructurePlaceSettings()
				.setRotation(rotation)
				.setMirror(Mirror.NONE)
				.setRotationPivot((BlockPos)IglooPieces.PIVOTS.get(resourceLocation))
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
				.setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
		}

		private static BlockPos makePosition(ResourceLocation resourceLocation, BlockPos blockPos, int i) {
			return blockPos.offset((Vec3i)IglooPieces.OFFSETS.get(resourceLocation)).below(i);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext structurePieceSerializationContext, CompoundTag compoundTag) {
			super.addAdditionalSaveData(structurePieceSerializationContext, compoundTag);
			compoundTag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
		}

		@Override
		protected long[] nativeTailWords() {
			return this.nativeTailWords(1);
		}

		@Override
		protected void handleDataMarker(String string, BlockPos blockPos, ServerLevelAccessor serverLevelAccessor, RandomSource randomSource, BoundingBox boundingBox) {
			if ("chest".equals(string)) {
				serverLevelAccessor.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
				BlockEntity blockEntity = serverLevelAccessor.getBlockEntity(blockPos.below());
				if (blockEntity instanceof ChestBlockEntity) {
					((ChestBlockEntity)blockEntity).setLootTable(BuiltInLootTables.IGLOO_CHEST, randomSource.nextLong());
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
			ResourceLocation resourceLocation = ResourceLocation.parse(this.templateName);
			StructurePlaceSettings structurePlaceSettings = makeSettings(this.placeSettings.getRotation(), resourceLocation);
			BlockPos blockPos2 = (BlockPos)IglooPieces.OFFSETS.get(resourceLocation);
			BlockPos blockPos3 = this.templatePosition
				.offset(StructureTemplate.calculateRelativePosition(structurePlaceSettings, new BlockPos(3 - blockPos2.getX(), 0, -blockPos2.getZ())));
			int i = worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockPos3.getX(), blockPos3.getZ());
			BlockPos blockPos4 = this.templatePosition;
			this.templatePosition = this.templatePosition.offset(0, i - 90 - 1, 0);
			super.postProcess(worldGenLevel, structureManager, chunkGenerator, randomSource, boundingBox, chunkPos, blockPos);
			if (resourceLocation.equals(IglooPieces.STRUCTURE_LOCATION_IGLOO)) {
				BlockPos blockPos5 = this.templatePosition.offset(StructureTemplate.calculateRelativePosition(structurePlaceSettings, new BlockPos(3, 0, 5)));
				BlockState blockState = worldGenLevel.getBlockState(blockPos5.below());
				if (!blockState.isAir() && !blockState.is(Blocks.LADDER)) {
					worldGenLevel.setBlock(blockPos5, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
				}
			}

			this.templatePosition = blockPos4;
		}
	}
}
