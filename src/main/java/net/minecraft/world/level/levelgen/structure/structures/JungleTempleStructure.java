package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class JungleTempleStructure extends SinglePieceStructure {
	public static final MapCodec<JungleTempleStructure> CODEC = simpleCodec(JungleTempleStructure::new);

	public JungleTempleStructure(Structure.StructureSettings structureSettings) {
		super(JungleTemplePiece::new, 12, 15, structureSettings);
	}

	/** {@code small_plan.rs}'s params: {@code [family 2]}. */
	@Override
	public int[] nativePlanParams(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
		return new int[]{2};
	}

	/** Rebuilds {@code small_plan.rs}'s pieces ({@link JungleTemplePiece#addNativePieces}). */
	@Override
	public void nativePlanPieces(net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager, int[] words) {
		JungleTemplePiece.addNativePieces(builder, manager, words);
	}

	@Override
	public StructureType<?> type() {
		return StructureType.JUNGLE_TEMPLE;
	}
}
