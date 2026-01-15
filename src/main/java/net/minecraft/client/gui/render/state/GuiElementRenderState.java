package net.minecraft.client.gui.render.state;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface GuiElementRenderState extends ScreenArea {
	void buildVertices(VertexConsumer vertexConsumer);

	RenderPipeline pipeline();

	TextureSetup textureSetup();

	@Nullable
	ScreenRectangle scissorArea();
}
