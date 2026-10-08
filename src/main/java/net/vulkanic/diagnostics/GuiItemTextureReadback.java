package net.vulkanic.diagnostics;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.blaze3d.textures.GpuTexture;

/** Optional baseline observation; no graphics handles leave the backend. */
public final class GuiItemTextureReadback {
    private GuiItemTextureReadback() {}
    public static void observe(GpuTexture texture, Consumer<ByteBuffer> pixels) {
        net.vulkanic.backends.opengl.GuiItemTextureReadback.observe(texture,pixels);
    }
}
