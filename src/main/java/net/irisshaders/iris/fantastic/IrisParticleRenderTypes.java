package net.irisshaders.iris.fantastic;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;

import static net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;

public class IrisParticleRenderTypes {
	public static final SingleQuadParticle.Layer TERRAIN_OPAQUE = new SingleQuadParticle.Layer(false, LOCATION_BLOCKS, RenderPipelines.OPAQUE_PARTICLE);

	//public static final SingleQuadParticle.Layer TERRAIN_OPAQUE = new ParticleRenderType("TERRAIN_OPAQUE", RenderType.opaqueParticle(LOCATION_BLOCKS));
}
