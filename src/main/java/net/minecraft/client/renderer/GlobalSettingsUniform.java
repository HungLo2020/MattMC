package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class GlobalSettingsUniform implements AutoCloseable {
	public static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putFloat().putFloat().putInt().get();
	private GpuBuffer buffer;

	public GlobalSettingsUniform() {
		throw new IllegalStateException("Java global-settings UBO is unavailable on selected Vulkan");
	}

	public void update(int i, int j, double d, long l, DeltaTracker deltaTracker, int k) {
		this.ensureRustSemanticRoute();
		return;
	}

	public void close() {
		if (this.buffer != null) {
			this.buffer.close();
			this.buffer = null;
		}
	}

	public void ensureRustSemanticRoute() {
		this.close();
	}
}
