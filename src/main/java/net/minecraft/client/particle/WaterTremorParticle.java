package net.minecraft.client.particle;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class WaterTremorParticle extends SimpleAnimatedParticle {

    protected WaterTremorParticle(ClientLevel level, double x, double y, double z, SpriteSet set) {
        super(level, x, y, z, set, 0);
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.alpha = 0.8F;
        this.gravity = 0.0F;
        this.quadSize = (float) ((level.getRandom().nextFloat() * 0.25F + 0.75F) * 0.5F);
        this.lifetime = 20;
        this.setColor(BiomeColors.getAverageWaterColor(level, BlockPos.containing(x, y, z)));
        this.setSpriteFromAge(set);
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos slightlyAbove = BlockPos.containing(this.x, this.y + 0.1F, this.z);
        BlockPos slightlyBelow = BlockPos.containing(this.x, this.y - 0.1F, this.z);

        if (!isWater(slightlyAbove) && !isWater(slightlyBelow)) {
            this.remove();
        }
    }

    private boolean isWater(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER_CAULDRON) || state.getFluidState().is(FluidTags.WATER);
    }

    @Environment(EnvType.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource randomSource) {
            return new WaterTremorParticle(worldIn, x, y, z, spriteSet);
        }
    }
}
