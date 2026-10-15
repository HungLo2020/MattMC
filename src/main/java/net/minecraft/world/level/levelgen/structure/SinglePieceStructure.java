package net.minecraft.world.level.levelgen.structure;

import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public abstract class SinglePieceStructure extends Structure {
	private final SinglePieceStructure.PieceConstructor constructor;
	private final int width;
	private final int depth;

	protected SinglePieceStructure(SinglePieceStructure.PieceConstructor pieceConstructor, int i, int j, Structure.StructureSettings structureSettings) {
		super(structureSettings);
		this.constructor = pieceConstructor;
		this.width = i;
		this.depth = j;
	}

	@Override
	public int[] nativeStub() {
		return new int[]{1, this.width, this.depth};
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext,
		net.minecraft.core.BlockPos stub, net.minecraft.world.level.block.Rotation rotation) {
		this.generatePieces(structurePiecesBuilder, generationContext);
	}

	/**
	 * {@code small_plan.rs}'s params for this structure's piece ({@code [family]}: 1 desert pyramid, 2 jungle temple),
	 * or null when Rust can't plan it.
	 */
	@org.jetbrains.annotations.Nullable
	public int[] nativePlanParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
		return null;
	}

	/** Rebuilds the piece {@code small_plan.rs} planned (with {@link #nativePlanParams}'s params), no draws. */
	public void nativePlanPieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager, int[] words) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		return getLowestY(generationContext, this.width, this.depth) < generationContext.chunkGenerator().getSeaLevel()
			? Optional.empty()
			: onTopOfChunkCenter(
				generationContext, Heightmap.Types.WORLD_SURFACE_WG, structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, generationContext)
			);
	}

	private void generatePieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext) {
		ChunkPos chunkPos = generationContext.chunkPos();
		structurePiecesBuilder.addPiece(this.constructor.construct(generationContext.random(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ()));
	}

	@FunctionalInterface
	protected interface PieceConstructor {
		StructurePiece construct(WorldgenRandom worldgenRandom, int i, int j);
	}
}
