package net.sodium.client.render.chunk.data;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Native lookup of the facings of a section that can face the camera,
 * used by the meshing task to mark visible slices.
 */
public final class SectionVisibleFaces {
    private static final int OK = 0;
    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle VISIBLE_FACES = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_section_render_data_visible_faces",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final int VERIFY_STATUS = invokeVerify();

    private SectionVisibleFaces() {
    }

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        if (VERIFY_STATUS != OK) {
            throw new IllegalStateException("native section visible-face verification failed with native status " + VERIFY_STATUS);
        }
        try {
            return (int) VISIBLE_FACES.invokeExact(originX, originY, originZ, chunkX, chunkY, chunkZ);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section visible-face calculation downcall failed", throwable);
        }
    }

    private static int invokeVerify() {
        try {
            return (int) VERIFY.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust section render data verification downcall failed", throwable);
        }
    }
}
