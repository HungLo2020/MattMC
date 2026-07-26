package net.vulkanic.gui;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.vulkanic.bridge.RustGalFrameScheduler;
import org.jetbrains.annotations.Nullable;

public record RustGalGuiElementRenderState(
	RustGalFrameScheduler.Token token,
	GuiRenderStratum stratum,
	String producerId,
	int selectedSlot,
	float progressFraction,
	GuiFillDirection fillDirection,
	int sourceX,
	int sourceY,
	int sourceWidth,
	int sourceHeight,
	int x,
	int y,
	int width,
	int height,
	int guiWidth,
	int guiHeight
) implements GuiElementRenderState {
	public long batchId() {
		return this.token.batchId();
	}

	public long sequence() {
		return this.token.sequence();
	}

	public long generation() {
		return this.token.generation();
	}

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
		String context = "rust-gal:" + this.producerId + ":" + this.stratum.id()
			+ ":src=" + this.sourceX + "," + this.sourceY + "," + this.sourceWidth + "," + this.sourceHeight;
		if (this.selectedSlot >= 0) {
			context += ":slot=" + this.selectedSlot;
		}
		if (this.progressFraction >= 0.0F) {
			context += ":progress=" + this.progressFraction;
		}
		if (this.fillDirection != GuiFillDirection.NONE) {
			context += ":fill=" + this.fillDirection.id();
		}
		return context;
	}
}
