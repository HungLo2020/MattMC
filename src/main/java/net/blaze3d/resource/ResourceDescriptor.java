package net.blaze3d.resource;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Blaze3D compatibility alias for {@link net.vulkanic.ResourceDescriptor}.
 *
 * <p>This interface extends the Vulkanic version so that Blaze3D code
 * continues to compile unchanged while the migration from
 * {@code net.blaze3d.resource} → {@code net.vulkanic} proceeds.
 * New code should import {@link net.vulkanic.ResourceDescriptor} directly.
 */
@Environment(EnvType.CLIENT)
public interface ResourceDescriptor<T> extends net.vulkanic.ResourceDescriptor<T> {
}
