package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class GlobalSettingsUniform implements AutoCloseable {
	public static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putFloat().putFloat().putInt().get();
	private final GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Global Settings UBO", 136, UBO_SIZE);

	public void update(int i, int j, double d, long l, DeltaTracker deltaTracker, int k) {
		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, UBO_SIZE)
				.putVec2(i, j)
				.putFloat((float)d)
				.putFloat(((float)(l % 24000L) + deltaTracker.getGameTimeDeltaPartialTick(false)) / 24000.0F)
				.putInt(k)
				.get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
		}

		RenderSystem.setGlobalSettingsUniform(this.buffer);
	}

	public void close() {
		this.buffer.close();
	}
}
