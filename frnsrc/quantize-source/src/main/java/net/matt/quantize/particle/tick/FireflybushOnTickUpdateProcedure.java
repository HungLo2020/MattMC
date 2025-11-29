package net.matt.quantize.particle.tick;

import net.matt.quantize.particle.QParticles;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

public class FireflybushOnTickUpdateProcedure {

   public static void execute(LevelAccessor world, double x, double y, double z) {

      /* 1 ▸ guards ---------------------------------------------------------- */
      if (!(world instanceof Level lvl) || lvl.isClientSide() || lvl.isDay()) {
         return;                       // server‑side, night‑time only
      }
      ServerLevel level = (ServerLevel) world;
      BlockPos pos     = BlockPos.containing(x, y, z);

      /* 2 ▸ spawn particles ------------------------------------------------- */
      // (randomTickSpeed default ≈ 3‑4 ticks/min → raise chance a bit)
      if (level.getRandom().nextFloat() < 0.40f) {          // 40 % spawn chance
         double offX = Mth.nextDouble(level.getRandom(), -4.5, 4.5);
         double offY = Mth.nextDouble(level.getRandom(),  1.5, 5.5);
         double offZ = Mth.nextDouble(level.getRandom(), -4.5, 4.5);

         double velX = Mth.nextDouble(level.getRandom(), -0.75, 0.75);
         double velY = Mth.nextDouble(level.getRandom(), -0.01, 0.75);
         double velZ = Mth.nextDouble(level.getRandom(), -0.75, 0.75);

         level.sendParticles((SimpleParticleType) QParticles.FIREFLIES.get(),
                 x + 0.5 + offX, y + offY, z + 0.5 + offZ,
                 1, velX, velY, velZ, 0.2);
      }

      /* 3 ▸ play rustling sound (rarer‑now) -------------------------------- */
      if (level.getRandom().nextInt(240) == 0) {            // ~½‑1 per minute
         int idx = Mth.nextInt(level.getRandom(), 1, 11);
         SoundEvent snd = ForgeRegistries.SOUND_EVENTS
                 .getValue(new ResourceIdentifier("block.firefly_bush.bush" + idx));
         if (snd != null) {
            level.playSound(null, pos, snd, SoundSource.MASTER, 1.0F, 1.0F);
         }
      }
   }
}