package net.blaze3d.resource;

import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.pipeline.TextureTarget;
import net.blaze3d.systems.RenderSystem;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record RenderTargetDescriptor(int width, int height, boolean useDepth, int clearColor) implements ResourceDescriptor<RenderTarget> {
	public RenderTarget allocate() {
		return new TextureTarget(null, this.width, this.height, this.useDepth);
	}

	public void prepare(RenderTarget renderTarget) {
		if (this.useDepth) {
			net.vulkanic.VulkanicAPI.createCommandEncoder()
				.clearColorAndDepthTextures(renderTarget.getColorTexture(), this.clearColor, renderTarget.getDepthTexture(), 1.0);
		} else {
			net.vulkanic.VulkanicAPI.createCommandEncoder().clearColorTexture(renderTarget.getColorTexture(), this.clearColor);
		}
	}

	public void free(RenderTarget renderTarget) {
		renderTarget.destroyBuffers();
	}

	@Override
	public boolean canUsePhysicalResource(ResourceDescriptor<?> resourceDescriptor) {
		return resourceDescriptor instanceof RenderTargetDescriptor renderTargetDescriptor && this.width == renderTargetDescriptor.width && this.height == renderTargetDescriptor.height && this.useDepth == renderTargetDescriptor.useDepth;
	}
}
