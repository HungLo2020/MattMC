package mattmc.client.renderer.texture;

import mattmc.client.settings.OptionsManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal PNG loader (RGBA) from classpath. */
public final class Texture implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Texture.class);

    public final int id;
    public final int width;
    public final int height;

    private Texture(int id, int w, int h) { this.id = id; this.width = w; this.height = h; }

    public static Texture load(String classpath) {
        ByteBuffer fileData = readResourceToBuffer(classpath);
        if (fileData == null) throw new RuntimeException("Missing resource on classpath: " + classpath);

        int[] x = new int[1], y = new int[1], comp = new int[1];
        STBImage.stbi_set_flip_vertically_on_load(true);
        ByteBuffer pixels = STBImage.stbi_load_from_memory(fileData, x, y, comp, 4);
        if (pixels == null) {
            throw new RuntimeException("stbi_load failed: " + STBImage.stbi_failure_reason());
        }

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        // Upload texture data
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, x[0], y[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        STBImage.stbi_image_free(pixels);
        
        // Apply NEAREST filtering for UI textures (pixel art should not be blurred)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        // logger.info("Loaded texture {} {}x{} id={}", classpath, x[0], y[0], tex);
        return new Texture(tex, x[0], y[0]);
    }

    private static ByteBuffer readResourceToBuffer(String path) {
        byte[] data = mattmc.util.ResourceLoader.loadBinaryResource(path);
        if (data == null) return null;
        
        ByteBuffer buf = BufferUtils.createByteBuffer(data.length);
        buf.put(data).flip();
        return buf;
    }

    public void bind() { glBindTexture(GL_TEXTURE_2D, id); }

    @Override public void close() { glDeleteTextures(id); }
}
