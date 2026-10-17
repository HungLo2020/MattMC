package net.minecraft.world.entity;

import com.google.common.collect.ImmutableSet;
import net.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.alexscaves.server.entity.living.*;
import net.alexsmobs.entity.*;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.alexscaves.server.entity.living.RelicheirusEntity;
import net.alexsmobs.entity.EntityKangaroo;
import net.alexsmobs.entity.EntityBunfungus;
import net.alexsmobs.entity.EntityMungus;
import net.alexsmobs.entity.EntityBlobfish;
import net.alexsmobs.entity.EntityCosmicCod;
import net.alexsmobs.entity.EntityCrocodile;
import net.alexsmobs.entity.EntityFlyingFish;
import net.alexsmobs.entity.EntityAlligatorSnappingTurtle;
import net.alexsmobs.entity.EntityAnaconda;
import net.alexsmobs.entity.EntityAnacondaPart;
import net.alexsmobs.entity.EntityTiger;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.TaczBullet;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.ThrownSplashPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.ChestRaft;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.entity.vehicle.MinecartSpawner;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.entity.vehicle.Raft;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class EntityType<T extends Entity> implements FeatureElement, EntityTypeTest<Entity, T> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Holder.Reference<EntityType<?>> builtInRegistryHolder = BuiltInRegistries.ENTITY_TYPE.createIntrusiveHolder(this);
	public static final Codec<EntityType<?>> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec();
	public static final StreamCodec<RegistryFriendlyByteBuf, EntityType<?>> STREAM_CODEC = ByteBufCodecs.registry(Registries.ENTITY_TYPE);
	public static final EntityType<Boat> ACACIA_BOAT = register("acacia_boat", boatFactory(() -> Items.ACACIA_BOAT));
	public static final EntityType<ChestBoat> ACACIA_CHEST_BOAT = register("acacia_chest_boat", chestBoatFactory(() -> Items.ACACIA_CHEST_BOAT));
	public static final EntityType<Allay> ALLAY = register("allay", Allay::new);
	public static final EntityType<EntityAlligatorSnappingTurtle> ALLIGATOR_SNAPPING_TURTLE = register("alligator_snapping_turtle", EntityAlligatorSnappingTurtle::new);
	public static final EntityType<EntityAnaconda> ANACONDA = register("anaconda", EntityAnaconda::new);
	public static final EntityType<EntityAnacondaPart> ANACONDA_PART = register("anaconda_part", EntityAnacondaPart::new);
	public static final EntityType<AreaEffectCloud> AREA_EFFECT_CLOUD = register("area_effect_cloud", AreaEffectCloud::new);
	public static final EntityType<AtlatitanEntity> ATLATITAN = register("atlatitan", AtlatitanEntity::new);
	public static final EntityType<Armadillo> ARMADILLO = register("armadillo", Armadillo::new);
	public static final EntityType<ArmorStand> ARMOR_STAND = register("armor_stand", ArmorStand::new);
	public static final EntityType<Arrow> ARROW = register("arrow", Arrow::new);
	public static final EntityType<TaczBullet> TACZ_BULLET = register("bullet", TaczBullet::new);
	public static final EntityType<Axolotl> AXOLOTL = register("axolotl", Axolotl::new);
	public static final EntityType<ChestRaft> BAMBOO_CHEST_RAFT = register("bamboo_chest_raft", chestRaftFactory(() -> Items.BAMBOO_CHEST_RAFT));
	public static final EntityType<Raft> BAMBOO_RAFT = register("bamboo_raft", raftFactory(() -> Items.BAMBOO_RAFT));
	public static final EntityType<Bat> BAT = register("bat", Bat::new);
	public static final EntityType<Bee> BEE = register("bee", Bee::new);
	public static final EntityType<Boat> BIRCH_BOAT = register("birch_boat", boatFactory(() -> Items.BIRCH_BOAT));
	public static final EntityType<ChestBoat> BIRCH_CHEST_BOAT = register("birch_chest_boat", chestBoatFactory(() -> Items.BIRCH_CHEST_BOAT));
	public static final EntityType<Blaze> BLAZE = register("blaze", Blaze::new);
	public static final EntityType<EntityBlobfish> BLOBFISH = register("blobfish", EntityBlobfish::new);
	public static final EntityType<EntityBlueJay> BLUE_JAY = register("blue_jay", EntityBlueJay::new);
	public static final EntityType<EntityCachalotWhale> CACHALOT_WHALE = register("cachalot_whale", EntityCachalotWhale::new);
	public static final EntityType<EntityCachalotEcho> CACHALOT_ECHO = register("cachalot_echo", EntityCachalotEcho::new);
	public static final EntityType<Display.BlockDisplay> BLOCK_DISPLAY = register("block_display", Display.BlockDisplay::new);
	public static final EntityType<Bogged> BOGGED = register("bogged", Bogged::new);
	public static final EntityType<Breeze> BREEZE = register("breeze", Breeze::new);
	public static final EntityType<BreezeWindCharge> BREEZE_WIND_CHARGE = register("breeze_wind_charge", BreezeWindCharge::new);
	public static final EntityType<Camel> CAMEL = register("camel", Camel::new);
	public static final EntityType<EntityCatfish> CATFISH = register("catfish", EntityCatfish::new);
	public static final EntityType<Cat> CAT = register("cat", Cat::new);
	public static final EntityType<CaveSpider> CAVE_SPIDER = register("cave_spider", CaveSpider::new);
	public static final EntityType<EntityCockroach> COCKROACH = register("cockroach", EntityCockroach::new);
	public static final EntityType<EntityCockroachEgg> COCKROACH_EGG = register("cockroach_egg", EntityCockroachEgg::new);
	public static final EntityType<EntityCombJelly> COMB_JELLY = register("comb_jelly", EntityCombJelly::new);
	public static final EntityType<EntityCosmicCod> COSMIC_COD = register("cosmic_cod", EntityCosmicCod::new);
	public static final EntityType<EntityFrilledShark> FRILLED_SHARK = register("frilled_shark", EntityFrilledShark::new);
	public static final EntityType<EntityCosmaw> COSMAW = register("cosmaw", EntityCosmaw::new);
	public static final EntityType<Boat> CHERRY_BOAT = register("cherry_boat", boatFactory(() -> Items.CHERRY_BOAT));
	public static final EntityType<ChestBoat> CHERRY_CHEST_BOAT = register("cherry_chest_boat", chestBoatFactory(() -> Items.CHERRY_CHEST_BOAT));
	public static final EntityType<MinecartChest> CHEST_MINECART = register("chest_minecart", MinecartChest::new);
	public static final EntityType<Chicken> CHICKEN = register("chicken", Chicken::new);
	public static final EntityType<Cod> COD = register("cod", Cod::new);
	public static final EntityType<EntityCrocodile> CROCODILE = register("crocodile", EntityCrocodile::new);
	public static final EntityType<CopperGolem> COPPER_GOLEM = register("copper_golem", CopperGolem::new);
	public static final EntityType<MinecartCommandBlock> COMMAND_BLOCK_MINECART = register("command_block_minecart", MinecartCommandBlock::new);
	public static final EntityType<Cow> COW = register("cow", Cow::new);
	public static final EntityType<EntityCrow> CROW = register("crow", EntityCrow::new);
	public static final EntityType<Creaking> CREAKING = register("creaking", Creaking::new);
	public static final EntityType<Creeper> CREEPER = register("creeper", Creeper::new);
	public static final EntityType<Boat> DARK_OAK_BOAT = register("dark_oak_boat", boatFactory(() -> Items.DARK_OAK_BOAT));
	public static final EntityType<ChestBoat> DARK_OAK_CHEST_BOAT = register("dark_oak_chest_boat", chestBoatFactory(() -> Items.DARK_OAK_CHEST_BOAT));
	public static final EntityType<Dolphin> DOLPHIN = register("dolphin", Dolphin::new);
	public static final EntityType<Donkey> DONKEY = register("donkey", Donkey::new);
	public static final EntityType<DragonFireball> DRAGON_FIREBALL = register("dragon_fireball", DragonFireball::new);
	public static final EntityType<Drowned> DROWNED = register("drowned", Drowned::new);
	public static final EntityType<ThrownEgg> EGG = register("egg", ThrownEgg::new);
	public static final EntityType<EntityEmu> EMU = register("emu", EntityEmu::new);
	public static final EntityType<EntityEmuEgg> EMU_EGG = register("emu_egg", EntityEmuEgg::new);
	public static final EntityType<EntityElephant> ELEPHANT = register("elephant", EntityElephant::new);
	public static final EntityType<ElderGuardian> ELDER_GUARDIAN = register("elder_guardian", ElderGuardian::new);
	public static final EntityType<EnderMan> ENDERMAN = register("enderman", EnderMan::new);
	public static final EntityType<Endermite> ENDERMITE = register("endermite", Endermite::new);
	public static final EntityType<EnderDragon> ENDER_DRAGON = register("ender_dragon", EnderDragon::new);
	public static final EntityType<ThrownEnderpearl> ENDER_PEARL = register("ender_pearl", ThrownEnderpearl::new);
	public static final EntityType<EntityEndergrade> ENDERGRADE = register("endergrade", EntityEndergrade::new);
	public static final EntityType<EntityEnderiophage> ENDERIOPHAGE = register("enderiophage", EntityEnderiophage::new);
	public static final EntityType<EndCrystal> END_CRYSTAL = register("end_crystal", EndCrystal::new);
	public static final EntityType<Evoker> EVOKER = register("evoker", Evoker::new);
	public static final EntityType<EvokerFangs> EVOKER_FANGS = register("evoker_fangs", EvokerFangs::new);
	public static final EntityType<ThrownExperienceBottle> EXPERIENCE_BOTTLE = register("experience_bottle", ThrownExperienceBottle::new);
	public static final EntityType<ExperienceOrb> EXPERIENCE_ORB = register("experience_orb", ExperienceOrb::new);
	public static final EntityType<EyeOfEnder> EYE_OF_ENDER = register("eye_of_ender", EyeOfEnder::new);
	public static final EntityType<FallingBlockEntity> FALLING_BLOCK = register("falling_block", FallingBlockEntity::new);
	// CRUSHED_BLOCK and FALLING_TREE_BLOCK removed - falling trees functionality not needed
	public static final EntityType<LargeFireball> FIREBALL = register("fireball", LargeFireball::new);
	public static final EntityType<FireworkRocketEntity> FIREWORK_ROCKET = register("firework_rocket", FireworkRocketEntity::new);
	public static final EntityType<EntityFlyingFish> FLYING_FISH = register("flying_fish", EntityFlyingFish::new);
	public static final EntityType<Fox> FOX = register("fox", Fox::new);
	public static final EntityType<Frog> FROG = register("frog", Frog::new);
	public static final EntityType<EntityGeladaMonkey> GELADA_MONKEY = register("gelada_monkey", EntityGeladaMonkey::new);
	public static final EntityType<MinecartFurnace> FURNACE_MINECART = register("furnace_minecart", MinecartFurnace::new);
	public static final EntityType<EntityGazelle> GAZELLE = register("gazelle", EntityGazelle::new);
	public static final EntityType<Ghast> GHAST = register("ghast", Ghast::new);
	public static final EntityType<HappyGhast> HAPPY_GHAST = register("happy_ghast", HappyGhast::new);
	public static final EntityType<Giant> GIANT = register("giant", Giant::new);
	public static final EntityType<GlowItemFrame> GLOW_ITEM_FRAME = register("glow_item_frame", GlowItemFrame::new);
	public static final EntityType<GlowSquid> GLOW_SQUID = register("glow_squid", GlowSquid::new);
	public static final EntityType<EntityGiantSquid> GIANT_SQUID = register("giant_squid", EntityGiantSquid::new);
	public static final EntityType<Goat> GOAT = register("goat", Goat::new);
	public static final EntityType<EntityGorilla> GORILLA = register("gorilla", EntityGorilla::new);
	public static final EntityType<EntityGrizzlyBear> GRIZZLY_BEAR = register("grizzly_bear", EntityGrizzlyBear::new);
	public static final EntityType<GrottoceratopsEntity> GROTTOCERATOPS = register("grottoceratops", GrottoceratopsEntity::new);
	public static final EntityType<EntityHammerheadShark> HAMMERHEAD_SHARK = register("hammerhead_shark", EntityHammerheadShark::new);
	public static final EntityType<Guardian> GUARDIAN = register("guardian", Guardian::new);
	public static final EntityType<Hoglin> HOGLIN = register("hoglin", Hoglin::new);
	public static final EntityType<MinecartHopper> HOPPER_MINECART = register("hopper_minecart", MinecartHopper::new);
	public static final EntityType<EntityHummingbird> HUMMINGBIRD = register("hummingbird", EntityHummingbird::new);
	public static final EntityType<Horse> HORSE = register("horse", Horse::new);
	public static final EntityType<Husk> HUSK = register("husk", Husk::new);
	public static final EntityType<Illusioner> ILLUSIONER = register("illusioner", Illusioner::new);
	public static final EntityType<Interaction> INTERACTION = register("interaction", Interaction::new);
	public static final EntityType<EntityJerboa> JERBOA = register("jerboa", EntityJerboa::new);
	public static final EntityType<IronGolem> IRON_GOLEM = register("iron_golem", IronGolem::new);
	public static final EntityType<ItemEntity> ITEM = register("item", ItemEntity::new);
	public static final EntityType<Display.ItemDisplay> ITEM_DISPLAY = register("item_display", Display.ItemDisplay::new);
	public static final EntityType<ItemFrame> ITEM_FRAME = register("item_frame", ItemFrame::new);
	public static final EntityType<Boat> JUNGLE_BOAT = register("jungle_boat", boatFactory(() -> Items.JUNGLE_BOAT));
	public static final EntityType<ChestBoat> JUNGLE_CHEST_BOAT = register("jungle_chest_boat", chestBoatFactory(() -> Items.JUNGLE_CHEST_BOAT));
	public static final EntityType<LeashFenceKnotEntity> LEASH_KNOT = register("leash_knot", LeashFenceKnotEntity::new);
	public static final EntityType<LightningBolt> LIGHTNING_BOLT = register("lightning_bolt", LightningBolt::new);
	public static final EntityType<Llama> LLAMA = register("llama", Llama::new);
	public static final EntityType<LlamaSpit> LLAMA_SPIT = register("llama_spit", LlamaSpit::new);
	public static final EntityType<net.minecraft.world.entity.animal.EntityLobster> LOBSTER = register("lobster", (type, level) -> new net.minecraft.world.entity.animal.EntityLobster(level));
	public static final EntityType<MagmaCube> MAGMA_CUBE = register("magma_cube", MagmaCube::new);
	public static final EntityType<Boat> MANGROVE_BOAT = register("mangrove_boat", boatFactory(() -> Items.MANGROVE_BOAT));
	public static final EntityType<ChestBoat> MANGROVE_CHEST_BOAT = register("mangrove_chest_boat", chestBoatFactory(() -> Items.MANGROVE_CHEST_BOAT));
	public static final EntityType<Mannequin> MANNEQUIN = register("mannequin", Mannequin::create);
	public static final EntityType<Marker> MARKER = register("marker", Marker::new);
	public static final EntityType<EntityMantisShrimp> MANTIS_SHRIMP = register("mantis_shrimp", EntityMantisShrimp::new);
	public static final EntityType<EntityMimicOctopus> MIMIC_OCTOPUS = register("mimic_octopus", EntityMimicOctopus::new);
	public static final EntityType<Minecart> MINECART = register("minecart", Minecart::new);
	public static final EntityType<MushroomCow> MOOSHROOM = register("mooshroom", MushroomCow::new);
	public static final EntityType<EntityMoose> MOOSE = register("moose", EntityMoose::new);
	public static final EntityType<Mule> MULE = register("mule", Mule::new);
	public static final EntityType<EntityMudBall> MUD_BALL = register("mud_ball", EntityMudBall::new);
	public static final EntityType<EntityTossedItem> TOSSED_ITEM = register("tossed_item", EntityTossedItem::new);
	public static final EntityType<EntityMudskipper> MUDSKIPPER = register("mudskipper", EntityMudskipper::new);
	public static final EntityType<Nautilus> NAUTILUS = register("nautilus", Nautilus::new);
	public static final EntityType<Boat> OAK_BOAT = register("oak_boat", boatFactory(() -> Items.OAK_BOAT));
	public static final EntityType<ChestBoat> OAK_CHEST_BOAT = register("oak_chest_boat", chestBoatFactory(() -> Items.OAK_CHEST_BOAT));
	public static final EntityType<Ocelot> OCELOT = register("ocelot", Ocelot::new);
	public static final EntityType<OminousItemSpawner> OMINOUS_ITEM_SPAWNER = register("ominous_item_spawner", OminousItemSpawner::new);
	public static final EntityType<EntityOrca> ORCA = register("orca", EntityOrca::new);
	public static final EntityType<Painting> PAINTING = register("painting", Painting::new);
	public static final EntityType<Boat> PALE_OAK_BOAT = register("pale_oak_boat", boatFactory(() -> Items.PALE_OAK_BOAT));
	public static final EntityType<ChestBoat> PALE_OAK_CHEST_BOAT = register("pale_oak_chest_boat", chestBoatFactory(() -> Items.PALE_OAK_CHEST_BOAT));
	public static final EntityType<Boat> PEWEN_BOAT = register("pewen_boat", boatFactory(() -> Items.PEWEN_BOAT));
	public static final EntityType<ChestBoat> PEWEN_CHEST_BOAT = register("pewen_chest_boat", chestBoatFactory(() -> Items.PEWEN_CHEST_BOAT));
	public static final EntityType<Panda> PANDA = register("panda", Panda::new);
	public static final EntityType<Parrot> PARROT = register("parrot", Parrot::new);
	public static final EntityType<Phantom> PHANTOM = register("phantom", Phantom::new);
	public static final EntityType<Pig> PIG = register("pig", Pig::new);
	public static final EntityType<Piglin> PIGLIN = register("piglin", Piglin::new);
	public static final EntityType<PiglinBrute> PIGLIN_BRUTE = register("piglin_brute", PiglinBrute::new);
	public static final EntityType<Pillager> PILLAGER = register("pillager", Pillager::new);
	public static final EntityType<EntityPlatypus> PLATYPUS = register("platypus", EntityPlatypus::new);
	public static final EntityType<PolarBear> POLAR_BEAR = register("polar_bear", PolarBear::new);
	public static final EntityType<EntityPotoo> POTOO = register("potoo", EntityPotoo::new);
	public static final EntityType<ThrownSplashPotion> SPLASH_POTION = register("splash_potion", ThrownSplashPotion::new);
	public static final EntityType<ThrownLingeringPotion> LINGERING_POTION = register("lingering_potion", ThrownLingeringPotion::new);
	public static final EntityType<Pufferfish> PUFFERFISH = register("pufferfish", Pufferfish::new);
	public static final EntityType<Rabbit> RABBIT = register("rabbit", Rabbit::new);
	public static final EntityType<EntityRaccoon> RACCOON = register("raccoon", EntityRaccoon::new);
	public static final EntityType<EntityRainFrog> RAIN_FROG = register("rain_frog", EntityRainFrog::new);
	public static final EntityType<EntityRattlesnake> RATTLESNAKE = register("rattlesnake", EntityRattlesnake::new);
	public static final EntityType<EntityRhinoceros> RHINOCEROS = register("rhinoceros", EntityRhinoceros::new);
	public static final EntityType<EntityRoadrunner> ROADRUNNER = register("roadrunner", EntityRoadrunner::new);
	public static final EntityType<Ravager> RAVAGER = register("ravager", Ravager::new);
	public static final EntityType<RelicheirusEntity> RELICHEIRUS = register("relicheirus", RelicheirusEntity::new);
	public static final EntityType<Salmon> SALMON = register("salmon", Salmon::new);
	public static final EntityType<EntityAnteater> ANTEATER = register("anteater", EntityAnteater::new);
	public static final EntityType<EntityMimicube> MIMICUBE = register("mimicube", EntityMimicube::new);
	public static final EntityType<EntitySeagull> SEAGULL = register("seagull", EntitySeagull::new);
	public static final EntityType<EntitySunbird> SUNBIRD = register("sunbird", EntitySunbird::new);
	public static final EntityType<EntityBaldEagle> BALD_EAGLE = register("bald_eagle", EntityBaldEagle::new);
	public static final EntityType<EntityBison> BISON = register("bison", EntityBison::new);
	public static final EntityType<EntityCaiman> CAIMAN = register("caiman", EntityCaiman::new);
	public static final EntityType<EntityCapuchinMonkey> CAPUCHIN_MONKEY = register("capuchin_monkey", EntityCapuchinMonkey::new);
	public static final EntityType<EntityCentipedeHead> CAVE_CENTIPEDE_HEAD = register("cave_centipede_head", EntityCentipedeHead::new);
	public static final EntityType<EntityCentipedeBody> CAVE_CENTIPEDE_BODY = register("cave_centipede_body", (type, level) -> new EntityCentipedeBody(type, level));
	public static final EntityType<EntityCentipedeTail> CAVE_CENTIPEDE_TAIL = register("cave_centipede_tail", EntityCentipedeTail::new);
	public static final EntityType<Sheep> SHEEP = register("sheep", Sheep::new);
	public static final EntityType<EntityShoebill> SHOEBILL = register("shoebill", EntityShoebill::new);
	public static final EntityType<EntitySkunk> SKUNK = register("skunk", EntitySkunk::new);
	public static final EntityType<EntitySpectre> SPECTRE = register("spectre", EntitySpectre::new);
	public static final EntityType<EntitySugarGlider> SUGAR_GLIDER = register("sugar_glider", EntitySugarGlider::new);
	public static final EntityType<Shulker> SHULKER = register("shulker", Shulker::new);
	public static final EntityType<ShulkerBullet> SHULKER_BULLET = register("shulker_bullet", ShulkerBullet::new);
	public static final EntityType<Silverfish> SILVERFISH = register("silverfish", Silverfish::new);
	public static final EntityType<Skeleton> SKELETON = register("skeleton", Skeleton::new);
	public static final EntityType<EntitySkelewag> SKELEWAG = register("skelewag", EntitySkelewag::new);
	public static final EntityType<SkeletonHorse> SKELETON_HORSE = register("skeleton_horse", SkeletonHorse::new);
	public static final EntityType<Slime> SLIME = register("slime", Slime::new);
	public static final EntityType<SmallFireball> SMALL_FIREBALL = register("small_fireball", SmallFireball::new);
	public static final EntityType<Sniffer> SNIFFER = register("sniffer", Sniffer::new);
	public static final EntityType<SubterranodonEntity> SUBTERRANODON = register("subterranodon", SubterranodonEntity::new);
	public static final EntityType<VallumraptorEntity> VALLUMRAPTOR = register("vallumraptor", VallumraptorEntity::new);
	public static final EntityType<EntityKangaroo> KANGAROO = register("kangaroo", EntityKangaroo::new);
	public static final EntityType<EntityKomodoDragon> KOMODO_DRAGON = register("komodo_dragon", EntityKomodoDragon::new);
	public static final EntityType<EntityLeafcutterAnt> LEAFCUTTER_ANT = register("leafcutter_ant", EntityLeafcutterAnt::new);
	public static final EntityType<EntityMungus> MUNGUS = register("mungus", (EntityType<EntityMungus> type, Level level) -> new EntityMungus(type, level));
	public static final EntityType<EntityBunfungus> BUNFUNGUS = register("bunfungus", (EntityType<EntityBunfungus> type, Level level) -> new EntityBunfungus(type, level));
	public static final EntityType<Snowball> SNOWBALL = register("snowball", Snowball::new);
	public static final EntityType<SnowGolem> SNOW_GOLEM = register("snow_golem", SnowGolem::new);
	public static final EntityType<EntitySnowLeopard> SNOW_LEOPARD = register("snow_leopard", EntitySnowLeopard::new);
	public static final EntityType<MinecartSpawner> SPAWNER_MINECART = register("spawner_minecart", MinecartSpawner::new);
	public static final EntityType<SpectralArrow> SPECTRAL_ARROW = register("spectral_arrow", SpectralArrow::new);
	public static final EntityType<Spider> SPIDER = register("spider", Spider::new);
	public static final EntityType<Boat> SPRUCE_BOAT = register("spruce_boat", boatFactory(() -> Items.SPRUCE_BOAT));
	public static final EntityType<ChestBoat> SPRUCE_CHEST_BOAT = register("spruce_chest_boat", chestBoatFactory(() -> Items.SPRUCE_CHEST_BOAT));
	public static final EntityType<Squid> SQUID = register("squid", Squid::new);
	public static final EntityType<Stray> STRAY = register("stray", Stray::new);
	public static final EntityType<Strider> STRIDER = register("strider", Strider::new);
	public static final EntityType<Tadpole> TADPOLE = register("tadpole", Tadpole::new);
	public static final EntityType<EntityTarantulaHawk> TARANTULA_HAWK = register("tarantula_hawk", EntityTarantulaHawk::new);
	public static final EntityType<EntityTasmanianDevil> TASMANIAN_DEVIL = register("tasmanian_devil", EntityTasmanianDevil::new);
	public static final EntityType<EntityTiger> TIGER = register("tiger", EntityTiger::new);
	public static final EntityType<EntityUnderminer> UNDERMINER = register("underminer", EntityUnderminer::new);
	public static final EntityType<EntityWarpedToad> WARPED_TOAD = register("warped_toad", EntityWarpedToad::new);
	public static final EntityType<Display.TextDisplay> TEXT_DISPLAY = register("text_display", Display.TextDisplay::new);
	public static final EntityType<PrimedTnt> TNT = register("tnt", PrimedTnt::new);
	public static final EntityType<MinecartTNT> TNT_MINECART = register("tnt_minecart", MinecartTNT::new);
	public static final EntityType<TraderLlama> TRADER_LLAMA = register("trader_llama", TraderLlama::new);
	public static final EntityType<ThrownTrident> TRIDENT = register("trident", ThrownTrident::new);
	public static final EntityType<TremorsaurusEntity> TREMORSAURUS = register("tremorsaurus", TremorsaurusEntity::new);
	public static final EntityType<TrilocarisEntity> TRILOCARIS = register("trilocaris", TrilocarisEntity::new);
	public static final EntityType<EntityToucan> TOUCAN = register("toucan", EntityToucan::new);
	public static final EntityType<TropicalFish> TROPICAL_FISH = register("tropical_fish", TropicalFish::new);
	public static final EntityType<Turtle> TURTLE = register("turtle", Turtle::new);
	public static final EntityType<EntityTerrapin> TERRAPIN = register("terrapin", EntityTerrapin::new);
	public static final EntityType<Vex> VEX = register("vex", Vex::new);
	public static final EntityType<Villager> VILLAGER = register("villager", Villager::new);
	public static final EntityType<Vindicator> VINDICATOR = register("vindicator", Vindicator::new);
	public static final EntityType<WanderingTrader> WANDERING_TRADER = register("wandering_trader", WanderingTrader::new);
	public static final EntityType<Warden> WARDEN = register("warden", Warden::new);
	public static final EntityType<WindCharge> WIND_CHARGE = register("wind_charge", WindCharge::new);
	public static final EntityType<Witch> WITCH = register("witch", Witch::new);
	public static final EntityType<WitherBoss> WITHER = register("wither", WitherBoss::new);
	public static final EntityType<WitherSkeleton> WITHER_SKELETON = register("wither_skeleton", WitherSkeleton::new);
	public static final EntityType<WitherSkull> WITHER_SKULL = register("wither_skull", WitherSkull::new);
	public static final EntityType<Wolf> WOLF = register("wolf", Wolf::new);
	public static final EntityType<Zoglin> ZOGLIN = register("zoglin", Zoglin::new);
	public static final EntityType<Zombie> ZOMBIE = register("zombie", Zombie::new);
	public static final EntityType<ZombieHorse> ZOMBIE_HORSE = register("zombie_horse", ZombieHorse::new);
	public static final EntityType<ZombieNautilus> ZOMBIE_NAUTILUS = register("zombie_nautilus", ZombieNautilus::new);
	public static final EntityType<ZombieVillager> ZOMBIE_VILLAGER = register("zombie_villager", ZombieVillager::new);
	public static final EntityType<ZombifiedPiglin> ZOMBIFIED_PIGLIN = register("zombified_piglin", ZombifiedPiglin::new);
	public static final EntityType<Player> PLAYER = register("player", nothing());
	public static final EntityType<FishingHook> FISHING_BOBBER = register("fishing_bobber", FishingHook::new);
	private static final Set<EntityType<?>> OP_ONLY_CUSTOM_DATA = Set.of(FALLING_BLOCK, COMMAND_BLOCK_MINECART, SPAWNER_MINECART);
	private final EntityType.EntityFactory<T> factory;
	private final MobCategory category;
	private final ImmutableSet<Block> immuneTo;
	private final boolean serialize;
	private final boolean summon;
	private final boolean fireImmune;
	private final boolean canSpawnFarFromPlayer;
	private final int clientTrackingRange;
	private final int updateInterval;
	private final String descriptionId;
	@Nullable
	private Component description;
	private final Optional<ResourceKey<LootTable>> lootTable;
	private final EntityDimensions dimensions;
	private final float spawnDimensionsScale;
	private final FeatureFlagSet requiredFeatures;
	private final boolean allowedInPeaceful;

	/**
	 * Registers the entity type named {@code name} with its behavior factory.
	 * Every other setting comes from its Rust definition (content/entity), which
	 * must be the next entry of the catalog.
	 */
	private static <T extends Entity> EntityType<T> register(String name, EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> resourceKey = vanillaEntityId(name);
		var definition = NativeEntityTypes.definition(BuiltInRegistries.ENTITY_TYPE.size(), name);
		if (definition.serialize()) {
			Util.fetchChoiceType(References.ENTITY_TREE, resourceKey.location().toString());
		}
		var immuneTo = ImmutableSet.<Block>builder();
		for (String block : definition.immuneTo()) {
			immuneTo.add(BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(block))
				.orElseThrow(() -> new IllegalStateException("Unknown block " + block + " in entity type " + name)));
		}
		var features = new java.util.ArrayList<ResourceLocation>();
		for (String feature : definition.requiredFeatures()) features.add(ResourceLocation.parse(feature));
		var type = new EntityType<>(
			factory,
			definition.category(),
			definition.serialize(),
			definition.summon(),
			definition.fireImmune(),
			definition.spawnFar(),
			immuneTo.build(),
			new EntityDimensions(definition.width(), definition.height(), definition.eyeHeight(),
				new EntityAttachments(definition.attachments()), false),
			definition.spawnScale(),
			definition.trackingRange(),
			definition.updateInterval(),
			Util.makeDescriptionId("entity", resourceKey.location()),
			definition.lootTable()
				? Optional.of(ResourceKey.create(Registries.LOOT_TABLE, resourceKey.location().withPrefix("entities/")))
				: Optional.empty(),
			FeatureFlags.REGISTRY.fromNames(features, unknown -> {
				throw new IllegalStateException("Unknown feature flag " + unknown + " for entity type " + name);
			}),
			definition.peaceful()
		);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, resourceKey, type);
	}

	/** For types Java never instantiates through the factory (players, fishing hooks). */
	private static <T extends Entity> EntityType.EntityFactory<T> nothing() {
		return (entityType, level) -> null;
	}

	private static ResourceKey<EntityType<?>> vanillaEntityId(String string) {
		return ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace(string));
	}

	public static ResourceLocation getKey(EntityType<?> entityType) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
	}

	public static Optional<EntityType<?>> byString(String string) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(string));
	}

	public EntityType(
		EntityType.EntityFactory<T> entityFactory,
		MobCategory mobCategory,
		boolean bl,
		boolean bl2,
		boolean bl3,
		boolean bl4,
		ImmutableSet<Block> immutableSet,
		EntityDimensions entityDimensions,
		float f,
		int i,
		int j,
		String string,
		Optional<ResourceKey<LootTable>> optional,
		FeatureFlagSet featureFlagSet,
		boolean bl5
	) {
		this.factory = entityFactory;
		this.category = mobCategory;
		this.canSpawnFarFromPlayer = bl4;
		this.serialize = bl;
		this.summon = bl2;
		this.fireImmune = bl3;
		this.immuneTo = immutableSet;
		this.dimensions = entityDimensions;
		this.spawnDimensionsScale = f;
		this.clientTrackingRange = i;
		this.updateInterval = j;
		this.descriptionId = string;
		this.lootTable = optional;
		this.requiredFeatures = featureFlagSet;
		this.allowedInPeaceful = bl5;
	}

	@Nullable
	public T spawn(
		ServerLevel serverLevel,
		@Nullable ItemStack itemStack,
		@Nullable LivingEntity livingEntity,
		BlockPos blockPos,
		EntitySpawnReason entitySpawnReason,
		boolean bl,
		boolean bl2
	) {
		Consumer<T> consumer;
		if (itemStack != null) {
			consumer = createDefaultStackConfig(serverLevel, itemStack, livingEntity);
		} else {
			consumer = entity -> {};
		}

		return this.spawn(serverLevel, consumer, blockPos, entitySpawnReason, bl, bl2);
	}

	public static <T extends Entity> Consumer<T> createDefaultStackConfig(Level level, ItemStack itemStack, @Nullable LivingEntity livingEntity) {
		return appendDefaultStackConfig(entity -> {}, level, itemStack, livingEntity);
	}

	public static <T extends Entity> Consumer<T> appendDefaultStackConfig(
		Consumer<T> consumer, Level level, ItemStack itemStack, @Nullable LivingEntity livingEntity
	) {
		return appendCustomEntityStackConfig(appendComponentsConfig(consumer, itemStack), level, itemStack, livingEntity);
	}

	public static <T extends Entity> Consumer<T> appendComponentsConfig(Consumer<T> consumer, ItemStack itemStack) {
		return consumer.andThen(entity -> entity.applyComponentsFromItemStack(itemStack));
	}

	public static <T extends Entity> Consumer<T> appendCustomEntityStackConfig(
		Consumer<T> consumer, Level level, ItemStack itemStack, @Nullable LivingEntity livingEntity
	) {
		TypedEntityData<EntityType<?>> typedEntityData = itemStack.get(DataComponents.ENTITY_DATA);
		return typedEntityData != null ? consumer.andThen(entity -> updateCustomEntityTag(level, livingEntity, entity, typedEntityData)) : consumer;
	}

	@Nullable
	public T spawn(ServerLevel serverLevel, BlockPos blockPos, EntitySpawnReason entitySpawnReason) {
		return this.spawn(serverLevel, null, blockPos, entitySpawnReason, false, false);
	}

	@Nullable
	public T spawn(ServerLevel serverLevel, @Nullable Consumer<T> consumer, BlockPos blockPos, EntitySpawnReason entitySpawnReason, boolean bl, boolean bl2) {
		T entity = this.create(serverLevel, consumer, blockPos, entitySpawnReason, bl, bl2);
		if (entity != null) {
			serverLevel.addFreshEntityWithPassengers(entity);
			if (entity instanceof Mob mob) {
				mob.playAmbientSound();
			}
		}

		return entity;
	}

	@Nullable
	public T create(ServerLevel serverLevel, @Nullable Consumer<T> consumer, BlockPos blockPos, EntitySpawnReason entitySpawnReason, boolean bl, boolean bl2) {
		T entity = this.create(serverLevel, entitySpawnReason);
		if (entity == null) {
			return null;
		} else {
			double d;
			if (bl) {
				entity.setPos(blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5);
				d = getYOffset(serverLevel, blockPos, bl2, entity.getBoundingBox());
			} else {
				d = 0.0;
			}

			entity.snapTo(blockPos.getX() + 0.5, blockPos.getY() + d, blockPos.getZ() + 0.5, Mth.wrapDegrees(serverLevel.random.nextFloat() * 360.0F), 0.0F);
			if (entity instanceof Mob mob) {
				mob.yHeadRot = mob.getYRot();
				mob.yBodyRot = mob.getYRot();
				mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()), entitySpawnReason, null);
			}

			if (consumer != null) {
				consumer.accept(entity);
			}

			return entity;
		}
	}

	protected static double getYOffset(LevelReader levelReader, BlockPos blockPos, boolean bl, AABB aABB) {
		AABB aABB2 = new AABB(blockPos);
		if (bl) {
			aABB2 = aABB2.expandTowards(0.0, -1.0, 0.0);
		}

		Iterable<VoxelShape> iterable = levelReader.getCollisions(null, aABB2);
		return 1.0 + Shapes.collide(Direction.Axis.Y, aABB, iterable, bl ? -2.0 : -1.0);
	}

	public static void updateCustomEntityTag(
		Level level, @Nullable LivingEntity livingEntity, @Nullable Entity entity, TypedEntityData<EntityType<?>> typedEntityData
	) {
		MinecraftServer minecraftServer = level.getServer();
		if (minecraftServer != null && entity != null) {
			if (entity.getType() == typedEntityData.type()) {
				if (level.isClientSide()
					|| !entity.getType().onlyOpCanSetNbt()
					|| livingEntity instanceof Player player && minecraftServer.getPlayerList().isOp(player.nameAndId())) {
					typedEntityData.loadInto(entity);
				}
			}
		}
	}

	public boolean canSerialize() {
		return this.serialize;
	}

	public boolean canSummon() {
		return this.summon;
	}

	public boolean fireImmune() {
		return this.fireImmune;
	}

	public boolean canSpawnFarFromPlayer() {
		return this.canSpawnFarFromPlayer;
	}

	public MobCategory getCategory() {
		return this.category;
	}

	public String getDescriptionId() {
		return this.descriptionId;
	}

	public Component getDescription() {
		if (this.description == null) {
			this.description = Component.translatable(this.getDescriptionId());
		}

		return this.description;
	}

	public String toString() {
		return this.getDescriptionId();
	}

	public String toShortString() {
		int i = this.getDescriptionId().lastIndexOf(46);
		return i == -1 ? this.getDescriptionId() : this.getDescriptionId().substring(i + 1);
	}

	public Optional<ResourceKey<LootTable>> getDefaultLootTable() {
		return this.lootTable;
	}

	public float getWidth() {
		return this.dimensions.width();
	}

	public float getHeight() {
		return this.dimensions.height();
	}

	@Override
	public FeatureFlagSet requiredFeatures() {
		return this.requiredFeatures;
	}

	@Nullable
	public T create(Level level, EntitySpawnReason entitySpawnReason) {
		return !this.isEnabled(level.enabledFeatures()) ? null : this.factory.create(this, level);
	}

	public static Optional<Entity> create(ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason) {
		return Util.ifElse(
			by(valueInput).map(entityType -> entityType.create(level, entitySpawnReason)),
			entity -> entity.load(valueInput),
			() -> LOGGER.warn("Skipping Entity with id {}", valueInput.getStringOr("id", "[invalid]"))
		);
	}

	public static Optional<Entity> create(EntityType<?> entityType, ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason) {
		Optional<Entity> optional = Optional.ofNullable(entityType.create(level, entitySpawnReason));
		optional.ifPresent(entity -> entity.load(valueInput));
		return optional;
	}

	public AABB getSpawnAABB(double d, double e, double f) {
		float g = this.spawnDimensionsScale * this.getWidth() / 2.0F;
		float h = this.spawnDimensionsScale * this.getHeight();
		return new AABB(d - g, e, f - g, d + g, e + h, f + g);
	}

	public boolean isBlockDangerous(BlockState blockState) {
		if (this.immuneTo.contains(blockState.getBlock())) {
			return false;
		} else {
			return !this.fireImmune && NodeEvaluator.isBurningBlock(blockState)
				? true
				: blockState.is(Blocks.WITHER_ROSE) || blockState.is(Blocks.SWEET_BERRY_BUSH) || blockState.is(Blocks.CACTUS) || blockState.is(Blocks.POWDER_SNOW);
		}
	}

	public EntityDimensions getDimensions() {
		return this.dimensions;
	}

	public static Optional<EntityType<?>> by(ValueInput valueInput) {
		return valueInput.read("id", CODEC);
	}

	@Nullable
	public static Entity loadEntityRecursive(CompoundTag compoundTag, Level level, EntitySpawnReason entitySpawnReason, Function<Entity, Entity> function) {
		Entity var5;
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
			var5 = loadEntityRecursive(TagValueInput.create(scopedCollector, level.registryAccess(), compoundTag), level, entitySpawnReason, function);
		}

		return var5;
	}

	@Nullable
	public static Entity loadEntityRecursive(
		EntityType<?> entityType, CompoundTag compoundTag, Level level, EntitySpawnReason entitySpawnReason, Function<Entity, Entity> function
	) {
		Entity var6;
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
			var6 = loadEntityRecursive(entityType, TagValueInput.create(scopedCollector, level.registryAccess(), compoundTag), level, entitySpawnReason, function);
		}

		return var6;
	}

	@Nullable
	public static Entity loadEntityRecursive(ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason, Function<Entity, Entity> function) {
		return (Entity)loadStaticEntity(valueInput, level, entitySpawnReason)
			.map(function)
			.map(entity -> loadPassengersRecursive(entity, valueInput, level, entitySpawnReason, function))
			.orElse(null);
	}

	@Nullable
	public static Entity loadEntityRecursive(
		EntityType<?> entityType, ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason, Function<Entity, Entity> function
	) {
		return (Entity)loadStaticEntity(entityType, valueInput, level, entitySpawnReason)
			.map(function)
			.map(entity -> loadPassengersRecursive(entity, valueInput, level, entitySpawnReason, function))
			.orElse(null);
	}

	private static Entity loadPassengersRecursive(
		Entity entity, ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason, Function<Entity, Entity> function
	) {
		for (ValueInput valueInput2 : valueInput.childrenListOrEmpty("Passengers")) {
			Entity entity2 = loadEntityRecursive(valueInput2, level, entitySpawnReason, function);
			if (entity2 != null) {
				entity2.startRiding(entity, true, false);
			}
		}

		return entity;
	}

	public static Stream<Entity> loadEntitiesRecursive(ValueInput.ValueInputList valueInputList, Level level, EntitySpawnReason entitySpawnReason) {
		return valueInputList.stream().mapMulti((valueInput, consumer) -> loadEntityRecursive(valueInput, level, entitySpawnReason, entity -> {
			consumer.accept(entity);
			return entity;
		}));
	}

	private static Optional<Entity> loadStaticEntity(ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason) {
		try {
			return create(valueInput, level, entitySpawnReason);
		} catch (RuntimeException var4) {
			LOGGER.warn("Exception loading entity: ", (Throwable)var4);
			return Optional.empty();
		}
	}

	private static Optional<Entity> loadStaticEntity(EntityType<?> entityType, ValueInput valueInput, Level level, EntitySpawnReason entitySpawnReason) {
		try {
			return create(entityType, valueInput, level, entitySpawnReason);
		} catch (RuntimeException var5) {
			LOGGER.warn("Exception loading entity: ", (Throwable)var5);
			return Optional.empty();
		}
	}

	public int clientTrackingRange() {
		return this.clientTrackingRange;
	}

	public int updateInterval() {
		return this.updateInterval;
	}

	public boolean trackDeltas() {
		return this != PLAYER
			&& this != LLAMA_SPIT
			&& this != WITHER
			&& this != BAT
			&& this != ITEM_FRAME
			&& this != GLOW_ITEM_FRAME
			&& this != LEASH_KNOT
			&& this != PAINTING
			&& this != END_CRYSTAL
			&& this != EVOKER_FANGS;
	}

	public boolean is(TagKey<EntityType<?>> tagKey) {
		return this.builtInRegistryHolder.is(tagKey);
	}

	public boolean is(HolderSet<EntityType<?>> holderSet) {
		return holderSet.contains(this.builtInRegistryHolder);
	}

	@Nullable
	public T tryCast(Entity entity) {
		return (T)(entity.getType() == this ? entity : null);
	}

	@Override
	public Class<? extends Entity> getBaseClass() {
		return Entity.class;
	}

	@Deprecated
	public Holder.Reference<EntityType<?>> builtInRegistryHolder() {
		return this.builtInRegistryHolder;
	}

	public boolean isAllowedInPeaceful() {
		return this.allowedInPeaceful;
	}

	private static EntityType.EntityFactory<Boat> boatFactory(Supplier<Item> supplier) {
		return (entityType, level) -> new Boat(entityType, level, supplier);
	}

	private static EntityType.EntityFactory<ChestBoat> chestBoatFactory(Supplier<Item> supplier) {
		return (entityType, level) -> new ChestBoat(entityType, level, supplier);
	}

	private static EntityType.EntityFactory<Raft> raftFactory(Supplier<Item> supplier) {
		return (entityType, level) -> new Raft(entityType, level, supplier);
	}

	private static EntityType.EntityFactory<ChestRaft> chestRaftFactory(Supplier<Item> supplier) {
		return (entityType, level) -> new ChestRaft(entityType, level, supplier);
	}

	public boolean onlyOpCanSetNbt() {
		return OP_ONLY_CUSTOM_DATA.contains(this);
	}

	@FunctionalInterface
	public interface EntityFactory<T extends Entity> {
		@Nullable
		T create(EntityType<T> entityType, Level level);
	}
}
