package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryPostEffect implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		ResourceLocation resourceLocation = minecraft.gameRenderer.currentPostEffect();
		if (resourceLocation != null) {
			debugScreenDisplayer.addLine("Post: " + resourceLocation);
		}
	}
}
