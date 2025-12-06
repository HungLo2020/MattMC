package com.mojang.blaze3d.shaders;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum ShaderType {
	VERTEX("vertex", ".vsh"),
	FRAGMENT("fragment", ".fsh");

	private static final ShaderType[] TYPES = values();
	private final String name;
	private final String extension;

	private ShaderType(final String string2, final String string3) {
		this.name = string2;
		this.extension = string3;
	}

	@Nullable
	public static ShaderType byLocation(ResourceLocation resourceLocation) {
		for (ShaderType shaderType : TYPES) {
			if (resourceLocation.getPath().endsWith(shaderType.extension)) {
				return shaderType;
			}
		}

		return null;
	}

	public String getName() {
		return this.name;
	}

	public FileToIdConverter idConverter() {
		return new FileToIdConverter("shaders", this.extension);
	}
}
