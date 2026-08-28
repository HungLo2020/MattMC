package net.vulkanic.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.TextGlyphQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.vulkanic.gui.RustGalGuiRawImageAssets;
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
	private static final int MAX_SEMANTIC_GLYPHS_PER_SUBMIT = 8_192;
	/** Bounds submit metadata before sorting or preparing glyphs. */
	private static final int MAX_SEMANTIC_SUBMITS_PER_COLLECTION = 8_192;
	/** Must match Rust's whole-frame world-text quad admission bound. */
	private static final int MAX_SEMANTIC_QUADS_PER_COLLECTION = 65_536;
	/** Must match Rust's bounded world-text image residency. */
	private static final int MAX_SEMANTIC_IMAGES_PER_COLLECTION = 4_096;
	/** Must match Rust's world-text image pixel bound. */
	private static final int MAX_SEMANTIC_ATLAS_BYTES = 4 * 1024 * 1024;
	/** Must match Rust's aggregate world-text image residency bound. */
	private static final long MAX_SEMANTIC_ATLAS_BYTES_TOTAL = 64L * 1024 * 1024;
	public static final int DEPTH_SEE_THROUGH = 1;
	public static final int DEPTH_NORMAL = 2;
	public static final int DEPTH_POLYGON_OFFSET = 3;

	private WorldTextSemanticCollector() {
	}

	private static void appendBoundedGlyph(List<TextGlyphQuad> glyphs, TextGlyphQuad glyph) {
		if (glyphs.size() >= MAX_SEMANTIC_GLYPHS_PER_SUBMIT) {
			throw new SemanticGlyphLimitExceeded();
		}
		glyphs.add(glyph);
	}

	private static final class SemanticGlyphLimitExceeded extends RuntimeException {
		private SemanticGlyphLimitExceeded() {
		}
	}

	public static Result collectNameTags(NameTagFeatureRenderer.Storage.SemanticSnapshot snapshot, Font font) {
		if (snapshot == null
			|| font == null
			|| snapshot.seeThrough().size() > MAX_SEMANTIC_SUBMITS_PER_COLLECTION
			|| snapshot.normal().size() > MAX_SEMANTIC_SUBMITS_PER_COLLECTION) {
			return new Result(List.of(), List.of(), 1);
		}
		List<WorldTextQuad> quads = new ArrayList<>();
		LinkedHashMap<Long, WorldTextImage> images = new LinkedHashMap<>();
		int unsupportedSubmits = 0;
		for (SubmitNodeStorage.NameTagSubmit submit : snapshot.seeThrough().stream()
			.sorted(Comparator.nullsLast(Comparator.comparing(SubmitNodeStorage.NameTagSubmit::distanceToCameraSq).reversed()))
			.toList()) {
			if (submit == null || submit.text() == null || submit.pose() == null) {
				unsupportedSubmits++;
				continue;
			}
			unsupportedSubmits += collect(submit, DEPTH_SEE_THROUGH, 0, font, quads, images);
		}
		for (SubmitNodeStorage.NameTagSubmit submit : snapshot.normal()) {
			if (submit == null || submit.text() == null || submit.pose() == null) {
				unsupportedSubmits++;
				continue;
			}
			unsupportedSubmits += collect(submit, DEPTH_NORMAL, 0, font, quads, images);
		}
		return new Result(List.copyOf(quads), List.copyOf(images.values()), unsupportedSubmits);
	}

	/**
	 * Extracts the ordinary resolved text-submit family through the same copied
	 * glyph/image contract as name tags. Outlined text is expanded into bounded
	 * neighboring semantic glyph quads behind the original glyphs, so no Java
	 * font draw or backend texture state crosses the route boundary.
	 */
	public static Result collectTextSubmits(List<SubmitNodeStorage.TextSubmit> submits, Font font) {
		if (submits == null || font == null || submits.size() > MAX_SEMANTIC_SUBMITS_PER_COLLECTION) {
			return new Result(List.of(), List.of(), 1);
		}
		List<WorldTextQuad> quads = new ArrayList<>();
		LinkedHashMap<Long, WorldTextImage> images = new LinkedHashMap<>();
		int unsupportedSubmits = 0;
		for (SubmitNodeStorage.TextSubmit submit : submits) {
			if (submit == null || submit.string() == null || submit.displayMode() == null || submit.pose() == null) {
				unsupportedSubmits++;
				continue;
			}
			if (quads.size() >= MAX_SEMANTIC_QUADS_PER_COLLECTION) {
				unsupportedSubmits++;
				break;
			}
			int depthPolicy = textSubmitDepthPolicy(submit);
			unsupportedSubmits += collect(
				submit.string(), submit.x(), submit.y(), submit.color(), submit.dropShadow(),
				submit.backgroundColor(), submit.lightCoords(), 0.0, submit.pose(), depthPolicy,
				submit.outlineColor(), submit.blockEntityId(), font, quads, images
			);
		}
		return new Result(List.copyOf(quads), List.copyOf(images.values()), unsupportedSubmits);
	}

	/** Returns the explicit depth policy for a resolved text submit. */
	static int textSubmitDepthPolicy(SubmitNodeStorage.TextSubmit submit) {
		return switch (submit.displayMode()) {
			case NORMAL -> DEPTH_NORMAL;
			case SEE_THROUGH -> DEPTH_SEE_THROUGH;
			case POLYGON_OFFSET -> DEPTH_POLYGON_OFFSET;
		};
	}

	private static int collect(
		SubmitNodeStorage.NameTagSubmit submit,
		int depthPolicy,
		int outlineColor,
		Font font,
		List<WorldTextQuad> output,
		LinkedHashMap<Long, WorldTextImage> images
	) {
		return collect(
			submit.text().getVisualOrderText(), submit.x(), submit.y(), submit.color(), false,
			 submit.backgroundColor(), submit.lightCoords(), submit.distanceToCameraSq(), submit.pose(), depthPolicy,
			outlineColor, -1, font, output, images
		);
	}

	private static int collect(
		net.minecraft.util.FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
		int backgroundColor, int packedLight, double distanceToCameraSq, org.joml.Matrix4f pose,
		int depthPolicy, int outlineColor, int blockEntityId, Font font, List<WorldTextQuad> output, LinkedHashMap<Long, WorldTextImage> images
	) {
		if (font == null || pose == null || !finiteMatrix(pose) || !Double.isFinite(distanceToCameraSq)) {
			return 1;
		}
		List<TextGlyphQuad> glyphs = new ArrayList<>(Math.min(MAX_SEMANTIC_GLYPHS_PER_SUBMIT, 128));
		Font.SemanticTextExtraction extraction;
		try {
			extraction = font
				.prepareText(text, x, y, color, dropShadow, backgroundColor)
				.collectSemanticQuads(glyph -> appendBoundedGlyph(glyphs, glyph));
		} catch (SemanticGlyphLimitExceeded limit) {
			return 1;
		}
		if (extraction.unsupportedRenderableCount() != 0) {
			return 1;
		}
		int glyphMultiplier = outlineColor == 0 ? 1 : 9;
		if (glyphs.size() > MAX_SEMANTIC_GLYPHS_PER_SUBMIT / glyphMultiplier) {
			return 1;
		}
		if ((long) output.size() + (long) glyphs.size() * glyphMultiplier > MAX_SEMANTIC_QUADS_PER_COLLECTION) {
			return 1;
		}
		LightTexture lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
		// Account residency without invoking the defensive-copy accessor; world
		// text collections can contain many glyphs sharing one atlas snapshot.
		long imageBytesTotal = images.values().stream().mapToLong(WorldTextImage::pixelByteCount).sum();
		for (TextGlyphQuad glyph : glyphs) {
			int semanticColor = lightTexture.rustSemanticPackedLightColor(packedLight, glyph.colorArgb());
			FontTexture.SemanticAtlasSnapshot atlas = FontTexture.semanticAtlasSnapshot(glyph.atlasIdentity());
			String imageIdentity = glyph.atlasIdentity();
			long imageGeneration;
			long imageRevision;
			boolean imageColored;
			int imageWidth;
			int imageHeight;
			byte[] atlasPixels;
			if (atlas != null) {
				imageGeneration = atlas.generation();
				imageRevision = atlas.revision();
				imageColored = atlas.colored();
				imageWidth = atlas.width();
				imageHeight = atlas.height();
				atlasPixels = atlas.pixels();
			} else {
				TextureAtlas.SemanticRawSnapshot raw = semanticTextureAtlasSnapshot(glyph.atlasIdentity());
				if (raw != null) {
					imageGeneration = raw.generation();
					// TextureAtlas snapshots predate the font-atlas revision field, but
					// their generation is still the complete copied-pixel identity. Use
					// that positive generation as the revision as well so atlas-backed
					// glyphs remain admissible through the same explicit world-text ABI.
					imageRevision = rawAtlasRevision(raw.generation());
					imageColored = true;
					imageWidth = raw.width();
					imageHeight = raw.height();
					atlasPixels = raw.pixels();
				} else {
					RustGalGuiRawImageAssets.SemanticRawImageSnapshot image = semanticRawImageSnapshot(glyph.atlasIdentity());
					if (image == null) return 1;
					imageGeneration = image.generation();
					imageRevision = image.revision();
					imageColored = true;
					imageWidth = image.width();
					imageHeight = image.height();
					atlasPixels = image.pixels();
				}
			}
			if (atlasPixels.length > MAX_SEMANTIC_ATLAS_BYTES) {
				return 1;
			}
			long assetId = semanticAssetId(imageIdentity, imageColored);
			WorldTextImage previous = images.get(assetId);
			if (previous != null) {
				if (!previous.matchesGeneration(imageGeneration, imageRevision, imageColored, imageWidth, imageHeight)) {
					return 1;
				}
				// The atlas is already owned by this collection. Avoid constructing a
				// defensive-copy record for every glyph that references it.
				if (outlineColor != 0) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dy = -1; dy <= 1; dy++) {
							if (dx == 0 && dy == 0) continue;
							output.add(new WorldTextQuad(
								glyph.atlasIdentity(), imageGeneration, imageRevision, imageColored, depthPolicy,
								packedLight, lightTexture.rustSemanticPackedLightColor(packedLight, outlineColor),
								distanceToCameraSq, matrixValues(pose), blockEntityId,
								shiftGlyph(glyph, dx, dy, outlineColor)
							));
						}
					}
				}
				output.add(new WorldTextQuad(
					glyph.atlasIdentity(), imageGeneration, imageRevision, imageColored, depthPolicy,
					packedLight, semanticColor, distanceToCameraSq, matrixValues(pose), blockEntityId, glyph
				));
				continue;
			}
			WorldTextImage image = new WorldTextImage(
				assetId, imageIdentity, imageGeneration, imageRevision, imageColored,
				imageWidth, imageHeight, atlasPixels
			);
			if (imageBytesTotal + atlasPixels.length > MAX_SEMANTIC_ATLAS_BYTES_TOTAL) {
				images.remove(assetId);
				return 1;
			}
			if (images.size() >= MAX_SEMANTIC_IMAGES_PER_COLLECTION) {
				images.remove(assetId);
				return 1;
			}
			images.put(assetId, image);
			imageBytesTotal += atlasPixels.length;
			if (outlineColor != 0) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						if (dx == 0 && dy == 0) continue;
						output.add(new WorldTextQuad(
							glyph.atlasIdentity(), imageGeneration, imageRevision, imageColored, depthPolicy,
							packedLight, lightTexture.rustSemanticPackedLightColor(packedLight, outlineColor),
							distanceToCameraSq, matrixValues(pose), blockEntityId,
							shiftGlyph(glyph, dx, dy, outlineColor)
						));
					}
				}
			}
			output.add(new WorldTextQuad(
				glyph.atlasIdentity(), imageGeneration, imageRevision, imageColored, depthPolicy,
				packedLight, semanticColor, distanceToCameraSq, matrixValues(pose), blockEntityId, glyph
			));
		}
		return 0;
	}

	private static TextureAtlas.SemanticRawSnapshot semanticTextureAtlasSnapshot(String identity) {
		ResourceLocation location;
		try {
			location = ResourceLocation.parse(identity);
		} catch (RuntimeException error) {
			return null;
		}
		TextureAtlas[] match = new TextureAtlas[1];
		Minecraft.getInstance().getAtlasManager().forEach((atlasId, atlas) -> {
			if (atlas.location().equals(location)) match[0] = atlas;
		});
		return match[0] == null ? null : match[0].semanticRawSnapshot();
	}

	/** TextureAtlas snapshots have no separate revision; generation is their copied-pixel revision. */
	static long rawAtlasRevision(long generation) {
		if (generation <= 0L) throw new IllegalArgumentException("raw atlas generation must be positive");
		return generation;
	}

	private static RustGalGuiRawImageAssets.SemanticRawImageSnapshot semanticRawImageSnapshot(String identity) {
		try {
			return RustGalGuiRawImageAssets.semanticSnapshotUnstaged(ResourceLocation.parse(identity));
		} catch (RuntimeException error) {
			return null;
		}
	}

	private static TextGlyphQuad shiftGlyph(TextGlyphQuad glyph, float dx, float dy, int colorArgb) {
		return new TextGlyphQuad(glyph.atlasIdentity(), glyph.colored(),
			glyph.x0() + dx, glyph.y0() + dy, glyph.x1() + dx, glyph.y1() + dy,
			glyph.x2() + dx, glyph.y2() + dy, glyph.x3() + dx, glyph.y3() + dy,
			glyph.z(), glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(), colorArgb);
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

	private static boolean finiteMatrix(org.joml.Matrix4f matrix) {
		return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01())
			&& Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
			&& Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11())
			&& Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
			&& Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21())
			&& Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
			&& Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31())
			&& Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
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
			long expectedBytes;
			try {
				expectedBytes = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), colored ? 4L : 1L);
			} catch (ArithmeticException error) {
				throw new IllegalArgumentException("world text image extent overflows RGBA byte count", error);
			}
			if (pixels.length != expectedBytes) {
				throw new IllegalArgumentException("world text image must contain exactly width*height format bytes");
			}
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}

		/** Internal bounded-residency accounting without cloning atlas pixels. */
		private int pixelByteCount() {
			return this.pixels.length;
		}

		boolean matchesGeneration(WorldTextImage other) {
			return matchesGeneration(other.atlasGeneration, other.atlasRevision, other.colored, other.width, other.height);
		}

		boolean matchesGeneration(long generation, long revision, boolean colored, int width, int height) {
			return this.atlasGeneration == generation
				&& this.atlasRevision == revision
				&& this.colored == colored
				&& this.width == width
				&& this.height == height;
		}
	}

	public record WorldTextQuad(
		String atlasIdentity,
		long atlasGeneration,
		long atlasRevision,
		boolean colored,
		int depthPolicy,
		int packedLight,
		int colorArgb,
		double distanceToCameraSq,
		float[] modelViewMatrix,
		int blockEntityId,
		TextGlyphQuad glyph
	) {
		public WorldTextQuad(String atlasIdentity, long atlasGeneration, long atlasRevision, boolean colored,
			int depthPolicy, int packedLight, int colorArgb, double distanceToCameraSq,
			float[] modelViewMatrix, TextGlyphQuad glyph) {
			this(atlasIdentity, atlasGeneration, atlasRevision, colored, depthPolicy, packedLight, colorArgb,
				distanceToCameraSq, modelViewMatrix, -1, glyph);
		}
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
			if (!Double.isFinite(distanceToCameraSq) || distanceToCameraSq < 0.0) {
				throw new IllegalArgumentException("world text camera distance must be finite and non-negative");
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
				this.colorArgb,
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
				},
				this.blockEntityId
			);
		}
	}
}
