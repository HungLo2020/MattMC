package net.minecraft.client.particle;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.TaczBulletHoleParticleOptions;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class TaczBulletHoleParticle extends SingleQuadParticle {
	private static final int BASE_LIFETIME = 400;
	private static final double FADE_THRESHOLD = 0.98;
	private final Direction direction;
	private final BlockPos pos;
	private int uOffset;
	private int vOffset;
	private float uDensity;
	private float vDensity;

	public TaczBulletHoleParticle(ClientLevel clientLevel, double d, double e, double f, Direction direction, BlockPos blockPos) {
		super(clientLevel, d, e, f, getSprite(clientLevel, blockPos));
		this.direction = direction;
		this.pos = blockPos;
		this.lifetime = BASE_LIFETIME + clientLevel.random.nextInt(BASE_LIFETIME / 2);
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.quadSize = 0.05F;
		this.alpha = 0.9F;
		this.randomizeTextureCell();
		if (this.shouldRemove()) {
			this.remove();
		}
	}

	private static TextureAtlasSprite getSprite(ClientLevel clientLevel, BlockPos blockPos) {
		BlockState blockState = clientLevel.getBlockState(blockPos);
		return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(blockState);
	}

	@Override
	protected void setSprite(TextureAtlasSprite textureAtlasSprite) {
		super.setSprite(textureAtlasSprite);
		this.randomizeTextureCell();
	}

	private void randomizeTextureCell() {
		if (this.sprite == null) {
			return;
		}
		this.uOffset = this.random.nextInt(16);
		this.vOffset = this.random.nextInt(16);
		this.uDensity = (this.sprite.getU1() - this.sprite.getU0()) / 16.0F;
		this.vDensity = (this.sprite.getV1() - this.sprite.getV0()) / 16.0F;
	}

	@Override
	protected float getU0() {
		return this.sprite.getU0() + this.uOffset * this.uDensity;
	}

	@Override
	protected float getU1() {
		return this.getU0() + this.uDensity;
	}

	@Override
	protected float getV0() {
		return this.sprite.getV0() + this.vOffset * this.vDensity;
	}

	@Override
	protected float getV1() {
		return this.getV0() + this.vDensity;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.shouldRemove()) {
			this.remove();
		}
	}

	@Override
	public void extract(QuadParticleRenderState quadParticleRenderState, Camera camera, float f) {
		Vec3 vec3 = camera.getPosition();
		float x = (float)(Mth.lerp(f, this.xo, this.x) - vec3.x());
		float y = (float)(Mth.lerp(f, this.yo, this.y) - vec3.y());
		float z = (float)(Mth.lerp(f, this.zo, this.z) - vec3.z());
		x += this.direction.getStepX() * 0.002F;
		y += this.direction.getStepY() * 0.002F;
		z += this.direction.getStepZ() * 0.002F;
		float light = Math.max(15.0F - this.age / 2.0F, 0.0F);
		float colorPercent = light / 15.0F;
		float fadeStart = (float)(FADE_THRESHOLD * this.lifetime);
		float fadeLength = Math.max(this.lifetime - fadeStart, 1.0F);
		float fade = 1.0F - Mth.clamp((this.age - fadeStart) / fadeLength, 0.0F, 1.0F);
		int color = ARGB.colorFromFloat(this.alpha * fade, this.rCol * colorPercent, this.gCol * colorPercent, this.bCol * colorPercent);
		Quaternionf quaternionf = this.direction.getRotation().rotateX((float)(-Math.PI / 2.0));
		quadParticleRenderState.add(
			this.getLayer(),
			x,
			y,
			z,
			quaternionf.x,
			quaternionf.y,
			quaternionf.z,
			quaternionf.w,
			this.getQuadSize(f),
			this.getU0(),
			this.getU1(),
			this.getV0(),
			this.getV1(),
			color,
			LightTexture.pack((int)light, (int)light)
		);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TERRAIN;
	}

	@Override
	public int getLightColor(float f) {
		int i = super.getLightColor(f);
		return i == 0 && this.level.hasChunkAt(this.pos) ? LevelRenderer.getLightColor(this.level, this.pos) : i;
	}

	private boolean shouldRemove() {
		BlockState blockState = this.level.getBlockState(this.pos);
		if (blockState.isAir()) {
			return true;
		}
		VoxelShape voxelShape = blockState.getCollisionShape(this.level, this.pos);
		if (voxelShape.isEmpty()) {
			return true;
		}
		AABB aabb = voxelShape.bounds().move(this.pos);
		return !aabb.intersects(this.x - 0.1, this.y - 0.1, this.z - 0.1, this.x + 0.1, this.y + 0.1, this.z + 0.1);
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<TaczBulletHoleParticleOptions> {
		@Nullable
		@Override
		public Particle createParticle(
			TaczBulletHoleParticleOptions options,
			ClientLevel clientLevel,
			double d,
			double e,
			double f,
			double g,
			double h,
			double i,
			RandomSource randomSource
		) {
			return new TaczBulletHoleParticle(clientLevel, d, e, f, options.direction(), options.pos());
		}
	}
}
