package mattmc.client.renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to capture vertices emitted during block rendering.
 * This allows us to reuse the same geometry generation code for both in-game 3D rendering
 * and 2D isometric item rendering.
 */
public class VertexCapture {
    
    /**
     * Represents a single vertex with position and texture coordinates.
     */
    public static class Vertex {
        public final float x, y, z;
        public final float u, v;
        
        public Vertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }
    
    /**
     * Represents a face (triangle) made up of 3 vertices.
     */
    public static class Face {
        public final Vertex v1, v2, v3;
        
        public Face(Vertex v1, Vertex v2, Vertex v3) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }
    }
    
    private final List<Face> faces = new ArrayList<>();
    private final List<Vertex> currentVertices = new ArrayList<>();
    private float currentU = 0, currentV = 0;
    
    /**
     * Set the current texture coordinates (called before addVertex).
     */
    public void texCoord(float u, float v) {
        this.currentU = u;
        this.currentV = v;
    }
    
    /**
     * Add a vertex with the current texture coordinates.
     * Every 3 vertices forms a triangle face.
     */
    public void addVertex(float x, float y, float z) {
        Vertex vertex = new Vertex(x, y, z, currentU, currentV);
        currentVertices.add(vertex);
        
        // Every 3 vertices forms a triangle
        if (currentVertices.size() == 3) {
            faces.add(new Face(
                currentVertices.get(0),
                currentVertices.get(1),
                currentVertices.get(2)
            ));
            currentVertices.clear();
        }
    }
    
    /**
     * Get all captured faces.
     */
    public List<Face> getFaces() {
        return faces;
    }
    
    /**
     * Clear all captured data.
     */
    public void clear() {
        faces.clear();
        currentVertices.clear();
    }
}
