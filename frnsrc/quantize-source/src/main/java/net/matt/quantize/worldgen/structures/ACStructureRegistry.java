package net.matt.quantize.worldgen.structures;

import net.matt.quantize.Quantize;
import net.matt.quantize.worldgen.structures.structures.DinoBowlStructure;
import net.matt.quantize.worldgen.structures.structures.VolcanoStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ACStructureRegistry {

    public static final DeferredRegister<StructureType<?>> DEF_REG = DeferredRegister.create(Registries.STRUCTURE_TYPE, Quantize.MOD_ID);

    public static final RegistryObject<StructureType<VolcanoStructure>> VOLCANO = DEF_REG.register("volcano", () -> () -> VolcanoStructure.CODEC);
    public static final RegistryObject<StructureType<DinoBowlStructure>> DINO_BOWL = DEF_REG.register("dino_bowl", () -> () -> DinoBowlStructure.CODEC);

}
