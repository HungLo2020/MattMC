package net.vulkanic.bridge;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;

public record RustGalGuiElementRenderState(
	long batchId,
	long sequence,
	long generation,
	RustGalFrameQueue.RenderStratum stratum,
	String producerId,
	int x,
	int y,
	int width,
	int height,
	int guiWidth,
	int guiHeight
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vertexConsumer) {
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.CROSSHAIR;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	@Nullable
	public ScreenRectangle scissorArea() {
		return null;
	}

	@Override
	@Nullable
	public ScreenRectangle bounds() {
		return new ScreenRectangle(this.x, this.y, this.width, this.height);
	}

	@Override
	public String shaderInputParityGeometryContext() {
		return "rust-gal:" + this.producerId + ":" + this.stratum.id();
	}
}
