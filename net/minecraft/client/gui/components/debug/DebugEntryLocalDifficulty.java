package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryLocalDifficulty implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		if (entity != null && minecraft.level != null && levelChunk2 != null && level != null) {
			BlockPos blockPos = entity.blockPosition();
			if (minecraft.level.isInsideBuildHeight(blockPos.getY())) {
				float f = level.getMoonBrightness();
				long l = levelChunk2.getInhabitedTime();
				DifficultyInstance difficultyInstance = new DifficultyInstance(level.getDifficulty(), level.getDayTime(), l, f);
				debugScreenDisplayer.addLine(
					String.format(
						Locale.ROOT,
						"Local Difficulty: %.2f // %.2f (Day %d)",
						difficultyInstance.getEffectiveDifficulty(),
						difficultyInstance.getSpecialMultiplier(),
						minecraft.level.getDayTime() / 24000L
					)
				);
			}
		}
	}
}
