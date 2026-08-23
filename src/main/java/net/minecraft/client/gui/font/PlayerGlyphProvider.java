package net.minecraft.client.gui.font;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.blaze3d.font.GlyphInfo;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexConsumer;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.PlayerSprite;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerGlyphProvider {
	private static final float WIDTH = 8.0F;
	private static final float HEIGHT = 8.0F;
	static final GlyphInfo GLYPH_INFO = GlyphInfo.simple(8.0F);
	final PlayerSkinRenderCache playerSkinRenderCache;
	private final LoadingCache<PlayerSprite, GlyphSource> wrapperCache = CacheBuilder.newBuilder()
		.expireAfterAccess(PlayerSkinRenderCache.CACHE_DURATION)
		.build(new CacheLoader<PlayerSprite, GlyphSource>() {
			public GlyphSource load(PlayerSprite playerSprite) {
				final Supplier<PlayerSkinRenderCache.RenderInfo> supplier = PlayerGlyphProvider.this.playerSkinRenderCache.createLookup(playerSprite.profile());
				final boolean bl = playerSprite.hat();
				return new SingleSpriteSource(new BakedGlyph() {
					@Override
					public GlyphInfo info() {
						return PlayerGlyphProvider.GLYPH_INFO;
					}

					@Override
					public TextRenderable createGlyph(float f, float g, int i, int j, Style style, float h, float k) {
						return new PlayerGlyphProvider.Instance(supplier, bl, supplier.get().playerSkin().body().texturePath().toString(), f, g, i, j, k);
					}
				});
			}
		});

	public PlayerGlyphProvider(PlayerSkinRenderCache playerSkinRenderCache) {
		this.playerSkinRenderCache = playerSkinRenderCache;
	}

	public GlyphSource sourceForPlayer(PlayerSprite playerSprite) {
		return this.wrapperCache.getUnchecked(playerSprite);
	}

	@Environment(EnvType.CLIENT)
	record Instance(Supplier<PlayerSkinRenderCache.RenderInfo> skin, boolean hat, String textureIdentity, float x, float y, int color, int shadowColor, float shadowOffset)
		implements PlainTextRenderable {
		@Override
		public void renderSprite(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, int j) {
			float k = f + this.left();
			float l = f + this.right();
			float m = g + this.top();
			float n = g + this.bottom();
			renderQuad(matrix4f, vertexConsumer, i, k, l, m, n, h, j, 8.0F, 8.0F, 8, 8, 64, 64);
			if (this.hat) {
				renderQuad(matrix4f, vertexConsumer, i, k, l, m, n, h, j, 40.0F, 8.0F, 8, 8, 64, 64);
			}
		}

		@Override
		public int collectSemanticQuads(Consumer<TextGlyphQuad> consumer) {
			int count = 0;
			if (this.shadowColor() != 0) {
				consumer.accept(this.semanticQuad(this.x() + this.shadowOffset(), this.y() + this.shadowOffset(), this.shadowColor(), 0.0F));
				count++;
				if (this.hat()) {
					consumer.accept(this.semanticQuad(this.x() + this.shadowOffset(), this.y() + this.shadowOffset(), this.shadowColor(), 0.001F,
						40.0F, 8.0F));
					count++;
				}
			}
			consumer.accept(this.semanticQuad(this.x(), this.y(), this.color(), this.shadowColor() == 0 ? 0.0F : 0.03F));
			if (this.hat()) {
				consumer.accept(this.semanticQuad(this.x(), this.y(), this.color(), this.shadowColor() == 0 ? 0.001F : 0.031F,
					40.0F, 8.0F));
			}
			return count + 1 + (this.hat() ? 1 : 0);
		}

		private TextGlyphQuad semanticQuad(float x, float y, int color, float z) {
			return semanticQuad(x, y, color, z, 8.0F, 8.0F);
		}

		private TextGlyphQuad semanticQuad(float x, float y, int color, float z, float sourceX, float sourceY) {
			String identity = this.skin.get().playerSkin().body().texturePath().toString();
			return new TextGlyphQuad(identity, true, x + this.left(), y + this.top(),
				x + this.left(), y + this.bottom(), x + this.right(), y + this.bottom(), x + this.right(), y + this.top(), z,
				sourceX / 64.0F, sourceY / 64.0F, (sourceX + 8.0F) / 64.0F, (sourceY + 8.0F) / 64.0F, color);
		}

		private static void renderQuad(
			Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, float j, float k, int l, float m, float n, int o, int p, int q, int r
		) {
			float s = (m + 0.0F) / q;
			float t = (m + o) / q;
			float u = (n + 0.0F) / r;
			float v = (n + p) / r;
			vertexConsumer.addVertex(matrix4f, f, h, k).setUv(s, u).setColor(l).setLight(i);
			vertexConsumer.addVertex(matrix4f, f, j, k).setUv(s, v).setColor(l).setLight(i);
			vertexConsumer.addVertex(matrix4f, g, j, k).setUv(t, v).setColor(l).setLight(i);
			vertexConsumer.addVertex(matrix4f, g, h, k).setUv(t, u).setColor(l).setLight(i);
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			return ((PlayerSkinRenderCache.RenderInfo)this.skin.get()).glyphRenderTypes().select(displayMode);
		}

		@Override
		public RenderPipeline guiPipeline() {
			return ((PlayerSkinRenderCache.RenderInfo)this.skin.get()).glyphRenderTypes().guiPipeline();
		}

		@Override
		@Nullable
		public GpuTextureView textureView() {
			return net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				? null
				: ((PlayerSkinRenderCache.RenderInfo)this.skin.get()).textureView();
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
