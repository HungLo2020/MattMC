package net.matt.quantize.block.block;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generic leaves block that spawns whichever particle its caller supplies.
 */
public class CustomLeavesBlock extends LeavesBlock {

    /** Particle to spawn; a RegistryObject works because it is a Supplier */
    private final Supplier<? extends ParticleOptions> particle;

    public CustomLeavesBlock(Properties props,
                             Supplier<? extends ParticleOptions> particle) {
        super(props);
        this.particle = particle;
    }

    // ─────────────────────────────────── client‑side visuals ────────────
    @Override
    public void animateTick(BlockState state, Level level,
                            BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        if (random.nextInt(10) == 0) {
            level.addParticle(
                    particle.get(),                                     // <-- use it
                    pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(),
                    0.0, 0.0, 0.0);
        }
    }

    @Override public boolean isRandomlyTicking(BlockState state) { return true; }
    @Override public int  getLightBlock   (BlockState s, BlockGetter g, BlockPos p) { return 1; }
}
