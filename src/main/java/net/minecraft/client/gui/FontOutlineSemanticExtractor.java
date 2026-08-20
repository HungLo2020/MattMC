package net.minecraft.client.gui;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.TextGlyphQuad;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.FormattedCharSequence;

/**
 * CPU-only semantic equivalent of {@link Font#drawInBatch8xOutline}.
 *
 * <p>The normal Java renderer remains untouched. This helper lives beside
 * {@link Font} so it can reuse the exact glyph resolution, advance, bold and
 * shadow-offset rules without exposing a renderer, buffer source or GPU
 * object to the Rust world-text boundary.</p>
 */
@Environment(EnvType.CLIENT)
public final class FontOutlineSemanticExtractor {
	private FontOutlineSemanticExtractor() {
	}

	public static Font.SemanticTextExtraction collect(
		Font font,
		FormattedCharSequence text,
		float x,
		float y,
		int textColor,
		int outlineColor,
		Consumer<TextGlyphQuad> outlineConsumer,
		Consumer<TextGlyphQuad> fillConsumer
	) {
		Objects.requireNonNull(font, "font");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(outlineConsumer, "outlineConsumer");
		Objects.requireNonNull(fillConsumer, "fillConsumer");

		Font.PreparedTextBuilder outline = font.new PreparedTextBuilder(0.0F, 0.0F, outlineColor, false);
		for (int xOffset = -1; xOffset <= 1; xOffset++) {
			for (int yOffset = -1; yOffset <= 1; yOffset++) {
				if (xOffset == 0 && yOffset == 0) {
					continue;
				}
				float[] cursor = new float[] {x};
				int resolvedXOffset = xOffset;
				int resolvedYOffset = yOffset;
				text.accept((index, style, codePoint) -> {
					boolean bold = style.isBold();
					BakedGlyph glyph = font.getGlyph(codePoint, style);
					outline.x = cursor[0] + resolvedXOffset * glyph.info().getShadowOffset();
					outline.y = y + resolvedYOffset * glyph.info().getShadowOffset();
					cursor[0] += glyph.info().getAdvance(bold);
					return outline.accept(index, style.withColor(outlineColor), glyph);
				});
			}
		}

		int renderableCount = 0;
		int quadCount = 0;
		int unsupportedRenderableCount = 0;
		// Vanilla drawInBatch8xOutline intentionally renders only the outline
		// builder's glyph list. Underline/strikethrough effects from the outline
		// copies are not drawn, so do not emit them into the semantic stream.
		for (TextRenderable renderable : outline.glyphs) {
			renderableCount++;
			int emitted = renderable.collectSemanticQuads(outlineConsumer);
			quadCount += emitted;
			if (emitted == 0) {
				unsupportedRenderableCount++;
			}
		}

		Font.PreparedTextBuilder fill = font.new PreparedTextBuilder(x, y, textColor, false);
		text.accept(fill);
		Font.SemanticTextExtraction fillExtraction = fill.collectSemanticQuads(fillConsumer);
		return new Font.SemanticTextExtraction(
			renderableCount + fillExtraction.renderableCount(),
			quadCount + fillExtraction.quadCount(),
			unsupportedRenderableCount + fillExtraction.unsupportedRenderableCount()
		);
	}
}
