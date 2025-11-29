package net.matt.quantize.entities;

import net.matt.quantize.Quantize;
import net.matt.quantize.entities.render.*;
import net.matt.quantize.render.renderer.CrushedBlockRenderer;
import net.matt.quantize.render.renderer.FallingTreeBlockRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class QEntityRenderers {
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(QEntities.CRAB.get(), CrabRenderer::new);
        event.registerEntityRenderer(QEntities.CAIMAN.get(), RenderCaiman::new);
        event.registerEntityRenderer(QEntities.CROCODILE.get(), RenderCrocodile::new);
        event.registerEntityRenderer(QEntities.LOBSTER.get(), RenderLobster::new);
        event.registerEntityRenderer(QEntities.MANTIS_SHRIMP.get(), RenderMantisShrimp::new);
        event.registerEntityRenderer(QEntities.MIMIC_OCTOPUS.get(), RenderMimicOctopus::new);
        event.registerEntityRenderer(QEntities.ALLIGATOR_SNAPPING_TURTLE.get(), RenderAlligatorSnappingTurtle::new);
        event.registerEntityRenderer(QEntities.ANACONDA.get(), RenderAnaconda::new);
        event.registerEntityRenderer(QEntities.ANACONDA_PART.get(), RenderAnacondaPart::new);
        event.registerEntityRenderer(QEntities.ANTEATER.get(), RenderAnteater::new);
        event.registerEntityRenderer(QEntities.LEAFCUTTER_ANT.get(), RenderLeafcutterAnt::new);
        event.registerEntityRenderer(QEntities.BALD_EAGLE.get(), RenderBaldEagle::new);
        event.registerEntityRenderer(QEntities.SHOEBILL.get(), RenderShoebill::new);
        event.registerEntityRenderer(QEntities.CACHALOT_WHALE.get(), RenderCachalotWhale::new);
        event.registerEntityRenderer(QEntities.GIANT_SQUID.get(), RenderGiantSquid::new);
        event.registerEntityRenderer(QEntities.CACHALOT_ECHO.get(), RenderCachalotEcho::new);
        event.registerEntityRenderer(QEntities.ORCA.get(), RenderOrca::new);
        event.registerEntityRenderer(QEntities.ATLATITAN.get(), AtlatitanRenderer::new);
        event.registerEntityRenderer(QEntities.SUBTERRANODON.get(), SubterranodonRenderer::new);
        event.registerEntityRenderer(QEntities.VALLUMRAPTOR.get(), VallumraptorRenderer::new);
        event.registerEntityRenderer(QEntities.GROTTOCERATOPS.get(), GrottoceratopsRenderer::new);
        event.registerEntityRenderer(QEntities.TRILOCARIS.get(), TrilocarisRenderer::new);
        event.registerEntityRenderer(QEntities.TREMORSAURUS.get(), TremorsaurusRenderer::new);
        event.registerEntityRenderer(QEntities.RELICHEIRUS.get(), RelicheirusRenderer::new);
        event.registerEntityRenderer(QEntities.LUXTRUCTOSAURUS.get(), LuxtructosaurusRenderer::new);
        event.registerEntityRenderer(QEntities.FALLING_TREE_BLOCK.get(), FallingTreeBlockRenderer::new);
        event.registerEntityRenderer(QEntities.CRUSHED_BLOCK.get(), CrushedBlockRenderer::new);
        event.registerEntityRenderer(QEntities.TEPHRA.get(), TephraRenderer::new);
        event.registerEntityRenderer(QEntities.DINOSAUR_SPIRIT.get(), DinosaurSpiritRenderer::new);
        event.registerEntityRenderer(QEntities.CAPUCHIN_MONKEY.get(), RenderCapuchinMonkey::new);
        event.registerEntityRenderer(QEntities.CATFISH.get(), RenderCatfish::new);
        event.registerEntityRenderer(QEntities.FRILLED_SHARK.get(), RenderFrilledShark::new);
        event.registerEntityRenderer(QEntities.BLOBFISH.get(), RenderBlobfish::new);
        event.registerEntityRenderer(QEntities.CENTIPEDE_HEAD.get(), RenderCentipedeHead::new);
        event.registerEntityRenderer(QEntities.CENTIPEDE_BODY.get(), RenderCentipedeBody::new);
        event.registerEntityRenderer(QEntities.CENTIPEDE_TAIL.get(), RenderCentipedeTail::new);
        event.registerEntityRenderer(QEntities.COCKROACH.get(), RenderCockroach::new);
        event.registerEntityRenderer(QEntities.EMU.get(), RenderEmu::new);
        event.registerEntityRenderer(QEntities.COMB_JELLY.get(), RenderCombJelly::new);
        event.registerEntityRenderer(QEntities.CROW.get(), RenderCrow::new);
        event.registerEntityRenderer(QEntities.COSMIC_COD.get(), RenderCosmicCod::new);
        event.registerEntityRenderer(QEntities.ELEPHANT.get(), RenderElephant::new);
        event.registerEntityRenderer(QEntities.ENDERGRADE.get(), RenderEndergrade::new);
        event.registerEntityRenderer(QEntities.ENDERIOPHAGE.get(), RenderEnderiophage::new);
        event.registerEntityRenderer(QEntities.GAZELLE.get(), RenderGazelle::new);
        event.registerEntityRenderer(QEntities.GELADA_MONKEY.get(), RenderGeladaMonkey::new);
        event.registerEntityRenderer(QEntities.GORILLA.get(), RenderGorilla::new);
        event.registerEntityRenderer(QEntities.GRIZZLY_BEAR.get(), RenderGrizzlyBear::new);
        event.registerEntityRenderer(QEntities.HAMMERHEAD_SHARK.get(), RenderHammerheadShark::new);
        event.registerEntityRenderer(QEntities.KOMODO_DRAGON.get(), RenderKomodoDragon::new);
        event.registerEntityRenderer(QEntities.UNDERMINER.get(), RenderUnderminer::new);
        event.registerEntityRenderer(QEntities.TOUCAN.get(), RenderToucan::new);
        event.registerEntityRenderer(QEntities.TIGER.get(), RenderTiger::new);
        event.registerEntityRenderer(QEntities.SNOW_LEOPARD.get(), RenderSnowLeopard::new);
        event.registerEntityRenderer(QEntities.SEAL.get(), RenderSeal::new);
        event.registerEntityRenderer(QEntities.SEAGULL.get(), RenderSeagull::new);
        event.registerEntityRenderer(QEntities.ROADRUNNER.get(), RenderRoadrunner::new);
        event.registerEntityRenderer(QEntities.RHINOCEROS.get(), RenderRhinoceros::new);
        event.registerEntityRenderer(QEntities.POTOO.get(), RenderPotoo::new);

        // Vanilla overrides
        event.registerEntityRenderer(EntityType.COW, RenderCowVariant::new);
        event.registerEntityRenderer(EntityType.CHICKEN, RenderChickenVariant::new);
        event.registerEntityRenderer(EntityType.SKELETON, RenderSkeletonVariant::new);
        event.registerEntityRenderer(EntityType.ZOMBIE, RenderZombieVariant::new);


    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(QEntityRenderers::registerEntityRenderers);
    }
}