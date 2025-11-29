package mattmc.client.renderer.backend.opengl;

import mattmc.client.particle.ParticleAtlas;
import mattmc.client.particle.ParticleAtlasBuilder;
import mattmc.client.particle.ParticleSprite;
import mattmc.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

/**
 * OpenGL implementation of the particle texture atlas.
 * 
 * <p>This is a generic texture atlas that consumes sprite data from
 * {@link ParticleAtlasBuilder}. It does not know about specific particle
 * types or texture names - it only manages the OpenGL texture and UV mappings.
 * 
 * <p>The atlas is built at runtime from textures under textures/particle/,
 * configured via atlases/particles.json, mirroring Minecraft's approach.
 */
public class OpenGLParticleAtlas implements ParticleAtlas, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLParticleAtlas.class);
    
    private final int atlasTextureId;
    private final int atlasWidth;
    private final int atlasHeight;
    
    /** Sprite lookup by resource location. */
    private final Map<ResourceLocation, ParticleSprite> sprites = new HashMap<>();
    
    /** The missing/fallback sprite. */
    private ParticleSprite missingSprite;
    
    /**
     * Create a new particle atlas by building from textures/particle/ directory.
     * 
     * <p>This is called at runtime when the game starts, mirroring Minecraft's
     * texture atlas generation behavior.
     */
    public OpenGLParticleAtlas() {
        logger.info("Building OpenGL particle texture atlas...");
        
        // Build the atlas from the particle texture directory
        ParticleAtlasBuilder.AtlasResult result = ParticleAtlasBuilder.buildAtlas();
        
        this.atlasWidth = result.atlasWidth;
        this.atlasHeight = result.atlasHeight;
        
        // Convert sprite entries to ParticleSprites
        for (Map.Entry<ResourceLocation, ParticleAtlasBuilder.SpriteEntry> entry : result.sprites.entrySet()) {
            ParticleAtlasBuilder.SpriteEntry spriteEntry = entry.getValue();
            ParticleSprite sprite = new ParticleSprite(
                spriteEntry.location.getPath(),
                spriteEntry.u0, spriteEntry.v0,
                spriteEntry.u1, spriteEntry.v1
            );
            sprites.put(entry.getKey(), sprite);
        }
        
        // Create missing sprite
        ParticleAtlasBuilder.SpriteEntry missingSpriteEntry = result.missingSprite;
        this.missingSprite = new ParticleSprite(
            "missing",
            missingSpriteEntry.u0, missingSpriteEntry.v0,
            missingSpriteEntry.u1, missingSpriteEntry.v1
        );
        
        // Upload to GPU
        atlasTextureId = createGLTexture(result.atlasImage);
        
        logger.info("OpenGL particle atlas created: {}x{} with {} sprites", 
                    atlasWidth, atlasHeight, sprites.size());
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
