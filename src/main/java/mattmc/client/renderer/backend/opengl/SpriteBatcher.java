package mattmc.client.renderer.backend.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A 2D sprite batcher for efficient UI rendering.
 * 
 * <p>This class replaces immediate mode rendering (glBegin/glEnd) with batched
 * VBO-based rendering. All quads added during a frame are batched together and
 * rendered in a single draw call when {@link #flush()} is called.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * spriteBatcher.begin();
 * spriteBatcher.setColor(1, 1, 1, 1);
 * spriteBatcher.addQuad(x, y, width, height);          // Solid color quad
 * spriteBatcher.addTexturedQuad(x, y, w, h, u0, v0, u1, v1);  // Textured quad
 * spriteBatcher.flush();  // Renders all quads in one draw call
 * spriteBatcher.end();
 * </pre>
 * 
 * <p><b>Architecture:</b> This class is part of the rendering backend and
 * uses modern OpenGL (VBOs, VAOs) instead of deprecated immediate mode.
 * 
 * <p><b>Performance:</b> Batching many quads into a single draw call
 * significantly improves performance compared to individual glBegin/glEnd calls.
 * 
 * @since Stage 4 of rendering refactor (Issue #12)
 */
public class SpriteBatcher {
    
    /** Maximum number of quads that can be batched before flush is required */
    private static final int MAX_QUADS = 1000;
    
    /** Vertices per quad (4 corners) */
    private static final int VERTICES_PER_QUAD = 4;
    
    /** Floats per vertex: x, y, u, v, r, g, b, a */
    private static final int FLOATS_PER_VERTEX = 8;
    
    /** Indices per quad (2 triangles = 6 indices) */
    private static final int INDICES_PER_QUAD = 6;
    
    /** Vertex buffer */
    private final FloatBuffer vertexBuffer;
    
    /** OpenGL VAO handle */
    private int vaoId;
    
    /** OpenGL VBO handle for vertices */
    private int vboId;
    
    /** OpenGL VBO handle for indices (EBO) */
    private int eboId;
    
    /** Current number of quads in the batch */
    private int quadCount;
    
    /** Current color for new quads */
    private float colorR = 1.0f;
    private float colorG = 1.0f;
    private float colorB = 1.0f;
    private float colorA = 1.0f;
    
    /** Whether the batcher is currently active (between begin/end) */
    private boolean isDrawing;
    
    /** Whether OpenGL resources have been initialized */
    private boolean initialized;
    
    /**
     * Create a new SpriteBatcher.
     */
    public SpriteBatcher() {
        // Allocate vertex buffer (direct buffer for OpenGL)
        int bufferSize = MAX_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX * Float.BYTES;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
        byteBuffer.order(ByteOrder.nativeOrder());
        this.vertexBuffer = byteBuffer.asFloatBuffer();
        
        this.initialized = false;
        this.isDrawing = false;
        this.quadCount = 0;
    }
    
    /**
     * Initialize OpenGL resources.
     * Must be called from the OpenGL thread.
     */
    private void initialize() {
        if (initialized) return;
        
        // Create VAO
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        // Create VBO for vertices
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int vboSize = MAX_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX * Float.BYTES;
        glBufferData(GL_ARRAY_BUFFER, vboSize, GL_DYNAMIC_DRAW);
        
        // Create EBO for indices (static - pattern is always the same)
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        
        // Generate index pattern: 0,1,2, 2,3,0, 4,5,6, 6,7,4, ...
        int[] indices = new int[MAX_QUADS * INDICES_PER_QUAD];
        for (int i = 0; i < MAX_QUADS; i++) {
            int offset = i * VERTICES_PER_QUAD;
            int idx = i * INDICES_PER_QUAD;
            indices[idx + 0] = offset + 0;
            indices[idx + 1] = offset + 1;
            indices[idx + 2] = offset + 2;
            indices[idx + 3] = offset + 2;
            indices[idx + 4] = offset + 3;
            indices[idx + 5] = offset + 0;
        }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // Setup vertex attributes
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        
        // Position (x, y) - location 0
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        
        // Texture coords (u, v) - location 1
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Color (r, g, b, a) - location 2
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        // Unbind
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        
        initialized = true;
    }
    
    /**
     * Begin a new batch.
     * Must be called before adding quads.
     */
    public void begin() {
        if (isDrawing) {
            throw new IllegalStateException("SpriteBatcher.begin() called while already drawing");
        }
        
        if (!initialized) {
            initialize();
        }
        
        isDrawing = true;
        quadCount = 0;
        vertexBuffer.clear();
    }
    
    /**
     * Set the color for subsequent quads.
     * 
     * @param r red component (0-1)
     * @param g green component (0-1)
     * @param b blue component (0-1)
     * @param a alpha component (0-1)
     */
    public void setColor(float r, float g, float b, float a) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.colorA = a;
    }
    
    /**
     * Set the color from an RGB int and alpha.
     * 
     * @param rgb packed RGB color
     * @param alpha alpha component (0-1)
     */
    public void setColor(int rgb, float alpha) {
        this.colorR = ((rgb >> 16) & 0xFF) / 255f;
        this.colorG = ((rgb >> 8) & 0xFF) / 255f;
        this.colorB = (rgb & 0xFF) / 255f;
        this.colorA = alpha;
    }
    
    /**
     * Add a solid color quad (no texture).
     * Uses texture coords of (0,0) which works with a white texture or no texture.
     * 
     * @param x left edge
     * @param y top edge
     * @param width width of quad
     * @param height height of quad
     */
    public void addQuad(float x, float y, float width, float height) {
        addTexturedQuad(x, y, width, height, 0, 0, 0, 0);
    }
    
    /**
     * Add a textured quad.
     * 
     * @param x left edge
     * @param y top edge
     * @param width width of quad
     * @param height height of quad
     * @param u0 left texture coord
     * @param v0 top texture coord
     * @param u1 right texture coord
     * @param v1 bottom texture coord
     */
    public void addTexturedQuad(float x, float y, float width, float height,
                                 float u0, float v0, float u1, float v1) {
        if (!isDrawing) {
            throw new IllegalStateException("SpriteBatcher.addTexturedQuad() called without begin()");
        }
        
        if (quadCount >= MAX_QUADS) {
            flush();
        }
        
        float x2 = x + width;
        float y2 = y + height;
        
        // Vertex 0: top-left
        vertexBuffer.put(x);   vertexBuffer.put(y);
        vertexBuffer.put(u0);  vertexBuffer.put(v0);
        vertexBuffer.put(colorR); vertexBuffer.put(colorG);
        vertexBuffer.put(colorB); vertexBuffer.put(colorA);
        
        // Vertex 1: top-right
        vertexBuffer.put(x2);  vertexBuffer.put(y);
        vertexBuffer.put(u1);  vertexBuffer.put(v0);
        vertexBuffer.put(colorR); vertexBuffer.put(colorG);
        vertexBuffer.put(colorB); vertexBuffer.put(colorA);
        
        // Vertex 2: bottom-right
        vertexBuffer.put(x2);  vertexBuffer.put(y2);
        vertexBuffer.put(u1);  vertexBuffer.put(v1);
        vertexBuffer.put(colorR); vertexBuffer.put(colorG);
        vertexBuffer.put(colorB); vertexBuffer.put(colorA);
        
        // Vertex 3: bottom-left
        vertexBuffer.put(x);   vertexBuffer.put(y2);
        vertexBuffer.put(u0);  vertexBuffer.put(v1);
        vertexBuffer.put(colorR); vertexBuffer.put(colorG);
        vertexBuffer.put(colorB); vertexBuffer.put(colorA);
        
        quadCount++;
    }
    
    /**
     * Flush the current batch to the GPU and render.
     * This uploads all pending quads and renders them in a single draw call.
     * 
     * <p><b>Important:</b> The caller must ensure that:
     * <ul>
     *   <li>The appropriate shader is bound with correct projection matrix uniform set</li>
     *   <li>If using textures, the texture is bound and uUseTexture uniform is set to true</li>
     *   <li>Blending is enabled if alpha is used</li>
     * </ul>
     * 
     * <p>Vertex attributes are expected at these locations:
     * <ul>
     *   <li>Location 0: position (vec2)</li>
     *   <li>Location 1: texcoord (vec2)</li>
     *   <li>Location 2: color (vec4)</li>
     * </ul>
     */
    public void flush() {
        if (quadCount == 0) {
            return;
        }
        
        // Upload vertex data
        vertexBuffer.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        
        // Render
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, quadCount * INDICES_PER_QUAD, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        
        // Reset for next batch
        quadCount = 0;
        vertexBuffer.clear();
    }
    
    /**
     * End the current batch.
     * Flushes any remaining quads.
     */
    public void end() {
        if (!isDrawing) {
            throw new IllegalStateException("SpriteBatcher.end() called without begin()");
        }
        
        flush();
        isDrawing = false;
    }
    
    /**
     * Get the number of quads currently in the batch.
     */
    public int getQuadCount() {
        return quadCount;
    }
    
    /**
     * Check if the batcher is currently in a drawing state.
     */
    public boolean isDrawing() {
        return isDrawing;
    }
    
    /**
     * Release OpenGL resources.
     * Should be called when the batcher is no longer needed.
     */
    public void dispose() {
        if (initialized) {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
            glDeleteBuffers(eboId);
            initialized = false;
        }
    }
}
