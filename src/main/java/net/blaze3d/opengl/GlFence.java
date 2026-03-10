package net.blaze3d.opengl;

import net.blaze3d.buffers.GpuFence;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicAPI;

@Environment(EnvType.CLIENT)
public class GlFence implements GpuFence {
	private long handle = VulkanicAPI.createGpuCompletionFence(VulkanicAPI.getCommandContext());

	@Override
	public void close() {
		if (this.handle != 0L) {
			VulkanicAPI.destroySync(VulkanicAPI.getCommandContext(), this.handle);
			this.handle = 0L;
		}
	}

	@Override
	public boolean awaitCompletion(long l) {
		if (this.handle == 0L) {
			return true;
		} else {
			int i = VulkanicAPI.waitForSync(VulkanicAPI.getCommandContext(), this.handle, 0, l);
			if (VulkanicAPI.isSyncWaitTimeout(i)) {
				return false;
			} else if (VulkanicAPI.isSyncWaitFailed(i)) {
				throw new IllegalStateException("Failed to complete gpu fence");
			} else {
				return true;
			}
		}
	}
}
