package net.matt.quantize.worldgen.structures;

import net.matt.quantize.Quantize;
import net.matt.quantize.worldgen.structures.piece.DinoBowlStructurePiece;
import net.matt.quantize.worldgen.structures.piece.VolcanoStructurePiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ACStructurePieceRegistry {
    public static final DeferredRegister<StructurePieceType> DEF_REG = DeferredRegister.create(Registries.STRUCTURE_PIECE, Quantize.MOD_ID);

    public static final RegistryObject<StructurePieceType> DINO_BOWL = DEF_REG.register("dino_bowl", () -> DinoBowlStructurePiece::new);
    public static final RegistryObject<StructurePieceType> VOLCANO = DEF_REG.register("volcano", () -> VolcanoStructurePiece::new);


}
