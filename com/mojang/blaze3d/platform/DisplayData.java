package com.mojang.blaze3d.platform;

import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record DisplayData(int width, int height, OptionalInt fullscreenWidth, OptionalInt fullscreenHeight, boolean isFullscreen) {
	public DisplayData withSize(int i, int j) {
		return new DisplayData(i, j, this.fullscreenWidth, this.fullscreenHeight, this.isFullscreen);
	}

	public DisplayData withFullscreen(boolean bl) {
		return new DisplayData(this.width, this.height, this.fullscreenWidth, this.fullscreenHeight, bl);
	}
}
