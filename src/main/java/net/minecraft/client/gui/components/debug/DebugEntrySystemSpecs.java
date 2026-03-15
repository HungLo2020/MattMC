package net.minecraft.client.gui.components.debug;

import net.blaze3d.platform.GLX;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntrySystemSpecs implements DebugScreenEntry {
	private static final ResourceLocation GROUP = ResourceLocation.withDefaultNamespace("system");

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		GpuDevice gpuDevice = net.vulkanic.VulkanicAPI.getDevice();
		GpuDevice.GpuDeviceInfo gpuDeviceInfo = gpuDevice.getDeviceInfo();
		debugScreenDisplayer.addToGroup(
			GROUP,
			List.of(
				String.format(Locale.ROOT, "Java: %s", System.getProperty("java.version")),
				String.format(Locale.ROOT, "CPU: %s", GLX._getCpuInfo()),
				String.format(
					Locale.ROOT, "Display: %dx%d (%s)", Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), gpuDeviceInfo.vendor()
				),
				gpuDeviceInfo.rendererDisplayString(),
				gpuDeviceInfo.backendDisplayString()
			)
		);
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
