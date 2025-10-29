package MattMC.gfx;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

/** Minimal PNG texture loader (RGBA) for resources on the classpath. */
public final class Texture implements AutoCloseable {
    public final int id;
    public final int width;
    public final int height;

    private Texture(int id, int w, int h) {
        this.id = id; this.width = w; this.height = h;
    }

    public static Texture load(String classpath) {
        // Read the resource into a ByteBuffer
        ByteBuffer fileData = readResourceToBuffer(classpath);
        if (fileData == null) throw new RuntimeException("Missing resource: " + classpath);

        // Decode with STBImage into RGBA
        int[] x = new int[1], y = new int[1], comp = new int[1];
        STBImage.stbi_set_flip_vertically_on_load(true);
        ByteBuffer pixels = STBImage.stbi_load_from_memory(fileData, x, y, comp, 4);
        if (pixels == null) {
            throw new RuntimeException("stbi_load failed for " + classpath + " : " + STBImage.stbi_failure_reason());
        }

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, x[0], y[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        STBImage.stbi_image_free(pixels);
        glBindTexture(GL_TEXTURE_2D, 0);

        return new Texture(tex, x[0], y[0]);
    }

    private static ByteBuffer readResourceToBuffer(String path) {
        try (InputStream in = Texture.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] tmp = in.readAllBytes();
            ByteBuffer buf = BufferUtils.createByteBuffer(tmp.length);
            buf.put(tmp).flip();
            return buf;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    public void bind() { glBindTexture(GL_TEXTURE_2D, id); }

    @Override public void close() {
        glDeleteTextures(id);
    }
}
