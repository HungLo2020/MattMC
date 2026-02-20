package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.DirectStateAccess;
import net.blaze3d.opengl.GlBuffer;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Vulkanic-managed OpenGL buffer.
 *
 * <p>Extends {@link GlBuffer} so that all existing Blaze3D code that casts
 * {@code GpuBuffer → GlBuffer} (e.g. {@code GlCommandEncoder}) continues to work
 * correctly after the buffer-lifecycle migration, while this class also satisfies
 * the {@link net.vulkanic.resources.VulkanicBuffer} contract through the
 * {@code implements VulkanicBuffer} declaration on {@code GlBuffer}.
 *
 * <p><b>Lifecycle:</b> Created exclusively by {@link OpenGLBackend}.
 * {@link net.blaze3d.opengl.GlDevice#createBuffer} now delegates to
 * {@link net.vulkanic.VulkanicAPI#createVulkanicBuffer}, which dispatches here
 * through {@link OpenGLBackend#createVulkanicBuffer} — this is the
 * "Blaze3D is a thin facade over Vulkanic" relationship described in
 * {@code VULKANIC-MIGRATION.md §5 Phase 3a}.
 */
public class OpenGLBuffer extends GlBuffer {

    /**
     * Package-private constructor used by {@link OpenGLBackend}.
     *
     * @param label            Optional debug label supplier
     * @param dsa              {@link DirectStateAccess} helper (may be {@code null}
     *                         when no persistent mapping is used)
     * @param usage            Usage flags (GpuBuffer / VulkanicBuffer USAGE_* constants)
     * @param size             Size in bytes
     * @param glHandle         GL buffer object name returned by glGenBuffers / glCreateBuffers
     * @param persistentBuffer Pre-mapped ByteBuffer for persistent-mapping storage, or
     *                         {@code null} when not using persistent mapping
     */
    OpenGLBuffer(@Nullable Supplier<String> label,
                 @Nullable DirectStateAccess dsa,
                 int usage, int size, int glHandle,
                 @Nullable ByteBuffer persistentBuffer) {
        // Protected GlBuffer constructor is accessible from a subclass in any package.
        // Passing null for dsa is safe when persistentBuffer is null (close() only
        // accesses dsa to unmap a persistent buffer).
        super(label, dsa, usage, size, glHandle, persistentBuffer);
    }

    // All VulkanicBuffer and GpuBuffer methods are inherited from GlBuffer.
    // No additional logic is required here — the class exists solely to provide
    // the correct subtype so that both VulkanicAPI callers and legacy GlBuffer
    // casts in GlCommandEncoder receive a compatible object.
}

