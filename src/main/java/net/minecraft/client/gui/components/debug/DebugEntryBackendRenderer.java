package net.minecraft.client.gui.components.debug;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
class DebugEntryBackendRenderer implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		debugScreenDisplayer.addLine("Backend Renderer: " + VulkanicAPI.getBackendName());
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
