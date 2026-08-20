package net.vulkanic.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.FontOutlineSemanticExtractor;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.TextGlyphQuad;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.vulkanic.bridge.VulkanicGalBridge;

/**
 * Extracts resolved world text without invoking the Java font renderer.
 *
 * <p>The returned records deliberately retain model-view glyph geometry and
 * font-atlas identity. They are a producer-neutral world-text contract: a
 * later Rust-owned world-text frontend owns texture upload, batching, depth
 * policy, and execution. Java never passes a font texture, vertex buffer, or
 * renderer object.</p>
 */
public final class WorldTextSemanticCollector {
	public static final int DEPTH_SEE_THROUGH = 1;
	public static final int DEPTH_NORMAL = 2;
	public static final int DEPTH_POLYGON_OFFSET = 3;

	private WorldTextSemanticCollector() {
	}

	public static Result collectNameTags(NameTagFeatureRenderer.Storage.SemanticSnapshot snapshot, Font font) {
		List<WorldTextQuad> quads = new ArrayList<>();
		LinkedHashMap<Long, WorldTextImage> images = new LinkedHashMap<>();
		int unsupportedSubmits = 0;
		for (SubmitNodeStorage.NameTagSubmit submit : snapshot.seeThrough().stream()
			.sorted(Comparator.comparing(SubmitNodeStorage.NameTagSubmit::distanceToCameraSq).reversed())
			.toList()) {
			unsupportedSubmits += collect(submit, DEPTH_SEE_THROUGH, font, quads, images);
		}
		for (SubmitNodeStorage.NameTagSubmit submit : snapshot.normal()) {
			unsupportedSubmits += collect(submit, DEPTH_NORMAL, font, quads, images);
		}
		return new Result(List.copyOf(quads), List.copyOf(images.values()), unsupportedSubmits);
	}

	/**
	 * Extracts the ordinary resolved text-submit family through the same copied
	 * glyph/image contract as name tags. Vanilla 8x-outline text is expanded
	 * into its exact ordered normal-depth outline glyphs followed by the
	 * polygon-offset fill; no Java font draw participates in the Rust route.
	 */
	public static Result collectTextSubmits(List<SubmitNodeStorage.TextSubmit> submits, Font font) {
		List<WorldTextQuad> quads = new ArrayList<>();
		LinkedHashMap<Long, WorldTextImage> images = new LinkedHashMap<>();
		int unsupportedSubmits = 0;
		for (SubmitNodeStorage.TextSubmit submit : submits) {
			if (submit.outlineColor() != 0) {
				unsupportedSubmits += collectOutlinedTextSubmit(submit, font, quads, images);
				continue;
			}
			int depthPolicy = textSubmitDepthPolicy(submit);
			if (depthPolicy == 0) {
				unsupportedSubmits++;
				continue;
			}
			unsupportedSubmits += collect(
				submit.string(), submit.x(), submit.y(), submit.color(), submit.dropShadow(),
				submit.backgroundColor(), submit.lightCoords(), 0.0, submit.pose(), depthPolicy,
				font, quads, images
			);
		}
		return new Result(List.copyOf(quads), List.copyOf(images.values()), unsupportedSubmits);
	}

	/**
	 * Returns zero when a submit is not a single-pass world-text depth mode.
	 * Outlined text deliberately returns zero here because it is expanded by
	 * {@link #collectOutlinedTextSubmit} into normal-depth outline passes plus a
	 * polygon-offset fill rather than being represented by one depth policy.
	 */
	static int textSubmitDepthPolicy(SubmitNodeStorage.TextSubmit submit) {
		if (submit.outlineColor() != 0) {
			return 0;
		}
		return switch (submit.displayMode()) {
			case NORMAL -> DEPTH_NORMAL;
			case SEE_THROUGH -> DEPTH_SEE_THROUGH;
			case POLYGON_OFFSET -> DEPTH_POLYGON_OFFSET;
		};
	}

	private static int collectOutlinedTextSubmit(
		SubmitNodeStorage.TextSubmit submit,
		Font font,
		List<WorldTextQuad> output,
		LinkedHashMap<Long, WorldTextImage> images
	) {
		List<TextGlyphQuad> outlineGlyphs = new ArrayList<>();
		List<TextGlyphQuad> fillGlyphs = new ArrayList<>();
		Font.SemanticTextExtraction extraction = FontOutlineSemanticExtractor.collect(
			font,
			submit.string(),
			submit.x(),
			submit.y(),
			submit.color(),
			submit.outlineColor(),
			outlineGlyphs::add,
			fillGlyphs::add
		);
		if (extraction.unsupportedRenderableCount() != 0) {
			return 1;
		}
		// drawInBatch8xOutline renders all eight outline copies first with the
		// normal depth pipeline, then the original fill with polygon offset.
		if (appendGlyphs(
			outlineGlyphs, submit.lightCoords(), 0.0, submit.pose(), DEPTH_NORMAL, output, images
		) != 0) {
			return 1;
		}
		return appendGlyphs(
			fillGlyphs, submit.lightCoords(), 0.0, submit.pose(), DEPTH_POLYGON_OFFSET, output, images
		);
	}

	private static int collect(
		SubmitNodeStorage.NameTagSubmit submit,
		int depthPolicy,
		Font font,
		List<WorldTextQuad> output,
		LinkedHashMap<Long, WorldTextImage> images
	) {
		return collect(
			submit.text().getVisualOrderText(), submit.x(), submit.y(), submit.color(), false,
			submit.backgroundColor(), submit.lightCoords(), submit.distanceToCameraSq(), submit.pose(), depthPolicy,
			font, output, images
		);
	}

	private static int collect(
		net.minecraft.util.FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
		int backgroundColor, int packedLight, double distanceToCameraSq, org.joml.Matrix4f pose,
		int depthPolicy, Font font, List<WorldTextQuad> output, LinkedHashMap<Long, WorldTextImage> images
	) {
		List<TextGlyphQuad> glyphs = new ArrayList<>();
		Font.SemanticTextExtraction extraction = font
			.prepareText(text, x, y, color, dropShadow, backgroundColor)
			.collectSemanticQuads(glyphs::add);
		if (extraction.unsupportedRenderableCount() != 0) {
			return 1;
		}
		return appendGlyphs(glyphs, packedLight, distanceToCameraSq, pose, depthPolicy, output, images);
	}

	private static int appendGlyphs(
		List<TextGlyphQuad> glyphs,
		int packedLight,
		double distanceToCameraSq,
		org.joml.Matrix4f pose,
		int depthPolicy,
		List<WorldTextQuad> output,
		LinkedHashMap<Long, WorldTextImage> images
	) {
		for (TextGlyphQuad glyph : glyphs) {
			FontTexture.SemanticAtlasSnapshot atlas = FontTexture.semanticAtlasSnapshot(glyph.atlasIdentity());
			if (atlas == null) {
				return 1;
			}
			long assetId = semanticAssetId(atlas.identity(), atlas.colored());
			WorldTextImage image = new WorldTextImage(
				assetId, atlas.identity(), atlas.generation(), atlas.revision(), atlas.colored(),
				atlas.width(), atlas.height(), atlas.pixels()
			);
			WorldTextImage previous = images.putIfAbsent(assetId, image);
			if (previous != null && !previous.matchesGeneration(image)) {
				return 1;
			}
			output.add(new WorldTextQuad(
				glyph.atlasIdentity(), atlas.generation(), atlas.revision(), atlas.colored(), depthPolicy,
				packedLight, distanceToCameraSq, matrixValues(pose), glyph
			));
		}
		return 0;
	}

	/** Stable semantic identity, not a Java atlas or backend object identity. */
	public static long semanticAssetId(String atlasIdentity, boolean colored) {
		Objects.requireNonNull(atlasIdentity, "atlasIdentity");
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < atlasIdentity.length(); i++) {
			hash ^= atlasIdentity.charAt(i);
			hash *= 0x100000001b3L;
		}
		hash ^= colored ? 1L : 0L;
		hash *= 0x100000001b3L;
		return hash == 0L ? 1L : hash;
	}

	private static float[] matrixValues(org.joml.Matrix4f matrix) {
		return new float[] {
			matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
			matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
			matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
			matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
		};
	}

	public record Result(List<WorldTextQuad> quads, List<WorldTextImage> images, int unsupportedSubmits) {
		public Result {
			quads = List.copyOf(quads);
			images = List.copyOf(images);
		}

		public boolean fullySupported() {
			return unsupportedSubmits == 0;
		}
	}

	/** Copied semantic pixels paired with the atlas generation named by glyphs. */
	public record WorldTextImage(
		long assetId,
		String atlasIdentity,
		long atlasGeneration,
		long atlasRevision,
		boolean colored,
		int width,
		int height,
		byte[] pixels
	) {
		public WorldTextImage {
			Objects.requireNonNull(atlasIdentity, "atlasIdentity");
			Objects.requireNonNull(pixels, "pixels");
			if (assetId == 0L || atlasIdentity.isBlank() || atlasGeneration <= 0L || atlasRevision <= 0L
				|| width <= 0 || height <= 0) {
				throw new IllegalArgumentException("invalid world text image semantics");
			}
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}

		boolean matchesGeneration(WorldTextImage other) {
			return this.atlasGeneration == other.atlasGeneration
				&& this.atlasRevision == other.atlasRevision
				&& this.colored == other.colored
				&& this.width == other.width
				&& this.height == other.height;
		}
	}

	public record WorldTextQuad(
		String atlasIdentity,
		long atlasGeneration,
		long atlasRevision,
		boolean colored,
		int depthPolicy,
		int packedLight,
		double distanceToCameraSq,
		float[] modelViewMatrix,
		TextGlyphQuad glyph
	) {
		public WorldTextQuad {
			Objects.requireNonNull(atlasIdentity, "atlasIdentity");
			Objects.requireNonNull(modelViewMatrix, "modelViewMatrix");
			Objects.requireNonNull(glyph, "glyph");
			if (atlasIdentity.isBlank() || atlasGeneration <= 0L || atlasRevision <= 0L) {
				throw new IllegalArgumentException("world text quad requires a valid atlas generation and revision");
			}
			if (depthPolicy != DEPTH_SEE_THROUGH && depthPolicy != DEPTH_NORMAL && depthPolicy != DEPTH_POLYGON_OFFSET) {
				throw new IllegalArgumentException("unknown world text depth policy " + depthPolicy);
			}
			if (modelViewMatrix.length != 16) {
				throw new IllegalArgumentException("world text model-view matrix must contain 16 floats");
			}
			for (float value : modelViewMatrix) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world text model-view matrix must be finite");
				}
			}
			if (!finiteGlyph(glyph)) {
				throw new IllegalArgumentException("world text glyph geometry must be finite");
			}
			modelViewMatrix = modelViewMatrix.clone();
		}

		@Override
		public float[] modelViewMatrix() {
			return this.modelViewMatrix.clone();
		}

		private static boolean finiteGlyph(TextGlyphQuad glyph) {
			return Float.isFinite(glyph.x0()) && Float.isFinite(glyph.y0())
				&& Float.isFinite(glyph.x1()) && Float.isFinite(glyph.y1())
				&& Float.isFinite(glyph.x2()) && Float.isFinite(glyph.y2())
				&& Float.isFinite(glyph.x3()) && Float.isFinite(glyph.y3())
				&& Float.isFinite(glyph.z()) && Float.isFinite(glyph.u0()) && Float.isFinite(glyph.v0())
				&& Float.isFinite(glyph.u1()) && Float.isFinite(glyph.v1());
		}

		public VulkanicGalBridge.WorldTextQuadRecord toBridgeRecord() {
			return new VulkanicGalBridge.WorldTextQuadRecord(
				semanticAssetId(this.atlasIdentity, this.colored),
				this.atlasGeneration,
				this.atlasRevision,
				this.colored,
				this.depthPolicy,
				this.packedLight,
				this.glyph.colorArgb(),
				this.distanceToCameraSq,
				this.modelViewMatrix,
				new float[] {
					this.glyph.x0(), this.glyph.y0(), this.glyph.z(),
					this.glyph.x1(), this.glyph.y1(), this.glyph.z(),
					this.glyph.x2(), this.glyph.y2(), this.glyph.z(),
					this.glyph.x3(), this.glyph.y3(), this.glyph.z()
				},
				new float[] {
					this.glyph.u0(), this.glyph.v0(),
					this.glyph.u0(), this.glyph.v1(),
					this.glyph.u1(), this.glyph.v1(),
					this.glyph.u1(), this.glyph.v0()
				}
			);
		}
	}
}
