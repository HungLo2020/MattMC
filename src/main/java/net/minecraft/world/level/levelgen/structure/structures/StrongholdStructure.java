package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class StrongholdStructure extends Structure {
	public static final MapCodec<StrongholdStructure> CODEC = simpleCodec(StrongholdStructure::new);

	public StrongholdStructure(Structure.StructureSettings structureSettings) {
		super(structureSettings);
	}

	@Override
	public int[] nativeStub() {
		return new int[]{9, 0, 0};
	}

	@Override
	public int[] nativeStubParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager,
		net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator, net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor) {
		return new int[]{chunkGenerator.getMinY()};
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext,
		net.minecraft.core.BlockPos stub, net.minecraft.world.level.block.Rotation rotation, long[] extra, int[] pieces) {
		// Rust planned the pieces and their retries (structure/stronghold_plan.rs).
		StrongholdPieces.addNativePieces(structurePiecesBuilder, pieces);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		return Optional.of(
			new Structure.GenerationStub(
				generationContext.chunkPos().getWorldPosition(), structurePiecesBuilder -> generatePieces(structurePiecesBuilder, generationContext)
			)
		);
	}

	private static void generatePieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext) {
		int i = 0;

		StrongholdPieces.StartPiece startPiece;
		do {
			structurePiecesBuilder.clear();
			generationContext.random().setLargeFeatureSeed(generationContext.seed() + i++, generationContext.chunkPos().x, generationContext.chunkPos().z);
			StrongholdPieces.resetPieces();
			startPiece = new StrongholdPieces.StartPiece(
				generationContext.random(), generationContext.chunkPos().getBlockX(2), generationContext.chunkPos().getBlockZ(2)
			);
			structurePiecesBuilder.addPiece(startPiece);
			startPiece.addChildren(startPiece, structurePiecesBuilder, generationContext.random());
			List<StructurePiece> list = startPiece.pendingChildren;

			while (!list.isEmpty()) {
				int j = generationContext.random().nextInt(list.size());
				StructurePiece structurePiece = (StructurePiece)list.remove(j);
				structurePiece.addChildren(startPiece, structurePiecesBuilder, generationContext.random());
			}

			structurePiecesBuilder.moveBelowSeaLevel(
				generationContext.chunkGenerator().getSeaLevel(), generationContext.chunkGenerator().getMinY(), generationContext.random(), 10
			);
		} while (structurePiecesBuilder.isEmpty() || startPiece.portalRoomPiece == null);
	}

	@Override
	public StructureType<?> type() {
		return StructureType.STRONGHOLD;
	}
}
