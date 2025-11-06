package mattmc.client.renderer;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11.*;

/**
 * Frustum culling implementation for OpenGL.
 * Extracts the view frustum planes from the current modelview and projection matrices
 * and provides methods to test if objects (like chunks) are visible.
 * 
 * Based on the classic Gribb-Hartmann method for extracting frustum planes.
 */
public class Frustum {
    // Six frustum planes: left, right, bottom, top, near, far
    private final float[][] planes = new float[6][4];
    
    // Plane indices
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int BOTTOM = 2;
    private static final int TOP = 3;
    private static final int NEAR = 4;
    private static final int FAR = 5;
    
    /**
     * Update the frustum planes from the current OpenGL matrices.
     * Call this after setting up the projection and modelview matrices.
     */
    public void update() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer proj = stack.mallocFloat(16);
            FloatBuffer modl = stack.mallocFloat(16);
            FloatBuffer clip = stack.mallocFloat(16);
            
            // Get current matrices
            glGetFloatv(GL_PROJECTION_MATRIX, proj);
            glGetFloatv(GL_MODELVIEW_MATRIX, modl);
            
            // Multiply projection * modelview to get clip matrix
            multiplyMatrices(clip, proj, modl);
            
            // Extract the six frustum planes from the clip matrix
            extractPlanes(clip);
        }
    }
    
    /**
     * Multiply two 4x4 matrices (result = a * b).
     */
    private void multiplyMatrices(FloatBuffer result, FloatBuffer a, FloatBuffer b) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += a.get(i * 4 + k) * b.get(k * 4 + j);
                }
                result.put(i * 4 + j, sum);
            }
        }
    }
    
    /**
     * Extract frustum planes from the clip matrix.
     */
    private void extractPlanes(FloatBuffer clip) {
        // Right plane
        planes[RIGHT][0] = clip.get(3) - clip.get(0);
        planes[RIGHT][1] = clip.get(7) - clip.get(4);
        planes[RIGHT][2] = clip.get(11) - clip.get(8);
        planes[RIGHT][3] = clip.get(15) - clip.get(12);
        normalizePlane(RIGHT);
        
        // Left plane
        planes[LEFT][0] = clip.get(3) + clip.get(0);
        planes[LEFT][1] = clip.get(7) + clip.get(4);
        planes[LEFT][2] = clip.get(11) + clip.get(8);
        planes[LEFT][3] = clip.get(15) + clip.get(12);
        normalizePlane(LEFT);
        
        // Bottom plane
        planes[BOTTOM][0] = clip.get(3) + clip.get(1);
        planes[BOTTOM][1] = clip.get(7) + clip.get(5);
        planes[BOTTOM][2] = clip.get(11) + clip.get(9);
        planes[BOTTOM][3] = clip.get(15) + clip.get(13);
        normalizePlane(BOTTOM);
        
        // Top plane
        planes[TOP][0] = clip.get(3) - clip.get(1);
        planes[TOP][1] = clip.get(7) - clip.get(5);
        planes[TOP][2] = clip.get(11) - clip.get(9);
        planes[TOP][3] = clip.get(15) - clip.get(13);
        normalizePlane(TOP);
        
        // Far plane
        planes[FAR][0] = clip.get(3) - clip.get(2);
        planes[FAR][1] = clip.get(7) - clip.get(6);
        planes[FAR][2] = clip.get(11) - clip.get(10);
        planes[FAR][3] = clip.get(15) - clip.get(14);
        normalizePlane(FAR);
        
        // Near plane
        planes[NEAR][0] = clip.get(3) + clip.get(2);
        planes[NEAR][1] = clip.get(7) + clip.get(6);
        planes[NEAR][2] = clip.get(11) + clip.get(10);
        planes[NEAR][3] = clip.get(15) + clip.get(14);
        normalizePlane(NEAR);
    }
    
    /**
     * Normalize a frustum plane.
     */
    private void normalizePlane(int plane) {
        float length = (float) Math.sqrt(
            planes[plane][0] * planes[plane][0] +
            planes[plane][1] * planes[plane][1] +
            planes[plane][2] * planes[plane][2]
        );
        
        if (length > 0) {
            planes[plane][0] /= length;
            planes[plane][1] /= length;
            planes[plane][2] /= length;
            planes[plane][3] /= length;
        }
    }
    
    /**
     * Test if an axis-aligned bounding box is inside or intersecting the frustum.
     * 
     * @param minX Minimum X coordinate of the box
     * @param minY Minimum Y coordinate of the box
     * @param minZ Minimum Z coordinate of the box
     * @param maxX Maximum X coordinate of the box
     * @param maxY Maximum Y coordinate of the box
     * @param maxZ Maximum Z coordinate of the box
     * @return true if the box is visible (inside or intersecting frustum), false if completely outside
     */
    public boolean isBoxVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Test all six planes
        for (int i = 0; i < 6; i++) {
            // Get the "positive vertex" (vertex furthest along plane normal)
            float px = planes[i][0] > 0 ? maxX : minX;
            float py = planes[i][1] > 0 ? maxY : minY;
            float pz = planes[i][2] > 0 ? maxZ : minZ;
            
            // If positive vertex is outside, the entire box is outside
            if (planes[i][0] * px + planes[i][1] * py + planes[i][2] * pz + planes[i][3] < 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Test if a chunk is visible in the frustum.
     * 
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param chunkWidth Width of the chunk in blocks
     * @param chunkDepth Depth of the chunk in blocks
     * @param minY Minimum Y coordinate of the chunk
     * @param maxY Maximum Y coordinate of the chunk
     * @return true if the chunk is visible
     */
    public boolean isChunkVisible(int chunkX, int chunkZ, int chunkWidth, int chunkDepth, int minY, int maxY) {
        // Calculate world coordinates of the chunk bounding box
        float minX = chunkX * chunkWidth;
        float minZ = chunkZ * chunkDepth;
        float maxX = minX + chunkWidth;
        float maxZ = minZ + chunkDepth;
        
        return isBoxVisible(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
