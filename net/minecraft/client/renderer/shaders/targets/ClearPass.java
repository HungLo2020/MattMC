package net.minecraft.client.renderer.shaders.targets;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Represents a single clear pass that clears specified render targets
 * to a given color.
 * 
 * <p>Clear passes are batched by color and viewport dimensions to minimize
 * OpenGL state changes.
 * 
 * <p>Following IRIS 1.21.9 ClearPass.java structure
 */
public class ClearPass {
    private final Vector4f color;
    private final IntSupplier viewportX;
    private final IntSupplier viewportY;
    private final GlFramebuffer framebuffer;
    private final int clearFlags;

    public ClearPass(Vector4f color, IntSupplier viewportX, IntSupplier viewportY, GlFramebuffer framebuffer, int clearFlags) {
        this.color = color;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
        this.framebuffer = framebuffer;
        this.clearFlags = clearFlags;
    }

    /**
     * Executes the clear pass.
     * 
     * @param defaultClearColor The default clear color to use if this pass doesn't specify one
     */
    public void execute(Vector4f defaultClearColor) {
        // Set viewport
        GlStateManager._viewport(0, 0, viewportX.getAsInt(), viewportY.getAsInt());
        
        // Bind framebuffer
        framebuffer.bind();

        // Determine clear color
        Vector4f color = Objects.requireNonNull(defaultClearColor);

        if (this.color != null) {
            color = this.color;
        }

        // Set clear color and execute clear
        GL11.glClearColor(color.x, color.y, color.z, color.w);
        GlStateManager._clear(clearFlags);
    }

    public GlFramebuffer getFramebuffer() {
        return framebuffer;
    }
}
