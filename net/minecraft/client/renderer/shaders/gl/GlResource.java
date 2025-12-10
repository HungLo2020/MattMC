package net.minecraft.client.renderer.shaders.gl;

/**
 * Base class for OpenGL resources that need lifecycle management.
 * Copied verbatim from IRIS GlResource.java.
 * 
 * @see <a href="https://github.com/IrisShaders/Iris">IRIS Source</a>
 */
public abstract class GlResource {
    private final int id;
    private boolean isValid;

    protected GlResource(int id) {
        this.id = id;
        isValid = true;
    }

    public final void destroy() {
        destroyInternal();
        isValid = false;
    }

    protected abstract void destroyInternal();

    protected void assertValid() {
        if (!isValid) {
            throw new IllegalStateException("Tried to use a destroyed GlResource");
        }
    }

    protected int getGlId() {
        assertValid();

        return id;
    }
}
