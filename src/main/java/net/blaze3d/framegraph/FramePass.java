package net.blaze3d.framegraph;

import java.util.function.Consumer;
import net.blaze3d.resource.ResourceDescriptor;
import net.blaze3d.resource.ResourceHandle;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.CommandContext;

@Environment(EnvType.CLIENT)
public interface FramePass {
	<T> ResourceHandle<T> createsInternal(String string, ResourceDescriptor<T> resourceDescriptor);

	<T> void reads(ResourceHandle<T> resourceHandle);

	<T> ResourceHandle<T> readsAndWrites(ResourceHandle<T> resourceHandle);

	void requires(FramePass framePass);

	void disableCulling();

	void executes(Runnable runnable);

	/**
	 * New API: execute this pass with an explicit CommandContext.
	 *
	 * Implementations should prefer this context-aware variant when present.
	 */
	void executesWithContext(Consumer<CommandContext> consumer);
}
