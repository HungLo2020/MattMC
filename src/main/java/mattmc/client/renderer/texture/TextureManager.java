package mattmc.client.renderer.texture;

import mattmc.client.Minecraft;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages texture loading and binding for block textures.
 * Similar to Minecraft's texture management system.
 */
public class TextureManager {
    private static final Logger logger = LoggerFactory.getLogger(TextureManager.class);

    private final Map<String, Integer> textureCache = new HashMap<>();
    
    /**
     * Load a texture from resources and return its OpenGL texture ID.
     * Textures are cached to avoid reloading.
     */
    public int loadTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
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
            
            // Set texture parameters
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            
            // Upload texture data
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            
            textureCache.put(path, textureID);
            logger.info("Loaded texture: {}{}{}{}", path, " (ID: ", textureID, ")");
            
            return textureID;
        } catch (IOException e) {
            logger.error("Failed to load texture: {}", path);
            e.printStackTrace();
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
     * Clean up all loaded textures.
     */
    public void cleanup() {
        for (int textureID : textureCache.values()) {
            glDeleteTextures(textureID);
        }
        textureCache.clear();
    }
}
