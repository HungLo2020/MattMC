package mattmc.client.renderer.texture;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates mipmap levels using gamma-correct blending.
 * 
 * This implementation replicates Minecraft's MipmapGenerator exactly,
 * using sRGB gamma correction (gamma 2.2) for proper color blending.
 * 
 * Without gamma correction, averaging colors in sRGB space produces
 * incorrect results that appear as gray artifacts, especially visible
 * on distant flat terrain.
 * 
 * The algorithm:
 * 1. Convert sRGB colors to linear space using gamma 2.2
 * 2. Average the linear values
 * 3. Convert back to sRGB using gamma 1/2.2 (0.454545)
 */
public class MipmapGenerator {
    
    /**
     * Alpha cutoff threshold for transparent textures.
     * Alpha values below this are treated as fully transparent.
     */
    private static final int ALPHA_CUTOUT_CUTOFF = 96;
    
    /**
     * Gamma value for sRGB to linear conversion (2.2).
     */
    private static final double GAMMA = 2.2D;
    
    /**
     * Inverse gamma for linear to sRGB conversion (1/2.2 ≈ 0.454545).
     */
    private static final double GAMMA_INVERSE = 1.0D / GAMMA;
    
    /**
     * Precomputed gamma 2.2 lookup table for fast sRGB to linear conversion.
     * POW22[i] = (i/255)^2.2
     */
    private static final float[] POW22 = new float[256];
    
    static {
        for (int i = 0; i < 256; i++) {
            POW22[i] = (float) Math.pow((double) ((float) i / 255.0F), GAMMA);
        }
    }
    
    private MipmapGenerator() {
        // Utility class
    }
    
    /**
     * Generate mipmap levels for a texture image.
     * 
     * @param originalImage the original full-size texture
     * @param mipLevel the maximum mipmap level to generate (0 = original only)
     * @return array of BufferedImage containing mipmap levels [0] = original, [1] = half size, etc.
     */
    public static BufferedImage[] generateMipLevels(BufferedImage originalImage, int mipLevel) {
        if (mipLevel <= 0) {
            return new BufferedImage[] { originalImage };
        }
        
        List<BufferedImage> mipLevels = new ArrayList<>();
        mipLevels.add(originalImage);
        
        // Check if texture has any transparent pixels
        boolean hasTransparent = hasTransparentPixel(originalImage);
        
        // Calculate max mipmap level based on texture size
        int maxMipmapLevel = getMaxMipmapLevel(originalImage.getWidth(), originalImage.getHeight());
        
        BufferedImage previousLevel = originalImage;
        for (int level = 1; level <= mipLevel; level++) {
            int newWidth = Math.max(1, previousLevel.getWidth() >> 1);
            int newHeight = Math.max(1, previousLevel.getHeight() >> 1);
            
            BufferedImage mipmapLevel = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            
            // Only generate if within valid mipmap range
            if (level <= maxMipmapLevel) {
                for (int x = 0; x < newWidth; x++) {
                    for (int y = 0; y < newHeight; y++) {
                        // Sample 4 pixels from previous level
                        int col0 = previousLevel.getRGB(x * 2, y * 2);
                        int col1 = previousLevel.getRGB(Math.min(x * 2 + 1, previousLevel.getWidth() - 1), y * 2);
                        int col2 = previousLevel.getRGB(x * 2, Math.min(y * 2 + 1, previousLevel.getHeight() - 1));
                        int col3 = previousLevel.getRGB(Math.min(x * 2 + 1, previousLevel.getWidth() - 1), 
                                                        Math.min(y * 2 + 1, previousLevel.getHeight() - 1));
                        
                        int blendedColor = alphaBlend(col0, col1, col2, col3, hasTransparent);
                        mipmapLevel.setRGB(x, y, blendedColor);
                    }
                }
            }
            
            mipLevels.add(mipmapLevel);
            previousLevel = mipmapLevel;
        }
        
        return mipLevels.toArray(new BufferedImage[0]);
    }
    
    /**
     * Calculate the maximum valid mipmap level for a texture.
     * This prevents generating mipmaps smaller than 1x1.
     */
    private static int getMaxMipmapLevel(int width, int height) {
        int minDim = Math.min(width, height);
        int level = 0;
        while (minDim > 1) {
            minDim >>= 1;
            level++;
        }
        return level;
    }
    
    /**
     * Check if image has any fully transparent pixels.
     */
    private static boolean hasTransparentPixel(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >> 24) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Add color components from a pixel to the accumulator arrays if the pixel is not fully transparent.
     * 
     * @param color the ARGB color value
     * @param a alpha accumulator (single-element array)
     * @param r red accumulator (single-element array)
     * @param g green accumulator (single-element array)
     * @param b blue accumulator (single-element array)
     */
    private static void addColorComponentsIfOpaque(int color, float[] a, float[] r, float[] g, float[] b) {
        if ((color >> 24) != 0) {
            a[0] += getPow22(color >> 24);
            r[0] += getPow22(color >> 16);
            g[0] += getPow22(color >> 8);
            b[0] += getPow22(color);
        }
    }
    
    /**
     * Blend 4 colors using gamma-correct averaging.
     * This is the core of Minecraft's mipmap generation.
     * 
     * @param col0 first color (ARGB format)
     * @param col1 second color (ARGB format)
     * @param col2 third color (ARGB format)
     * @param col3 fourth color (ARGB format)
     * @param hasTransparent whether the texture has transparent pixels
     * @return blended color (ARGB format)
     */
    private static int alphaBlend(int col0, int col1, int col2, int col3, boolean hasTransparent) {
        if (hasTransparent) {
            // For textures with transparency, only blend non-transparent pixels
            float[] a = {0.0F};
            float[] r = {0.0F};
            float[] g = {0.0F};
            float[] b = {0.0F};
            
            // Only include pixels that are not fully transparent
            addColorComponentsIfOpaque(col0, a, r, g, b);
            addColorComponentsIfOpaque(col1, a, r, g, b);
            addColorComponentsIfOpaque(col2, a, r, g, b);
            addColorComponentsIfOpaque(col3, a, r, g, b);
            
            // Average in linear space
            a[0] /= 4.0F;
            r[0] /= 4.0F;
            g[0] /= 4.0F;
            b[0] /= 4.0F;
            
            // Convert back to sRGB using inverse gamma
            int aOut = (int) (Math.pow((double) a[0], GAMMA_INVERSE) * 255.0D);
            int rOut = (int) (Math.pow((double) r[0], GAMMA_INVERSE) * 255.0D);
            int gOut = (int) (Math.pow((double) g[0], GAMMA_INVERSE) * 255.0D);
            int bOut = (int) (Math.pow((double) b[0], GAMMA_INVERSE) * 255.0D);
            
            // Apply alpha cutoff threshold
            if (aOut < ALPHA_CUTOUT_CUTOFF) {
                aOut = 0;
            }
            
            return (aOut << 24) | (rOut << 16) | (gOut << 8) | bOut;
        } else {
            // For opaque textures, blend all channels with gamma correction
            int a = gammaBlend(col0, col1, col2, col3, 24);
            int r = gammaBlend(col0, col1, col2, col3, 16);
            int g = gammaBlend(col0, col1, col2, col3, 8);
            int b = gammaBlend(col0, col1, col2, col3, 0);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    
    /**
     * Blend a single color channel from 4 colors using gamma correction.
     * 
     * @param col0 first color (ARGB)
     * @param col1 second color (ARGB)
     * @param col2 third color (ARGB)
     * @param col3 fourth color (ARGB)
     * @param bitOffset bit offset for the channel (24=A, 16=R, 8=G, 0=B)
     * @return blended channel value (0-255)
     */
    private static int gammaBlend(int col0, int col1, int col2, int col3, int bitOffset) {
        // Convert to linear space
        float c0 = getPow22(col0 >> bitOffset);
        float c1 = getPow22(col1 >> bitOffset);
        float c2 = getPow22(col2 >> bitOffset);
        float c3 = getPow22(col3 >> bitOffset);
        
        // Average in linear space, then convert back to sRGB using inverse gamma
        float avg = (c0 + c1 + c2 + c3) * 0.25F;
        float result = (float) Math.pow((double) avg, GAMMA_INVERSE);
        
        return (int) ((double) result * 255.0D);
    }
    
    /**
     * Get the gamma 2.2 value for a color component.
     * Uses precomputed lookup table for performance.
     * 
     * @param value the color component value (masked to 8 bits)
     * @return the linear-space value
     */
    private static float getPow22(int value) {
        return POW22[value & 255];
    }
}
