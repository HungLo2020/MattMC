package mattmc.client.renderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal cubemap loader for six square PNGs from the classpath. */
public final class CubeMap implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CubeMap.class);

    public final int id;
    public final int size;

    private CubeMap(int id, int size) { this.id = id; this.size = size; }

    /**
     * Loads 6 images into a cubemap.
     *
     * Expected resources:
     *   basePathNoIndex + [0..5] + ext  → e.g. "/assets/textures/gui/panorama1_0.png"
     *
     * OpenGL upload face order:
     *   0 → GL_TEXTURE_CUBE_MAP_POSITIVE_X (right)
     *   1 → GL_TEXTURE_CUBE_MAP_NEGATIVE_X (left)
     *   2 → GL_TEXTURE_CUBE_MAP_POSITIVE_Y (top)
     *   3 → GL_TEXTURE_CUBE_MAP_NEGATIVE_Y (bottom)
     *   4 → GL_TEXTURE_CUBE_MAP_POSITIVE_Z (front)
     *   5 → GL_TEXTURE_CUBE_MAP_NEGATIVE_Z (back)
     *
     * Your images are arranged as:
     *   0 = right, 1 = left, 2 = front, 3 = back, 4 = top, 5 = bottom
     *
     * We want sides 0→1→2→3 to wrap around the horizon in order, with 4=top and 5=bottom.
     * Mapping chosen:
     *   +X(right)=1, -X(left)=3, +Y(top)=4, -Y(bottom)=5, +Z(front)=0, -Z(back)=2
     */
    public static CubeMap load(String basePathNoIndex, String ext) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, tex);

        // Do NOT flip vertically for cubemaps; author assets per-face as needed.
        STBImage.stbi_set_flip_vertically_on_load(false);

        // In case widths aren't multiples of 4 bytes, avoid stride issues.
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        int size = -1;

        // Remap file indices to OpenGL cubemap faces:
        //   GL +X, -X, +Y, -Y, +Z, -Z
        final int[] faceIndex = { 1, 3, 4, 5, 0, 2 };

        for (int face = 0; face < 6; face++) {
            int imgIndex = faceIndex[face];
            String path = basePathNoIndex + imgIndex + ext;

            ByteBuffer fileData = read(path);
            if (fileData == null) throw new RuntimeException("Missing cubemap face: " + path);

            int[] w = new int[1], h = new int[1], comp = new int[1];
            ByteBuffer pixels = STBImage.stbi_load_from_memory(fileData, w, h, comp, 4);
            if (pixels == null)
                throw new RuntimeException("stbi failed for " + path + " : " + STBImage.stbi_failure_reason());
            if (w[0] != h[0])
                throw new RuntimeException("Cubemap face not square: " + path + " " + w[0] + "x" + h[0]);

            if (size == -1) size = w[0];
            if (w[0] != size || h[0] != size)
                throw new RuntimeException("Cubemap faces must match size. Expected " + size + " got " + w[0] + " for " + path);

            int target = GL_TEXTURE_CUBE_MAP_POSITIVE_X + face;
            glTexImage2D(target, 0, GL_RGBA8, w[0], h[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            STBImage.stbi_image_free(pixels);
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        logger.info("Loaded cubemap {}[0..5]{} size={} id={}", basePathNoIndex, ext, size, tex);
        return new CubeMap(tex, size);
    }

    private static ByteBuffer read(String path) {
        try (InputStream in = CubeMap.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] tmp = in.readAllBytes();
            ByteBuffer buf = BufferUtils.createByteBuffer(tmp.length);
            buf.put(tmp).flip();
            return buf;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    public void bind() { glBindTexture(GL_TEXTURE_CUBE_MAP, id); }

    @Override public void close() { glDeleteTextures(id); }
}
