package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class BuriedTreasureStructure extends Structure {
	public static final MapCodec<BuriedTreasureStructure> CODEC = simpleCodec(BuriedTreasureStructure::new);

	public BuriedTreasureStructure(Structure.StructureSettings structureSettings) {
		super(structureSettings);
	}

	@Override
	public int[] nativeStub() {
		return new int[]{2, 1, 0};
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext,
		net.minecraft.core.BlockPos stub, net.minecraft.world.level.block.Rotation rotation) {
		generatePieces(structurePiecesBuilder, generationContext);
	}

	/** {@code small_plan.rs}'s params: {@code [family 3]}. */
	public int[] nativePlanParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
		return new int[]{3};
	}

	/** Rebuilds {@code small_plan.rs}'s pieces ({@link BuriedTreasurePieces#addNativePieces}). */
	public void nativePlanPieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager, int[] words) {
		BuriedTreasurePieces.addNativePieces(builder, manager, words);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		return onTopOfChunkCenter(
			generationContext, Heightmap.Types.OCEAN_FLOOR_WG, structurePiecesBuilder -> generatePieces(structurePiecesBuilder, generationContext)
		);
	}

	private static void generatePieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext) {
		BlockPos blockPos = new BlockPos(generationContext.chunkPos().getBlockX(9), 90, generationContext.chunkPos().getBlockZ(9));
		structurePiecesBuilder.addPiece(new BuriedTreasurePieces.BuriedTreasurePiece(blockPos));
	}

	@Override
	public StructureType<?> type() {
		return StructureType.BURIED_TREASURE;
	}
}
