package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public class WeatherEffectRenderer {
	private static final int RAIN_RADIUS = 10;
	private static final int RAIN_DIAMETER = 21;
	private static final ResourceLocation RAIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
	private static final ResourceLocation SNOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
	private static final int RAIN_TABLE_SIZE = 32;
	private static final int HALF_RAIN_TABLE_SIZE = 16;
	private int rainSoundTime;
	private final float[] columnSizeX = new float[1024];
	private final float[] columnSizeZ = new float[1024];

	public WeatherEffectRenderer() {
		for (int i = 0; i < 32; i++) {
			for (int j = 0; j < 32; j++) {
				float f = j - 16;
				float g = i - 16;
				float h = Mth.length(f, g);
				this.columnSizeX[i * 32 + j] = -g / h;
				this.columnSizeZ[i * 32 + j] = f / h;
			}
		}
	}

	public void extractRenderState(Level level, int i, float f, Vec3 vec3, WeatherRenderState weatherRenderState) {
		weatherRenderState.intensity = level.getRainLevel(f);
		if (!(weatherRenderState.intensity <= 0.0F)) {
			weatherRenderState.radius = Minecraft.useFancyGraphics() ? 10 : 5;
			int j = Mth.floor(vec3.x);
			int k = Mth.floor(vec3.y);
			int l = Mth.floor(vec3.z);
			MutableBlockPos mutableBlockPos = new MutableBlockPos();
			RandomSource randomSource = RandomSource.create();

			for (int m = l - weatherRenderState.radius; m <= l + weatherRenderState.radius; m++) {
				for (int n = j - weatherRenderState.radius; n <= j + weatherRenderState.radius; n++) {
					int o = level.getHeight(Types.MOTION_BLOCKING, n, m);
					int p = Math.max(k - weatherRenderState.radius, o);
					int q = Math.max(k + weatherRenderState.radius, o);
					if (q - p != 0) {
						Precipitation precipitation = this.getPrecipitationAt(level, mutableBlockPos.set(n, k, m));
						if (precipitation != Precipitation.NONE) {
							int r = n * n * 3121 + n * 45238971 ^ m * m * 418711 + m * 13761;
							randomSource.setSeed(r);
							int s = Math.max(k, o);
							int t = LevelRenderer.getLightColor(level, mutableBlockPos.set(n, s, m));
							if (precipitation == Precipitation.RAIN) {
								weatherRenderState.rainColumns.add(this.createRainColumnInstance(randomSource, i, n, p, q, m, t, f));
							} else if (precipitation == Precipitation.SNOW) {
								weatherRenderState.snowColumns.add(this.createSnowColumnInstance(randomSource, i, n, p, q, m, t, f));
							}
						}
					}
				}
			}
		}
	}

	public void render(MultiBufferSource multiBufferSource, Vec3 vec3, WeatherRenderState weatherRenderState) {
		if (!weatherRenderState.rainColumns.isEmpty()) {
			RenderType renderType = RenderType.weather(RAIN_LOCATION, Minecraft.useShaderTransparency());
			this.renderInstances(
				multiBufferSource.getBuffer(renderType), weatherRenderState.rainColumns, vec3, 1.0F, weatherRenderState.radius, weatherRenderState.intensity
			);
		}

		if (!weatherRenderState.snowColumns.isEmpty()) {
			RenderType renderType = RenderType.weather(SNOW_LOCATION, Minecraft.useShaderTransparency());
			this.renderInstances(
				multiBufferSource.getBuffer(renderType), weatherRenderState.snowColumns, vec3, 0.8F, weatherRenderState.radius, weatherRenderState.intensity
			);
		}
	}

	private WeatherEffectRenderer.ColumnInstance createRainColumnInstance(RandomSource randomSource, int i, int j, int k, int l, int m, int n, float f) {
		int o = i & 131071;
		int p = j * j * 3121 + j * 45238971 + m * m * 418711 + m * 13761 & 0xFF;
		float g = 3.0F + randomSource.nextFloat();
		float h = -(o + p + f) / 32.0F * g;
		float q = h % 32.0F;
		return new WeatherEffectRenderer.ColumnInstance(j, m, k, l, 0.0F, q, n);
	}

	private WeatherEffectRenderer.ColumnInstance createSnowColumnInstance(RandomSource randomSource, int i, int j, int k, int l, int m, int n, float f) {
		float g = i + f;
		float h = (float)(randomSource.nextDouble() + g * 0.01F * (float)randomSource.nextGaussian());
		float o = (float)(randomSource.nextDouble() + g * (float)randomSource.nextGaussian() * 0.001F);
		float p = -((i & 511) + f) / 512.0F;
		int q = LightTexture.pack((LightTexture.block(n) * 3 + 15) / 4, (LightTexture.sky(n) * 3 + 15) / 4);
		return new WeatherEffectRenderer.ColumnInstance(j, m, k, l, h, p + o, q);
	}

	private void renderInstances(VertexConsumer vertexConsumer, List<WeatherEffectRenderer.ColumnInstance> list, Vec3 vec3, float f, int i, float g) {
		for (WeatherEffectRenderer.ColumnInstance columnInstance : list) {
			float h = (float)(columnInstance.x + 0.5 - vec3.x);
			float j = (float)(columnInstance.z + 0.5 - vec3.z);
			float k = (float)Mth.lengthSquared(h, j);
			float l = Mth.lerp(k / (i * i), f, 0.5F) * g;
			int m = ARGB.white(l);
			int n = (columnInstance.z - Mth.floor(vec3.z) + 16) * 32 + columnInstance.x - Mth.floor(vec3.x) + 16;
			float o = this.columnSizeX[n] / 2.0F;
			float p = this.columnSizeZ[n] / 2.0F;
			float q = h - o;
			float r = h + o;
			float s = (float)(columnInstance.topY - vec3.y);
			float t = (float)(columnInstance.bottomY - vec3.y);
			float u = j - p;
			float v = j + p;
			float w = columnInstance.uOffset + 0.0F;
			float x = columnInstance.uOffset + 1.0F;
			float y = columnInstance.bottomY * 0.25F + columnInstance.vOffset;
			float z = columnInstance.topY * 0.25F + columnInstance.vOffset;
			vertexConsumer.addVertex(q, s, u).setUv(w, y).setColor(m).setLight(columnInstance.lightCoords);
			vertexConsumer.addVertex(r, s, v).setUv(x, y).setColor(m).setLight(columnInstance.lightCoords);
			vertexConsumer.addVertex(r, t, v).setUv(x, z).setColor(m).setLight(columnInstance.lightCoords);
			vertexConsumer.addVertex(q, t, u).setUv(w, z).setColor(m).setLight(columnInstance.lightCoords);
		}
	}

	public void tickRainParticles(ClientLevel clientLevel, Camera camera, int i, ParticleStatus particleStatus) {
		float f = clientLevel.getRainLevel(1.0F) / (Minecraft.useFancyGraphics() ? 1.0F : 2.0F);
		if (!(f <= 0.0F)) {
			RandomSource randomSource = RandomSource.create(i * 312987231L);
			BlockPos blockPos = BlockPos.containing(camera.getPosition());
			BlockPos blockPos2 = null;
			int j = (int)(100.0F * f * f) / (particleStatus == ParticleStatus.DECREASED ? 2 : 1);

			for (int k = 0; k < j; k++) {
				int l = randomSource.nextInt(21) - 10;
				int m = randomSource.nextInt(21) - 10;
				BlockPos blockPos3 = clientLevel.getHeightmapPos(Types.MOTION_BLOCKING, blockPos.offset(l, 0, m));
				if (blockPos3.getY() > clientLevel.getMinY()
					&& blockPos3.getY() <= blockPos.getY() + 10
					&& blockPos3.getY() >= blockPos.getY() - 10
					&& this.getPrecipitationAt(clientLevel, blockPos3) == Precipitation.RAIN) {
					blockPos2 = blockPos3.below();
					if (particleStatus == ParticleStatus.MINIMAL) {
						break;
					}

					double d = randomSource.nextDouble();
					double e = randomSource.nextDouble();
					BlockState blockState = clientLevel.getBlockState(blockPos2);
					FluidState fluidState = clientLevel.getFluidState(blockPos2);
					VoxelShape voxelShape = blockState.getCollisionShape(clientLevel, blockPos2);
					double g = voxelShape.max(Axis.Y, d, e);
					double h = fluidState.getHeight(clientLevel, blockPos2);
					double n = Math.max(g, h);
					ParticleOptions particleOptions = !fluidState.is(FluidTags.LAVA) && !blockState.is(Blocks.MAGMA_BLOCK) && !CampfireBlock.isLitCampfire(blockState)
						? ParticleTypes.RAIN
						: ParticleTypes.SMOKE;
					clientLevel.addParticle(particleOptions, blockPos2.getX() + d, blockPos2.getY() + n, blockPos2.getZ() + e, 0.0, 0.0, 0.0);
				}
			}

			if (blockPos2 != null && randomSource.nextInt(3) < this.rainSoundTime++) {
				this.rainSoundTime = 0;
				if (blockPos2.getY() > blockPos.getY() + 1 && clientLevel.getHeightmapPos(Types.MOTION_BLOCKING, blockPos).getY() > Mth.floor(blockPos.getY())) {
					clientLevel.playLocalSound(blockPos2, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1F, 0.5F, false);
				} else {
					clientLevel.playLocalSound(blockPos2, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2F, 1.0F, false);
				}
			}
		}
	}

	private Precipitation getPrecipitationAt(Level level, BlockPos blockPos) {
		if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()))) {
			return Precipitation.NONE;
		} else {
			Biome biome = (Biome)level.getBiome(blockPos).value();
			return biome.getPrecipitationAt(blockPos, level.getSeaLevel());
		}
	}

	@Environment(EnvType.CLIENT)
	public record ColumnInstance(int x, int z, int bottomY, int topY, float uOffset, float vOffset, int lightCoords) {
	}
}
