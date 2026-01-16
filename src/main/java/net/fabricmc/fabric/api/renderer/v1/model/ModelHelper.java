package net.fabricmc.fabric.api.renderer.v1.model;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for model rendering.
 */
public final class ModelHelper {
    private static final Direction[] DIRECTIONS = Direction.values();
    
    /** Face ID for null/non-culled face (the 7th face, index 6). */
    public static final int NULL_FACE_ID = DIRECTIONS.length;
    
    private ModelHelper() { }
    
    /**
     * Gets a Direction from a face index, or null for the non-culled face.
     */
    @Nullable
    public static Direction faceFromIndex(int faceIndex) {
        if (faceIndex < 0 || faceIndex >= DIRECTIONS.length) {
            return null;
        }
        return DIRECTIONS[faceIndex];
    }
    
    /**
     * Gets the face index for a direction, or the count of directions for null (non-culled).
     */
    public static int toFaceIndex(@Nullable Direction face) {
        return face == null ? DIRECTIONS.length : face.ordinal();
    }
}
