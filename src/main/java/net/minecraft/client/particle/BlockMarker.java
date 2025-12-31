package net.minecraft.client.particle;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class BlockMarker extends SingleQuadParticle {
	// Iris: Track whether particle is opaque (from MixinStationaryItemParticle)
	private boolean isOpaque;
	
	BlockMarker(ClientLevel clientLevel, double d, double e, double f, BlockState blockState) {
		super(clientLevel, d, e, f, Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(blockState));
		this.gravity = 0.0F;
		this.lifetime = 80;
		this.hasPhysics = false;
		
		// Iris: Resolve translucency (from MixinStationaryItemParticle)
		net.minecraft.client.renderer.chunk.ChunkSectionLayer type = net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(blockState);
		if (type == net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID || 
		    type == net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT || 
		    type == net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT_MIPPED) {
			isOpaque = true;
		}
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		// Iris: Override particle render type for opaque particles (from MixinStationaryItemParticle)
		if (isOpaque) {
			return net.irisshaders.iris.fantastic.IrisParticleRenderTypes.TERRAIN_OPAQUE;
		}
		return SingleQuadParticle.Layer.TERRAIN;
	}

	@Override
	public float getQuadSize(float f) {
		return 0.5F;
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<BlockParticleOption> {
		public Particle createParticle(
			BlockParticleOption blockParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BlockMarker(clientLevel, d, e, f, blockParticleOption.getState());
		}
	}
}
