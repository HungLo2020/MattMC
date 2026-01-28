package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import org.slf4j.Logger;
import com.github.alexthe666.alexsmobs.client.render.RenderBlobfish;
import com.github.alexthe666.alexsmobs.client.render.RenderCombJelly;
import com.github.alexthe666.alexsmobs.client.render.RenderCosmicCod;
import com.github.alexthe666.alexsmobs.client.render.RenderFrilledShark;
import com.github.alexthe666.alexsmobs.client.render.RenderEndergrade;
import com.github.alexthe666.alexsmobs.client.render.RenderFlyingFish;
import com.github.alexthe666.alexsmobs.client.render.RenderGazelle;
import com.github.alexthe666.alexsmobs.client.render.RenderMimicOctopus;
import com.github.alexthe666.alexsmobs.client.render.RenderPlatypus;
import com.github.alexthe666.alexsmobs.client.render.RenderTerrapin;

@Environment(EnvType.CLIENT)
public class EntityRenderers {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<EntityType<?>, EntityRendererProvider<?>> PROVIDERS = new Object2ObjectOpenHashMap<>();

	public static <T extends Entity> void register(EntityType<? extends T> entityType, EntityRendererProvider<T> entityRendererProvider) {
		PROVIDERS.put(entityType, entityRendererProvider);
	}

	public static Map<EntityType<?>, EntityRenderer<?, ?>> createEntityRenderers(EntityRendererProvider.Context context) {
		Builder<EntityType<?>, EntityRenderer<?, ?>> builder = ImmutableMap.builder();
		PROVIDERS.forEach((entityType, entityRendererProvider) -> {
			try {
				builder.put(entityType, entityRendererProvider.create(context));
			} catch (Exception var5) {
				throw new IllegalArgumentException("Failed to create model for " + BuiltInRegistries.ENTITY_TYPE.getKey(entityType), var5);
			}
		});
		return builder.build();
	}

	public static <T extends Avatar & ClientAvatarEntity> Map<PlayerModelType, AvatarRenderer<T>> createAvatarRenderers(EntityRendererProvider.Context context) {
		try {
			return Map.of(PlayerModelType.WIDE, new AvatarRenderer(context, false), PlayerModelType.SLIM, new AvatarRenderer(context, true));
		} catch (Exception var2) {
			throw new IllegalArgumentException("Failed to create avatar models", var2);
		}
	}

	public static boolean validateRegistrations() {
		boolean bl = true;

		for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
			if (entityType != EntityType.PLAYER && entityType != EntityType.MANNEQUIN && !PROVIDERS.containsKey(entityType)) {
				LOGGER.warn("No renderer registered for {}", BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
				bl = false;
			}
		}

		return !bl;
	}

	static {
		register(EntityType.ACACIA_BOAT, context -> new BoatRenderer(context, ModelLayers.ACACIA_BOAT));
		register(EntityType.ACACIA_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.ACACIA_CHEST_BOAT));
		register(EntityType.ALLAY, AllayRenderer::new);
		register(EntityType.ALLIGATOR_SNAPPING_TURTLE, com.github.alexthe666.alexsmobs.client.render.RenderAlligatorSnappingTurtle::new);
		register(EntityType.ANACONDA, com.github.alexthe666.alexsmobs.client.render.RenderAnaconda::new);
		register(EntityType.ANACONDA_PART, com.github.alexthe666.alexsmobs.client.render.RenderAnacondaPart::new);
		register(EntityType.AREA_EFFECT_CLOUD, NoopRenderer::new);
		register(EntityType.ATLATITAN, com.github.alexmodguy.alexscaves.client.render.entity.AtlatitanRenderer::new);
		register(EntityType.ARMADILLO, ArmadilloRenderer::new);
		register(EntityType.ARMOR_STAND, ArmorStandRenderer::new);
		register(EntityType.ARROW, TippableArrowRenderer::new);
		register(EntityType.AXOLOTL, AxolotlRenderer::new);
		register(EntityType.BAMBOO_CHEST_RAFT, context -> new RaftRenderer(context, ModelLayers.BAMBOO_CHEST_RAFT));
		register(EntityType.BAMBOO_RAFT, context -> new RaftRenderer(context, ModelLayers.BAMBOO_RAFT));
		register(EntityType.BALD_EAGLE, com.github.alexthe666.alexsmobs.client.render.RenderBaldEagle::new);
		register(EntityType.BAT, BatRenderer::new);
		register(EntityType.BEE, BeeRenderer::new);
		register(EntityType.BIRCH_BOAT, context -> new BoatRenderer(context, ModelLayers.BIRCH_BOAT));
		register(EntityType.BIRCH_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.BIRCH_CHEST_BOAT));
		register(EntityType.BISON, com.github.alexthe666.alexsmobs.client.render.RenderBison::new);
		register(EntityType.CAIMAN, com.github.alexthe666.alexsmobs.client.render.RenderCaiman::new);
		register(EntityType.CAPUCHIN_MONKEY, com.github.alexthe666.alexsmobs.client.render.RenderCapuchinMonkey::new);
		register(EntityType.CAVE_CENTIPEDE_HEAD, com.github.alexthe666.alexsmobs.client.render.RenderCentipedeHead::new);
		register(EntityType.CAVE_CENTIPEDE_BODY, com.github.alexthe666.alexsmobs.client.render.RenderCentipedeBody::new);
		register(EntityType.CAVE_CENTIPEDE_TAIL, com.github.alexthe666.alexsmobs.client.render.RenderCentipedeTail::new);
		register(EntityType.BLAZE, BlazeRenderer::new);
		register(EntityType.BLOBFISH, RenderBlobfish::new);
		register(EntityType.BLUE_JAY, com.github.alexthe666.alexsmobs.client.render.RenderBlueJay::new);
		register(EntityType.BLOCK_DISPLAY, DisplayRenderer.BlockDisplayRenderer::new);
		register(EntityType.BOGGED, BoggedRenderer::new);
		register(EntityType.BREEZE, BreezeRenderer::new);
		register(EntityType.BUNFUNGUS, com.github.alexthe666.alexsmobs.client.render.RenderBunfungus::new);
		register(EntityType.CACHALOT_WHALE, com.github.alexthe666.alexsmobs.client.render.RenderCachalotWhale::new);
		register(EntityType.CACHALOT_ECHO, com.github.alexthe666.alexsmobs.client.render.RenderCachalotEcho::new);
		register(EntityType.BREEZE_WIND_CHARGE, WindChargeRenderer::new);
		register(EntityType.CAMEL, CamelRenderer::new);
		register(EntityType.CATFISH, com.github.alexthe666.alexsmobs.client.render.RenderCatfish::new);
		register(EntityType.CAT, CatRenderer::new);
		register(EntityType.CAVE_SPIDER, CaveSpiderRenderer::new);
		register(EntityType.CHERRY_BOAT, context -> new BoatRenderer(context, ModelLayers.CHERRY_BOAT));
		register(EntityType.CHERRY_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.CHERRY_CHEST_BOAT));
		register(EntityType.CHEST_MINECART, context -> new MinecartRenderer(context, ModelLayers.CHEST_MINECART));
		register(EntityType.CHICKEN, ChickenRenderer::new);
		register(EntityType.COCKROACH, com.github.alexthe666.alexsmobs.client.render.RenderCockroach::new);
		register(EntityType.COCKROACH_EGG, ThrownItemRenderer::new);
		register(EntityType.COMB_JELLY, RenderCombJelly::new);
		register(EntityType.COSMIC_COD, RenderCosmicCod::new);
		register(EntityType.FRILLED_SHARK, RenderFrilledShark::new);
		register(EntityType.COSMAW, com.github.alexthe666.alexsmobs.client.render.RenderCosmaw::new);
		register(EntityType.COD, CodRenderer::new);
		register(EntityType.CROCODILE, com.github.alexthe666.alexsmobs.client.render.RenderCrocodile::new);
		register(EntityType.COMMAND_BLOCK_MINECART, context -> new MinecartRenderer(context, ModelLayers.COMMAND_BLOCK_MINECART));
		register(EntityType.COPPER_GOLEM, CopperGolemRenderer::new);
		register(EntityType.COW, CowRenderer::new);
		register(EntityType.CROW, com.github.alexthe666.alexsmobs.client.render.RenderCrow::new);
		register(EntityType.CREAKING, CreakingRenderer::new);
		register(EntityType.CREEPER, CreeperRenderer::new);
		register(EntityType.DARK_OAK_BOAT, context -> new BoatRenderer(context, ModelLayers.DARK_OAK_BOAT));
		register(EntityType.DARK_OAK_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.DARK_OAK_CHEST_BOAT));
		register(EntityType.DOLPHIN, DolphinRenderer::new);
		register(EntityType.DONKEY, context -> new DonkeyRenderer(context, DonkeyRenderer.Type.DONKEY));
		register(EntityType.DRAGON_FIREBALL, DragonFireballRenderer::new);
		register(EntityType.DROWNED, DrownedRenderer::new);
		register(EntityType.EGG, ThrownItemRenderer::new);
		register(EntityType.ELEPHANT, com.github.alexthe666.alexsmobs.client.render.RenderElephant::new);
		register(EntityType.EMU, com.github.alexthe666.alexsmobs.client.render.RenderEmu::new);
		register(EntityType.EMU_EGG, ThrownItemRenderer::new);
		register(EntityType.ELDER_GUARDIAN, ElderGuardianRenderer::new);
		register(EntityType.ENDERGRADE, RenderEndergrade::new);
		register(EntityType.ENDERIOPHAGE, com.github.alexthe666.alexsmobs.client.render.RenderEnderiophage::new);
		register(EntityType.ENDERMAN, EndermanRenderer::new);
		register(EntityType.ENDERMITE, EndermiteRenderer::new);
		register(EntityType.ENDER_DRAGON, EnderDragonRenderer::new);
		register(EntityType.ENDER_PEARL, ThrownItemRenderer::new);
		register(EntityType.END_CRYSTAL, EndCrystalRenderer::new);
		register(EntityType.EVOKER, EvokerRenderer::new);
		register(EntityType.EVOKER_FANGS, EvokerFangsRenderer::new);
		register(EntityType.EXPERIENCE_BOTTLE, ThrownItemRenderer::new);
		register(EntityType.EXPERIENCE_ORB, ExperienceOrbRenderer::new);
		register(EntityType.EYE_OF_ENDER, context -> new ThrownItemRenderer(context, 1.0F, true));
		register(EntityType.FALLING_BLOCK, FallingBlockRenderer::new);
		register(EntityType.FIREBALL, context -> new ThrownItemRenderer(context, 3.0F, true));
		register(EntityType.FIREWORK_ROCKET, FireworkEntityRenderer::new);
		register(EntityType.FISHING_BOBBER, FishingHookRenderer::new);
		register(EntityType.FLYING_FISH, RenderFlyingFish::new);
		register(EntityType.FOX, FoxRenderer::new);
		register(EntityType.FROG, FrogRenderer::new);
		register(EntityType.FURNACE_MINECART, context -> new MinecartRenderer(context, ModelLayers.FURNACE_MINECART));
		register(EntityType.GAZELLE, RenderGazelle::new);
		register(EntityType.GELADA_MONKEY, com.github.alexthe666.alexsmobs.client.render.RenderGeladaMonkey::new);
		register(EntityType.GHAST, GhastRenderer::new);
		register(EntityType.HAPPY_GHAST, HappyGhastRenderer::new);
		register(EntityType.GIANT, context -> new GiantMobRenderer(context, 6.0F));
		register(EntityType.GLOW_ITEM_FRAME, ItemFrameRenderer::new);
		register(
			EntityType.GLOW_SQUID,
			context -> new GlowSquidRenderer(
				context, new SquidModel(context.bakeLayer(ModelLayers.GLOW_SQUID)), new SquidModel(context.bakeLayer(ModelLayers.GLOW_SQUID_BABY))
			)
		);
		register(EntityType.GIANT_SQUID, com.github.alexthe666.alexsmobs.client.render.RenderGiantSquid::new);
		register(EntityType.GOAT, GoatRenderer::new);
		register(EntityType.GORILLA, com.github.alexthe666.alexsmobs.client.render.RenderGorilla::new);
		register(EntityType.GRIZZLY_BEAR, com.github.alexthe666.alexsmobs.client.render.RenderGrizzlyBear::new);
		register(EntityType.GROTTOCERATOPS, com.github.alexmodguy.alexscaves.client.render.entity.GrottoceratopsRenderer::new);
		register(EntityType.GUARDIAN, GuardianRenderer::new);
		register(EntityType.HAMMERHEAD_SHARK, com.github.alexthe666.alexsmobs.client.render.RenderHammerheadShark::new);
		register(EntityType.HOGLIN, HoglinRenderer::new);
		register(EntityType.HOPPER_MINECART, context -> new MinecartRenderer(context, ModelLayers.HOPPER_MINECART));
		register(EntityType.HUMMINGBIRD, com.github.alexthe666.alexsmobs.client.render.RenderHummingbird::new);
		register(EntityType.HORSE, HorseRenderer::new);
		register(EntityType.HUSK, HuskRenderer::new);
		register(EntityType.ILLUSIONER, IllusionerRenderer::new);
		register(EntityType.INTERACTION, NoopRenderer::new);
		register(EntityType.JERBOA, com.github.alexthe666.alexsmobs.client.render.RenderJerboa::new);
		register(EntityType.IRON_GOLEM, IronGolemRenderer::new);
		register(EntityType.ITEM, ItemEntityRenderer::new);
		register(EntityType.ITEM_DISPLAY, DisplayRenderer.ItemDisplayRenderer::new);
		register(EntityType.ITEM_FRAME, ItemFrameRenderer::new);
		register(EntityType.JUNGLE_BOAT, context -> new BoatRenderer(context, ModelLayers.JUNGLE_BOAT));
		register(EntityType.JUNGLE_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.JUNGLE_CHEST_BOAT));
		register(EntityType.KANGAROO, com.github.alexthe666.alexsmobs.client.render.KangarooRenderer::new);
		register(EntityType.KOMODO_DRAGON, com.github.alexthe666.alexsmobs.client.render.RenderKomodoDragon::new);
		register(EntityType.LEAFCUTTER_ANT, com.github.alexthe666.alexsmobs.client.render.RenderLeafcutterAnt::new);
		register(EntityType.LEASH_KNOT, LeashKnotRenderer::new);
		register(EntityType.LIGHTNING_BOLT, LightningBoltRenderer::new);
		register(EntityType.LINGERING_POTION, ThrownItemRenderer::new);
		register(EntityType.LLAMA, context -> new LlamaRenderer(context, ModelLayers.LLAMA, ModelLayers.LLAMA_BABY));
		register(EntityType.LLAMA_SPIT, LlamaSpitRenderer::new);
		register(EntityType.LOBSTER, net.minecraft.client.renderer.entity.RenderLobster::new);
		register(EntityType.MAGMA_CUBE, MagmaCubeRenderer::new);
		register(EntityType.MANGROVE_BOAT, context -> new BoatRenderer(context, ModelLayers.MANGROVE_BOAT));
		register(EntityType.MANGROVE_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.MANGROVE_CHEST_BOAT));
		register(EntityType.MANTIS_SHRIMP, com.github.alexthe666.alexsmobs.client.render.RenderMantisShrimp::new);
		register(EntityType.MARKER, NoopRenderer::new);
		register(EntityType.MIMIC_OCTOPUS, RenderMimicOctopus::new);
		register(EntityType.MINECART, context -> new MinecartRenderer(context, ModelLayers.MINECART));
		register(EntityType.MOOSHROOM, MushroomCowRenderer::new);
		register(EntityType.MUD_BALL, ThrownItemRenderer::new);
		register(EntityType.MUDSKIPPER, com.github.alexthe666.alexsmobs.client.render.RenderMudskipper::new);
		register(EntityType.MOOSE, com.github.alexthe666.alexsmobs.client.render.RenderMoose::new);
		register(EntityType.MULE, context -> new DonkeyRenderer(context, DonkeyRenderer.Type.MULE));
		register(EntityType.MUNGUS, com.github.alexthe666.alexsmobs.client.render.RenderMungus::new);
		register(EntityType.OAK_BOAT, context -> new BoatRenderer(context, ModelLayers.OAK_BOAT));
		register(EntityType.OAK_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.OAK_CHEST_BOAT));
		register(EntityType.OCELOT, OcelotRenderer::new);
		register(EntityType.ORCA, com.github.alexthe666.alexsmobs.client.render.RenderOrca::new);
		register(EntityType.OMINOUS_ITEM_SPAWNER, OminousItemSpawnerRenderer::new);
		register(EntityType.PAINTING, PaintingRenderer::new);
		register(EntityType.PALE_OAK_BOAT, context -> new BoatRenderer(context, ModelLayers.PALE_OAK_BOAT));
		register(EntityType.PALE_OAK_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.PALE_OAK_CHEST_BOAT));
		register(EntityType.PANDA, PandaRenderer::new);
		register(EntityType.PARROT, ParrotRenderer::new);
		register(EntityType.PHANTOM, PhantomRenderer::new);
		register(EntityType.PIG, PigRenderer::new);
		register(
			EntityType.PIGLIN,
			context -> new PiglinRenderer(context, ModelLayers.PIGLIN, ModelLayers.PIGLIN_BABY, ModelLayers.PIGLIN_ARMOR, ModelLayers.PIGLIN_BABY_ARMOR)
		);
		register(
			EntityType.PIGLIN_BRUTE,
			context -> new PiglinRenderer(context, ModelLayers.PIGLIN_BRUTE, ModelLayers.PIGLIN_BRUTE, ModelLayers.PIGLIN_BRUTE_ARMOR, ModelLayers.PIGLIN_BRUTE_ARMOR)
		);
		register(EntityType.PILLAGER, PillagerRenderer::new);
		register(EntityType.PLATYPUS, com.github.alexthe666.alexsmobs.client.render.RenderPlatypus::new);
		register(EntityType.POLAR_BEAR, PolarBearRenderer::new);
		register(EntityType.POTOO, com.github.alexthe666.alexsmobs.client.render.RenderPotoo::new);
		register(EntityType.PUFFERFISH, PufferfishRenderer::new);
		register(EntityType.RABBIT, RabbitRenderer::new);
		register(EntityType.RACCOON, com.github.alexthe666.alexsmobs.client.render.RenderRaccoon::new);
		register(EntityType.RAIN_FROG, com.github.alexthe666.alexsmobs.client.render.RenderRainFrog::new);
		register(EntityType.RATTLESNAKE, com.github.alexthe666.alexsmobs.client.render.RenderRattlesnake::new);
		register(EntityType.RHINOCEROS, com.github.alexthe666.alexsmobs.client.render.RenderRhinoceros::new);
		register(EntityType.ROADRUNNER, com.github.alexthe666.alexsmobs.client.render.RenderRoadrunner::new);
		register(EntityType.RAVAGER, RavagerRenderer::new);
		register(EntityType.RELICHEIRUS, com.github.alexmodguy.alexscaves.client.render.entity.RelicheirusRenderer::new);
		register(EntityType.SALMON, SalmonRenderer::new);
		register(EntityType.ANTEATER, com.github.alexthe666.alexsmobs.client.render.RenderAnteater::new);
		register(EntityType.SEAGULL, com.github.alexthe666.alexsmobs.client.render.RenderSeagull::new);
		register(EntityType.SHEEP, SheepRenderer::new);
		register(EntityType.SHOEBILL, com.github.alexthe666.alexsmobs.client.render.RenderShoebill::new);
		register(EntityType.SKUNK, com.github.alexthe666.alexsmobs.client.render.RenderSkunk::new);
		register(EntityType.SPECTRE, com.github.alexthe666.alexsmobs.client.render.RenderSpectre::new);
		register(EntityType.SUGAR_GLIDER, com.github.alexthe666.alexsmobs.client.render.RenderSugarGlider::new);
		register(EntityType.SUNBIRD, com.github.alexthe666.alexsmobs.client.render.RenderSunbird::new);
		register(EntityType.SHULKER, ShulkerRenderer::new);
		register(EntityType.SHULKER_BULLET, ShulkerBulletRenderer::new);
		register(EntityType.SILVERFISH, SilverfishRenderer::new);
		register(EntityType.SKELETON, SkeletonRenderer::new);
		register(EntityType.SKELEWAG, com.github.alexthe666.alexsmobs.client.render.RenderSkelewag::new);
		register(EntityType.SKELETON_HORSE, context -> new UndeadHorseRenderer(context, UndeadHorseRenderer.Type.SKELETON));
		register(EntityType.SLIME, SlimeRenderer::new);
		register(EntityType.SMALL_FIREBALL, context -> new ThrownItemRenderer(context, 0.75F, true));
		register(EntityType.SNIFFER, SnifferRenderer::new);
		register(EntityType.SNOWBALL, ThrownItemRenderer::new);
		register(EntityType.SNOW_GOLEM, SnowGolemRenderer::new);
		register(EntityType.SNOW_LEOPARD, com.github.alexthe666.alexsmobs.client.render.RenderSnowLeopard::new);
		register(EntityType.SUBTERRANODON, com.github.alexmodguy.alexscaves.client.render.entity.SubterranodonRenderer::new);
		register(EntityType.TREMORSAURUS, com.github.alexmodguy.alexscaves.client.render.entity.TremorsaurusRenderer::new);
		register(EntityType.TRILOCARIS, com.github.alexmodguy.alexscaves.client.render.entity.TrilocarisRenderer::new);
		register(EntityType.TOUCAN, com.github.alexthe666.alexsmobs.client.render.RenderToucan::new);
		register(EntityType.TARANTULA_HAWK, com.github.alexthe666.alexsmobs.client.render.RenderTarantulaHawk::new);
		register(EntityType.TASMANIAN_DEVIL, com.github.alexthe666.alexsmobs.client.render.RenderTasmanianDevil::new);
		register(EntityType.TIGER, com.github.alexthe666.alexsmobs.client.render.RenderTiger::new);
		register(EntityType.UNDERMINER, com.github.alexthe666.alexsmobs.client.render.RenderUnderminer::new);
		register(EntityType.WARPED_TOAD, com.github.alexthe666.alexsmobs.client.render.RenderWarpedToad::new);
		register(EntityType.VALLUMRAPTOR, com.github.alexmodguy.alexscaves.client.render.entity.VallumraptorRenderer::new);
		register(EntityType.SPAWNER_MINECART, context -> new MinecartRenderer(context, ModelLayers.SPAWNER_MINECART));
		register(EntityType.SPECTRAL_ARROW, SpectralArrowRenderer::new);
		register(EntityType.SPIDER, SpiderRenderer::new);
		register(EntityType.SPLASH_POTION, ThrownItemRenderer::new);
		register(EntityType.SPRUCE_BOAT, context -> new BoatRenderer(context, ModelLayers.SPRUCE_BOAT));
		register(EntityType.SPRUCE_CHEST_BOAT, context -> new BoatRenderer(context, ModelLayers.SPRUCE_CHEST_BOAT));
		register(
			EntityType.SQUID,
			context -> new SquidRenderer(context, new SquidModel(context.bakeLayer(ModelLayers.SQUID)), new SquidModel(context.bakeLayer(ModelLayers.SQUID_BABY)))
		);
		register(EntityType.STRAY, StrayRenderer::new);
		register(EntityType.STRIDER, StriderRenderer::new);
		register(EntityType.TADPOLE, TadpoleRenderer::new);
		register(EntityType.TEXT_DISPLAY, DisplayRenderer.TextDisplayRenderer::new);
		register(EntityType.TNT, TntRenderer::new);
		register(EntityType.TNT_MINECART, TntMinecartRenderer::new);
		register(EntityType.TRADER_LLAMA, context -> new LlamaRenderer(context, ModelLayers.TRADER_LLAMA, ModelLayers.TRADER_LLAMA_BABY));
		register(EntityType.TRIDENT, ThrownTridentRenderer::new);
		register(EntityType.TROPICAL_FISH, TropicalFishRenderer::new);
		register(EntityType.TURTLE, TurtleRenderer::new);
		register(EntityType.TERRAPIN, RenderTerrapin::new);
		register(EntityType.VEX, VexRenderer::new);
		register(EntityType.VILLAGER, VillagerRenderer::new);
		register(EntityType.VINDICATOR, VindicatorRenderer::new);
		register(EntityType.WANDERING_TRADER, WanderingTraderRenderer::new);
		register(EntityType.WARDEN, WardenRenderer::new);
		register(EntityType.WIND_CHARGE, WindChargeRenderer::new);
		register(EntityType.WITCH, WitchRenderer::new);
		register(EntityType.WITHER, WitherBossRenderer::new);
		register(EntityType.WITHER_SKELETON, WitherSkeletonRenderer::new);
		register(EntityType.WITHER_SKULL, WitherSkullRenderer::new);
		register(EntityType.WOLF, WolfRenderer::new);
		register(EntityType.ZOGLIN, ZoglinRenderer::new);
		register(EntityType.ZOMBIE, ZombieRenderer::new);
		register(EntityType.ZOMBIE_HORSE, context -> new UndeadHorseRenderer(context, UndeadHorseRenderer.Type.ZOMBIE));
		register(EntityType.ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
		register(
			EntityType.ZOMBIFIED_PIGLIN,
			context -> new ZombifiedPiglinRenderer(
				context, ModelLayers.ZOMBIFIED_PIGLIN, ModelLayers.ZOMBIFIED_PIGLIN_BABY, ModelLayers.ZOMBIFIED_PIGLIN_ARMOR, ModelLayers.ZOMBIFIED_PIGLIN_BABY_ARMOR
			)
		);
	}
}
