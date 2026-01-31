package net.blaze3d.textures;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum FilterMode {
	NEAREST,
	LINEAR
}
