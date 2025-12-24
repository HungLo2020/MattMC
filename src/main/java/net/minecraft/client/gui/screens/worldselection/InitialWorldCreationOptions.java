package net.minecraft.client.gui.screens.worldselection;

import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record InitialWorldCreationOptions(
	WorldCreationUiState.SelectedGameMode selectedGameMode,
	Set<Key<BooleanValue>> disabledGameRules,
	@Nullable ResourceKey<FlatLevelGeneratorPreset> flatLevelPreset
) {
}
