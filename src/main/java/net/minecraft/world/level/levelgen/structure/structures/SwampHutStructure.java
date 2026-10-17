package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class SwampHutStructure extends Structure {
	public static final MapCodec<SwampHutStructure> CODEC = simpleCodec(SwampHutStructure::new);

	public SwampHutStructure(Structure.StructureSettings structureSettings) {
		super(structureSettings);
	}

	@Override
	public int[] nativeStub() {
		return new int[]{2, 0, 0};
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext,
		net.minecraft.core.BlockPos stub, net.minecraft.world.level.block.Rotation rotation) {
		generatePieces(structurePiecesBuilder, generationContext);
	}

	/** {@code small_plan.rs}'s params: {@code [family 0]}. */
	public int[] nativePlanParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
		return new int[]{0};
	}

	/** Rebuilds {@code small_plan.rs}'s pieces ({@link SwampHutPiece#addNativePieces}). */
	public void nativePlanPieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager, int[] words) {
		SwampHutPiece.addNativePieces(builder, manager, words);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		return onTopOfChunkCenter(
			generationContext, Heightmap.Types.WORLD_SURFACE_WG, structurePiecesBuilder -> generatePieces(structurePiecesBuilder, generationContext)
		);
	}

	private static void generatePieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext) {
		structurePiecesBuilder.addPiece(
			new SwampHutPiece(generationContext.random(), generationContext.chunkPos().getMinBlockX(), generationContext.chunkPos().getMinBlockZ())
		);
	}

	@Override
	public StructureType<?> type() {
		return StructureType.SWAMP_HUT;
	}
}
