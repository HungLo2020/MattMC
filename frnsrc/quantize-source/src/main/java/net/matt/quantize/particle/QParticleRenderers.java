package net.matt.quantize.particle;

import net.matt.quantize.particle.particles.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
        bus = Bus.MOD,
        value = {Dist.CLIENT}
)
public class QParticleRenderers {
   @SubscribeEvent
   public static void registerParticles(RegisterParticleProvidersEvent event) {

      // Fireflies (unchanged)
      event.registerSpriteSet(QParticles.FIREFLIES.get(), FirefliesParticle::provider);

      // Silver‑birch leaves
      event.registerSpriteSet(
              QParticles.SILVER_BIRCH_LEAVES.get(),
              spriteSet -> (data, level, x, y, z, dx, dy, dz) ->
                      new LeafParticle(level, x, y, z, spriteSet)   // only 5 args
      );

      event.registerSpriteSet(QParticles.FLY.get(), FlyParticle.Factory::new);
      event.registerSpriteSet(QParticles.WHALE_SPLASH.get(), FlyParticle.Factory::new);
   }
}