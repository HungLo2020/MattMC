package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class FlyParticle extends SingleQuadParticle {

    private final SpriteSet spriteSet;
    private final double orbitX;
    private final double orbitY;
    private final double orbitZ;
    private final boolean reverseOrbit;
    private final float orbitSpeed;
    private final Vec3 orbitOffset;

    protected FlyParticle(ClientLevel world, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet spriteSet, TextureAtlasSprite sprite) {
        super(world, x, y, z, xSpeed, ySpeed, zSpeed, sprite);
        this.quadSize *= 1F + world.getRandom().nextFloat() * 0.3F;
        this.speedUpWhenYMotionIsBlocked = true;
        this.orbitX = xSpeed;
        this.orbitY = ySpeed;
        this.orbitZ = zSpeed;
        this.spriteSet = spriteSet;
        this.lifetime = (int) (Math.random() * 10.0D) + 40;
        this.friction = 0.8F;
        this.orbitOffset = new Vec3((0.5F - world.getRandom().nextFloat()) * 2.0F, 0, (0.5F - world.getRandom().nextFloat()) * 2.0F);
        this.reverseOrbit = world.getRandom().nextBoolean();
        this.orbitSpeed = 3 + world.getRandom().nextFloat() * 3F;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp(((float) this.age + scaleFactor) / (float) this.lifetime * 16.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        int spriteIndex = this.age % 4 >= 2 ? 1 : 0;
        this.setSprite(spriteSet.get(spriteIndex, 1));
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            Vec3 vec3 = getOrbitPosition(age);
            Vec3 movement = vec3.subtract(this.x, this.y, this.z).normalize().scale(0.1F);

            this.xd = movement.x + this.level.getRandom().nextGaussian() * 0.015F;
            this.yd += movement.y + this.level.getRandom().nextGaussian() * 0.015F;
            if (this.onGround) {
                yd += 0.3F;
            }
            this.zd += movement.z + this.level.getRandom().nextGaussian() * 0.015F;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= (double) this.friction;
            this.yd *= (double) this.friction;
            this.zd *= (double) this.friction;
        }
    }

    private Vec3 getOrbitPosition(float angle) {
        Vec3 center = new Vec3(orbitX, orbitY, orbitZ);
        float rot = angle * (reverseOrbit ? -orbitSpeed : orbitSpeed) * (float) (Math.PI / 180F);
        return center.add(orbitOffset.yRot(rot));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(
            SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource randomSource
        ) {
            FlyParticle flyParticle = new FlyParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet, this.spriteSet.get(randomSource));
            return flyParticle;
        }
    }
}
