package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class ShipwreckStructure extends Structure {
	public static final MapCodec<ShipwreckStructure> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(settingsCodec(instance), Codec.BOOL.fieldOf("is_beached").forGetter(shipwreckStructure -> shipwreckStructure.isBeached))
			.apply(instance, ShipwreckStructure::new)
	);
	public final boolean isBeached;

	public ShipwreckStructure(Structure.StructureSettings structureSettings, boolean bl) {
		super(structureSettings);
		this.isBeached = bl;
	}

	@Override
	public int[] nativeStub() {
		return new int[]{2, this.isBeached ? 0 : 1, 0};
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext,
		net.minecraft.core.BlockPos stub, net.minecraft.world.level.block.Rotation rotation) {
		this.generatePieces(structurePiecesBuilder, generationContext);
	}

	/** {@code small_plan.rs}'s params ({@link ShipwreckPieces#nativePlanParams}), or null for Java's path. */
	@org.jetbrains.annotations.Nullable
	public int[] nativePlanParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
		return ShipwreckPieces.nativePlanParams(manager, this.isBeached);
	}

	/** Rebuilds {@code small_plan.rs}'s pieces ({@link ShipwreckPieces#addNativePieces}). */
	public void nativePlanPieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager, int[] words) {
		ShipwreckPieces.addNativePieces(builder, manager, words);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		Heightmap.Types types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
		return onTopOfChunkCenter(generationContext, types, structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, generationContext));
	}

	private void generatePieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext) {
		Rotation rotation = Rotation.getRandom(generationContext.random());
		BlockPos blockPos = new BlockPos(generationContext.chunkPos().getMinBlockX(), 90, generationContext.chunkPos().getMinBlockZ());
		ShipwreckPieces.ShipwreckPiece shipwreckPiece = ShipwreckPieces.addRandomPiece(
			generationContext.structureTemplateManager(), blockPos, rotation, structurePiecesBuilder, generationContext.random(), this.isBeached
		);
		if (shipwreckPiece.isTooBigToFitInWorldGenRegion()) {
			BoundingBox boundingBox = shipwreckPiece.getBoundingBox();
			int j;
			if (this.isBeached) {
				int i = Structure.getLowestY(generationContext, boundingBox.minX(), boundingBox.getXSpan(), boundingBox.minZ(), boundingBox.getZSpan());
				j = shipwreckPiece.calculateBeachedPosition(i, generationContext.random());
			} else {
				j = Structure.getMeanFirstOccupiedHeight(generationContext, boundingBox.minX(), boundingBox.getXSpan(), boundingBox.minZ(), boundingBox.getZSpan());
			}

			shipwreckPiece.adjustPositionHeight(j);
		}
	}

	@Override
	public StructureType<?> type() {
		return StructureType.SHIPWRECK;
	}
}
