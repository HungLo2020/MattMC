package net.minecraft.world.level.levelgen.structure.structures;

import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class ShipwreckPieces {
	private static final int NUMBER_OF_BLOCKS_ALLOWED_IN_WORLD_GEN_REGION = 32;
	static final BlockPos PIVOT = new BlockPos(4, 0, 15);
	private static final ResourceLocation[] STRUCTURE_LOCATION_BEACHED = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("shipwreck/with_mast"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_full"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/with_mast_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")
	};
	private static final ResourceLocation[] STRUCTURE_LOCATION_OCEAN = new ResourceLocation[]{
		ResourceLocation.withDefaultNamespace("shipwreck/with_mast"),
		ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_full"),
		ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_fronthalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_backhalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_full"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf"),
		ResourceLocation.withDefaultNamespace("shipwreck/with_mast_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_full_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_fronthalf_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_backhalf_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_full_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"),
		ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")
	};
	static final Map<String, ResourceKey<LootTable>> MARKERS_TO_LOOT = Map.of(
		"map_chest", BuiltInLootTables.SHIPWRECK_MAP, "treasure_chest", BuiltInLootTables.SHIPWRECK_TREASURE, "supply_chest", BuiltInLootTables.SHIPWRECK_SUPPLY
	);

	/**
	 * {@code small_plan.rs}'s params: {@code [family 5, isBeached, template count, sizes (x, y, z) in the beached or
	 * ocean list's order]}, or null when a template is too big for the world-gen region (Java then moves it by heights).
	 */
	@org.jetbrains.annotations.Nullable
	public static int[] nativePlanParams(StructureTemplateManager structureTemplateManager, boolean bl) {
		ResourceLocation[] templates = bl ? STRUCTURE_LOCATION_BEACHED : STRUCTURE_LOCATION_OCEAN;
		int[] params = new int[3 + 3 * templates.length];
		params[0] = 5;
		params[1] = bl ? 1 : 0;
		params[2] = templates.length;
		for (int i = 0; i < templates.length; i++) {
			Vec3i size = structureTemplateManager.getOrCreate(templates[i]).getSize();
			if (size.getX() > 32 || size.getY() > 32) {
				return null;
			}
			params[3 + 3 * i] = size.getX();
			params[4 + 3 * i] = size.getY();
			params[5 + 3 * i] = size.getZ();
		}
		return params;
	}

	/**
	 * Rebuilds {@code small_plan.rs}'s piece, no draws: {@code [61, 0, box (6), 2 (north), 7, template index (in the
	 * beached or ocean list), rotation ordinal, mirror 0, template x, y, z, isBeached]}.
	 */
	public static void addNativePieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, StructureTemplateManager structureTemplateManager, int[] words) {
		int at = 0;
		while (at < words.length) {
			if (at + 16 + 1 > words.length || words[at] != 61 || words[at + 1] != 0 || words[at + 8] != 2 || words[at + 9] != 6 + 1
				|| words[at + 12] != 0) {
				throw new IllegalArgumentException("Bad native shipwreck words at " + at);
			}
			BoundingBox box = new BoundingBox(words[at + 2], words[at + 3], words[at + 4], words[at + 5], words[at + 6], words[at + 7]);
			int template = words[at + 10];
			Rotation rotation = Rotation.values()[words[at + 11]];
			BlockPos position = new BlockPos(words[at + 13], words[at + 14], words[at + 15]);
			int f = at + 16;
			at = f + 1;
			boolean bl = words[f] != 0;
			ResourceLocation[] templates = bl ? STRUCTURE_LOCATION_BEACHED : STRUCTURE_LOCATION_OCEAN;
			ShipwreckPieces.ShipwreckPiece piece = new ShipwreckPieces.ShipwreckPiece(structureTemplateManager, templates[template], position, rotation, bl);
			if (!piece.getBoundingBox().equals(box)) {
				throw new IllegalStateException("Native shipwreck box " + box + " differs from " + piece.getBoundingBox());
			}
			builder.addPiece(piece);
		}
	}

	public static ShipwreckPieces.ShipwreckPiece addRandomPiece(
		StructureTemplateManager structureTemplateManager,
		BlockPos blockPos,
		Rotation rotation,
		StructurePieceAccessor structurePieceAccessor,
		RandomSource randomSource,
		boolean bl
	) {
		ResourceLocation resourceLocation = Util.getRandom(bl ? STRUCTURE_LOCATION_BEACHED : STRUCTURE_LOCATION_OCEAN, randomSource);
		ShipwreckPieces.ShipwreckPiece shipwreckPiece = new ShipwreckPieces.ShipwreckPiece(structureTemplateManager, resourceLocation, blockPos, rotation, bl);
		structurePieceAccessor.addPiece(shipwreckPiece);
		return shipwreckPiece;
	}

	public static class ShipwreckPiece extends TemplateStructurePiece {
		private final boolean isBeached;

		public ShipwreckPiece(StructureTemplateManager structureTemplateManager, ResourceLocation resourceLocation, BlockPos blockPos, Rotation rotation, boolean bl) {
			super(StructurePieceType.SHIPWRECK_PIECE, 0, structureTemplateManager, resourceLocation, resourceLocation.toString(), makeSettings(rotation), blockPos);
			this.isBeached = bl;
		}

		public ShipwreckPiece(StructureTemplateManager structureTemplateManager, CompoundTag compoundTag) {
			super(
				StructurePieceType.SHIPWRECK_PIECE,
				compoundTag,
				structureTemplateManager,
				resourceLocation -> makeSettings((Rotation)compoundTag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow())
			);
			this.isBeached = compoundTag.getBooleanOr("isBeached", false);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext structurePieceSerializationContext, CompoundTag compoundTag) {
			super.addAdditionalSaveData(structurePieceSerializationContext, compoundTag);
			compoundTag.putBoolean("isBeached", this.isBeached);
			compoundTag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
		}

		private static StructurePlaceSettings makeSettings(Rotation rotation) {
			return new StructurePlaceSettings()
				.setRotation(rotation)
				.setMirror(Mirror.NONE)
				.setRotationPivot(ShipwreckPieces.PIVOT)
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		}

		@Override
		protected long[] nativeTailWords() {
			return this.nativeTailWords(2);
		}

		@Override
		protected void handleDataMarker(String string, BlockPos blockPos, ServerLevelAccessor serverLevelAccessor, RandomSource randomSource, BoundingBox boundingBox) {
			ResourceKey<LootTable> resourceKey = (ResourceKey<LootTable>)ShipwreckPieces.MARKERS_TO_LOOT.get(string);
			if (resourceKey != null) {
				RandomizableContainer.setBlockEntityLootTable(serverLevelAccessor, randomSource, blockPos.below(), resourceKey);
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
			if (this.isTooBigToFitInWorldGenRegion()) {
				super.postProcess(worldGenLevel, structureManager, chunkGenerator, randomSource, boundingBox, chunkPos, blockPos);
			} else {
				int i = worldGenLevel.getMaxY() + 1;
				int j = 0;
				Vec3i vec3i = this.template.getSize();
				Heightmap.Types types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
				int k = vec3i.getX() * vec3i.getZ();
				if (k == 0) {
					j = worldGenLevel.getHeight(types, this.templatePosition.getX(), this.templatePosition.getZ());
				} else {
					BlockPos blockPos2 = this.templatePosition.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);

					for (BlockPos blockPos3 : BlockPos.betweenClosed(this.templatePosition, blockPos2)) {
						int l = worldGenLevel.getHeight(types, blockPos3.getX(), blockPos3.getZ());
						j += l;
						i = Math.min(i, l);
					}

					j /= k;
				}

				this.adjustPositionHeight(this.isBeached ? this.calculateBeachedPosition(i, randomSource) : j);
				super.postProcess(worldGenLevel, structureManager, chunkGenerator, randomSource, boundingBox, chunkPos, blockPos);
			}
		}

		public boolean isTooBigToFitInWorldGenRegion() {
			Vec3i vec3i = this.template.getSize();
			return vec3i.getX() > 32 || vec3i.getY() > 32;
		}

		public int calculateBeachedPosition(int i, RandomSource randomSource) {
			return i - this.template.getSize().getY() / 2 - randomSource.nextInt(3);
		}

		public void adjustPositionHeight(int i) {
			this.templatePosition = new BlockPos(this.templatePosition.getX(), i, this.templatePosition.getZ());
		}
	}
}
