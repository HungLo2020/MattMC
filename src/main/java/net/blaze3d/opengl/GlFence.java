package net.blaze3d.opengl;

import net.blaze3d.buffers.GpuFence;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.resources.VulkanicFence;

@Environment(EnvType.CLIENT)
public class GlFence implements GpuFence, VulkanicFence {
	private long handle = GlStateManager._glFenceSync(37143, 0);

	@Override
	public void close() {
		if (this.handle != 0L) {
			GlStateManager._glDeleteSync(this.handle);
			this.handle = 0L;
		}
	}

	@Override
	public boolean awaitCompletion(long l) {
		if (this.handle == 0L) {
			return true;
		} else {
			int i = GlStateManager._glClientWaitSync(this.handle, 0, l);
			if (i == 37147) {
				return false;
			} else if (i == 37149) {
				throw new IllegalStateException("Failed to complete gpu fence");
			} else {
				return true;
			}
		}
	}
}
