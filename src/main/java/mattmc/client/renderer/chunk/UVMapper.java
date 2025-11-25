package mattmc.client.renderer.chunk;

import mattmc.util.ColorUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureCoordinateProvider;
import mattmc.world.level.block.Blocks;

/**
 * Handles UV mapping and color extraction for mesh building.
 * Maps block textures to texture atlas coordinates and applies color tints.
 * 
 * Extracted from MeshBuilder as part of refactoring to single-purpose classes.
 */
public class UVMapper {
    
    private final TextureCoordinateProvider textureAtlas;
    
    /**
     * Create a UV mapper with optional texture atlas support.
     * 
     * @param textureAtlas Texture atlas for UV mapping, or null to use fallback colors
     */
    public UVMapper(TextureCoordinateProvider textureAtlas) {
        this.textureAtlas = textureAtlas;
    }
    
    /**
     * Get the texture atlas.
     * @return The texture atlas, or null if not available
     */
    public TextureCoordinateProvider getTextureCoordinateProvider() {
        return textureAtlas;
    }
    
    /**
     * Get UV mapping from texture atlas for a face.
     * Returns null if no atlas or texture not found.
     */
    public TextureCoordinateProvider.UVMapping getUVMapping(BlockFaceCollector.FaceData face) {
        if (textureAtlas == null) {
            return null;
        }
        
        String texturePath = face.block.getTexturePath(face.faceType);
        if (texturePath == null) {
            return null;
        }
        
        return textureAtlas.getUVMapping(texturePath);
    }
    
    /**
     * Get UV mapping from texture atlas for a specific texture path.
     * Returns null if no atlas or texture not found.
     * Used by ModelElementRenderer for data-driven geometry rendering.
     */
    public TextureCoordinateProvider.UVMapping getUVMappingForTexture(String texturePath) {
        if (textureAtlas == null || texturePath == null) {
            return null;
        }
        
        return textureAtlas.getUVMapping(texturePath);
    }
    
    /**
     * Extract RGBA color from face data.
     * Uses white color with brightness when texture atlas is available,
     * otherwise uses fallback colors.
     */
    public float[] extractColor(BlockFaceCollector.FaceData face) {
        int renderColor;
        
        if (textureAtlas != null && face.block.hasTexture()) {
            // Use white color for texture modulation
            renderColor = 0xFFFFFF;
            
            // Apply grass green tint for grass_block top face (vanilla MattMC-like)
            if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
                renderColor = 0x5BB53B; // Grass green
            }
        } else {
            // Use fallback color when no texture atlas
            renderColor = ColorUtils.adjustColorBrightness(
                face.block.getFallbackColor(), 
                face.colorBrightness
            );
            
            // Apply grass green tint for grass_block top face
            if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
                renderColor = ColorUtils.applyTint(renderColor, 0x5BB53B, face.colorBrightness);
            }
        }
        
        // Apply brightness
        float brightness = face.brightness;
        
        int r = (renderColor >> 16) & 0xFF;
        int g = (renderColor >> 8) & 0xFF;
        int b = renderColor & 0xFF;
        
        return new float[] {
            (r / 255.0f) * brightness,
            (g / 255.0f) * brightness,
            (b / 255.0f) * brightness,
            1.0f // alpha
        };
    }
}
