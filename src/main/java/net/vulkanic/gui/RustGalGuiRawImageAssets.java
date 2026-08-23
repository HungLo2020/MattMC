package net.vulkanic.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.ARGB;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Owns bounded copies of resource-pack PNGs used by semantic GUI producers.
 * A producer receives only a stable raw-image identity; Java texture views and
 * atlas objects never cross the VulkanicGAL boundary.
 */
public final class RustGalGuiRawImageAssets {
	private static final int RAW_RGBA8 = 2;
	private static final int MAX_ENCODED_BYTES = 32 * 1024 * 1024;
	private static final int MAX_DECODED_PIXELS = 16 * 1024 * 1024;
	private static final int MAX_SEMANTIC_IDENTITIES = 4096;
	private static final int MAX_CACHED_ASSET_ENTRIES = 4096;
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Asset> CACHE = new HashMap<>();
	private static final Map<ResourceLocation, Asset> EARLY_VANILLA_CACHE = new HashMap<>();
	private static final Map<ResourceLocation, AtlasAsset> ATLAS_CACHE = new HashMap<>();
	private static final Map<ResourceLocation, Asset> CUBEMAP_CACHE = new HashMap<>();
	/** Dynamic sources are indexed by their semantic resource identity, never a Java texture handle. */
	private static final Map<ResourceLocation, DynamicTexture> DYNAMIC_TEXTURES = new HashMap<>();
	private static final Map<DynamicTexture, ResourceLocation> DYNAMIC_TEXTURE_IDS = new IdentityHashMap<>();
	private static final Map<ResourceLocation, Asset> DYNAMIC_ASSETS = new HashMap<>();
	private static final Set<Long> STAGED_CUBEMAP_ASSETS = new HashSet<>();
	private static final Map<Long, String> IDENTITIES = new HashMap<>();

	private RustGalGuiRawImageAssets() {
	}

	/**
	 * Binds a Java CPU image source to its public texture identity.  The copied
	 * pixels are staged through VulkanicGAL; the associated Java {@code GpuTexture}
	 * remains an inert metadata object and is never read by Rust.
	 */
	public static void registerDynamicTexture(ResourceLocation identity, DynamicTexture texture) {
		if (identity == null || texture == null) throw new IllegalArgumentException("dynamic texture identity and source are required");
		synchronized (LOCK) {
			DynamicTexture previous = DYNAMIC_TEXTURES.put(identity, texture);
			if (previous != null && previous != texture) DYNAMIC_TEXTURE_IDS.remove(previous);
			DYNAMIC_TEXTURE_IDS.put(texture, identity);
		}
		stageDynamicTexture(texture);
	}

	/** Removes a dynamic source only when it is still the source registered for the identity. */
	public static void unregisterDynamicTexture(ResourceLocation identity, DynamicTexture texture) {
		if (identity == null || texture == null) return;
		synchronized (LOCK) {
			if (DYNAMIC_TEXTURES.get(identity) == texture) {
				DYNAMIC_TEXTURES.remove(identity);
				DYNAMIC_ASSETS.remove(identity);
			}
			DYNAMIC_TEXTURE_IDS.remove(texture);
		}
	}

	/**
	 * Copies an updated DynamicTexture image into a Rust-owned raw image asset.
	 * An unregistered source has no public semantic identity and is deliberately
	 * not admitted to rendering.
	 */
	public static boolean stageDynamicTexture(DynamicTexture texture) {
		ResourceLocation identity;
		synchronized (LOCK) {
			identity = DYNAMIC_TEXTURE_IDS.get(texture);
		}
		if (identity == null) return false;
		NativeImage image = texture.getPixels();
		if (image == null || image.format() != NativeImage.Format.RGBA) {
			throw new IllegalStateException("whole-frame dynamic texture " + identity + " must retain an RGBA CPU image");
		}
		long pixelCount = (long)image.getWidth() * image.getHeight();
		if (pixelCount <= 0 || pixelCount > MAX_DECODED_PIXELS) {
			throw new IllegalStateException("whole-frame dynamic texture " + identity + " exceeds the semantic image bound");
		}
		byte[] pixels = new byte[Math.toIntExact(pixelCount * 4L)];
		MemoryUtil.memByteBuffer(image.getPointer(), pixels.length).get(pixels);
		Asset asset = new Asset(assetId("dynamic:" + identity), "dynamic:" + identity, image.getWidth(), image.getHeight(), pixels);
		synchronized (LOCK) {
			if (DYNAMIC_TEXTURES.get(identity) != texture) return false;
			if (!cachePutLocked(DYNAMIC_ASSETS, identity, asset)) return false;
		}
		stage(asset);
		return true;
	}

	static void invalidate() {
		synchronized (LOCK) {
			CACHE.clear();
			// The early cache exists only for the pre-reload loading overlay. It
			// must not survive a resource-pack reload or it can shadow the newly
			// selected pack's image for the same semantic identity.
			EARLY_VANILLA_CACHE.clear();
			ATLAS_CACHE.clear();
			CUBEMAP_CACHE.clear();
			STAGED_CUBEMAP_ASSETS.clear();
			IDENTITIES.clear();
		}
	}

	/**
	 * Resolves either an explicit PNG resource or a conventional sprite identity
	 * ({@code namespace:path} -> {@code namespace:textures/path.png}). Dynamic
	 * atlas locations have no file-backed contract and are deliberately declined.
	 */
	@Nullable
	static Asset resolve(ResourceLocation source) {
		// Atlas identities refer to the stitched CPU snapshot, not to a Java
		// texture handle or a file-backed PNG. Resolve them before the ordinary
		// resource candidates so model previews and semantic image producers can
		// consume resource-pack atlas contents through the same owned asset path.
		if (source != null && source.getPath().startsWith("textures/atlas/")) {
			Asset atlas = resolveAtlas(source);
			if (atlas != null) return atlas;
		}
		 synchronized (LOCK) {
			Asset earlySource = EARLY_VANILLA_CACHE.get(source);
			if (earlySource != null) return earlySource;
			Asset dynamic = DYNAMIC_ASSETS.get(source);
			if (dynamic != null) return dynamic;
		}
		for (ResourceLocation candidate : candidates(source)) {
			synchronized (LOCK) {
				Asset early = EARLY_VANILLA_CACHE.get(candidate);
				if (early != null) return early;
				Asset cached = CACHE.get(candidate);
				if (cached != null) {
					return cached;
				}
			}
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(candidate);
			if (resource.isEmpty()) {
				continue;
			}
			Asset decoded = decode(candidate, resource.get(), MAX_DECODED_PIXELS);
			if (decoded == null) {
				return null;
			}
			synchronized (LOCK) {
				if (!cachePutLocked(CACHE, candidate, decoded)) return null;
			}
			return decoded;
		}
		// The loading overlay can submit the vanilla logo before the first
		// ResourceManager reload has published its pack stack. Keep that early
		// frame semantic (CPU PNG -> Rust-owned image) instead of borrowing the
		// Java texture; normal resource-pack resolution above always wins.
		if (source != null && "minecraft".equals(source.getNamespace())) {
			for (ResourceLocation candidate : candidates(source)) {
				String classpathName = "/assets/" + candidate.getNamespace() + "/" + candidate.getPath();
				try (InputStream input = RustGalGuiRawImageAssets.class.getResourceAsStream(classpathName)) {
					if (input == null) continue;
					byte[] encoded = input.readNBytes(MAX_ENCODED_BYTES + 1);
					if (encoded.length > MAX_ENCODED_BYTES) return null;
					Asset decoded = decode(candidate, encoded, MAX_DECODED_PIXELS);
					if (decoded != null) {
						 synchronized (LOCK) {
							if (!cachePutLocked(CACHE, candidate, decoded)) return null;
						 }
						return decoded;
					}
				} catch (IOException error) {
					return null;
				}
			}
		}
		return null;
	}

	/** Returns a bounded CPU image snapshot for non-GUI semantic consumers. */
	@Nullable
	public static SemanticRawImageSnapshot semanticSnapshot(ResourceLocation source) {
		if (source == null) return null;
		Asset asset = resolve(source);
		if (asset == null) return null;
		stage(asset);
		long revision = 0xcbf29ce484222325L;
		for (byte value : asset.pixels()) {
			revision ^= value & 0xffL;
			revision *= 0x100000001b3L;
		}
		return new SemanticRawImageSnapshot(asset.identity(), asset.width(), asset.height(), 1L, revision, asset.pixels());
	}

	/** Stages an early vanilla-pack image before the reload manager publishes its stack. */
	public static boolean stageVanillaResource(ResourceLocation source, ResourceProvider provider) {
		if (source == null || provider == null) return false;
		try (InputStream input = provider.open(source)) {
			byte[] encoded = input.readNBytes(MAX_ENCODED_BYTES + 1);
			if (encoded.length > MAX_ENCODED_BYTES) return false;
			Asset asset = decode(source, encoded, MAX_DECODED_PIXELS);
			if (asset == null) return false;
			 synchronized (LOCK) {
				int newEntries = (EARLY_VANILLA_CACHE.containsKey(source) ? 0 : 1)
					+ (CACHE.containsKey(source) ? 0 : 1);
				if (!cacheHasCapacityLocked(newEntries)) return false;
				cachePutLocked(EARLY_VANILLA_CACHE, source, asset);
				cachePutLocked(CACHE, source, asset);
			 }
			stage(asset);
			return true;
		} catch (IOException error) {
			return false;
		}
	}

	/** Copies a CPU NativeImage produced by a vanilla loader into the Rust asset store. */
	public static boolean stageNativeImage(ResourceLocation source, NativeImage image) {
		if (source == null || image == null || image.format() != NativeImage.Format.RGBA) return false;
		long pixels = (long) image.getWidth() * image.getHeight();
		if (pixels <= 0 || pixels > MAX_DECODED_PIXELS) return false;
		byte[] data = new byte[Math.toIntExact(pixels * 4L)];
		MemoryUtil.memByteBuffer(image.getPointer(), data.length).get(data);
		Asset asset = new Asset(assetId(source.toString()), source.toString(), image.getWidth(), image.getHeight(), data);
		synchronized (LOCK) {
			int newEntries = (EARLY_VANILLA_CACHE.containsKey(source) ? 0 : 1)
				+ (CACHE.containsKey(source) ? 0 : 1);
			if (!cacheHasCapacityLocked(newEntries)) return false;
			cachePutLocked(EARLY_VANILLA_CACHE, source, asset);
			cachePutLocked(CACHE, source, asset);
		}
		stage(asset);
		return true;
	}

	/**
	 * Copies the currently selected frame of an animated sprite into one stable
	 * semantic image identity. The identity is intentionally frame-independent:
	 * staging a later frame replaces the same Rust-owned asset instead of
	 * allocating an unbounded resource per animation tick.
	 */
	@Nullable
	static Asset resolveAnimatedSprite(TextureAtlasSprite sprite) {
		if (sprite == null || sprite.contents() == null || !sprite.contents().isAnimated()) return null;
		SpriteContents contents = sprite.contents();
		int width = contents.width();
		int height = contents.height();
		if (width <= 0 || height <= 0 || (long)width * height > MAX_DECODED_PIXELS) return null;
		int frame = contents.semanticFrameIndex();
		if (contents.animatedTexture == null || frame < 0) return null;
		int frameX = contents.animatedTexture.getFrameX(frame);
		int frameY = contents.animatedTexture.getFrameY(frame);
		if (frameX < 0 || frameY < 0
			|| (long)(frameX + 1) * width > contents.originalImage.getWidth()
			|| (long)(frameY + 1) * height > contents.originalImage.getHeight()) return null;
		byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = contents.originalImage.getPixel(frameX * width + x, frameY * height + y);
				int offset = (y * width + x) * 4;
				pixels[offset] = (byte)ARGB.red(argb);
				pixels[offset + 1] = (byte)ARGB.green(argb);
				pixels[offset + 2] = (byte)ARGB.blue(argb);
				pixels[offset + 3] = (byte)ARGB.alpha(argb);
			}
		}
		String identity = "animated-sprite:" + contents.name() + ":" + width + "x" + height;
		return new Asset(assetId(identity), identity, width, height, pixels);
	}

	@Nullable
	private static Asset resolveCubeFace(ResourceLocation source) {
		synchronized (LOCK) {
			Asset cached = CACHE.get(source);
			if (cached != null) {
				return (long)cached.width() * cached.height() <= MAX_DECODED_PIXELS / 6L ? cached : null;
			}
		}
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(source);
		if (resource.isEmpty()) return null;
		Asset decoded = decode(source, resource.get(), MAX_DECODED_PIXELS / 6);
		if (decoded == null) return null;
		synchronized (LOCK) {
			if (!cachePutLocked(CACHE, source, decoded)) return null;
		}
		return decoded;
	}

	/**
	 * Copies the six source PNGs using the exact stacked-face ordering and
	 * vertical orientation of {@link net.minecraft.client.renderer.texture.CubeMapTexture}.
	 * The result is an ordinary Rust-owned RGBA image; it is never a Java
	 * cubemap, texture view, or GPU handle.
	 */
	@Nullable
	static Asset resolveCubeMap(ResourceLocation source) {
		synchronized (LOCK) {
			Asset cached = CUBEMAP_CACHE.get(source);
			if (cached != null) return cached;
		}
		String[] suffixes = {"_1.png", "_3.png", "_5.png", "_4.png", "_0.png", "_2.png"};
		Asset[] faces = new Asset[suffixes.length];
		int width = 0;
		int height = 0;
		for (int face = 0; face < suffixes.length; face++) {
			faces[face] = resolveCubeFace(source.withSuffix(suffixes[face]));
			if (faces[face] == null) return null;
			if (face == 0) {
				width = faces[face].width();
				height = faces[face].height();
			} else if (faces[face].width() != width || faces[face].height() != height) {
				return null;
			}
		}
		if (width <= 0 || height <= 0 || (long)width * height * suffixes.length > MAX_DECODED_PIXELS) return null;
		byte[] pixels;
		try {
			pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, Math.multiplyExact(height, suffixes.length)), 4)];
		} catch (ArithmeticException error) {
			return null;
		}
		for (int face = 0; face < suffixes.length; face++) {
			byte[] sourcePixels = faces[face].pixels();
			for (int y = 0; y < height; y++) {
				// CubeMapTexture.copyRect(..., false, true) flips every source face.
				int sourceOffset = ((height - 1 - y) * width) * 4;
				int targetOffset = ((face * height + y) * width) * 4;
				System.arraycopy(sourcePixels, sourceOffset, pixels, targetOffset, width * 4);
			}
		}
		Asset result = new Asset(assetId("cubemap:" + source), "cubemap:" + source, width, height * suffixes.length, pixels);
		synchronized (LOCK) {
			if (!cachePutLocked(CUBEMAP_CACHE, source, result)) return null;
		}
		return result;
	}

	static void stage(Asset asset) {
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			asset.assetId(), RAW_RGBA8, asset.width(), asset.height(), asset.pixels()
		));
	}

	/** Stages an immutable cube-map copy at most once per resource generation. */
	static void stageCubeMap(Asset asset) {
		synchronized (LOCK) {
			if (!STAGED_CUBEMAP_ASSETS.add(asset.assetId())) return;
		}
		stage(asset);
	}

	/** Resolves a stitched atlas by semantic texture location and copies only its CPU snapshot. */
	@Nullable
	static Asset resolveAtlas(ResourceLocation atlasTextureLocation) {
		TextureAtlas[] matchingAtlas = new TextureAtlas[1];
		Minecraft.getInstance().getAtlasManager().forEach((atlasId, atlas) -> {
			if (atlas.location().equals(atlasTextureLocation)) {
				matchingAtlas[0] = atlas;
			}
		});
		if (matchingAtlas[0] == null) {
			return null;
		}
		TextureAtlas.SemanticRawSnapshot snapshot = matchingAtlas[0].semanticRawSnapshot();
		if (snapshot == null) {
			return null;
		}
		synchronized (LOCK) {
			AtlasAsset cached = ATLAS_CACHE.get(snapshot.atlasLocation());
			if (cached != null && cached.generation() == snapshot.generation()) {
				return cached.asset();
			}
		}
		Asset asset = new Asset(
			assetId("atlas:" + snapshot.atlasLocation()),
			"atlas:" + snapshot.atlasLocation(), snapshot.width(), snapshot.height(), snapshot.pixels()
		);
		synchronized (LOCK) {
			if (!cachePutLocked(ATLAS_CACHE, snapshot.atlasLocation(), new AtlasAsset(snapshot.generation(), asset))) return null;
		}
		return asset;
	}

	private static LinkedHashSet<ResourceLocation> candidates(ResourceLocation source) {
		LinkedHashSet<ResourceLocation> values = new LinkedHashSet<>();
		String path = source.getPath();
		if (path.startsWith("textures/") && path.endsWith(".png")) {
			values.add(source);
		} else if (path.endsWith(".png")) {
			// Mod-owned semantic GUI producers may publish ordinary asset paths
			// (for example voxelmap:images/roundmap.png). Keep the source identity
			// intact; never infer a Java texture or GPU handle from it.
			values.add(source);
		} else if (!path.startsWith("textures/") && !path.endsWith(".png")) {
			values.add(ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "textures/" + path + ".png"));
		}
		return values;
	}

	@Nullable
	private static Asset decode(ResourceLocation resourceId, Resource resource, int maximumPixels) {
		try (InputStream input = resource.open()) {
			byte[] encoded = input.readNBytes(MAX_ENCODED_BYTES + 1);
			if (encoded.length > MAX_ENCODED_BYTES) {
				return null;
			}
			return decode(resourceId, encoded, maximumPixels);
		} catch (IOException error) {
			return null;
		}
	}

	@Nullable
	private static Asset decode(ResourceLocation resourceId, byte[] encoded, int maximumPixels) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
				|| (long)image.getWidth() * image.getHeight() > maximumPixels) {
				return null;
			}
			byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(image.getWidth(), image.getHeight()), 4)];
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getRGB(x, y);
					int offset = (y * image.getWidth() + x) * 4;
					pixels[offset] = (byte)ARGB.red(argb);
					pixels[offset + 1] = (byte)ARGB.green(argb);
					pixels[offset + 2] = (byte)ARGB.blue(argb);
					pixels[offset + 3] = (byte)ARGB.alpha(argb);
				}
			}
			return new Asset(assetId(resourceId.toString()), resourceId.toString(), image.getWidth(), image.getHeight(), pixels);
		} catch (IOException | ArithmeticException error) {
			return null;
		}
	}

	/** Must be called while holding {@link #LOCK}. */
	private static <K, V> boolean cachePutLocked(Map<K, V> cache, K key, V value) {
		if (!cache.containsKey(key) && !cacheHasCapacityLocked(1)) {
			return false;
		}
		cache.put(key, value);
		return true;
	}

	/** Must be called while holding {@link #LOCK}. */
	private static boolean cacheHasCapacityLocked(int newEntries) {
		return newEntries >= 0
			&& cachedAssetEntryCountLocked() <= MAX_CACHED_ASSET_ENTRIES - newEntries;
	}

	/** Must be called while holding {@link #LOCK}. */
	private static int cachedAssetEntryCountLocked() {
		return CACHE.size()
			+ EARLY_VANILLA_CACHE.size()
			+ ATLAS_CACHE.size()
			+ CUBEMAP_CACHE.size()
			+ DYNAMIC_ASSETS.size();
	}

	private static long assetId(String identity) {
		synchronized (LOCK) {
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < identity.length(); i++) {
			hash ^= identity.charAt(i);
			hash *= 0x100000001b3L;
		}
		if (hash == 0L) {
			hash = 1L;
		}
		String previous = IDENTITIES.get(hash);
		if (previous == null && IDENTITIES.size() >= MAX_SEMANTIC_IDENTITIES) {
			throw new IllegalStateException(
				"semantic GUI raw-image identity table exceeded bound " + MAX_SEMANTIC_IDENTITIES
			);
		}
		if (previous == null) {
			IDENTITIES.put(hash, identity);
		}
		if (previous != null && !previous.equals(identity)) {
			throw new IllegalStateException("semantic GUI raw-image identity collision");
		}
			return hash;
		}
	}

	record Asset(long assetId, String identity, int width, int height, byte[] pixels) {
		Asset {
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}
	}

	public record SemanticRawImageSnapshot(String identity, int width, int height, long generation, long revision, byte[] pixels) {
		public SemanticRawImageSnapshot {
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return pixels.clone();
		}
	}

	private record AtlasAsset(long generation, Asset asset) {
	}
}
