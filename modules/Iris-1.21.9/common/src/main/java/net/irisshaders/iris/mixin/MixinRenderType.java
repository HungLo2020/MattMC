package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.iris.mixinterface.RenderTypeInterface;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderType.CompositeRenderType.class)
public class MixinRenderType implements RenderTypeInterface {
	@Shadow
	@Final
	private RenderType.CompositeState state;

	@Shadow
	@Final
	private RenderPipeline renderPipeline;

	@Override
	public RenderTarget iris$getRenderTarget() {
		return this.state.outputState.getRenderTarget();
	}

	@Override
	public RenderPipeline iris$getPipeline() {
		return this.renderPipeline;
	}
}
