package net.vulkanic;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Backend-agnostic synchronization metadata for resource visibility barriers.
 *
 * <p>This is a pre-Vulkan seam that carries barrier intent independent of raw
 * OpenGL bitfields. Backends map this descriptor to native synchronization APIs:
 * OpenGL uses {@code glMemoryBarrier}, while Vulkan will map to
 * {@code vkCmdPipelineBarrier} / synchronization2 primitives.</p>
 */
public final class VulkanicResourceBarriers {

    private final EnumSet<Barrier> barriers;

    private VulkanicResourceBarriers(EnumSet<Barrier> barriers) {
        if (barriers.isEmpty()) {
            throw new IllegalArgumentException("barriers must contain at least one entry");
        }
        this.barriers = EnumSet.copyOf(barriers);
    }

    /**
     * Creates a barrier set from one or more barrier domains.
     */
    public static VulkanicResourceBarriers of(Barrier first, Barrier... rest) {
        Objects.requireNonNull(first, "first must not be null");
        EnumSet<Barrier> values = EnumSet.of(first);
        if (rest != null) {
            for (Barrier barrier : rest) {
                values.add(Objects.requireNonNull(barrier, "barrier entries must not be null"));
            }
        }
        return new VulkanicResourceBarriers(values);
    }

    /**
     * Common post-dispatch barrier set for compute writes consumed by following texture reads.
     */
    public static VulkanicResourceBarriers computeWritesVisibleToTextureSampling() {
        return of(
            Barrier.SHADER_IMAGE_ACCESS,
            Barrier.TEXTURE_FETCH,
            Barrier.SHADER_STORAGE
        );
    }

    public Set<Barrier> barriers() {
        return Collections.unmodifiableSet(barriers);
    }

    /**
     * OpenGL mapping helper for backend implementations.
     */
    public int toOpenGLBarrierBits() {
        int bits = 0;
        for (Barrier barrier : barriers) {
            bits |= barrier.openGLBit();
        }
        return bits;
    }

    /**
     * Converts legacy OpenGL barrier bits into the typed subset currently understood by Vulkanic.
     */
    public static Optional<VulkanicResourceBarriers> fromOpenGLBits(int bits) {
        EnumSet<Barrier> values = EnumSet.noneOf(Barrier.class);
        int remainingBits = bits;
        for (Barrier barrier : Barrier.values()) {
            if ((bits & barrier.openGLBit()) != 0) {
                values.add(barrier);
                remainingBits &= ~barrier.openGLBit();
            }
        }

        if (remainingBits != 0 || values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new VulkanicResourceBarriers(values));
    }

    /**
     * Logical barrier domains currently mapped by OpenGL and targeted for Vulkan translation.
     */
    public enum Barrier {
        SHADER_IMAGE_ACCESS(VulkanicAPI.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT),
        TEXTURE_FETCH(VulkanicAPI.GL_TEXTURE_FETCH_BARRIER_BIT),
        SHADER_STORAGE(VulkanicAPI.GL_SHADER_STORAGE_BARRIER_BIT);

        private final int openGLBit;

        Barrier(int openGLBit) {
            this.openGLBit = openGLBit;
        }

        public int openGLBit() {
            return openGLBit;
        }
    }
}
