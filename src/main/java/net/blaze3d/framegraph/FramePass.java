package net.blaze3d.framegraph;

import net.vulkanic.ResourceDescriptor;
import net.vulkanic.ResourceHandle;
import net.vulkanic.VulkanicFramePass;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Blaze3D rendering pass within a {@link FrameGraphBuilder}.
 *
 * <p>Extends {@link VulkanicFramePass} so that Blaze3D frame passes are also
 * usable wherever a {@code VulkanicFramePass} is expected.
 */
@Environment(EnvType.CLIENT)
public interface FramePass extends VulkanicFramePass {
	@Override
	<T> ResourceHandle<T> createsInternal(String string, ResourceDescriptor<T> resourceDescriptor);

	@Override
	<T> void reads(ResourceHandle<T> resourceHandle);

	@Override
	<T> ResourceHandle<T> readsAndWrites(ResourceHandle<T> resourceHandle);

	/** Requires {@code framePass} to execute before this pass. */
	void requires(FramePass framePass);

	/**
	 * Satisfies {@link VulkanicFramePass#requires(VulkanicFramePass)}.
	 * Delegates to {@link #requires(FramePass)} when {@code other} is a
	 * {@code FramePass}; otherwise throws.
	 */
	@Override
	default void requires(VulkanicFramePass other) {
		if (other instanceof FramePass fp) {
			this.requires(fp);
		} else {
			throw new IllegalArgumentException(
				"FramePass.requires only supports FramePass instances, got: " +
				other.getClass().getName());
		}
	}

	@Override
	void disableCulling();

	@Override
	void executes(Runnable runnable);
}
