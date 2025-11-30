package mattmc.client.renderer.chunk;

/**
 * Constants and utilities for per-vertex UV micro-inset.
 * 
 * <p>This technique comes from Minecraft's FaceBakery.java (lines 139-140) where
 * each vertex's UV is blended 99.9% toward itself and 0.1% toward the opposite corner.
 * This creates a tiny inward bias that prevents texture edge bleeding when distant
 * mipmaps are sampled.
 * 
 * <p>The effect is imperceptible up close but prevents gray artifacts that occur
 * when the GPU samples near texture boundaries at distant mipmap levels.
 */
public final class UVMicroInset {
    
    /**
     * Per-vertex UV inset ratio - the amount to keep from the original UV.
     * Each vertex UV is blended this much toward its own position (99.9%).
     */
    public static final float MICRO_INSET = 0.999f;
    
    /**
     * Per-vertex UV blend ratio - the amount to blend from the opposite corner.
     * Each vertex UV is blended this much toward the opposite corner (0.1%).
     */
    public static final float MICRO_BLEND = 0.001f;
    
    private UVMicroInset() {
        // Utility class - prevent instantiation
    }
}
