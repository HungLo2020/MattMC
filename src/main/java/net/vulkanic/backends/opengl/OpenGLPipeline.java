package net.vulkanic.backends.opengl;

import net.vulkanic.pipeline.PipelineHandle;
import org.lwjgl.opengl.GL20;

/**
 * OpenGL implementation of {@link PipelineHandle}.
 *
 * <p>Holds a linked GL program object created from vertex + fragment shader GLSL source.
 * Created and destroyed exclusively by {@link OpenGLBackend}.
 */
public class OpenGLPipeline implements PipelineHandle {

    /** Sentinel value used when shader compilation/linking failed. */
    public static final OpenGLPipeline INVALID = new OpenGLPipeline(0, "<invalid>");

    private final int programId;
    private final String debugLabel;
    private boolean deleted;

    OpenGLPipeline(int programId, String debugLabel) {
        this.programId  = programId;
        this.debugLabel = debugLabel;
        this.deleted    = false;
    }

    @Override
    public long getNativeHandle() {
        return programId;
    }

    @Override
    public boolean isValid() {
        return !deleted && programId != 0;
    }

    @Override
    public String getDebugLabel() {
        return debugLabel;
    }

    /** Deletes the underlying GL program object. */
    public void delete() {
        if (!deleted && programId != 0) {
            deleted = true;
            GL20.glDeleteProgram(programId);
        }
    }

    @Override
    public String toString() {
        return "OpenGLPipeline{program=" + programId + ", label=" + debugLabel
                + ", valid=" + isValid() + "}";
    }
}
