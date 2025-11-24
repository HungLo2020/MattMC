package mattmc.client.renderer.backend.opengl;

import mattmc.client.Minecraft;
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
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages texture loading and binding for block textures.
 * Similar to Minecraft's texture management system.
 * 
 * ISSUE-005 fix: Implements LRU eviction to prevent unbounded memory growth.
 * ISSUE-013 fix: Supports runtime texture filtering changes via reapplyFilteringSettings().
 */
public class TextureManager {
    private static final Logger logger = LoggerFactory.getLogger(TextureManager.class);

    // ISSUE-005 fix: LRU cache with maximum size limit
    private static final int MAX_TEXTURE_CACHE_SIZE = 256;
    
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
     * This should be called after glTexImage2D and before unbinding the texture.
     * 
     * @param useMipmaps Whether to use mipmap filtering (for block textures use GL_NEAREST, for UI use GL_LINEAR)
     */
    public static void applyTextureFiltering(boolean useMipmaps) {
        int mipmapLevel = OptionsManager.getMipmapLevel();
        
        if (mipmapLevel > 0 && useMipmaps) {
            // Generate mipmaps
            glGenerateMipmap(GL_TEXTURE_2D);
            // Use mipmap filtering (NEAREST for block textures to maintain pixelated look)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            // Set max mipmap level
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mipmapLevel);
        } else if (useMipmaps) {
            // No mipmaps for block textures
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            // Linear filtering for UI textures
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        
        // Apply anisotropic filtering settings
        int anisotropicLevel = OptionsManager.getAnisotropicFiltering();
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
        }
    }
    
    /**
     * Load a texture from resources and return its OpenGL texture ID.
     * Textures are cached to avoid reloading.
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
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Convert image to ByteBuffer
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            
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
            
            // Generate texture ID
            int textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
            
            // Upload texture data
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            
            // Apply filtering settings (using NEAREST for block textures)
            applyTextureFiltering(true);
            
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            
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
