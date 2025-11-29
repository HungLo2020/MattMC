package mattmc.client.particle;

/**
 * Interface for building particle vertices.
 * 
 * <p>This abstracts the vertex buffer operations needed for particle rendering,
 * allowing the backend to provide its own implementation without exposing
 * OpenGL-specific types to the particle system.
 */
public interface ParticleVertexBuilder {
    
    /**
     * Add a vertex with position, texture coordinates, color, and light.
     * 
     * @param x position X
     * @param y position Y
     * @param z position Z
     * @param u texture coordinate U
     * @param v texture coordinate V
     * @param r red color component (0-1)
     * @param g green color component (0-1)
     * @param b blue color component (0-1)
     * @param a alpha component (0-1)
     * @param light packed light value (skyLight | (blockLight << 16))
     */
    void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a, int light);
    
    /**
     * Convenience method for adding a vertex with double precision position.
     */
    default void vertex(double x, double y, double z, float u, float v, float r, float g, float b, float a, int light) {
        vertex((float) x, (float) y, (float) z, u, v, r, g, b, a, light);
    }
}
