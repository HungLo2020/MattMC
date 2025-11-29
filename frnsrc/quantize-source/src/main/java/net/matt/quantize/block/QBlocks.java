package net.matt.quantize.block;

import net.matt.quantize.Quantize;
import net.matt.quantize.entities.QEntities;
import net.matt.quantize.item.QItems;
import net.matt.quantize.particle.QParticles;
import net.matt.quantize.sounds.QSoundTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.grower.OakTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.function.Supplier;
import net.matt.quantize.block.block.*;
import net.minecraft.world.level.material.MapColor;
import net.matt.quantize.worldgen.saplinggrowers.*;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

public class QBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Quantize.MOD_ID);


    // Properties
    public static final BlockBehaviour.Properties LIMESTONE_PROPERTIES = BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).requiresCorrectToolForDrops().strength(1.2F, 4.5F).sound(SoundType.DRIPSTONE_BLOCK);
    public static final BlockBehaviour.Properties PEWEN_LOG_PROPERTIES = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.CHERRY_WOOD).instrument(NoteBlockInstrument.BASS);
    public static final BlockBehaviour.Properties PEWEN_PLANKS_PROPERTIES = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.CHERRY_WOOD).instrument(NoteBlockInstrument.BASS);
    public static final WoodType PEWEN_WOOD_TYPE = WoodType.register(new WoodType("pewen", BlockSetType.OAK));
    public static final WoodType CYPRESS_WOOD_TYPE = WoodType.register(new WoodType("cypress", BlockSetType.OAK));
    public static final BlockBehaviour.Properties CYPRESS_LOG_PROPERTIES = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.BAMBOO_WOOD).instrument(NoteBlockInstrument.BASS);
    public static final BlockBehaviour.Properties CYPRESS_PLANKS_PROPERTIES = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).instrument(NoteBlockInstrument.BASS);
    private static final BlockBehaviour.Properties COPPER_GRATE_PROPS = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion();


    //Regular Blocks
    ///public static final RegistryObject<Block> JADE_BRICKS = registerBlock("jade_bricks", () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).sound(SoundType.AMETHYST)));
    ///public static final RegistryObject<Block> JADE_ORE = registerBlock("jade_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.EMERALD_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));
    ///public static final RegistryObject<Block> DEEPSLATE_JADE_ORE = registerBlock("deepslate_jade_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_EMERALD_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));
    ///public static final RegistryObject<Block> DEEPSLATE_LEAD_ORE = registerBlock("deepslate_lead_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> DEEPSLATE_OSMIUM_ORE = registerBlock("deepslate_osmium_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> DEEPSLATE_TIN_ORE = registerBlock("deepslate_tin_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> DEEPSLATE_URANIUM_ORE = registerBlock("deepslate_uranium_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> URANIUM_ORE = registerBlock("uranium_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> TIN_ORE = registerBlock("tin_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> LEAD_ORE = registerBlock("lead_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    ///public static final RegistryObject<Block> OSMIUM_ORE = registerBlock("osmium_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).strength(2f).requiresCorrectToolForDrops(), UniformInt.of(2, 4)));
    public static final RegistryObject<Block> CAIMAN_EGG = registerBlock("caiman_egg", () -> new BlockReptileEgg(QEntities.CAIMAN));
    public static final RegistryObject<Block> CROCODILE_EGG = registerBlock("crocodile_egg", () -> new BlockReptileEgg(QEntities.CAIMAN));
    public static final RegistryObject<Block> FERN_THATCH = registerBlock("fern_thatch", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F).sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<Block> AMBER = registerBlock("amber", () -> new GlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).noOcclusion().requiresCorrectToolForDrops().strength(0.3F, 2.0F).sound(QSoundTypes.AMBER)));
    public static final RegistryObject<Block> AMBERSOL = registerBlock("ambersol", () -> new AmbersolBlock());
    public static final RegistryObject<Block> AMBERSOL_LIGHT = BLOCKS.register("ambersol_light", () -> new AmbersolLightBlock(BlockBehaviour.Properties.of().noOcclusion().strength(-1.0F, 3600000.8F).noLootTable().noOcclusion().replaceable().lightLevel(((state -> 15)))));
    public static final RegistryObject<Block> AMBER_MONOLITH = registerBlock("amber_monolith", () -> new AmberMonolithBlock());
    public static final RegistryObject<Block> SUBTERRANODON_EGG = registerBlock("subterranodon_egg", () -> new MultipleDinosaurEggsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.METAL).randomTicks(), QEntities.SUBTERRANODON, 4));
    public static final RegistryObject<Block> VALLUMRAPTOR_EGG = registerBlock("vallumraptor_egg", () -> new MultipleDinosaurEggsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.METAL).randomTicks(), QEntities.VALLUMRAPTOR, 4));
    public static final RegistryObject<Block> GROTTOCERATOPS_EGG = registerBlock("grottoceratops_egg", () -> new DinosaurEggBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.METAL).randomTicks(), QEntities.GROTTOCERATOPS, 8, 10));
    public static final RegistryObject<Block> TREMORSAURUS_EGG = registerBlock("tremorsaurus_egg", () -> new DinosaurEggBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.METAL).randomTicks(), QEntities.TREMORSAURUS, 10, 16));
    public static final RegistryObject<Block> RELICHEIRUS_EGG = registerBlock("relicheirus_egg", () -> new DinosaurEggBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.METAL).randomTicks(), QEntities.RELICHEIRUS, 14, 16));
    public static final RegistryObject<Block> ATLATITAN_EGG = registerBlock("atlatitan_egg", () -> new DinosaurEggBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.METAL).randomTicks(), QEntities.ATLATITAN, 16, 16));
    public static final RegistryObject<Block> DINOSAUR_CHOP = registerBlock("dinosaur_chop", () -> new DinosaurChopBlock(3, 0.2F));
    public static final RegistryObject<Block> LIMESTONE = registerBlock("limestone", () -> new Block(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> LIMESTONE_STAIRS = registerBlock("limestone_stairs", () -> new StairBlock(LIMESTONE.get().defaultBlockState(), LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> LIMESTONE_SLAB = registerBlock("limestone_slab", () -> new SlabBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> LIMESTONE_WALL = registerBlock("limestone_wall", () -> new WallBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> LIMESTONE_PILLAR = registerBlock("limestone_pillar", () -> new RotatedPillarBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> LIMESTONE_CHISELED = registerBlock("limestone_chiseled", () -> new DirectionalFacingBlock(LIMESTONE_PROPERTIES, true));
    public static final RegistryObject<Block> SMOOTH_LIMESTONE = registerBlock("smooth_limestone", () -> new SmoothLimestoneBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> SMOOTH_LIMESTONE_STAIRS = registerBlock("smooth_limestone_stairs", () -> new StairBlock(SMOOTH_LIMESTONE.get().defaultBlockState(), LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> SMOOTH_LIMESTONE_SLAB = registerBlock("smooth_limestone_slab", () -> new SlabBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> SMOOTH_LIMESTONE_WALL = registerBlock("smooth_limestone_wall", () -> new WallBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> COOKED_DINOSAUR_CHOP = registerBlock("cooked_dinosaur_chop", () -> new DinosaurChopBlock(7, 0.35F));
    public static final RegistryObject<Block> PRIMAL_MAGMA = registerBlock("primal_magma", () -> new PrimalMagmaBlock());
    public static final RegistryObject<Block> FISSURE_PRIMAL_MAGMA = BLOCKS.register("fissure_primal_magma", () -> new FissurePrimalMagmaBlock());
    public static final RegistryObject<Block> VOLCANIC_CORE = registerBlock("volcanic_core", () -> new VolcanicCoreBlock()/*, 7*/);
    public static final RegistryObject<Block> FLOOD_BASALT = registerBlock("flood_basalt", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(3.0F, 100.0F).sound(QSoundTypes.FLOOD_BASALT).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> THIN_BONE = registerBlock("thin_bone", () -> new ThinBoneBlock());
    //public static final RegistryObject<Block> BANANA_PEEL = registerBlock("banana_peel", () -> new BlockBananaPeel());
    public static final RegistryObject<Block> FIREFLY_JAR = registerBlock("firefly_jar", () -> new JarBlock(BlockBehaviour.Properties.of().lightLevel((state) -> 15).noOcclusion().noParticlesOnBreak().sound(SoundType.BONE_BLOCK).strength(0.3F, 3.0F)));
    public static final RegistryObject<Block> POLISHED_TUFF_STAIRS = registerBlock("polished_tuff_stairs", () -> new StairBlock(LIMESTONE.get().defaultBlockState(), LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_BRICK_STAIRS = registerBlock("tuff_brick_stairs", () -> new StairBlock(LIMESTONE.get().defaultBlockState(), LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_STAIRS = registerBlock("tuff_stairs", () -> new StairBlock(LIMESTONE.get().defaultBlockState(), LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> POLISHED_TUFF_SLAB = registerBlock("polished_tuff_slab", () -> new SlabBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_SLAB = registerBlock("tuff_slab", () -> new SlabBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_BRICK_SLAB = registerBlock("tuff_brick_slab", () -> new SlabBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_WALL = registerBlock("tuff_wall", () -> new WallBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_BRICK_WALL = registerBlock("tuff_brick_wall", () -> new WallBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> POLISHED_TUFF_WALL = registerBlock("polished_tuff_wall", () -> new WallBlock(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> TUFF_BRICKS = registerBlock("tuff_bricks", () -> new Block(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> CHISELED_TUFF_BRICKS = registerBlock("chiseled_tuff_bricks", () -> new Block(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> CHISELED_TUFF = registerBlock("chiseled_tuff", () -> new Block(LIMESTONE_PROPERTIES));
    public static final RegistryObject<Block> POLISHED_TUFF = registerBlock("polished_tuff", () -> new Block(LIMESTONE_PROPERTIES));

    // Copper Grates
    // Oxidation stages
    public static final RegistryObject<Block> COPPER_GRATE =
            registerBlock("copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    public static final RegistryObject<Block> EXPOSED_COPPER_GRATE =
            registerBlock("exposed_copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    public static final RegistryObject<Block> WEATHERED_COPPER_GRATE =
            registerBlock("weathered_copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    public static final RegistryObject<Block> OXIDIZED_COPPER_GRATE =
            registerBlock("oxidized_copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    // Waxed variants (same props, don’t oxidize)
    public static final RegistryObject<Block> WAXED_COPPER_GRATE =
            registerBlock("waxed_copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    public static final RegistryObject<Block> WAXED_EXPOSED_COPPER_GRATE =
            registerBlock("waxed_exposed_copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    public static final RegistryObject<Block> WAXED_WEATHERED_COPPER_GRATE =
            registerBlock("waxed_weathered_copper_grate", () -> new Block(COPPER_GRATE_PROPS));
    public static final RegistryObject<Block> WAXED_OXIDIZED_COPPER_GRATE =
            registerBlock("waxed_oxidized_copper_grate", () -> new Block(COPPER_GRATE_PROPS));



    // Leaves
    public static final RegistryObject<Block> SILVER_BIRCH_LEAVES = registerBlock("silver_birch_leaves", () -> new CustomLeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion(), QParticles.SILVER_BIRCH_LEAVES));
    public static final RegistryObject<Block> ANCIENT_LEAVES = registerBlock("ancient_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion().isSuffocating((blockState, getter, pos) -> false)));
    public static final RegistryObject<Block> PEWEN_BRANCH = registerBlock("pewen_branch", () -> new PewenBranchBlock());
    public static final RegistryObject<Block> PEWEN_PINES = registerBlock("pewen_pines", () -> new PewenPinesBlock());
    public static final RegistryObject<Block> CYPRESS_LEAVES = registerBlock("cypress_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion().isSuffocating((blockState, getter, pos) -> false)));
    public static final RegistryObject<Block> PALM_LEAVES = registerBlock("palm_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion().isSuffocating((blockState, getter, pos) -> false)));
    public static final RegistryObject<Block> JOSHUA_LEAVES = registerBlock("joshua_leaves", () -> new JoshuaLeavesBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noOcclusion().noCollission()));



    // Branches
    public static final RegistryObject<Block> PALM_BEARD = registerBlock("palm_beard", () -> new BranchBlock(BlockBehaviour.Properties.of().noOcclusion().sound(SoundType.MANGROVE_ROOTS).strength(1.0F, 1.5F).dynamicShape(), BranchBlock.BEARD));
    public static final RegistryObject<Block> JOSHUA_BEARD = registerBlock("joshua_beard", () -> new BranchBlock(BlockBehaviour.Properties.of().noOcclusion().sound(SoundType.MANGROVE_ROOTS).strength(1.0F, 1.5F).dynamicShape(), BranchBlock.BEARD));

    // Wood Blocks
    public static final RegistryObject<Block> SILVER_BIRCH_LOG = registerBlock("silver_birch_log", () -> new AspenLogBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0F).sound(SoundType.BAMBOO_WOOD)));
    public static final RegistryObject<Block> STRIPPED_SILVER_BIRCH_LOG = registerBlock("stripped_silver_birch_log", () -> new AspenLogBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0F).sound(SoundType.BAMBOO_WOOD)));
    public static final RegistryObject<Block> SILVER_BIRCH_WOOD = registerBlock("silver_birch_wood", () -> new AspenLogBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0F).sound(SoundType.BAMBOO_WOOD)));
    public static final RegistryObject<Block> SILVER_BIRCH_PLANKS = registerBlock("silver_birch_planks", () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).mapColor(MapColor.SAND).sound(SoundType.BAMBOO_WOOD)));
    public static final RegistryObject<Block> PEWEN_LOG = registerBlock("pewen_log", () -> new StrippableLogBlock(PEWEN_LOG_PROPERTIES));
    public static final RegistryObject<Block> PEWEN_WOOD = registerBlock("pewen_wood", () -> new StrippableLogBlock(PEWEN_LOG_PROPERTIES));
    public static final RegistryObject<Block> STRIPPED_PEWEN_LOG = registerBlock("stripped_pewen_log", () -> new RotatedPillarBlock(PEWEN_LOG_PROPERTIES));
    public static final RegistryObject<Block> STRIPPED_PEWEN_WOOD = registerBlock("stripped_pewen_wood", () -> new RotatedPillarBlock(PEWEN_LOG_PROPERTIES));
    public static final RegistryObject<Block> PEWEN_PLANKS = registerBlock("pewen_planks", () -> new Block(PEWEN_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> CYPRESS_LOG = registerBlock("cypress_log", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));
    public static final RegistryObject<Block> CYPRESS_WOOD = registerBlock("cypress_wood", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));
    public static final RegistryObject<Block> CYPRESS_PLANKS = registerBlock("cypress_planks", () -> new Block(CYPRESS_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> STRIPPED_CYPRESS_LOG = registerBlock("stripped_cypress_log", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));
    public static final RegistryObject<Block> PALM_LOG = registerBlock("palm_log", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));
    public static final RegistryObject<Block> PALM_WOOD = registerBlock("palm_wood", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));
    public static final RegistryObject<Block> JOSHUA_LOG = registerBlock("joshua_log", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));
    public static final RegistryObject<Block> JOSHUA_WOOD = registerBlock("joshua_wood", () -> new StrippableLogBlock(CYPRESS_LOG_PROPERTIES));

    // Stairs & Slabs
    public static final RegistryObject<Block> SILVER_BIRCH_STAIRS = registerBlock("silver_birch_stairs", () -> new StairBlock(() -> SILVER_BIRCH_PLANKS.get().defaultBlockState(), BlockBehaviour.Properties.copy(SILVER_BIRCH_PLANKS.get())));
    public static final RegistryObject<Block> SILVER_BIRCH_SLAB = registerBlock("silver_birch_slab", () -> new SlabBlock(BlockBehaviour.Properties.copy(SILVER_BIRCH_PLANKS.get())));
    public static final RegistryObject<Block> PEWEN_PLANKS_STAIRS = registerBlock("pewen_stairs", () -> new StairBlock(PEWEN_PLANKS.get().defaultBlockState(), PEWEN_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> PEWEN_PLANKS_SLAB = registerBlock("pewen_slab", () -> new SlabBlock(PEWEN_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> CYPRESS_SLAB = registerBlock("cypress_slab", () -> new SlabBlock(CYPRESS_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> CYPRESS_STAIRS = registerBlock("cypress_stairs", () -> new StairBlock(CYPRESS_PLANKS.get().defaultBlockState(), CYPRESS_PLANKS_PROPERTIES));

    //Doors and Misc
    public static final RegistryObject<Block> PEWEN_PLANKS_FENCE = registerBlock("pewen_fence", () -> new FenceBlock(PEWEN_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> PEWEN_PRESSURE_PLATE = registerBlock("pewen_pressure_plate", () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, BlockBehaviour.Properties.copy(PEWEN_PLANKS.get()).noCollission().strength(0.5F).sound(SoundType.CHERRY_WOOD), BlockSetType.CHERRY));
    public static final RegistryObject<Block> PEWEN_TRAPDOOR = registerBlock("pewen_trapdoor", () -> new TrapDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0F).sound(SoundType.CHERRY_WOOD).noOcclusion(), BlockSetType.CHERRY));
    public static final RegistryObject<Block> PEWEN_BUTTON = registerBlock("pewen_button", () -> new ButtonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).noCollission().strength(0.5F).sound(SoundType.CHERRY_WOOD), BlockSetType.CHERRY, 30, true));
    public static final RegistryObject<Block> PEWEN_FENCE_GATE = registerBlock("pewen_fence_gate", () -> new FenceGateBlock(BlockBehaviour.Properties.copy(PEWEN_PLANKS.get()).strength(2.0F, 3.0F).sound(SoundType.CHERRY_WOOD).forceSolidOn(), SoundEvents.CHERRY_WOOD_FENCE_GATE_CLOSE, SoundEvents.CHERRY_WOOD_FENCE_GATE_CLOSE));
    public static final RegistryObject<Block> PEWEN_DOOR = registerBlock("pewen_door", () -> new DoorBlock(BlockBehaviour.Properties.copy(PEWEN_PLANKS.get()).strength(3.0F).sound(SoundType.CHERRY_WOOD).noOcclusion(), BlockSetType.CHERRY));
    public static final RegistryObject<Block> CYPRESS_DOOR = registerBlock("cypress_door", () -> new DoorBlock(BlockBehaviour.Properties.copy(CYPRESS_PLANKS.get()).strength(3.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion(), BlockSetType.CHERRY));
    public static final RegistryObject<Block> CYPRESS_TRAPDOOR = registerBlock("cypress_trapdoor", () -> new TrapDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion(), BlockSetType.CHERRY));
    public static final RegistryObject<Block> CYPRESS_FENCE = registerBlock("cypress_fence", () -> new FenceBlock(CYPRESS_PLANKS_PROPERTIES));
    public static final RegistryObject<Block> CYPRESS_FENCE_GATE = registerBlock("cypress_fence_gate", () -> new FenceGateBlock(BlockBehaviour.Properties.copy(CYPRESS_PLANKS.get()).strength(2.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).forceSolidOn(), SoundEvents.CHERRY_WOOD_FENCE_GATE_CLOSE, SoundEvents.CHERRY_WOOD_FENCE_GATE_CLOSE));
    public static final RegistryObject<Block> SILVER_BIRCH_TRAPDOOR = registerBlock("silver_birch_trapdoor", () -> new TrapDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion(), BlockSetType.CHERRY));
    public static final RegistryObject<Block> SILVER_BIRCH_DOOR = registerBlock("silver_birch_door", () -> new DoorBlock(BlockBehaviour.Properties.copy(PEWEN_PLANKS.get()).strength(3.0F).sound(SoundType.CHERRY_WOOD).noOcclusion(), BlockSetType.CHERRY));

    // Saplings
    public static final RegistryObject<Block> SILVER_BIRCH_SAPLING = registerBlock("silver_birch_sapling", () -> new ModSaplingBlock(new SilverBirchTreeGrower()));
    public static final RegistryObject<Block> ANCIENT_SAPLING = registerBlock("ancient_sapling", () -> new SaplingBlock(new AncientTreeGrower(), BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)));
    public static final RegistryObject<Block> PEWEN_SAPLING = registerBlock("pewen_sapling", () -> new SaplingBlock(new PewenGrower(), BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)));
    public static final RegistryObject<Block> CYPRESS_SAPLING = registerBlock("cypress_sapling", () -> new SaplingBlock(new CypressTreeGrower(), BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)));
    public static final RegistryObject<Block> PALM_SAPLING = registerBlock("palm_sapling", () -> new SaplingBlock(new PalmTreeGrower(), BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)));
    public static final RegistryObject<Block> JOSHUA_SAPLING = registerBlock("joshua_sapling", () -> new SaplingBlock(new OakTreeGrower(), BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)));

    //Vegetation
    public static final RegistryObject<Block> FIDDLEHEAD = registerBlock("fiddlehead", FiddleheadBlock::new);
    public static final RegistryObject<Block> FLYTRAP = registerBlock("flytrap", () -> new FlytrapBlock());
    public static final RegistryObject<Block> CURLY_FERN = registerBlock("curly_fern", () -> new DoublePlantWithRotationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)));
    public static final RegistryObject<Block> CYCAD = registerBlock("cycad", () -> new CycadBlock());
    public static final RegistryObject<Block> FIREFLYBUSH = registerBlock("fireflybush", FireflybushBlock::new);
    public static final RegistryObject<Block> ARCHAIC_VINE = registerBlock("archaic_vine", () -> new ArchaicVineBlock());
    public static final RegistryObject<Block> ARCHAIC_VINE_PLANT = BLOCKS.register("archaic_vine_plant", () -> new ArchaicVinePlantBlock());
    public static final RegistryObject<Block> TREE_STAR = registerBlock("tree_star", () -> new TreeStarBlock());
    public static final RegistryObject<Block> SPANISH_MOSS = registerBlock("spanish_moss", () -> new SpanishMossBlock(BlockBehaviour.Properties.of().pushReaction(PushReaction.DESTROY).ignitedByLava().randomTicks().noCollission().instabreak().sound(SoundType.LILY_PAD)));
    public static final RegistryObject<Block> SPANISH_MOSS_PLANT = registerBlock("spanish_moss_plant", () -> new SpanishMossPlantBlock(BlockBehaviour.Properties.copy(SPANISH_MOSS.get())));
    public static final RegistryObject<Block> ELEPHANT_EAR = registerBlock("elephant_ear", () -> new ElephantEarBlock(BlockBehaviour.Properties.copy(Blocks.TALL_GRASS)));
    public static final RegistryObject<Block> FLOWERING_LILY_PAD = registerBlock("flowering_lily_pad", () -> new FloweringLilyBlock(BlockBehaviour.Properties.copy(Blocks.LILY_PAD)));
    public static final RegistryObject<Block> SANDY_GRASS = registerBlock("sandy_grass", () -> new RuSandyPlantBlock(BlockBehaviour.Properties.copy(Blocks.GRASS)));

    // Block Entities
    public static final RegistryObject<Block> SOLAR_PANEL = registerBlock("solar_panel", () -> new SolarPanelBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> ELECTRIC_FURNACE = registerBlock("electric_furnace", () -> new ElectricFurnaceBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> BATTERY = registerBlock("battery", () -> new BatteryBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> WIRELESS_CAPACITOR = registerBlock("wireless_capacitor", () -> new WirelessCapacitorBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> PULVERIZER = registerBlock("pulverizer", () -> new PulverizerBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> ENERGY_CONDUIT = registerBlock("energy_conduit", () -> new EnergyConduitBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> COG_BLOCK = registerBlock("cog_block", () -> new CogBlock(BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK).strength(3f, 6f).sound(SoundType.COPPER).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> REDSTONE_RANDOMIZER = registerBlock("redstone_randomizer", () -> new RedstoneRandomizerBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> ELEVATOR = registerBlock("elevator", ElevatorBlock::new);
    public static final RegistryObject<Block> LEAFCUTTER_ANTHILL = registerBlock("leafcutter_anthill", () -> new BlockLeafcutterAnthill());
    public static final RegistryObject<Block> LEAFCUTTER_ANT_CHAMBER = registerBlock("leafcutter_ant_chamber", () -> new BlockLeafcutterAntChamber());
    public static final RegistryObject<Block> BOTANY_POT = registerBlock("botany_pot", () -> new BotanyPotBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> HYDROPONICS_BASIN = registerBlock("hydroponics_basin", () -> new HydroponicsBasinBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> CRAFTER = registerBlock("crafter", () -> new CrafterBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));








    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block){
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block>RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return QItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    // properties helpers
    private static BlockBehaviour.Properties signProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .noCollission().strength(1.0F)
                .sound(SoundType.CHERRY_WOOD);
    }
    private static BlockBehaviour.Properties hangingProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .forceSolidOn()
                .instrument(NoteBlockInstrument.BASS)
                .noCollission().strength(1.0F);
    }


}
