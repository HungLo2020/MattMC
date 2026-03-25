package net.vulkanic.backends.opengl;

import net.vulkanic.CommandContext;
import net.vulkanic.PipelineHandle;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicRenderPass;

/**
 * OpenGL implementation of {@link VulkanicRenderPass}.
 *
 * <p>Owns a temporary framebuffer object (FBO) created during
 * {@link net.vulkanic.backends.opengl.OpenGLBackend#beginRenderPass}. All draw
 * commands are dispatched through {@link VulkanicAPI} (which routes to
 * {@link OpenGLBackend}) so that no {@code org.lwjgl.opengl.*} imports appear
 * in this class.
 *
 * <p>On {@link #close()} the FBO is unbound and deleted via VulkanicAPI.
 */
public class OpenGLRenderPass implements VulkanicRenderPass {

    private final int fbo;
    private final CommandContext ctx;
    private VulkanicIndexType currentIndexType = VulkanicIndexType.INT;
    private boolean closed;

    /**
     * Creates a new OpenGLRenderPass wrapping the given FBO.
     *
     * @param fbo the GL framebuffer object that is already bound and cleared
     * @param ctx the immediate-mode command context (OpenGLCommandContext.IMMEDIATE)
     */
    public OpenGLRenderPass(int fbo, CommandContext ctx) {
        this.fbo = fbo;
        this.ctx = ctx;
        this.closed = false;
    }

    /** Returns the GL FBO handle backing this render pass. */
    public int getFbo() {
        return fbo;
    }

    /** Marks this render pass as closed without issuing any GL calls. Used in unit tests only. */
    public void markClosedForTesting() {
        closed = true;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("This render pass has been closed");
        }
    }

    @Override
    public void setPipeline(PipelineHandle pipeline) {
        checkNotClosed();
        if (!(pipeline instanceof OpenGLPipelineHandle glHandle)) {
            throw new IllegalArgumentException(
                "OpenGL render pass requires an OpenGLPipelineHandle, got: " +
                (pipeline == null ? "null" : pipeline.getClass().getName()));
        }
        if (!glHandle.isValid()) {
            throw new IllegalStateException(
                "Cannot bind an invalid pipeline (shader compilation failed)");
        }
        int programId = glHandle.getGlRenderPipeline().program().getProgramId();
        VulkanicAPI.bindShaderProgram(ctx, programId);
    }

    @Override
    public void setVertexBuffer(int slot, VulkanicBuffer buffer) {
        checkNotClosed();
        if (!(buffer instanceof OpenGLBuffer glBuffer)) {
            throw new IllegalArgumentException(
                "OpenGL render pass requires an OpenGLBuffer for vertex buffer, got: " +
                (buffer == null ? "null" : buffer.getClass().getName()));
        }
        VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.VERTEX, glBuffer.getGlHandle());
    }

    @Override
    public void setIndexBuffer(VulkanicBuffer buffer, VulkanicIndexType indexType) {
        checkNotClosed();
        if (!(buffer instanceof OpenGLBuffer glBuffer)) {
            throw new IllegalArgumentException(
                "OpenGL render pass requires an OpenGLBuffer for index buffer, got: " +
                (buffer == null ? "null" : buffer.getClass().getName()));
        }
        VulkanicAPI.bindIndexBuffer(ctx, glBuffer.getGlHandle());
        this.currentIndexType = indexType;
    }

    @Override
    public void drawIndexed(int firstIndex, int indexCount, int baseVertex, int instanceCount) {
        checkNotClosed();
        // Offset in bytes = firstIndex * bytesPerIndex
        long offset = (long) firstIndex * currentIndexType.bytesPerIndex();
        if (instanceCount == 1 && baseVertex == 0) {
            VulkanicAPI.drawElements(ctx, VulkanicPrimitiveMode.TRIANGLES, indexCount, currentIndexType, offset);
        } else {
            VulkanicAPI.drawIndexedInstancedBaseVertex(ctx, VulkanicPrimitiveMode.TRIANGLES,
                indexCount, currentIndexType, offset, instanceCount, baseVertex);
        }
    }

    @Override
    public void draw(int firstVertex, int vertexCount) {
        checkNotClosed();
        VulkanicAPI.drawArrays(ctx, VulkanicPrimitiveMode.TRIANGLES, firstVertex, vertexCount);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            // Unbind the FBO → restore default framebuffer
            VulkanicAPI.bindDefaultFramebuffer(ctx);
            // Delete the temporary FBO we created for this render pass
            VulkanicAPI.deleteFramebuffer(ctx, fbo);
        }
    }

    @Override
    public String toString() {
        return "OpenGLRenderPass{fbo=" + fbo + ", closed=" + closed + "}";
    }
}
