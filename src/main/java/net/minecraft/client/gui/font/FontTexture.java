package net.minecraft.client.gui.font;

import net.blaze3d.font.GlyphBitmap;
import net.blaze3d.font.GlyphInfo;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.platform.TextureUtil;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.TextureFormat;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class FontTexture extends AbstractTexture implements Dumpable {
	private static final int SIZE = 256;
	private static final int MAX_SEMANTIC_ATLASES = 4096;
	private static final Map<String, FontTexture> SEMANTIC_ATLASES = new ConcurrentHashMap<>();
	private static final AtomicLong NEXT_SEMANTIC_GENERATION = new AtomicLong(1L);
	private final ResourceLocation semanticAtlasIdentity;
	private final long semanticAtlasGeneration;
	private final NativeImage semanticAtlasPixels;
	private boolean semanticAtlasComplete = true;
	private long semanticAtlasRevision;
	@Nullable
	private SemanticAtlasSnapshot semanticAtlasSnapshotCache;
	private final GlyphRenderTypes renderTypes;
	private final boolean colored;
	private final FontTexture.Node root;

	public FontTexture(ResourceLocation resourceLocation, GlyphRenderTypes glyphRenderTypes, boolean bl) {
		this.semanticAtlasIdentity = resourceLocation;
		this.semanticAtlasGeneration = NEXT_SEMANTIC_GENERATION.getAndIncrement();
		this.colored = bl;
		this.root = new FontTexture.Node(0, 0, 256, 256);
		this.semanticAtlasPixels = new NativeImage(bl ? NativeImage.Format.RGBA : NativeImage.Format.LUMINANCE, SIZE, SIZE, true);
		FontTexture previousAtlas;
		synchronized (SEMANTIC_ATLASES) {
			String identity = this.semanticAtlasIdentity.toString();
			if (!SEMANTIC_ATLASES.containsKey(identity) && SEMANTIC_ATLASES.size() >= MAX_SEMANTIC_ATLASES) {
				this.semanticAtlasPixels.close();
				throw new IllegalStateException("semantic font-atlas registry bound exceeded " + MAX_SEMANTIC_ATLASES);
			}
			previousAtlas = SEMANTIC_ATLASES.put(identity, this);
		}
		if (previousAtlas != null) {
			// Resource reloads reuse atlas identities. Retire the old semantic
			// pixels (and any OpenGL-only allocation) instead of leaking one per
			// generation; close() cannot remove this newly-installed entry.
			previousAtlas.close();
		}
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			this.texture = net.vulkanic.VulkanicAPI.createTexture(resourceLocation::toString, 7, bl ? TextureFormat.RGBA8 : TextureFormat.RED8, 256, 256, 1, 1);
			this.texture.setTextureFilter(FilterMode.NEAREST, false);
			this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
		}
		this.renderTypes = glyphRenderTypes;
	}

	@Nullable
	public BakedSheetGlyph add(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap) {
		if (glyphBitmap.isColored() != this.colored) {
			return null;
		} else {
			FontTexture.Node node = this.root.insert(glyphBitmap);
			if (node != null) {
				if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
					&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
					glyphBitmap.upload(node.x, node.y, this.getTexture());
				}
				// The whole-frame Vulkan route owns a copied atlas image, not this
				// Java texture.  Glyph pixels below are copied into that semantic
				// snapshot and uploaded through VulkanicGAL by the text collector.
				if (this.semanticAtlasComplete) {
					try {
						if (!glyphBitmap.copyTo(this.semanticAtlasPixels, node.x, node.y)) {
							this.semanticAtlasComplete = false;
						}
					} catch (RuntimeException runtimeException) {
						this.semanticAtlasComplete = false;
					}
				}
				this.semanticAtlasRevision++;
				this.semanticAtlasSnapshotCache = null;
				float f = 256.0F;
				float g = 256.0F;
				float h = 0.01F;
				net.blaze3d.textures.GpuTextureView glyphTextureView = (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
					|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected())
					? this.textureView
					: this.getTextureView();
				return new BakedSheetGlyph(
					glyphInfo,
					this.renderTypes,
					glyphTextureView,
					this.semanticAtlasIdentity.toString(),
					this.colored,
					(node.x + 0.01F) / 256.0F,
					(node.x - 0.01F + glyphBitmap.getPixelWidth()) / 256.0F,
					(node.y + 0.01F) / 256.0F,
					(node.y - 0.01F + glyphBitmap.getPixelHeight()) / 256.0F,
					glyphBitmap.getLeft(),
					glyphBitmap.getRight(),
					glyphBitmap.getTop(),
					glyphBitmap.getBottom()
				);
			} else {
				return null;
			}
		}
	}

	@Nullable
	public SemanticAtlasSnapshot semanticAtlasSnapshot() {
		if (!this.semanticAtlasComplete) {
			return null;
		}
		SemanticAtlasSnapshot cached = this.semanticAtlasSnapshotCache;
		if (cached != null && cached.revision() == this.semanticAtlasRevision) {
			return cached;
		}
		int i = SIZE * SIZE * this.semanticAtlasPixels.format().components();
		byte[] bytes = new byte[i];
		MemoryUtil.memByteBuffer(this.semanticAtlasPixels.getPointer(), i).get(bytes);
		SemanticAtlasSnapshot snapshot = new SemanticAtlasSnapshot(
			this.semanticAtlasIdentity.toString(), this.colored, SIZE, SIZE,
			this.semanticAtlasGeneration, this.semanticAtlasRevision, bytes
		);
		this.semanticAtlasSnapshotCache = snapshot;
		return snapshot;
	}

	@Nullable
	public static SemanticAtlasSnapshot semanticAtlasSnapshot(String identity) {
		FontTexture fontTexture;
		synchronized (SEMANTIC_ATLASES) {
			fontTexture = SEMANTIC_ATLASES.get(identity);
		}
		return fontTexture == null ? null : fontTexture.semanticAtlasSnapshot();
	}

	@Override
	public void close() {
		synchronized (SEMANTIC_ATLASES) {
			SEMANTIC_ATLASES.remove(this.semanticAtlasIdentity.toString(), this);
		}
		this.semanticAtlasSnapshotCache = null;
		super.close();
		this.semanticAtlasPixels.close();
	}

	/**
	 * Releases only a legacy Java GPU allocation after Vulkan selection. The
	 * copied semantic atlas remains live for Rust text extraction and staging.
	 */
	public void ensureRustSemanticRoute() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			// Keep the CPU atlas and semantic registry alive for Rust text
			// extraction.  Only the legacy Java GPU allocation crosses this
			// ownership boundary; full close() remains responsible for destroying
			// the semantic source during resource teardown.
			closeGpuAllocation();
		}
	}

	public record SemanticAtlasSnapshot(
		String identity, boolean colored, int width, int height, long generation, long revision, byte[] pixels
	) {
		public SemanticAtlasSnapshot {
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}
	}

	@Override
	public void dumpContents(ResourceLocation resourceLocation, Path path) {
		if (this.texture != null) {
			String string = resourceLocation.toDebugFileName();
			TextureUtil.writeAsPNG(path, string, this.texture, 0, i -> (i & 0xFF000000) == 0 ? -16777216 : i);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Node {
		final int x;
		final int y;
		private final int width;
		private final int height;
		@Nullable
		private FontTexture.Node left;
		@Nullable
		private FontTexture.Node right;
		private boolean occupied;

		Node(int i, int j, int k, int l) {
			this.x = i;
			this.y = j;
			this.width = k;
			this.height = l;
		}

		@Nullable
		FontTexture.Node insert(GlyphBitmap glyphBitmap) {
			if (this.left != null && this.right != null) {
				FontTexture.Node node = this.left.insert(glyphBitmap);
				if (node == null) {
					node = this.right.insert(glyphBitmap);
				}

				return node;
			} else if (this.occupied) {
				return null;
			} else {
				int i = glyphBitmap.getPixelWidth();
				int j = glyphBitmap.getPixelHeight();
				if (i > this.width || j > this.height) {
					return null;
				} else if (i == this.width && j == this.height) {
					this.occupied = true;
					return this;
				} else {
					int k = this.width - i;
					int l = this.height - j;
					if (k > l) {
						this.left = new FontTexture.Node(this.x, this.y, i, this.height);
						this.right = new FontTexture.Node(this.x + i + 1, this.y, this.width - i - 1, this.height);
					} else {
						this.left = new FontTexture.Node(this.x, this.y, this.width, j);
						this.right = new FontTexture.Node(this.x, this.y + j + 1, this.width, this.height - j - 1);
					}

					return this.left.insert(glyphBitmap);
				}
			}
		}
	}
}
