package net.blaze3d.resource;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Blaze3D compatibility alias for {@link net.vulkanic.GraphicsResourceAllocator}.
 *
 * <p>This interface extends the Vulkanic version so that Blaze3D code
 * continues to compile unchanged while the migration from
 * {@code net.blaze3d.resource} → {@code net.vulkanic} proceeds.
 * New code should import {@link net.vulkanic.GraphicsResourceAllocator} directly.
 */
@Environment(EnvType.CLIENT)
public interface GraphicsResourceAllocator extends net.vulkanic.GraphicsResourceAllocator {

    /**
     * Blaze3D-specific unpooled allocator (delegates to the Vulkanic version).
     */
    GraphicsResourceAllocator UNPOOLED = new GraphicsResourceAllocator() {
        @Override
        public <T> T acquire(net.vulkanic.ResourceDescriptor<T> descriptor) {
            T resource = descriptor.allocate();
            descriptor.prepare(resource);
            return resource;
        }

        @Override
        public <T> void release(net.vulkanic.ResourceDescriptor<T> descriptor, T resource) {
            descriptor.free(resource);
        }
    };
}
