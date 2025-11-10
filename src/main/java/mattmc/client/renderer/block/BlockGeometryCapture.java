package mattmc.client.renderer.block;

import mattmc.client.renderer.VertexCapture;

/**
 * Generates vertex geometry for block faces that can be captured for isometric item rendering.
 * This class provides the same geometry as BlockFaceGeometry but outputs to a VertexCapture
 * instead of directly to OpenGL.
 */
public final class BlockGeometryCapture {
    
    private BlockGeometryCapture() {} // Prevent instantiation
    
    /**
     * Capture vertices for the top face of a block.
     */
    public static void captureTopFace(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Triangle 1
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y1, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y1, z1);
        
        // Triangle 2
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y1, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);
    }
    
    /**
     * Capture vertices for the bottom face of a block.
     */
    public static void captureBottomFace(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y;
        float z0 = z, z1 = z + 1;
        
        // Triangle 1
        capture.texCoord(0, 0); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0); capture.addVertex(x1, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        
        // Triangle 2
        capture.texCoord(0, 0); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
    }
    
    /**
     * Capture vertices for the north face of a block.
     */
    public static void captureNorthFace(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z;
        
        // Triangle 1
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        // Triangle 2
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);
    }
    
    /**
     * Capture vertices for the south face of a block.
     */
    public static void captureSouthFace(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z1 = z + 1;
        
        // Triangle 1
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z1);
        
        // Triangle 2
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z1);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z1);
    }
    
    /**
     * Capture vertices for the west face of a block.
     */
    public static void captureWestFace(VertexCapture capture, float x, float y, float z) {
        float x0 = x;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Triangle 1
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0); capture.addVertex(x0, y1, z1);
        
        // Triangle 2
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0); capture.addVertex(x0, y1, z1);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
    }
    
    /**
     * Capture vertices for the east face of a block.
     */
    public static void captureEastFace(VertexCapture capture, float x, float y, float z) {
        float x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Triangle 1
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0); capture.addVertex(x1, y1, z0);
        
        // Triangle 2
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 0); capture.addVertex(x1, y1, z0);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z1);
    }
    
    /**
     * Capture vertices for a north-facing bottom stairs block.
     * This uses the exact same geometry as BlockFaceGeometry.drawStairsNorthBottom.
     */
    public static void captureStairsNorthBottom(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y05 = y + 0.5f, y1 = y + 1;
        float z0 = z, z05 = z + 0.5f, z1 = z + 1;
        
        // Bottom slab (0, 0, 0) to (1, 0.5, 1)
        // Top face of bottom slab
        capture.texCoord(0, 0); capture.addVertex(x0, y05, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y05, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y05, z0);
        
        // Bottom face of slab
        capture.texCoord(0, 0); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0); capture.addVertex(x1, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        
        // North face of slab
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        
        // South face of slab
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        
        // West face of slab
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        // East face of slab
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        // Top step (0, 0.5, 0) to (1, 1, 0.5) - north half only
        // Top face of step
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y1, z05);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y1, z05);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y1, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);
        
        // North face of step (full)
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);
        
        // South face of step (inner step face)
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z05);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z05);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z05);
        
        // West face of step
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(0.5f, 0); capture.addVertex(x0, y1, z05);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0.5f, 0); capture.addVertex(x0, y1, z05);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        // East face of step
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x1, y1, z0);
        
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0, 0); capture.addVertex(x1, y1, z0);
        capture.texCoord(0.5f, 0); capture.addVertex(x1, y1, z05);
    }
    
    /**
     * Capture vertices for a south-facing bottom stairs block (for item rendering).
     * The step rises toward the south (z=1), which looks better in isometric view.
     * 
     * Geometry:
     * - Bottom slab: (0,0,0) to (1,0.5,1) - full block, half height
     * - Top step: (0,0.5,0.5) to (1,1,1) - south half only, top half height
     */
    public static void captureStairsSouthBottom(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y05 = y + 0.5f, y1 = y + 1;
        float z0 = z, z05 = z + 0.5f, z1 = z + 1;
        
        // Bottom slab (0, 0, 0) to (1, 0.5, 1)
        // Top face of bottom slab - only the north half (z=0 to z=0.5)
        // The south half is covered by the top step
        capture.texCoord(0, 0); capture.addVertex(x0, y05, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y05, z0);
        
        // North face of slab
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        
        // South face of slab
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        
        // West face of slab
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        // East face of slab
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        // Top step (0, 0.5, 0.5) to (1, 1, 1) - south half only
        // Top face of step
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y1, z05);
        capture.texCoord(0, 1); capture.addVertex(x0, y1, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y1, z1);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y1, z05);
        capture.texCoord(1, 1); capture.addVertex(x1, y1, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y1, z05);
        
        // North face of step (inner step face at z=0.5)
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z05);
        
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z05);
        
        // South face of step (full outer face)
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z1);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z1);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z1);
        
        // West face of step
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 0); capture.addVertex(x0, y1, z1);
        
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0); capture.addVertex(x0, y1, z1);
        capture.texCoord(0.5f, 0); capture.addVertex(x0, y1, z05);
        
        // East face of step
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0.5f, 0); capture.addVertex(x1, y1, z05);
        
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(0.5f, 0); capture.addVertex(x1, y1, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z1);
    }
    
    /**
     * Capture vertices for a west-facing bottom stairs block (for item rendering).
     * The step rises toward the west (x=0), appearing on the back-left in isometric view.
     * 
     * Geometry:
     * - Bottom slab: (0,0,0) to (1,0.5,1) - full block, half height
     * - Top step: (0,0.5,0) to (0.5,1,1) - west half only, top half height
     */
    public static void captureStairsWestBottom(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x05 = x + 0.5f, x1 = x + 1;
        float y0 = y, y05 = y + 0.5f, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Bottom slab (0, 0, 0) to (1, 0.5, 1)
        // Top face of bottom slab
        capture.texCoord(0, 0); capture.addVertex(x0, y05, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 1); capture.addVertex(x1, y05, z1);
        capture.texCoord(1, 0); capture.addVertex(x1, y05, z0);
        
        // North face of slab
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        
        // South face of slab
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        
        // West face of slab
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        // East face of slab
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        // Top step (0, 0.5, 0) to (0.5, 1, 1) - west half only
        // Top face of step
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y1, z1);
        capture.texCoord(0.5f, 1); capture.addVertex(x05, y1, z1);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(0.5f, 1); capture.addVertex(x05, y1, z1);
        capture.texCoord(0.5f, 0); capture.addVertex(x05, y1, z0);
        
        // West face of step (full outer face)
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 0); capture.addVertex(x0, y1, z1);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0); capture.addVertex(x0, y1, z1);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        // East face of step (inner step face at x=0.5)
        capture.texCoord(1, 0.5f); capture.addVertex(x05, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x05, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x05, y1, z0);
        
        capture.texCoord(1, 0.5f); capture.addVertex(x05, y05, z1);
        capture.texCoord(0, 0); capture.addVertex(x05, y1, z0);
        capture.texCoord(1, 0); capture.addVertex(x05, y1, z1);
        
        // North face of step
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x05, y05, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x05, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(0.5f, 0); capture.addVertex(x05, y1, z0);
        
        // South face of step
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x05, y05, z1);
        capture.texCoord(0.5f, 0); capture.addVertex(x05, y1, z1);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(0.5f, 0); capture.addVertex(x05, y1, z1);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z1);
    }
    
    /**
     * Capture vertices for stairs with step at northwest corner (for isometric item rendering).
     * The step rises toward the northwest (low x, low z), appearing at the back/top in isometric view.
     * 
     * Geometry:
     * - Bottom slab: (0,0,0) to (1,0.5,1) - full block, half height
     * - Top step: (0,0.5,0) to (1,1,0.5) - north half, full width, top half height
     */
    public static void captureStairsNorthwestCorner(VertexCapture capture, float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y05 = y + 0.5f, y1 = y + 1;
        float z0 = z, z05 = z + 0.5f, z1 = z + 1;
        
        // Bottom slab (0, 0, 0) to (1, 0.5, 1) - full block, half height
        // Top face of bottom slab - only the SOUTH half where there's no top step
        // South half: (0, 0.5) to (1, 1) at y=0.5
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(0, 1); capture.addVertex(x0, y05, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 1); capture.addVertex(x1, y05, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        
        // North face of slab (z=0)
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        
        // South face of slab (z=1)
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z1);
        
        // West face of slab (x=0)
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 1); capture.addVertex(x0, y0, z1);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        
        capture.texCoord(0, 1); capture.addVertex(x0, y0, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x0, y05, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        
        // East face of slab (x=1)
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 1); capture.addVertex(x1, y0, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        
        capture.texCoord(1, 1); capture.addVertex(x1, y0, z1);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z1);
        
        // Top step (0, 0.5, 0) to (1, 1, 0.5) - north half, full width
        // Top face of step
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y1, z05);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y1, z05);
        
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y1, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);
        
        // West face of step (x=0, z from 0 to 0.5)
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(0.5f, 0); capture.addVertex(x0, y1, z05);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0.5f, 0); capture.addVertex(x0, y1, z05);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        // East face of step (x=1, z from 0 to 0.5)
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x1, y1, z0);
        
        capture.texCoord(0.5f, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(0, 0); capture.addVertex(x1, y1, z0);
        capture.texCoord(0.5f, 0); capture.addVertex(x1, y1, z05);
        
        // North face of step (z=0, x from 0 to 1) - FULL WIDTH
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z0);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);
        
        // South face of step (z=0.5, x from 0 to 1) - inner step face, FULL WIDTH
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0.5f); capture.addVertex(x1, y05, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z05);
        
        capture.texCoord(0, 0.5f); capture.addVertex(x0, y05, z05);
        capture.texCoord(1, 0); capture.addVertex(x1, y1, z05);
        capture.texCoord(0, 0); capture.addVertex(x0, y1, z05);
    }
}
