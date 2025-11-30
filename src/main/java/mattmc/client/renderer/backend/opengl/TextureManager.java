package mattmc.client.renderer.backend.opengl;

import mattmc.client.settings.OptionsManager;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages texture loading and binding for block textures.
 * Similar to MattMC's texture management system.
 * 
 * ISSUE-005 fix: Implements LRU eviction to prevent unbounded memory growth.
 * ISSUE-013 fix: Supports runtime texture filtering changes via reapplyFilteringSettings().
 */
public class TextureManager {
    private static final Logger logger = LoggerFactory.getLogger(TextureManager.class);

    // ISSUE-005 fix: LRU cache with maximum size limit
    private static final int MAX_TEXTURE_CACHE_SIZE = 256;
    
    /**
     * LOD bias to favor higher-resolution mipmap levels.
     * A small negative value reduces the distance at which LOD transitions occur,
     * which helps reduce visible banding patterns on flat surfaces at a distance.
     * Used by both TextureManager and TextureAtlas for consistency.
     */
    public static final float LOD_BIAS = -0.25f;
    
    private final Map<String, Integer> textureCache = new java.util.LinkedHashMap<String, Integer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            if (size() > MAX_TEXTURE_CACHE_SIZE) {
                // Clean up OpenGL texture before eviction
                glDeleteTextures(eldest.getValue());
                logger.debug("Evicted texture from cache: {} (cache size: {})", eldest.getKey(), size());
                return true;
            }
            return false;
        }
    };
    
    /**
     * Apply texture filtering settings (mipmaps and anisotropic filtering) to the currently bound texture.
     * This should be called after mipmap levels are uploaded and before unbinding the texture.
     * 
     * <p><b>Note:</b> This method no longer generates mipmaps - use {@link #uploadWithMipmaps} 
     * for proper gamma-correct mipmap generation like Minecraft.
     * 
     * <p>Sets LOD parameters to match Minecraft's TextureUtil.prepareImage behavior.
     * 
     * <p><b>Banding Fix:</b> When anisotropic filtering is enabled (> 0), we use trilinear
     * filtering (GL_LINEAR_MIPMAP_LINEAR) instead of GL_NEAREST_MIPMAP_LINEAR to eliminate
     * visible banding patterns on flat surfaces at a distance. The anisotropic filtering
     * combined with the slightly blurred mipmaps produces smoother LOD transitions without
     * the harsh bands that occur with nearest-neighbor mipmap sampling.
     * 
     * @param useMipmaps Whether to use mipmap filtering (for block textures use GL_NEAREST, for UI use GL_LINEAR)
     */
    public static void applyTextureFiltering(boolean useMipmaps) {
        applyTextureFiltering(useMipmaps, 0);
    }
    
    /**
     * Apply texture filtering settings with an optional maximum anisotropic filtering level.
     * 
     * <p>For texture atlases, anisotropic filtering can cause texture bleeding (gray banding)
     * because the elongated sampling footprint can cross sprite boundaries. This method allows
     * callers to limit the anisotropic level for such cases.
     * 
     * <p><b>Atlas Anisotropic Limit Calculation:</b> For a texture atlas with NxN textures of size S,
     * the safe anisotropic level is limited to prevent the sampling footprint from extending
     * beyond the UV shrink margin. A conservative limit is 2x for most atlas configurations,
     * which provides some filtering benefit while avoiding cross-sprite bleeding.
     * 
     * @param useMipmaps Whether to use mipmap filtering
     * @param maxAnisoForAtlas Maximum anisotropic level for texture atlases (0 = use user setting)
     */
    public static void applyTextureFiltering(boolean useMipmaps, int maxAnisoForAtlas) {
        int mipmapLevel = OptionsManager.getMipmapLevel();
        int anisotropicLevel = OptionsManager.getAnisotropicFiltering();
        
        // Limit or disable anisotropic filtering for texture atlases to prevent cross-sprite bleeding.
        // maxAnisoForAtlas == 0: completely disable anisotropic filtering (for texture atlases)
        // maxAnisoForAtlas > 0: limit to this level
        // maxAnisoForAtlas < 0: no limit (use user's configured setting)
        if (maxAnisoForAtlas == 0) {
            // Completely disable anisotropic filtering for this texture
            anisotropicLevel = 0;
        } else if (maxAnisoForAtlas > 0 && anisotropicLevel > maxAnisoForAtlas) {
            anisotropicLevel = maxAnisoForAtlas;
        }
        
        // Determine if user wants filtering quality (anisotropic enabled in settings)
        // even if we disable it for this specific texture (atlas)
        boolean userWantsFiltering = OptionsManager.getAnisotropicFiltering() > 0;
        
        if (mipmapLevel > 0 && useMipmaps) {
            // When user has anisotropic filtering enabled in settings, use trilinear filtering
            // (GL_LINEAR_MIPMAP_LINEAR) to provide smooth LOD transitions. This gives a quality
            // look even when we can't actually apply anisotropic filtering to an atlas.
            // When user has anisotropic disabled, use GL_NEAREST_MIPMAP_LINEAR for pixelated look.
            if (userWantsFiltering) {
                // Trilinear filtering: smooth mipmap transitions
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            } else {
                // Standard Minecraft-style: nearest within level, linear between levels
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
            }
            // Always use NEAREST for magnification to maintain pixelated look up close
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            // Set mipmap level parameters like Minecraft
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mipmapLevel);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
            // Set LOD parameters like Minecraft's TextureUtil.prepareImage
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0.0f);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, (float) mipmapLevel);
            // Small negative LOD bias to favor higher-resolution mipmap levels,
            // which helps reduce banding at LOD transitions
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, LOD_BIAS);
        } else if (useMipmaps) {
            // No mipmaps for block textures
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            // Linear filtering for UI textures
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        
        // Apply anisotropic filtering settings (only if not disabled for this texture)
        if (anisotropicLevel > 0) {
            try {
                // Get max anisotropic level supported by GPU
                float maxAniso = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                float aniso = Math.min(anisotropicLevel, maxAniso);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
            } catch (IllegalStateException | IllegalArgumentException e) {
                // Anisotropic filtering extension not supported, silently ignore
                // logger.debug("Anisotropic filtering not supported: {}", e.getMessage());
            }
        } else {
            // Explicitly set anisotropic to 1.0 (disabled) to ensure no aniso from previous state
            try {
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f);
            } catch (IllegalStateException | IllegalArgumentException e) {
                // Extension not supported
            }
        }
    }
    
    /**
     * Upload a texture with gamma-correct software-generated mipmaps.
     * 
     * <p>This method uses Minecraft's mipmap generation algorithm which performs
     * proper sRGB gamma correction when blending colors. This prevents the gray
     * artifacting that occurs with simple linear averaging (glGenerateMipmap).
     * 
     * @param image the source image to upload
     * @return the OpenGL texture ID
     */
    public static int uploadWithMipmaps(java.awt.image.BufferedImage image) {
        int mipmapLevel = OptionsManager.getMipmapLevel();
        
        // Generate gamma-correct mipmaps
        java.awt.image.BufferedImage[] mipLevels = 
            mattmc.client.renderer.texture.MipmapGenerator.generateMipLevels(image, mipmapLevel);
        
        // Generate texture ID
        int textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        
        // Set max mipmap level parameter before uploading
        if (mipmapLevel > 0) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, Math.min(mipmapLevel, mipLevels.length - 1));
        }
        
        // Upload each mipmap level
        for (int level = 0; level < mipLevels.length; level++) {
            java.awt.image.BufferedImage mipImage = mipLevels[level];
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
        
        return textureID;
    }
    
    /**
     * Load a texture from resources and return its OpenGL texture ID.
     * Textures are cached to avoid reloading.
     * Uses gamma-correct mipmap generation like Minecraft.
     */
    public int loadTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        
        try (InputStream is = mattmc.util.ResourceLoader.getResourceStreamFromClassLoader(path)) {
            if (is == null) {
                logger.error("Texture not found: {}{}", path, " (expected in resources folder)");
                return 0;
            }
            
            BufferedImage image = ImageIO.read(is);
            int mipmapLevel = OptionsManager.getMipmapLevel();
            
            // Generate gamma-correct mipmaps like Minecraft
            BufferedImage[] mipLevels = 
                mattmc.client.renderer.texture.MipmapGenerator.generateMipLevels(image, mipmapLevel);
            
            // Generate texture ID
            int textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
            
            // Set mipmap parameters BEFORE uploading textures (like Minecraft's TextureUtil.prepareImage)
            if (mipmapLevel >= 0) {
                int actualMaxLevel = Math.min(mipmapLevel, mipLevels.length - 1);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, actualMaxLevel);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0.0f);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, (float) actualMaxLevel);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, 0.0f);
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
            
            // Apply filtering settings (using NEAREST for block textures)
            applyTextureFiltering(true);
            
            // Use CLAMP_TO_EDGE to prevent edge bleeding
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            
            textureCache.put(path, textureID);
            // logger.info("Loaded texture: {} (ID: {})", path, textureID);
            
            return textureID;
        } catch (IOException e) {
            logger.error("Failed to load texture: {}", path, e);
            return 0;
        }
    }
    
    /**
     * Bind a texture by its ID.
     */
    public void bindTexture(int textureID) {
        glBindTexture(GL_TEXTURE_2D, textureID);
    }
    
    /**
     * Unbind the current texture.
     */
    public void unbindTexture() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    /**
     * Reapply texture filtering settings to all cached textures.
     * ISSUE-013 fix: Call this when mipmap or anisotropic filtering settings change.
     * Allows users to change graphics settings without restarting.
     */
    public void reapplyFilteringSettings() {
        int currentBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        
        for (int textureID : textureCache.values()) {
            glBindTexture(GL_TEXTURE_2D, textureID);
            applyTextureFiltering(true); // Reapply with current settings
        }
        
        // Restore previous binding
        glBindTexture(GL_TEXTURE_2D, currentBinding);
        logger.info("Reapplied texture filtering to {} textures", textureCache.size());
    }
    
    /**
     * Clean up all loaded textures.
     */
    public void cleanup() {
        for (int textureID : textureCache.values()) {
            glDeleteTextures(textureID);
        }
        textureCache.clear();
    }
}
