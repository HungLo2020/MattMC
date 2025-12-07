package net.minecraft.client.gui.screens.worldselection;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldGenSettings;

@Environment(EnvType.CLIENT)
public record DataPackReloadCookie(WorldGenSettings worldGenSettings, WorldDataConfiguration dataConfiguration) {
}
