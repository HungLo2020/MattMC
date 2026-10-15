package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class EndCityStructure extends Structure {
	public static final MapCodec<EndCityStructure> CODEC = simpleCodec(EndCityStructure::new);

	public EndCityStructure(Structure.StructureSettings structureSettings) {
		super(structureSettings);
	}

	@Override
	public int[] nativeStub() {
		return new int[]{4, 1, 0};
	}

	@Override
	public int[] nativeStubParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager,
		net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator, net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor) {
		return EndCityPieces.nativePlanParams(structureTemplateManager);
	}

	@Override
	public void nativeStubPieces(StructurePiecesBuilder structurePiecesBuilder, Structure.GenerationContext generationContext,
		net.minecraft.core.BlockPos stub, net.minecraft.world.level.block.Rotation rotation, long[] extra, int[] pieces) {
		// Rust drew the rotation and planned the towers (structure/end_city_plan.rs).
		EndCityPieces.addNativePieces(structurePiecesBuilder, generationContext.structureTemplateManager(), pieces);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext generationContext) {
		Rotation rotation = Rotation.getRandom(generationContext.random());
		BlockPos blockPos = this.getLowestYIn5by5BoxOffset7Blocks(generationContext, rotation);
		return blockPos.getY() < 60
			? Optional.empty()
			: Optional.of(
				new Structure.GenerationStub(blockPos, structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, blockPos, rotation, generationContext))
			);
	}

	private void generatePieces(StructurePiecesBuilder structurePiecesBuilder, BlockPos blockPos, Rotation rotation, Structure.GenerationContext generationContext) {
		List<StructurePiece> list = Lists.<StructurePiece>newArrayList();
		EndCityPieces.startHouseTower(generationContext.structureTemplateManager(), blockPos, rotation, list, generationContext.random());
		list.forEach(structurePiecesBuilder::addPiece);
	}

	@Override
	public StructureType<?> type() {
		return StructureType.END_CITY;
	}
}
