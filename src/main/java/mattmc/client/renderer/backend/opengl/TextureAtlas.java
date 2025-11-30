package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.texture.AnimatedTextureData;
import mattmc.client.renderer.texture.TextureCoordinateProvider;
import mattmc.client.resources.metadata.animation.AnimationMetadataSection;
import mattmc.client.resources.metadata.animation.FrameSize;
import mattmc.client.settings.OptionsManager;
import mattmc.world.level.block.Block;
import mattmc.registries.Blocks;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of texture coordinate provider using texture atlas.
 * Runtime texture atlas builder for block textures.
 * Packs all block textures into a single atlas at game startup,
 * enabling VBO rendering with multiple textures.
 * 
 * <p><b>Architecture:</b> The TextureAtlas is created at client/resource initialization
 * time, before a world is selected. Blocks/items and game logic use string texture paths
 * as canonical IDs (e.g. "mattmc:block/stone"). The rendering backend uses fast integer
 * texture IDs internally for performance.
 * 
 * <p>The atlas provides:
 * <ul>
 *   <li>String path → int ID mapping via {@link #getTextureId(String)}</li>
 *   <li>Int ID → UVMapping lookup via {@link #getUVMapping(int)}</li>
 *   <li>Backward-compatible string-based lookup via {@link #getUVMapping(String)}</li>
 * </ul>
 * 
 * Similar to modern MattMC's texture atlas system.
 * Implements AutoCloseable to ensure OpenGL resources are properly released.
 */
public class TextureAtlas implements TextureCoordinateProvider, AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(TextureAtlas.class);

	private final int atlasTextureId;
	private final int atlasWidth;
	private final int atlasHeight;
	private final int textureSize = 16; // Standard MattMC texture size
	
	/**
	 * UV shrink ratio for preventing texture bleeding at lower mipmap levels.
	 * Computed as 4.0f / max(atlasWidth, atlasHeight), matching Minecraft's
	 * TextureAtlasSprite.uvShrinkRatio() algorithm.
	 */
	private final float uvShrinkRatio;
	
	// Int-keyed UV mappings for fast hot path lookup
	private final Map<Integer, TextureCoordinateProvider.UVMapping> uvMappings = new HashMap<>();
	
	// String path → int ID mapping for texture ID lookup
	private final Map<String, Integer> pathToId = new HashMap<>();
	
	// Reverse lookup for debugging/tooling: int ID → string path
	private final List<String> idToPath = new ArrayList<>();
	
	// Animated texture data for textures with .mcmeta files
	private final Map<String, AnimatedTextureData> animatedTextures = new HashMap<>();
	
	// Atlas position data for animated texture updates
	private final Map<String, int[]> textureAtlasPositions = new HashMap<>();
    
	/**
	 * Build the texture atlas from all registered blocks.
	 * Call this once during game initialization.
	 * 
	 * <p><b>Note:</b> This constructor builds the atlas and populates all mappings.
	 * After construction, the string→int ID mapping is fixed for the atlas lifetime.
	 */
	public TextureAtlas() {
		// logger.info("Building texture atlas...");
		
		// Collect all unique texture paths from registered blocks
		Set<String> uniqueTexturePaths = new HashSet<>();
		for (String identifier : Blocks.getRegisteredIdentifiers()) {
			// Skip AIR block - it doesn't have a texture
			if (identifier.equals("mattmc:air")) {
				continue;
			}
			
			Block block = Blocks.getBlock(identifier);
			if (block != null) {
				Map<String, String> texturePaths = block.getTexturePaths();
				if (texturePaths != null) {
					uniqueTexturePaths.addAll(texturePaths.values());
				}
			}
		}
		
		// Remove null paths
		uniqueTexturePaths.remove(null);
		
		// logger.info("Found {} unique textures", uniqueTexturePaths.size());
		
		// Calculate atlas dimensions (must be power of 2 for mipmaps)
		int textureCount = uniqueTexturePaths.size();
		int texturesPerRow = (int) Math.ceil(Math.sqrt(textureCount));
		
		// Round up to next power of 2
		int powerOf2Width = nextPowerOf2(texturesPerRow * textureSize);
		int powerOf2Height = powerOf2Width; // Keep it square
		
		atlasWidth = powerOf2Width;
		atlasHeight = powerOf2Height;
		
		// Compute UV shrink ratio to prevent texture bleeding at lower mipmap levels.
		// This matches Minecraft's TextureAtlasSprite.uvShrinkRatio() algorithm:
		// uvShrinkRatio = 4.0f / atlasSize
		// where atlasSize = max(atlasWidth, atlasHeight) * textureSize / spriteSize
		// For our standard 16x16 textures in the atlas, this simplifies to:
		float atlasSize = (float) Math.max(atlasWidth, atlasHeight);
		this.uvShrinkRatio = atlasSize > 0 ? 4.0f / atlasSize : 0.0f;
		
		// logger.info("Atlas size: {}x{} ({} textures, {} per row)", atlasWidth, atlasHeight, textureCount, texturesPerRow);
		
		// Create atlas image
		BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlasImage.createGraphics();
		
		// Fill with magenta initially as a fallback for any unfilled regions
		// This helps diagnose issues - if magenta is visible, padding logic has a bug
		// Normal operation: magenta is fully overwritten by textures and padding
		g.setColor(new Color(255, 0, 255, 255));
		g.fillRect(0, 0, atlasWidth, atlasHeight);
		
		// Pack textures into atlas
		int x = 0, y = 0;
		List<String> textureList = new ArrayList<>(uniqueTexturePaths);
		
		// Use Src composite to completely replace destination pixels with source pixels
		// This preserves the texture's alpha channel instead of blending with the background
		g.setComposite(java.awt.AlphaComposite.Src);
		
		// Track position of last filled texture for filling unused space
		int lastFilledX = 0;
		int lastFilledY = 0;
		int texturesPlaced = 0;
		
		for (String texturePath : textureList) {
			try {
				// Load texture image (handles animated textures with .mcmeta files)
				BufferedImage texture = loadTextureForAtlas(texturePath);
				if (texture != null) {
					// Draw texture into atlas (Src composite preserves alpha)
					g.drawImage(texture, x, y, textureSize, textureSize, null);
					
					// Store atlas position for animated texture updates
					textureAtlasPositions.put(texturePath, new int[]{x, y});
					
					// Calculate UV coordinates (0.0 to 1.0)
					float u0 = (float) x / atlasWidth;
					float v0 = (float) y / atlasHeight;
					float u1 = (float) (x + textureSize) / atlasWidth;
					float v1 = (float) (y + textureSize) / atlasHeight;
					
					// Register texture with int ID mapping and UV shrink ratio
					// The shrink ratio prevents texture bleeding at lower mipmap levels
					registerTexture(texturePath, new TextureCoordinateProvider.UVMapping(u0, v0, u1, v1, uvShrinkRatio));
					
					// Keep track of last texture position for padding
					lastFilledX = x;
					lastFilledY = y;
					texturesPlaced++;
					
					// logger.info("  Packed: {} at ({},{}) UV: {},{} -> {},{}", texturePath, x, y, u0, v0, u1, v1);
				} else {
					logger.error("  Failed to load: {}", texturePath);
				}
			} catch (RuntimeException e) {
				logger.error("  Error loading {}: {}", texturePath, e.getMessage());
			}
			
			// Move to next position
			x += textureSize;
			if (x >= atlasWidth) {
				x = 0;
				y += textureSize;
			}
		}
		
		// Fill remaining space with edge-pixel padding to prevent mipmap bleeding
		// This is critical to prevent gray artifacts on distant terrain
		// Only fill if at least one texture was placed and there's unused space
		boolean hasUnusedHorizontalSpace = (lastFilledX + textureSize) < atlasWidth;
		boolean hasUnusedVerticalSpace = (lastFilledY + textureSize) < atlasHeight;
		if (texturesPlaced > 0 && (hasUnusedHorizontalSpace || hasUnusedVerticalSpace)) {
			fillUnusedAtlasSpace(atlasImage, lastFilledX, lastFilledY, atlasWidth, atlasHeight, textureSize);
		}
		
		g.dispose();
		
		// Upload atlas to GPU
		atlasTextureId = createGLTexture(atlasImage);
		
		// logger.info("Texture atlas built successfully! ID: {}", atlasTextureId);
	}
	
	/**
	 * Register a texture in the atlas with int ID mapping.
	 * Used during atlas building to associate texture paths with integer IDs.
	 * 
	 * <p>If the same path is registered multiple times, the existing ID and UV
	 * mapping are kept (since the same texture should always have the same UVs).
	 * 
	 * @param path the texture path
	 * @param uvMapping the UV coordinates for this texture
	 * @return the assigned texture ID (existing or newly created)
	 */
	private int registerTexture(String path, UVMapping uvMapping) {
		Integer existing = pathToId.get(path);
		if (existing != null) {
			// Same path registered again - return existing ID (UVs should be identical)
			return existing;
		}
		int id = idToPath.size();
		idToPath.add(path);
		pathToId.put(path, id);
		uvMappings.put(id, uvMapping);
		return id;
	}
    
	/**
	 * Load a texture image from resources for atlas packing.
	 * Handles animated textures by checking for .mcmeta files and extracting the first frame.
	 * 
	 * @param path the texture path (e.g., "assets/textures/block/crimson_stem.png")
	 * @return the texture image (first frame for animated textures), or null if not found
	 */
	private BufferedImage loadTextureForAtlas(String path) {
		// First, load the full texture image
		BufferedImage fullImage = loadTexture(path);
		if (fullImage == null) {
			return null;
		}
		
		// Check for animation metadata (.mcmeta file)
		String mcmetaPath = path + ".mcmeta";
		try (InputStream mcmetaStream = mattmc.util.ResourceLoader.getOptionalResourceStreamFromClassLoader(mcmetaPath)) {
			if (mcmetaStream != null) {
				// Parse the .mcmeta file
				AnimationMetadataSection metadata = AnimationMetadataSection.load(mcmetaStream);
				
				if (metadata != AnimationMetadataSection.EMPTY) {
					// This is an animated texture
					// Calculate frame size
					FrameSize frameSize = metadata.calculateFrameSize(fullImage.getWidth(), fullImage.getHeight());
					
					// Check if the image is actually taller than it is wide (animated strip)
					if (fullImage.getHeight() > fullImage.getWidth() || 
						fullImage.getHeight() > frameSize.height()) {
						
						// Create animated texture data for later animation updates
						AnimatedTextureData animData = new AnimatedTextureData(path, fullImage, metadata);
						animatedTextures.put(path, animData);
						
						// Return just the first frame for atlas packing
						return animData.getFirstFrame();
					}
				}
			}
		} catch (IOException e) {
			// No .mcmeta file or error reading it - treat as static texture
			logger.debug("No .mcmeta file for {}: {}", path, e.getMessage());
		}
		
		// Return the full image for static textures
		return fullImage;
	}
	
	/**
	 * Load a texture image from resources.
	 * Ensures the image is in ARGB format for consistent atlas packing.
	 * 
	 * <p>This is important for grayscale textures (like grass_block_top.png) which
	 * need to be explicitly converted to ARGB to ensure correct alpha handling
	 * during atlas composition and mipmap generation.
	 */
	private BufferedImage loadTexture(String path) {
		try (InputStream is = mattmc.util.ResourceLoader.getResourceStreamFromClassLoader(path)) {
			if (is == null) {
				logger.error("Texture not found: {}", path);
				return null;
			}
			BufferedImage original = ImageIO.read(is);
			if (original == null) {
				logger.error("Failed to read texture: {}", path);
				return null;
			}
			
			// Convert to TYPE_INT_ARGB if not already, to ensure consistent handling
			// This is especially important for grayscale images which otherwise may
			// have inconsistent alpha behavior when drawn to the atlas
			if (original.getType() != BufferedImage.TYPE_INT_ARGB) {
				BufferedImage converted = new BufferedImage(
					original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = converted.createGraphics();
				// Use nearest neighbor interpolation for pixel-perfect texture conversion
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				// Use Src composite to directly copy pixels without alpha blending
				g.setComposite(AlphaComposite.Src);
				g.drawImage(original, 0, 0, null);
				g.dispose();
				return converted;
			}
			return original;
		} catch (IOException e) {
			logger.error("Failed to load texture: {}", path, e);
			return null;
		}
	}
	
	/**
	 * Calculate the next power of 2 greater than or equal to the given value.
	 */
	private int nextPowerOf2(int value) {
		int power = 1;
		while (power < value) {
			power *= 2;
		}
		return power;
	}
	
	/**
	 * Fill unused atlas space to prevent mipmap bleeding artifacts.
	 * 
	 * <p>When textures don't completely fill the power-of-2 atlas, the unused space
	 * (if left as transparent black or magenta) will bleed into adjacent textures during
	 * mipmap generation, causing gray artifacts on distant terrain.
	 * 
	 * <p>This method fills unused space by:
	 * 1. Filling remaining slots in the last row with copies of the last texture (tile pattern)
	 * 2. Filling remaining rows by copying the entire last-filled row
	 * 
	 * <p>This approach ensures that mipmap filtering near the edges of the used area
	 * samples from valid texture data rather than empty space.
	 * 
	 * @param atlas the atlas image to fill
	 * @param lastFilledX X position of last filled texture
	 * @param lastFilledY Y position of last filled texture (start of last row with content)
	 * @param atlasWidth total atlas width
	 * @param atlasHeight total atlas height
	 * @param textureSize size of each texture slot
	 */
	private void fillUnusedAtlasSpace(BufferedImage atlas, int lastFilledX, int lastFilledY, 
									   int atlasWidth, int atlasHeight, int textureSize) {
		// Get the underlying pixel data for efficient batch operations
		int[] atlasPixels = new int[atlasWidth * atlasHeight];
		atlas.getRGB(0, 0, atlasWidth, atlasHeight, atlasPixels, 0, atlasWidth);
		
		// Fill remaining slots in the current row by copying the last filled texture slot
		int nextX = lastFilledX + textureSize;
		int currentY = lastFilledY;
		
		while (nextX < atlasWidth) {
			// Copy the last filled texture to this position (efficient array copy)
			for (int py = 0; py < textureSize; py++) {
				int srcRowStart = (currentY + py) * atlasWidth + lastFilledX;
				int dstRowStart = (currentY + py) * atlasWidth + nextX;
				System.arraycopy(atlasPixels, srcRowStart, atlasPixels, dstRowStart, textureSize);
			}
			
			nextX += textureSize;
		}
		
		// Copy the last filled row (now fully extended) to fill all remaining rows below
		int nextRowY = lastFilledY + textureSize;
		
		while (nextRowY < atlasHeight) {
			// Copy entire texture row using efficient array operations
			for (int py = 0; py < textureSize; py++) {
				int srcRowStart = (lastFilledY + py) * atlasWidth;
				int dstRowStart = (nextRowY + py) * atlasWidth;
				System.arraycopy(atlasPixels, srcRowStart, atlasPixels, dstRowStart, atlasWidth);
			}
			
			nextRowY += textureSize;
		}
		
		// Write the modified pixels back to the atlas
		atlas.setRGB(0, 0, atlasWidth, atlasHeight, atlasPixels, 0, atlasWidth);
	}
	
	/**
	 * Anisotropic filtering level for texture atlases: DISABLED.
	 * 
	 * <p><b>Why anisotropic filtering is disabled for texture atlases:</b>
	 * Anisotropic filtering samples texels in an elongated footprint along the direction
	 * of maximum texture compression. On flat terrain viewed at a grazing angle, this
	 * footprint can extend many texels - easily crossing sprite boundaries in the atlas
	 * and causing gray banding artifacts (sampling adjacent textures).
	 * 
	 * <p>This is a fundamental incompatibility between anisotropic filtering and texture
	 * atlases. Even at 2x anisotropic filtering, the elongated sample footprint at grazing
	 * angles can extend beyond the UV shrink margin and sample neighboring textures.
	 * 
	 * <p><b>Alternative solutions considered:</b>
	 * <ul>
	 *   <li>UV shrinking with 4.0/atlasSize - not enough margin for aniso sampling</li>
	 *   <li>Per-vertex UV micro-inset - doesn't prevent aniso from sampling outside</li>
	 *   <li>Limiting to 2x aniso - still causes banding on flat surfaces</li>
	 * </ul>
	 * 
	 * <p>The only reliable solution is to completely disable anisotropic filtering for
	 * texture atlases. The trilinear filtering (GL_LINEAR_MIPMAP_LINEAR) still provides
	 * smooth mipmap transitions without the cross-sprite bleeding issue.
	 * 
	 * <p>To properly support anisotropic filtering, the renderer would need to use
	 * GL_TEXTURE_2D_ARRAY (texture arrays) instead of a texture atlas, which is a
	 * significant architectural change.
	 */
	private static final int MAX_ATLAS_ANISOTROPIC_LEVEL = 0; // 0 = disabled
	
	/**
	 * Create an OpenGL texture from a BufferedImage with gamma-correct mipmaps.
	 * 
	 * Uses Minecraft's mipmap generation algorithm for proper sRGB color blending.
	 * Sets all necessary OpenGL texture parameters to match Minecraft's behavior.
	 * 
	 * <p><b>Texture Atlas LOD Limiting:</b> For texture atlases, we limit the maximum
	 * LOD to prevent texture bleeding. At high mipmap levels, adjacent textures in
	 * the atlas would blend together, causing gray artifacts on distant terrain.
	 * The limit ensures each mipmap texel represents at most one individual texture.
	 * 
	 * <p><b>Anisotropic Filtering Limiting:</b> Anisotropic filtering is limited to
	 * {@value #MAX_ATLAS_ANISOTROPIC_LEVEL}x for texture atlases to prevent the elongated
	 * sampling footprint from crossing sprite boundaries, which causes gray banding
	 * on distant flat terrain.
	 */
	private int createGLTexture(BufferedImage image) {
		int mipmapLevel = OptionsManager.getMipmapLevel();
		
		// Calculate maximum safe LOD for texture atlas to prevent texture bleeding.
		// At high mipmap levels, adjacent textures in the atlas blend together.
		// The maximum safe LOD ensures each mipmap texel covers at most one individual texture.
		// Formula: maxSafeLOD = log2(atlasSize / textureSize) - 1
		// For a 256x256 atlas with 16x16 textures: log2(256/16) - 1 = log2(16) - 1 = 4 - 1 = 3
		// We subtract 1 as safety margin because GL_NEAREST_MIPMAP_LINEAR interpolates
		// between two mip levels, which could cause bleeding at the transition.
		// Note: texturesPerSide is always a power of 2 because both atlasWidth (power of 2)
		// and textureSize (16, also power of 2) are powers of 2. We use bit shifting for
		// precise calculation without floating point operations.
		int texturesPerSide = Math.max(1, atlasWidth / textureSize);
		int log2TexturesPerSide = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(texturesPerSide);
		int maxSafeLOD = Math.max(0, log2TexturesPerSide - 1);
		
		// Use the more restrictive of user setting and safe LOD limit
		int effectiveMaxLevel = Math.min(mipmapLevel, maxSafeLOD);
		
		// Generate gamma-correct mipmaps only up to effectiveMaxLevel to save memory
		BufferedImage[] mipLevels = 
			mattmc.client.renderer.texture.MipmapGenerator.generateMipLevels(image, effectiveMaxLevel);
		
		// Generate texture ID
		int textureID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureID);
		
		// Set mipmap parameters BEFORE uploading textures (like Minecraft's TextureUtil.prepareImage)
		if (effectiveMaxLevel >= 0) {
			int actualMaxLevel = Math.min(effectiveMaxLevel, mipLevels.length - 1);
			
			// GL_TEXTURE_MAX_LEVEL - maximum mipmap level that can be used
			// Set to actualMaxLevel to prevent GPU from generating/using unsafe mip levels
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, actualMaxLevel);
			// GL_TEXTURE_BASE_LEVEL - base mipmap level (always 0)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
			// GL_TEXTURE_MIN_LOD - minimum LOD value (clamping)
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0.0f);
			// GL_TEXTURE_MAX_LOD - maximum LOD value (clamping to prevent atlas bleeding)
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, (float) actualMaxLevel);
			// GL_TEXTURE_LOD_BIAS - small negative bias to favor higher-resolution mipmap levels
			// This helps reduce visible banding on flat surfaces at a distance by using
			// sharper textures where possible
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, TextureManager.LOD_BIAS);
		}
		
		// Upload each mipmap level
		for (int level = 0; level < mipLevels.length; level++) {
			BufferedImage mipImage = mipLevels[level];
			int width = mipImage.getWidth();
			int height = mipImage.getHeight();
			
			// Convert image to ByteBuffer
			int[] pixels = new int[width * height];
			mipImage.getRGB(0, 0, width, height, pixels, 0, width);
			
			ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int pixel = pixels[y * width + x];
					buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
					buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
					buffer.put((byte) (pixel & 0xFF));         // Blue
					buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
				}
			}
			buffer.flip();
			
			// Upload this mipmap level
			glTexImage2D(GL_TEXTURE_2D, level, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		}
		
		// Apply texture filtering settings with atlas-specific anisotropic limit
		// The limit prevents the anisotropic sampling footprint from crossing sprite boundaries
		TextureManager.applyTextureFiltering(true, MAX_ATLAS_ANISOTROPIC_LEVEL);
		
		// Use CLAMP_TO_EDGE for texture atlas to prevent edge bleeding
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		
		glBindTexture(GL_TEXTURE_2D, 0);
		
		return textureID;
	}
	
	/**
	 * Get the integer texture ID for a given texture path.
	 * 
	 * <p>IDs are stable for the lifetime of this TextureAtlas instance.
	 * Once the atlas is built during resource loading, the string→int mapping
	 * does not change. Use these IDs in hot paths instead of string lookups.
	 * 
	 * @param path the texture path (e.g., "assets/textures/block/stone.png")
	 * @return the integer texture ID, or -1 if the path is not found in the atlas
	 */
	public int getTextureId(String path) {
		Integer id = pathToId.get(path);
		return id != null ? id : -1;
	}
	
	/**
	 * Get the UV mapping for an integer texture ID.
	 * 
	 * <p>This is the fast lookup method for hot paths. Use {@link #getTextureId(String)}
	 * once to convert a texture path to an ID, then use this method for repeated UV lookups.
	 * 
	 * @param textureId the integer texture ID from {@link #getTextureId(String)}
	 * @return the UV mapping, or null if the ID is invalid (including -1)
	 */
	public UVMapping getUVMapping(int textureId) {
		return uvMappings.get(textureId);
	}
	
	/**
	 * Get the UV mapping for a texture path.
	 * Returns null if the texture is not in the atlas.
	 * 
	 * <p>This is a convenience method that delegates to {@link #getTextureId(String)}
	 * and {@link #getUVMapping(int)}. For hot paths, prefer resolving the texture ID
	 * once and using the int-based overload.
	 */
	@Override
	public UVMapping getUVMapping(String texturePath) {
		Integer id = pathToId.get(texturePath);
		return id != null ? uvMappings.get(id) : null;
	}
	
	/**
	 * Get the texture path for a given texture ID.
	 * Useful for debugging and tooling.
	 * 
	 * @param textureId the integer texture ID
	 * @return the texture path, or null if the ID is invalid
	 */
	public String getTexturePath(int textureId) {
		if (textureId >= 0 && textureId < idToPath.size()) {
			return idToPath.get(textureId);
		}
		return null;
	}
	
	/**
	 * Bind the atlas texture for rendering.
	 */
	public void bind() {
		glBindTexture(GL_TEXTURE_2D, atlasTextureId);
	}
	
	/**
	 * Get the OpenGL texture ID of the atlas.
	 */
	public int getAtlasTextureId() {
		return atlasTextureId;
	}
	
	/**
	 * Get the OpenGL texture ID of the atlas.
	 * @deprecated Use {@link #getAtlasTextureId()} for clarity.
	 */
	@Deprecated
	public int getTextureId() {
		return atlasTextureId;
	}
	
	/**
	 * Clean up GPU resources.
	 * Implements AutoCloseable.close() for proper resource management.
	 */
	@Override
	public void close() {
		glDeleteTextures(atlasTextureId);
	}
	
	/**
	 * Reapply texture filtering settings to the atlas.
	 * 
	 * <p>Call this when graphics settings (mipmap level, anisotropic filtering) change
	 * to apply the new settings immediately without restarting.
	 * 
	 * <p><b>Note:</b> Anisotropic filtering is limited to {@value #MAX_ATLAS_ANISOTROPIC_LEVEL}x
	 * for texture atlases to prevent cross-sprite bleeding.
	 */
	public void reapplyFilteringSettings() {
		int currentBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
		boolean needsRestore = currentBinding != atlasTextureId;
		
		if (needsRestore) {
			glBindTexture(GL_TEXTURE_2D, atlasTextureId);
		}
		
		TextureManager.applyTextureFiltering(true, MAX_ATLAS_ANISOTROPIC_LEVEL);
		
		if (needsRestore) {
			glBindTexture(GL_TEXTURE_2D, currentBinding);
		}
	}
	
	/**
	 * Get the number of textures in the atlas.
	 * 
	 * @return the number of textures with UV mappings
	 */
	public int getTextureCount() {
		return uvMappings.size();
	}
	
	/**
	 * Check if this atlas has any animated textures.
	 * 
	 * @return true if there are animated textures
	 */
	public boolean hasAnimatedTextures() {
		return !animatedTextures.isEmpty();
	}
	
	/**
	 * Get the number of animated textures in this atlas.
	 * 
	 * @return the number of animated textures
	 */
	public int getAnimatedTextureCount() {
		return animatedTextures.size();
	}
	
	/**
	 * Tick all animated textures and update the atlas.
	 * Call this once per game tick to advance animations.
	 * 
	 * <p>This method updates the OpenGL texture directly using glTexSubImage2D
	 * for efficient partial updates. Mipmaps are regenerated once after all
	 * texture updates are complete for better performance.
	 */
	public void tickAnimations() {
		if (animatedTextures.isEmpty()) {
			return;
		}
		
		boolean anyChanged = false;
		
		// Bind atlas texture once for all updates
		glBindTexture(GL_TEXTURE_2D, atlasTextureId);
		
		for (Map.Entry<String, AnimatedTextureData> entry : animatedTextures.entrySet()) {
			AnimatedTextureData animData = entry.getValue();
			if (animData.tick()) {
				// Frame changed, need to update atlas
				anyChanged = true;
				updateAnimatedTextureNoMipmap(entry.getKey(), animData);
			}
		}
		
		// Regenerate mipmaps once after all updates (more efficient than per-texture)
		// Note: We use glGenerateMipmap here for animated textures for performance reasons.
		// The gamma-correct software mipmap generation is used for static textures at load time,
		// but for animated frame updates that happen every tick, the hardware-generated mipmaps
		// are acceptable since animated regions are typically small and the visual impact is minimal.
		if (anyChanged) {
			glGenerateMipmap(GL_TEXTURE_2D);
		}
		
		glBindTexture(GL_TEXTURE_2D, 0);
	}
	
	/**
	 * Update a single animated texture in the atlas (without mipmap regeneration).
	 * Called during tickAnimations() with the atlas already bound.
	 */
	private void updateAnimatedTextureNoMipmap(String texturePath, AnimatedTextureData animData) {
		int[] position = textureAtlasPositions.get(texturePath);
		if (position == null) {
			return;
		}
		
		int x = position[0];
		int y = position[1];
		
		// Get the current frame image
		BufferedImage currentFrame;
		
		if (animData.isInterpolated()) {
			// Interpolate between current and next frame
			currentFrame = interpolateFrames(animData);
		} else {
			currentFrame = animData.getCurrentFrame();
		}
		
		if (currentFrame == null) {
			return;
		}
		
		// Upload the new frame to the atlas (texture already bound)
		uploadTextureRegionNoBindUnbind(currentFrame, x, y);
	}
	
	/**
	 * Interpolate between two frames for smooth animation.
	 */
	private BufferedImage interpolateFrames(AnimatedTextureData animData) {
		BufferedImage current = animData.getCurrentFrame();
		BufferedImage next = animData.getNextFrame();
		float progress = animData.getInterpolationProgress();
		
		if (current == null || next == null || progress == 0.0f) {
			return current;
		}
		
		int width = current.getWidth();
		int height = current.getHeight();
		
		BufferedImage interpolated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int c1 = current.getRGB(px, py);
				int c2 = next.getRGB(px, py);
				
				// Interpolate each color channel
				int a1 = (c1 >> 24) & 0xFF;
				int r1 = (c1 >> 16) & 0xFF;
				int g1 = (c1 >> 8) & 0xFF;
				int b1 = c1 & 0xFF;
				
				int a2 = (c2 >> 24) & 0xFF;
				int r2 = (c2 >> 16) & 0xFF;
				int g2 = (c2 >> 8) & 0xFF;
				int b2 = c2 & 0xFF;
				
				int a = mix(progress, a1, a2);
				int r = mix(progress, r1, r2);
				int g = mix(progress, g1, g2);
				int b = mix(progress, b1, b2);
				
				interpolated.setRGB(px, py, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
		
		return interpolated;
	}
	
	/**
	 * Mix two color values based on interpolation progress.
	 */
	private int mix(float progress, int c1, int c2) {
		return (int) ((1.0f - progress) * c1 + progress * c2);
	}
	
	/**
	 * Upload a texture region to the atlas (assumes texture is already bound).
	 * Used during batch animation updates for better performance.
	 */
	private void uploadTextureRegionNoBindUnbind(BufferedImage image, int x, int y) {
		int width = Math.min(image.getWidth(), textureSize);
		int height = Math.min(image.getHeight(), textureSize);
		
		// Convert image to ByteBuffer
		int[] pixels = new int[width * height];
		image.getRGB(0, 0, width, height, pixels, 0, width);
		
		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int pixel = pixels[py * width + px];
				buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
				buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
				buffer.put((byte) (pixel & 0xFF));         // Blue
				buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
			}
		}
		buffer.flip();
		
		// Upload to GPU (texture already bound, mipmap regeneration handled by caller)
		glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}
}
