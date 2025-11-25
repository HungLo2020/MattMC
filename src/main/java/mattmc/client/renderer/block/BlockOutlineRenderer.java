package mattmc.client.renderer.block;

import mattmc.client.renderer.backend.RenderBackend;

/**
 * API-agnostic block outline rendering utility.
 * 
 * <p>This class provides methods for drawing block outlines without depending on
 * specific backend implementations (like OpenGL). It uses the {@link RenderBackend}
 * interface for all rendering operations.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * backend.begin3DLines();
 * backend.setColor(0x000000, 1f);
 * BlockOutlineRenderer.drawBlockOutline(x, y, z, backend);
 * backend.end3DLines();
 * </pre>
 * 
 * @since Rendering refactor - API abstraction
 */
public final class BlockOutlineRenderer {
    
    private BlockOutlineRenderer() {} // Prevent instantiation
    
    /**
     * Draw a complete outline around a cube using RenderBackend abstraction.
     * Used for highlighting the targeted block.
     * Must be called between backend.begin3DLines() and backend.end3DLines().
     * 
     * @param x block X position
     * @param y block Y position
     * @param z block Z position
     * @param backend the render backend to use
     */
    public static void drawBlockOutline(float x, float y, float z, RenderBackend backend) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Bottom 4 edges
        backend.addLineVertex(x0, y0, z0); backend.addLineVertex(x1, y0, z0);
        backend.addLineVertex(x1, y0, z0); backend.addLineVertex(x1, y0, z1);
        backend.addLineVertex(x1, y0, z1); backend.addLineVertex(x0, y0, z1);
        backend.addLineVertex(x0, y0, z1); backend.addLineVertex(x0, y0, z0);
        
        // Top 4 edges
        backend.addLineVertex(x0, y1, z0); backend.addLineVertex(x1, y1, z0);
        backend.addLineVertex(x1, y1, z0); backend.addLineVertex(x1, y1, z1);
        backend.addLineVertex(x1, y1, z1); backend.addLineVertex(x0, y1, z1);
        backend.addLineVertex(x0, y1, z1); backend.addLineVertex(x0, y1, z0);
        
        // 4 vertical edges
        backend.addLineVertex(x0, y0, z0); backend.addLineVertex(x0, y1, z0);
        backend.addLineVertex(x1, y0, z0); backend.addLineVertex(x1, y1, z0);
        backend.addLineVertex(x1, y0, z1); backend.addLineVertex(x1, y1, z1);
        backend.addLineVertex(x0, y0, z1); backend.addLineVertex(x0, y1, z1);
    }
}
