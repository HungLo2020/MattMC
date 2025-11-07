package mattmc.client.renderer.texture;

import mattmc.client.settings.OptionsManager;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime texture atlas builder for block textures.
 * Packs all block textures into a single atlas at game startup,
 * enabling VBO rendering with multiple textures.
 * 
 * Similar to modern Minecraft's texture atlas system.
 */
public class TextureAtlas {
    private static final Logger logger = LoggerFactory.getLogger(TextureAtlas.class);

    private final int atlasTextureId;
    private final int atlasWidth;
    private final int atlasHeight;
    private final int textureSize = 16; // Standard Minecraft texture size
    private final Map<String, UVMapping> uvMappings = new HashMap<>();
    
    /**
     * UV coordinates for a texture in the atlas.
     */
    public static class UVMapping {
        public final float u0, v0, u1, v1;
        
        public UVMapping(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }
    
    /**
     * Build the texture atlas from all registered blocks.
     * Call this once during game initialization.
     */
    public TextureAtlas() {
        logger.info("Building texture atlas...");
        
        // Collect all unique texture paths from registered blocks
        Set<String> uniqueTexturePaths = new HashSet<>();
        for (String identifier : Blocks.getRegisteredIdentifiers()) {
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
        
        logger.info("Found {} unique textures", uniqueTexturePaths.size());
        
        // Calculate atlas dimensions (power of 2, square layout)
        int textureCount = uniqueTexturePaths.size();
        int texturesPerRow = (int) Math.ceil(Math.sqrt(textureCount));
        atlasWidth = texturesPerRow * textureSize;
        atlasHeight = texturesPerRow * textureSize;
        
        logger.info("Atlas size: {}x{} ({} textures per row)", atlasWidth, atlasHeight, texturesPerRow);
        
        // Create atlas image
        BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlasImage.createGraphics();
        
        // Fill with magenta (missing texture color)
        g.setColor(new Color(255, 0, 255));
        g.fillRect(0, 0, atlasWidth, atlasHeight);
        
        // Pack textures into atlas
        int x = 0, y = 0;
        List<String> textureList = new ArrayList<>(uniqueTexturePaths);
        
        for (String texturePath : textureList) {
            try {
                // Load texture image
                BufferedImage texture = loadTexture(texturePath);
                if (texture != null) {
                    // Draw texture into atlas
                    g.drawImage(texture, x, y, textureSize, textureSize, null);
                    
                    // Calculate UV coordinates (0.0 to 1.0)
                    float u0 = (float) x / atlasWidth;
                    float v0 = (float) y / atlasHeight;
                    float u1 = (float) (x + textureSize) / atlasWidth;
                    float v1 = (float) (y + textureSize) / atlasHeight;
                    
                    uvMappings.put(texturePath, new UVMapping(u0, v0, u1, v1));
                    
                    logger.info("  Packed: {} at ({},{}) UV: {},{} -> {},{}", texturePath, x, y, u0, v0, u1, v1);
                } else {
                    logger.error("  Failed to load: {}", texturePath);
                }
            } catch (Exception e) {
                logger.error("  Error loading {}: {}", texturePath, e.getMessage());
            }
            
            // Move to next position
            x += textureSize;
            if (x >= atlasWidth) {
                x = 0;
                y += textureSize;
            }
        }
        
        g.dispose();
        
        // Upload atlas to GPU
        atlasTextureId = createGLTexture(atlasImage);
        
        logger.info("Texture atlas built successfully! ID: {}", atlasTextureId);
    }
    
    /**
     * Load a texture image from resources.
     */
    private BufferedImage loadTexture(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                logger.error("Texture not found: {}", path);
                return null;
            }
            return ImageIO.read(is);
        } catch (Exception e) {
            logger.error("Failed to load texture: {}", path);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Create an OpenGL texture from a BufferedImage.
     */
    private int createGLTexture(BufferedImage image) {
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
        
        // Texture atlases should NOT use mipmaps to avoid color bleeding between packed textures
        // Always use NEAREST filtering for block textures to maintain the pixelated look
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        // Apply anisotropic filtering if enabled (helps with distant terrain without mipmaps)
        int anisotropicLevel = OptionsManager.getAnisotropicFiltering();
        if (anisotropicLevel > 0) {
            try {
                float maxAniso = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                float aniso = Math.min(anisotropicLevel, maxAniso);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
            } catch (IllegalStateException | IllegalArgumentException e) {
                logger.debug("Anisotropic filtering not supported: {}", e.getMessage());
            }
        }
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return textureID;
    }
    
    /**
     * Get the UV mapping for a texture path.
     * Returns null if the texture is not in the atlas.
     */
    public UVMapping getUVMapping(String texturePath) {
        return uvMappings.get(texturePath);
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
    public int getTextureId() {
        return atlasTextureId;
    }
    
    /**
     * Clean up GPU resources.
     */
    public void cleanup() {
        glDeleteTextures(atlasTextureId);
    }
    
    /**
     * Get the number of textures in the atlas.
     */
    public int getTextureCount() {
        return uvMappings.size();
    }
}
