package net.minecraft.client.gui.font;

import net.blaze3d.font.GlyphInfo;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AtlasGlyphProvider {
	private static final float WIDTH = 8.0F;
	private static final float HEIGHT = 8.0F;
	static final GlyphInfo GLYPH_INFO = GlyphInfo.simple(8.0F);
	final TextureAtlas atlas;
	final GlyphRenderTypes renderTypes;
	private final GlyphSource missingWrapper;
	private final Map<ResourceLocation, GlyphSource> wrapperCache = new HashMap();
	private final Function<ResourceLocation, GlyphSource> spriteResolver;

	public AtlasGlyphProvider(TextureAtlas textureAtlas) {
		this.atlas = textureAtlas;
		this.renderTypes = GlyphRenderTypes.createForColorTexture(textureAtlas.location());
		TextureAtlasSprite textureAtlasSprite = textureAtlas.missingSprite();
		this.missingWrapper = this.createSprite(textureAtlasSprite);
		this.spriteResolver = resourceLocation -> {
			TextureAtlasSprite textureAtlasSprite2 = textureAtlas.getSprite(resourceLocation);
			return textureAtlasSprite2 == textureAtlasSprite ? this.missingWrapper : this.createSprite(textureAtlasSprite2);
		};
	}

	public GlyphSource sourceForSprite(ResourceLocation resourceLocation) {
		return (GlyphSource)this.wrapperCache.computeIfAbsent(resourceLocation, this.spriteResolver);
	}

	private GlyphSource createSprite(TextureAtlasSprite textureAtlasSprite) {
		return new SingleSpriteSource(
			new BakedGlyph() {
				@Override
				public GlyphInfo info() {
					return AtlasGlyphProvider.GLYPH_INFO;
				}

				@Override
				public TextRenderable createGlyph(float f, float g, int i, int j, Style style, float h, float k) {
					return new AtlasGlyphProvider.Instance(
						AtlasGlyphProvider.this.renderTypes,
						(net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
							|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected())
							? null : AtlasGlyphProvider.this.atlas.getTextureView(),
						AtlasGlyphProvider.this.atlas.location().toString(), textureAtlasSprite, f, g, i, j, k
					);
				}
			}
		);
	}

	@Environment(EnvType.CLIENT)
	record Instance(
		GlyphRenderTypes renderTypes, @Nullable GpuTextureView textureView, String atlasIdentity, TextureAtlasSprite sprite,
		float x, float y, int color, int shadowColor, float shadowOffset
	) implements PlainTextRenderable {
		@Override
		public void renderSprite(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, int j) {
			float k = f + this.left();
			float l = f + this.right();
			float m = g + this.top();
			float n = g + this.bottom();
			vertexConsumer.addVertex(matrix4f, k, m, h).setUv(this.sprite.getU0(), this.sprite.getV0()).setColor(j).setLight(i);
			vertexConsumer.addVertex(matrix4f, k, n, h).setUv(this.sprite.getU0(), this.sprite.getV1()).setColor(j).setLight(i);
			vertexConsumer.addVertex(matrix4f, l, n, h).setUv(this.sprite.getU1(), this.sprite.getV1()).setColor(j).setLight(i);
			vertexConsumer.addVertex(matrix4f, l, m, h).setUv(this.sprite.getU1(), this.sprite.getV0()).setColor(j).setLight(i);
		}

		@Override
		public int collectSemanticQuads(Consumer<TextGlyphQuad> consumer) {
			int count = 0;
			if (this.shadowColor() != 0) {
				consumer.accept(this.semanticQuad(this.x() + this.shadowOffset(), this.y() + this.shadowOffset(), this.shadowColor(), 0.0F));
				count++;
			}
			consumer.accept(this.semanticQuad(this.x(), this.y(), this.color(), this.shadowColor() == 0 ? 0.0F : 0.03F));
			return count + 1;
		}

		private TextGlyphQuad semanticQuad(float x, float y, int color, float z) {
			return new TextGlyphQuad(this.atlasIdentity, true, x + this.left(), y + this.top(),
				x + this.left(), y + this.bottom(), x + this.right(), y + this.bottom(), x + this.right(), y + this.top(), z,
				this.sprite.getU0(), this.sprite.getV0(), this.sprite.getU1(), this.sprite.getV1(), color);
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			return this.renderTypes.select(displayMode);
		}

		@Override
		public RenderPipeline guiPipeline() {
			return this.renderTypes.guiPipeline();
		}

		@Override
		public float left() {
			return 0.0F;
		}

		@Override
		public float right() {
			return 8.0F;
		}

		@Override
		public float top() {
			return -1.0F;
		}

		@Override
		public float bottom() {
			return 7.0F;
		}
	}
}
