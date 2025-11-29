package mattmc.client.renderer.backend.opengl;

import mattmc.client.particle.ParticleAtlas;
import mattmc.client.particle.ParticleSprite;
import mattmc.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL implementation of the particle texture atlas.
 * 
 * <p>Builds a texture atlas from particle textures located at
 * assets/&lt;namespace&gt;/textures/particle/&lt;name&gt;.png
 * 
 * <p>This is similar to the block TextureAtlas but specifically for particle textures.
 */
public class OpenGLParticleAtlas implements ParticleAtlas, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLParticleAtlas.class);
    
    private final int atlasTextureId;
    private final int atlasWidth;
    private final int atlasHeight;
    private final int textureSize = 8; // Particle textures are typically 8x8
    
    /** Sprite lookup by resource location. */
    private final Map<ResourceLocation, ParticleSprite> sprites = new HashMap<>();
    
    /** The missing/fallback sprite. */
    private ParticleSprite missingSprite;
    
    /**
     * Create a new particle atlas with default textures.
     */
    public OpenGLParticleAtlas() {
        logger.info("Building particle texture atlas...");
        
        // Define the particle textures we need
        // These match the JSON particle definitions
        String[] textureNames = {
            "generic_0", "generic_1", "generic_2", "generic_3",
            "generic_4", "generic_5", "generic_6", "generic_7",
            "flame",
            "cherry_0", "cherry_1", "cherry_2", "cherry_3",
            "cherry_4", "cherry_5", "cherry_6", "cherry_7",
            "cherry_8", "cherry_9", "cherry_10", "cherry_11"
        };
        
        // Calculate atlas size
        int textureCount = textureNames.length + 1; // +1 for missing texture
        int texturesPerRow = (int) Math.ceil(Math.sqrt(textureCount));
        int powerOf2Width = nextPowerOf2(texturesPerRow * textureSize);
        
        atlasWidth = powerOf2Width;
        atlasHeight = powerOf2Width;
        
        // Create atlas image
        BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlasImage.createGraphics();
        
        // Fill with transparent
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, atlasWidth, atlasHeight);
        g.setComposite(java.awt.AlphaComposite.Src);
        
        // Create missing texture first (magenta/black checkerboard)
        BufferedImage missingTexture = createMissingTexture();
        g.drawImage(missingTexture, 0, 0, textureSize, textureSize, null);
        missingSprite = createSprite("missing", 0, 0);
        
        // Pack textures
        int x = textureSize; // Start after missing texture
        int y = 0;
        
        for (String name : textureNames) {
            BufferedImage texture = loadParticleTexture(name);
            if (texture == null) {
                texture = missingTexture;
            }
            
            g.drawImage(texture, x, y, textureSize, textureSize, null);
            
            ResourceLocation location = new ResourceLocation(name);
            sprites.put(location, createSprite(name, x, y));
            
            x += textureSize;
            if (x >= atlasWidth) {
                x = 0;
                y += textureSize;
            }
        }
        
        g.dispose();
        
        // Upload to GPU
        atlasTextureId = createGLTexture(atlasImage);
        
        logger.info("Particle atlas built: {}x{} with {} textures", atlasWidth, atlasHeight, sprites.size());
    }
    
    /**
     * Create a sprite for a texture at the given atlas position.
     */
    private ParticleSprite createSprite(String name, int x, int y) {
        float u0 = (float) x / atlasWidth;
        float v0 = (float) y / atlasHeight;
        float u1 = (float) (x + textureSize) / atlasWidth;
        float v1 = (float) (y + textureSize) / atlasHeight;
        return new ParticleSprite(name, u0, v0, u1, v1);
    }
    
    /**
     * Load a particle texture from resources.
     */
    private BufferedImage loadParticleTexture(String name) {
        String path = "/assets/textures/particle/" + name + ".png";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                // Try alternate path
                path = "/assets/mattmc/textures/particle/" + name + ".png";
                try (InputStream is2 = getClass().getResourceAsStream(path)) {
                    if (is2 == null) {
                        logger.debug("Particle texture not found: {} (creating procedural)", name);
                        return createProceduralTexture(name);
                    }
                    return ImageIO.read(is2);
                }
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            logger.warn("Failed to load particle texture: {}", name, e);
            return null;
        }
    }
    
    /**
     * Create a procedural texture when the actual texture is not found.
     * This allows the particle system to work even without texture assets.
     */
    private BufferedImage createProceduralTexture(String name) {
        BufferedImage image = new BufferedImage(textureSize, textureSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        if (name.startsWith("generic_")) {
            // Create smoke-like generic particle textures
            // Each generic_N is a different stage of a smoke animation
            int stage = 0;
            try {
                stage = Integer.parseInt(name.substring(8));
            } catch (NumberFormatException ignored) {}
            
            // Fade based on stage (generic_0 is brightest, generic_7 is dimmest)
            float brightness = 1.0f - (stage / 10.0f);
            float alpha = 0.8f - (stage * 0.08f);
            
            // Create a circular gradient
            int centerX = textureSize / 2;
            int centerY = textureSize / 2;
            float maxDist = textureSize / 2.0f;
            
            for (int py = 0; py < textureSize; py++) {
                for (int px = 0; px < textureSize; px++) {
                    float dx = px - centerX + 0.5f;
                    float dy = py - centerY + 0.5f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float t = Math.max(0, 1.0f - (dist / maxDist));
                    t = t * t; // Quadratic falloff
                    
                    int gray = (int) (brightness * 200 * t);
                    int a = (int) (alpha * 255 * t);
                    image.setRGB(px, py, (a << 24) | (gray << 16) | (gray << 8) | gray);
                }
            }
        } else if (name.equals("flame")) {
            // Create flame texture
            int centerX = textureSize / 2;
            int centerY = textureSize / 2;
            float maxDist = textureSize / 2.0f;
            
            for (int py = 0; py < textureSize; py++) {
                for (int px = 0; px < textureSize; px++) {
                    float dx = px - centerX + 0.5f;
                    float dy = py - centerY + 0.5f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float t = Math.max(0, 1.0f - (dist / maxDist));
                    t = t * t;
                    
                    // Orange/yellow gradient
                    int r = (int) (255 * t);
                    int green = (int) (200 * t * t);
                    int b = (int) (50 * t * t * t);
                    int a = (int) (255 * t);
                    image.setRGB(px, py, (a << 24) | (r << 16) | (green << 8) | b);
                }
            }
        } else if (name.startsWith("cherry_")) {
            // Create cherry petal texture - pink/white petal shapes
            int stage = 0;
            try {
                stage = Integer.parseInt(name.substring(7));
            } catch (NumberFormatException ignored) {}
            
            // Each stage is a slightly different petal orientation
            float rotation = stage * 0.5f;
            
            int centerX = textureSize / 2;
            int centerY = textureSize / 2;
            
            // Petal ellipse shape factors - width is narrower (2.0), height is taller (0.5)
            final float PETAL_WIDTH_FACTOR = 2.0f;  // Stretched horizontally (narrow petal)
            final float PETAL_HEIGHT_FACTOR = 0.5f; // Compressed vertically (tall petal)
            
            for (int py = 0; py < textureSize; py++) {
                for (int px = 0; px < textureSize; px++) {
                    float dx = (px - centerX + 0.5f) / (textureSize / 2.0f);
                    float dy = (py - centerY + 0.5f) / (textureSize / 2.0f);
                    
                    // Rotate based on stage
                    float cos = (float) Math.cos(rotation);
                    float sin = (float) Math.sin(rotation);
                    float rdx = dx * cos - dy * sin;
                    float rdy = dx * sin + dy * cos;
                    
                    // Petal shape: ellipse with pointed ends
                    float shape = (rdx * rdx * PETAL_WIDTH_FACTOR) + (rdy * rdy * PETAL_HEIGHT_FACTOR);
                    float t = Math.max(0, 1.0f - shape * 2.0f);
                    t = t * t;
                    
                    // Pink color (cherry blossom pink)
                    int red = (int) (255 * t);
                    int green = (int) (180 * t);
                    int blue = (int) (200 * t);
                    int alpha = (int) (255 * t);
                    image.setRGB(px, py, (alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            }
        } else {
            // Default white circle
            int centerX = textureSize / 2;
            int centerY = textureSize / 2;
            float maxDist = textureSize / 2.0f;
            
            for (int py = 0; py < textureSize; py++) {
                for (int px = 0; px < textureSize; px++) {
                    float dx = px - centerX + 0.5f;
                    float dy = py - centerY + 0.5f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float t = Math.max(0, 1.0f - (dist / maxDist));
                    
                    int val = (int) (255 * t);
                    image.setRGB(px, py, (val << 24) | (val << 16) | (val << 8) | val);
                }
            }
        }
        
        g.dispose();
        return image;
    }
    
    /**
     * Create the missing texture (magenta/black checkerboard).
     */
    private BufferedImage createMissingTexture() {
        BufferedImage image = new BufferedImage(textureSize, textureSize, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < textureSize; y++) {
            for (int x = 0; x < textureSize; x++) {
                boolean checker = ((x + y) % 2) == 0;
                int color = checker ? 0xFFFF00FF : 0xFF000000; // Magenta or black
                image.setRGB(x, y, color);
            }
        }
        return image;
    }
    
    /**
     * Calculate next power of 2.
     */
    private int nextPowerOf2(int value) {
        int power = 1;
        while (power < value) {
            power *= 2;
        }
        return power;
    }
    
    /**
     * Create OpenGL texture from image.
     */
    private int createGLTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();
        
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        // Use nearest filtering for pixel art look
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return textureId;
    }
    
    @Override
    public ParticleSprite getSprite(ResourceLocation location) {
        return sprites.get(location);
    }
    
    @Override
    public ParticleSprite getMissingSprite() {
        return missingSprite;
    }
    
    @Override
    public int getAtlasTextureId() {
        return atlasTextureId;
    }
    
    @Override
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, atlasTextureId);
    }
    
    @Override
    public void close() {
        glDeleteTextures(atlasTextureId);
    }
}
