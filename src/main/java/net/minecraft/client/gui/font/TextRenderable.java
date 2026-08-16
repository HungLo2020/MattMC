package net.minecraft.client.gui.font;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public interface TextRenderable {
	void render(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, boolean bl);

	RenderType renderType(Font.DisplayMode displayMode);

	GpuTextureView textureView();

	RenderPipeline guiPipeline();

	float left();

	float top();

	float right();

	float bottom();

	/**
	 * Emits resolved glyph geometry without touching Java rendering state. Only
	 * baked font-sheet glyphs currently implement this; callers must treat an
	 * empty result as unsupported semantic work rather than inventing geometry.
	 */
	default int collectSemanticQuads(Consumer<TextGlyphQuad> consumer) {
		return 0;
	}
}
