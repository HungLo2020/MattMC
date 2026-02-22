package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlRenderPipeline;
import net.vulkanic.PipelineHandle;

/**
 * OpenGL implementation of {@link PipelineHandle}.
 *
 * <p>Wraps a {@link GlRenderPipeline} — a compiled GL shader program plus
 * its associated pipeline state. The underlying GlProgram is managed by the
 * GlDevice pipeline cache; closing this handle does not free the program
 * (the device cache manages lifetime).
 */
public class OpenGLPipelineHandle implements PipelineHandle {

    private final GlRenderPipeline glRenderPipeline;
    private boolean closed;

    /**
     * Creates an OpenGLPipelineHandle wrapping the given compiled pipeline.
     *
     * @param glRenderPipeline the compiled OpenGL render pipeline
     */
    public OpenGLPipelineHandle(GlRenderPipeline glRenderPipeline) {
        this.glRenderPipeline = glRenderPipeline;
        this.closed = false;
    }

    /** Returns the underlying {@link GlRenderPipeline}. */
    public GlRenderPipeline getGlRenderPipeline() {
        return glRenderPipeline;
    }

    @Override
    public boolean isValid() {
        return !closed && glRenderPipeline.isValid();
    }

    @Override
    public void close() {
        // The GlDevice pipeline cache manages the actual GlProgram lifetime.
        // Closing this handle simply marks it as invalid for future use.
        closed = true;
    }

    @Override
    public String toString() {
        return "OpenGLPipelineHandle{valid=" + isValid() + ", closed=" + closed + "}";
    }
}
