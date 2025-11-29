package mattmc.client.renderer.backend.opengl;

import mattmc.client.particle.Particle;
import mattmc.client.particle.ParticleEngine;
import mattmc.client.particle.ParticleRenderType;
import mattmc.client.particle.ParticleVertexBuilder;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL renderer for particles.
 * 
 * <p>This class handles the OpenGL-specific rendering of particles,
 * batching quads for efficiency and managing render state.
 */
public class OpenGLParticleRenderer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLParticleRenderer.class);
    
    /** Maximum particles per batch. */
    private static final int MAX_PARTICLES_PER_BATCH = 4096;
    
    /** Vertices per particle (4 for a quad). */
    private static final int VERTICES_PER_PARTICLE = 4;
    
    /** Floats per vertex: x, y, z, u, v, r, g, b, a (9 floats). */
    private static final int FLOATS_PER_VERTEX = 9;
    
    /** Indices per particle (6 for two triangles). */
    private static final int INDICES_PER_PARTICLE = 6;
    
    // VAO and VBOs
    private final int vao;
    private final int vbo;
    private final int ebo;
    
    // Shader
    private final Shader shader;
    
    // Vertex buffer
    private final FloatBuffer vertexBuffer;
    private int currentVertexCount;
    
    // Particle atlas
    private OpenGLParticleAtlas particleAtlas;
    
    // Block texture atlas (for terrain particles)
    private TextureAtlas blockAtlas;
    
    /**
     * Create a new particle renderer.
     */
    public OpenGLParticleRenderer() {
        // Create vertex buffer
        int maxVertices = MAX_PARTICLES_PER_BATCH * VERTICES_PER_PARTICLE;
        vertexBuffer = BufferUtils.createFloatBuffer(maxVertices * FLOATS_PER_VERTEX);
        
        // Create VAO
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        // Create VBO
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) maxVertices * FLOATS_PER_VERTEX * Float.BYTES, GL_DYNAMIC_DRAW);
        
        // Create EBO with pre-generated indices
        ebo = glGenBuffers();
        IntBuffer indices = createIndices(MAX_PARTICLES_PER_BATCH);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // Set up vertex attributes
        // Position (x, y, z)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // Texture coords (u, v)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Color (r, g, b, a)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        glBindVertexArray(0);
        
        // Create shader
        shader = createParticleShader();
        
        logger.debug("Particle renderer initialized");
    }
    
    /**
     * Set the particle atlas.
     */
    public void setParticleAtlas(OpenGLParticleAtlas atlas) {
        this.particleAtlas = atlas;
    }
    
    /**
     * Set the block texture atlas (for terrain particles).
     */
    public void setBlockAtlas(TextureAtlas atlas) {
        this.blockAtlas = atlas;
    }
    
    /**
     * Create index buffer for quads.
     */
    private IntBuffer createIndices(int quadCount) {
        IntBuffer indices = BufferUtils.createIntBuffer(quadCount * INDICES_PER_PARTICLE);
        for (int i = 0; i < quadCount; i++) {
            int base = i * VERTICES_PER_PARTICLE;
            // Two triangles per quad
            indices.put(base);
            indices.put(base + 1);
            indices.put(base + 2);
            indices.put(base + 2);
            indices.put(base + 3);
            indices.put(base);
        }
        indices.flip();
        return indices;
    }
    
    /**
     * Create the particle shader.
     */
    private Shader createParticleShader() {
        String vertexSource = """
            #version 130
            
            in vec3 aPosition;
            in vec2 aTexCoord;
            in vec4 aColor;
            
            out vec2 vTexCoord;
            out vec4 vColor;
            
            uniform mat4 uProjection;
            uniform mat4 uModelView;
            
            void main() {
                gl_Position = uProjection * uModelView * vec4(aPosition, 1.0);
                vTexCoord = aTexCoord;
                vColor = aColor;
            }
            """;
        
        String fragmentSource = """
            #version 130
            
            in vec2 vTexCoord;
            in vec4 vColor;
            
            out vec4 fragColor;
            
            uniform sampler2D uTexture;
            
            void main() {
                vec4 texColor = texture(uTexture, vTexCoord);
                fragColor = texColor * vColor;
                
                // Discard fully transparent pixels
                if (fragColor.a < 0.01) {
                    discard;
                }
            }
            """;
        
        return new Shader(vertexSource, fragmentSource);
    }
    
    /**
     * Render all particles in a particle engine.
     * 
     * @param engine the particle engine
     * @param cameraX camera X position
     * @param cameraY camera Y position
     * @param cameraZ camera Z position
     * @param partialTicks interpolation factor
     */
    public void render(ParticleEngine engine, double cameraX, double cameraY, double cameraZ, float partialTicks) {
        if (engine.countParticles() == 0) {
            return;
        }
        
        // Get matrices from current OpenGL state
        // Note: This uses the deprecated fixed-function matrix stack (GL_PROJECTION_MATRIX, GL_MODELVIEW_MATRIX)
        // which is consistent with the rest of the codebase's immediate-mode OpenGL usage.
        // A future Vulkan backend would receive matrices as explicit parameters instead.
        FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
        FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_PROJECTION_MATRIX, projectionBuffer);
        glGetFloatv(GL_MODELVIEW_MATRIX, modelViewBuffer);
        
        // Set up shader
        shader.bind();
        shader.setUniformMatrix4("uProjection", projectionBuffer);
        shader.setUniformMatrix4("uModelView", modelViewBuffer);
        shader.setUniformInt("uTexture", 0);
        
        // Bind VAO
        glBindVertexArray(vao);
        
        // Render by render type
        for (ParticleRenderType type : ParticleRenderType.values()) {
            if (type == ParticleRenderType.NO_RENDER) continue;
            
            Iterable<Particle> particles = engine.getParticles(type);
            if (!hasParticles(particles)) continue;
            
            // Set up render state for this type
            setupRenderState(type);
            
            // Bind appropriate texture
            bindTextureForType(type);
            
            // Render particles
            renderParticles(particles, cameraX, cameraY, cameraZ, partialTicks);
        }
        
        // Restore state
        glBindVertexArray(0);
        shader.unbind();
        glDisable(GL_BLEND);
        glDepthMask(true);
    }
    
    private boolean hasParticles(Iterable<Particle> particles) {
        return particles.iterator().hasNext();
    }
    
    /**
     * Set up OpenGL state for a render type.
     */
    private void setupRenderState(ParticleRenderType type) {
        // Depth test
        if (type.usesDepthTest()) {
            glEnable(GL_DEPTH_TEST);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
        
        // Blending
        if (type.usesBlending()) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            glDisable(GL_BLEND);
        }
        
        // Depth writing for opaque particles
        if (type == ParticleRenderType.PARTICLE_SHEET_OPAQUE) {
            glDepthMask(true);
        } else if (type == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT) {
            glDepthMask(false);
        } else {
            glDepthMask(true);
        }
    }
    
    /**
     * Bind the appropriate texture for a render type.
     */
    private void bindTextureForType(ParticleRenderType type) {
        if (type.usesTerrainAtlas() && blockAtlas != null) {
            blockAtlas.bind();
        } else if (particleAtlas != null) {
            particleAtlas.bind();
        }
    }
    
    /**
     * Render a group of particles.
     */
    private void renderParticles(Iterable<Particle> particles, double cameraX, double cameraY, double cameraZ, float partialTicks) {
        currentVertexCount = 0;
        vertexBuffer.clear();
        
        ParticleVertexBuilder builder = new ParticleVertexBuilder() {
            @Override
            public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a, int light) {
                if (currentVertexCount >= MAX_PARTICLES_PER_BATCH * VERTICES_PER_PARTICLE) {
                    // Flush current batch
                    flushBatch();
                }
                
                vertexBuffer.put(x).put(y).put(z);
                vertexBuffer.put(u).put(v);
                vertexBuffer.put(r).put(g).put(b).put(a);
                currentVertexCount++;
            }
        };
        
        for (Particle particle : particles) {
            particle.render(builder, cameraX, cameraY, cameraZ, partialTicks);
        }
        
        // Flush remaining
        if (currentVertexCount > 0) {
            flushBatch();
        }
    }
    
    /**
     * Flush the current vertex batch to the GPU and render.
     */
    private void flushBatch() {
        if (currentVertexCount == 0) return;
        
        vertexBuffer.flip();
        
        // Upload vertex data
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        
        // Draw
        int quadCount = currentVertexCount / VERTICES_PER_PARTICLE;
        int indexCount = quadCount * INDICES_PER_PARTICLE;
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        
        // Reset for next batch
        vertexBuffer.clear();
        currentVertexCount = 0;
    }
    
    @Override
    public void close() {
        shader.dispose();
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
