package net.irisshaders.iris.mixin.fabric;

import net.minecraft.client.renderer.chunk.advanced.compile.pipeline.DefaultFluidRenderer;
// UPDATED: Target integrated Sodium class instead of module class
import net.minecraft.client.renderer.sodium.fabric.render.FluidRendererImpl;
import net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// FIXED: Mixin now targets the integrated Sodium FluidRendererImpl (net.minecraft.client.renderer.sodium.fabric.render.FluidRendererImpl)
// instead of the module version (net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl)
@Mixin(FluidRendererImpl.class)
public class MixinFluidRendererImpl implements VertexEncoderInterface {
	@Shadow
	@Final
	private DefaultFluidRenderer defaultRenderer;

	@Override
	public void beginBlock(int blockId, byte isFluid, byte lightEmission, int x, int y, int z) {
		((VertexEncoderInterface) this.defaultRenderer).beginBlock(blockId, isFluid, lightEmission, x, y, z);
	}
}
