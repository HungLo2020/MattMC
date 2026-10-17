package net.minecraft.world.level.block;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.NativeSoundDefinitions;

public class SoundType {
    private static final SoundType[] NATIVE_TYPES = NativeSoundDefinitions.createSoundTypes();
    public static SoundType nativeView(int id) { return NATIVE_TYPES[id]; }
	public static final SoundType EMPTY = nativeView(NativeSoundDefinitions.typeId("EMPTY"));
	public static final SoundType WOOD = nativeView(NativeSoundDefinitions.typeId("WOOD"));
	public static final SoundType GRAVEL = nativeView(NativeSoundDefinitions.typeId("GRAVEL"));
	public static final SoundType GRASS = nativeView(NativeSoundDefinitions.typeId("GRASS"));
	public static final SoundType LILY_PAD = nativeView(NativeSoundDefinitions.typeId("LILY_PAD"));
	public static final SoundType STONE = nativeView(NativeSoundDefinitions.typeId("STONE"));
	public static final SoundType METAL = nativeView(NativeSoundDefinitions.typeId("METAL"));
	public static final SoundType GLASS = nativeView(NativeSoundDefinitions.typeId("GLASS"));
	public static final SoundType WOOL = nativeView(NativeSoundDefinitions.typeId("WOOL"));
	public static final SoundType SAND = nativeView(NativeSoundDefinitions.typeId("SAND"));
	public static final SoundType SNOW = nativeView(NativeSoundDefinitions.typeId("SNOW"));
	public static final SoundType POWDER_SNOW = nativeView(NativeSoundDefinitions.typeId("POWDER_SNOW"));
	public static final SoundType LADDER = nativeView(NativeSoundDefinitions.typeId("LADDER"));
	public static final SoundType ANVIL = nativeView(NativeSoundDefinitions.typeId("ANVIL"));
	public static final SoundType SLIME_BLOCK = nativeView(NativeSoundDefinitions.typeId("SLIME_BLOCK"));
	public static final SoundType HONEY_BLOCK = nativeView(NativeSoundDefinitions.typeId("HONEY_BLOCK"));
	public static final SoundType WET_GRASS = nativeView(NativeSoundDefinitions.typeId("WET_GRASS"));
	public static final SoundType CORAL_BLOCK = nativeView(NativeSoundDefinitions.typeId("CORAL_BLOCK"));
	public static final SoundType BAMBOO = nativeView(NativeSoundDefinitions.typeId("BAMBOO"));
	public static final SoundType BAMBOO_SAPLING = nativeView(NativeSoundDefinitions.typeId("BAMBOO_SAPLING"));
	public static final SoundType SCAFFOLDING = nativeView(NativeSoundDefinitions.typeId("SCAFFOLDING"));
	public static final SoundType SWEET_BERRY_BUSH = nativeView(NativeSoundDefinitions.typeId("SWEET_BERRY_BUSH"));
	public static final SoundType CROP = nativeView(NativeSoundDefinitions.typeId("CROP"));
	public static final SoundType HARD_CROP = nativeView(NativeSoundDefinitions.typeId("HARD_CROP"));
	public static final SoundType VINE = nativeView(NativeSoundDefinitions.typeId("VINE"));
	public static final SoundType NETHER_WART = nativeView(NativeSoundDefinitions.typeId("NETHER_WART"));
	public static final SoundType LANTERN = nativeView(NativeSoundDefinitions.typeId("LANTERN"));
	public static final SoundType STEM = nativeView(NativeSoundDefinitions.typeId("STEM"));
	public static final SoundType NYLIUM = nativeView(NativeSoundDefinitions.typeId("NYLIUM"));
	public static final SoundType FUNGUS = nativeView(NativeSoundDefinitions.typeId("FUNGUS"));
	public static final SoundType ROOTS = nativeView(NativeSoundDefinitions.typeId("ROOTS"));
	public static final SoundType SHROOMLIGHT = nativeView(NativeSoundDefinitions.typeId("SHROOMLIGHT"));
	public static final SoundType WEEPING_VINES = nativeView(NativeSoundDefinitions.typeId("WEEPING_VINES"));
	public static final SoundType TWISTING_VINES = nativeView(NativeSoundDefinitions.typeId("TWISTING_VINES"));
	public static final SoundType SOUL_SAND = nativeView(NativeSoundDefinitions.typeId("SOUL_SAND"));
	public static final SoundType SOUL_SOIL = nativeView(NativeSoundDefinitions.typeId("SOUL_SOIL"));
	public static final SoundType BASALT = nativeView(NativeSoundDefinitions.typeId("BASALT"));
	public static final SoundType WART_BLOCK = nativeView(NativeSoundDefinitions.typeId("WART_BLOCK"));
	public static final SoundType NETHERRACK = nativeView(NativeSoundDefinitions.typeId("NETHERRACK"));
	public static final SoundType NETHER_BRICKS = nativeView(NativeSoundDefinitions.typeId("NETHER_BRICKS"));
	public static final SoundType NETHER_SPROUTS = nativeView(NativeSoundDefinitions.typeId("NETHER_SPROUTS"));
	public static final SoundType NETHER_ORE = nativeView(NativeSoundDefinitions.typeId("NETHER_ORE"));
	public static final SoundType BONE_BLOCK = nativeView(NativeSoundDefinitions.typeId("BONE_BLOCK"));
	public static final SoundType NETHERITE_BLOCK = nativeView(NativeSoundDefinitions.typeId("NETHERITE_BLOCK"));
	public static final SoundType ANCIENT_DEBRIS = nativeView(NativeSoundDefinitions.typeId("ANCIENT_DEBRIS"));
	public static final SoundType LODESTONE = nativeView(NativeSoundDefinitions.typeId("LODESTONE"));
	public static final SoundType CHAIN = nativeView(NativeSoundDefinitions.typeId("CHAIN"));
	public static final SoundType NETHER_GOLD_ORE = nativeView(NativeSoundDefinitions.typeId("NETHER_GOLD_ORE"));
	public static final SoundType GILDED_BLACKSTONE = nativeView(NativeSoundDefinitions.typeId("GILDED_BLACKSTONE"));
	public static final SoundType CANDLE = nativeView(NativeSoundDefinitions.typeId("CANDLE"));
	public static final SoundType AMETHYST = nativeView(NativeSoundDefinitions.typeId("AMETHYST"));
	public static final SoundType AMETHYST_CLUSTER = nativeView(NativeSoundDefinitions.typeId("AMETHYST_CLUSTER"));
	public static final SoundType SMALL_AMETHYST_BUD = nativeView(NativeSoundDefinitions.typeId("SMALL_AMETHYST_BUD"));
	public static final SoundType MEDIUM_AMETHYST_BUD = nativeView(NativeSoundDefinitions.typeId("MEDIUM_AMETHYST_BUD"));
	public static final SoundType LARGE_AMETHYST_BUD = nativeView(NativeSoundDefinitions.typeId("LARGE_AMETHYST_BUD"));
	public static final SoundType TUFF = nativeView(NativeSoundDefinitions.typeId("TUFF"));
	public static final SoundType TUFF_BRICKS = nativeView(NativeSoundDefinitions.typeId("TUFF_BRICKS"));
	public static final SoundType POLISHED_TUFF = nativeView(NativeSoundDefinitions.typeId("POLISHED_TUFF"));
	public static final SoundType CALCITE = nativeView(NativeSoundDefinitions.typeId("CALCITE"));
	public static final SoundType DRIPSTONE_BLOCK = nativeView(NativeSoundDefinitions.typeId("DRIPSTONE_BLOCK"));
	public static final SoundType POINTED_DRIPSTONE = nativeView(NativeSoundDefinitions.typeId("POINTED_DRIPSTONE"));
	public static final SoundType COPPER = nativeView(NativeSoundDefinitions.typeId("COPPER"));
	public static final SoundType COPPER_BULB = nativeView(NativeSoundDefinitions.typeId("COPPER_BULB"));
	public static final SoundType COPPER_GRATE = nativeView(NativeSoundDefinitions.typeId("COPPER_GRATE"));
	public static final SoundType COPPER_GOLEM_STATUE = nativeView(NativeSoundDefinitions.typeId("COPPER_GOLEM_STATUE"));
	public static final SoundType CAVE_VINES = nativeView(NativeSoundDefinitions.typeId("CAVE_VINES"));
	public static final SoundType SPORE_BLOSSOM = nativeView(NativeSoundDefinitions.typeId("SPORE_BLOSSOM"));
	public static final SoundType CACTUS_FLOWER = nativeView(NativeSoundDefinitions.typeId("CACTUS_FLOWER"));
	public static final SoundType AZALEA = nativeView(NativeSoundDefinitions.typeId("AZALEA"));
	public static final SoundType FLOWERING_AZALEA = nativeView(NativeSoundDefinitions.typeId("FLOWERING_AZALEA"));
	public static final SoundType MOSS_CARPET = nativeView(NativeSoundDefinitions.typeId("MOSS_CARPET"));
	public static final SoundType PINK_PETALS = nativeView(NativeSoundDefinitions.typeId("PINK_PETALS"));
	public static final SoundType LEAF_LITTER = nativeView(NativeSoundDefinitions.typeId("LEAF_LITTER"));
	public static final SoundType MOSS = nativeView(NativeSoundDefinitions.typeId("MOSS"));
	public static final SoundType BIG_DRIPLEAF = nativeView(NativeSoundDefinitions.typeId("BIG_DRIPLEAF"));
	public static final SoundType SMALL_DRIPLEAF = nativeView(NativeSoundDefinitions.typeId("SMALL_DRIPLEAF"));
	public static final SoundType ROOTED_DIRT = nativeView(NativeSoundDefinitions.typeId("ROOTED_DIRT"));
	public static final SoundType HANGING_ROOTS = nativeView(NativeSoundDefinitions.typeId("HANGING_ROOTS"));
	public static final SoundType AZALEA_LEAVES = nativeView(NativeSoundDefinitions.typeId("AZALEA_LEAVES"));
	public static final SoundType SCULK_SENSOR = nativeView(NativeSoundDefinitions.typeId("SCULK_SENSOR"));
	public static final SoundType SCULK_CATALYST = nativeView(NativeSoundDefinitions.typeId("SCULK_CATALYST"));
	public static final SoundType SCULK = nativeView(NativeSoundDefinitions.typeId("SCULK"));
	public static final SoundType SCULK_VEIN = nativeView(NativeSoundDefinitions.typeId("SCULK_VEIN"));
	public static final SoundType SCULK_SHRIEKER = nativeView(NativeSoundDefinitions.typeId("SCULK_SHRIEKER"));
	public static final SoundType GLOW_LICHEN = nativeView(NativeSoundDefinitions.typeId("GLOW_LICHEN"));
	public static final SoundType DEEPSLATE = nativeView(NativeSoundDefinitions.typeId("DEEPSLATE"));
	public static final SoundType DEEPSLATE_BRICKS = nativeView(NativeSoundDefinitions.typeId("DEEPSLATE_BRICKS"));
	public static final SoundType DEEPSLATE_TILES = nativeView(NativeSoundDefinitions.typeId("DEEPSLATE_TILES"));
	public static final SoundType POLISHED_DEEPSLATE = nativeView(NativeSoundDefinitions.typeId("POLISHED_DEEPSLATE"));
	public static final SoundType FROGLIGHT = nativeView(NativeSoundDefinitions.typeId("FROGLIGHT"));
	public static final SoundType FROGSPAWN = nativeView(NativeSoundDefinitions.typeId("FROGSPAWN"));
	public static final SoundType MANGROVE_ROOTS = nativeView(NativeSoundDefinitions.typeId("MANGROVE_ROOTS"));
	public static final SoundType MUDDY_MANGROVE_ROOTS = nativeView(NativeSoundDefinitions.typeId("MUDDY_MANGROVE_ROOTS"));
	public static final SoundType MUD = nativeView(NativeSoundDefinitions.typeId("MUD"));
	public static final SoundType MUD_BRICKS = nativeView(NativeSoundDefinitions.typeId("MUD_BRICKS"));
	public static final SoundType PACKED_MUD = nativeView(NativeSoundDefinitions.typeId("PACKED_MUD"));
	public static final SoundType HANGING_SIGN = nativeView(NativeSoundDefinitions.typeId("HANGING_SIGN"));
	public static final SoundType NETHER_WOOD_HANGING_SIGN = nativeView(NativeSoundDefinitions.typeId("NETHER_WOOD_HANGING_SIGN"));
	public static final SoundType BAMBOO_WOOD_HANGING_SIGN = nativeView(NativeSoundDefinitions.typeId("BAMBOO_WOOD_HANGING_SIGN"));
	public static final SoundType BAMBOO_WOOD = nativeView(NativeSoundDefinitions.typeId("BAMBOO_WOOD"));
	public static final SoundType NETHER_WOOD = nativeView(NativeSoundDefinitions.typeId("NETHER_WOOD"));
	public static final SoundType CHERRY_WOOD = nativeView(NativeSoundDefinitions.typeId("CHERRY_WOOD"));
	public static final SoundType CHERRY_SAPLING = nativeView(NativeSoundDefinitions.typeId("CHERRY_SAPLING"));
	public static final SoundType CHERRY_LEAVES = nativeView(NativeSoundDefinitions.typeId("CHERRY_LEAVES"));
	public static final SoundType CHERRY_WOOD_HANGING_SIGN = nativeView(NativeSoundDefinitions.typeId("CHERRY_WOOD_HANGING_SIGN"));
	public static final SoundType CHISELED_BOOKSHELF = nativeView(NativeSoundDefinitions.typeId("CHISELED_BOOKSHELF"));
	public static final SoundType SHELF = nativeView(NativeSoundDefinitions.typeId("SHELF"));
	public static final SoundType SUSPICIOUS_SAND = nativeView(NativeSoundDefinitions.typeId("SUSPICIOUS_SAND"));
	public static final SoundType SUSPICIOUS_GRAVEL = nativeView(NativeSoundDefinitions.typeId("SUSPICIOUS_GRAVEL"));
	public static final SoundType DECORATED_POT = nativeView(NativeSoundDefinitions.typeId("DECORATED_POT"));
	public static final SoundType DECORATED_POT_CRACKED = nativeView(NativeSoundDefinitions.typeId("DECORATED_POT_CRACKED"));
	public static final SoundType TRIAL_SPAWNER = nativeView(NativeSoundDefinitions.typeId("TRIAL_SPAWNER"));
	public static final SoundType SPONGE = nativeView(NativeSoundDefinitions.typeId("SPONGE"));
	public static final SoundType WET_SPONGE = nativeView(NativeSoundDefinitions.typeId("WET_SPONGE"));
	public static final SoundType VAULT = nativeView(NativeSoundDefinitions.typeId("VAULT"));
	public static final SoundType CREAKING_HEART = nativeView(NativeSoundDefinitions.typeId("CREAKING_HEART"));
	public static final SoundType HEAVY_CORE = nativeView(NativeSoundDefinitions.typeId("HEAVY_CORE"));
	public static final SoundType COBWEB = nativeView(NativeSoundDefinitions.typeId("COBWEB"));
	public static final SoundType SPAWNER = nativeView(NativeSoundDefinitions.typeId("SPAWNER"));
	public static final SoundType RESIN = nativeView(NativeSoundDefinitions.typeId("RESIN"));
	public static final SoundType RESIN_BRICKS = nativeView(NativeSoundDefinitions.typeId("RESIN_BRICKS"));
	public static final SoundType IRON = nativeView(NativeSoundDefinitions.typeId("IRON"));
	public static final SoundType DRIED_GHAST = nativeView(NativeSoundDefinitions.typeId("DRIED_GHAST"));
	public final float volume;
	public final float pitch;
	private final SoundEvent breakSound;
	private final SoundEvent stepSound;
	private final SoundEvent placeSound;
	private final SoundEvent hitSound;
	private final SoundEvent fallSound;

	public SoundType(float f, float g, SoundEvent soundEvent, SoundEvent soundEvent2, SoundEvent soundEvent3, SoundEvent soundEvent4, SoundEvent soundEvent5) {
		this.volume = f;
		this.pitch = g;
		this.breakSound = soundEvent;
		this.stepSound = soundEvent2;
		this.placeSound = soundEvent3;
		this.hitSound = soundEvent4;
		this.fallSound = soundEvent5;
	}

	public float getVolume() {
		return this.volume;
	}

	public float getPitch() {
		return this.pitch;
	}

	public SoundEvent getBreakSound() {
		return this.breakSound;
	}

	public SoundEvent getStepSound() {
		return this.stepSound;
	}

	public SoundEvent getPlaceSound() {
		return this.placeSound;
	}

	public SoundEvent getHitSound() {
		return this.hitSound;
	}

	public SoundEvent getFallSound() {
		return this.fallSound;
	}
}
