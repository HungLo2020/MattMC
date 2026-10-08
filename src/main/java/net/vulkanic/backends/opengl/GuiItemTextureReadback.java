package net.vulkanic.backends.opengl;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.blaze3d.opengl.GlTexture;
import net.blaze3d.textures.GpuTexture;
import net.logging.LogUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.system.MemoryUtil;

/** Read-only DSA diagnostic: never changes bindings or pixel-pack state. */
public final class GuiItemTextureReadback {
    private GuiItemTextureReadback() {}
    public static void observe(GpuTexture texture, Consumer<ByteBuffer> observer) {
        if (!(texture instanceof GlTexture gl)) {
            LogUtils.getLogger().info("gui.item.raster-pixels unavailable=texture-type type={}", texture.getClass().getName());
            return;
        }
        boolean coreDsa = GL.getCapabilities().OpenGL45;
        if (!coreDsa && !GL.getCapabilities().GL_ARB_direct_state_access) {
            LogUtils.getLogger().info("gui.item.raster-pixels unavailable=no-direct-state-access");
            return;
        }
        int width=texture.getWidth(0),height=texture.getHeight(0);
        if (width<=0 || height<=0 || width>2048 || height>2048) return;
        if (GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) != 0
            || GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH) != 0
            || GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS) != 0
            || GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS) != 0
            || GL11.glGetInteger(GL12.GL_PACK_IMAGE_HEIGHT) != 0
            || GL11.glGetInteger(GL12.GL_PACK_SKIP_IMAGES) != 0
            || GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT) > 4) {
            LogUtils.getLogger().info("gui.item.raster-pixels unavailable=non-default-pack-state");
            return;
        }
        ByteBuffer pixels=MemoryUtil.memAlloc(width*height*4);
        try {
            int name=OpenGLBackend.diagnosticTextureName(gl);
            if (coreDsa) GL45.glGetTextureImage(name,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            else ARBDirectStateAccess.glGetTextureImage(name,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            observer.accept(pixels.asReadOnlyBuffer());
        } finally { MemoryUtil.memFree(pixels); }
    }
}
