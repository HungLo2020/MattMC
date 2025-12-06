package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RealmsDescriptionDto extends ValueObject implements ReflectionBasedSerialization {
	@SerializedName("name")
	@Nullable
	public String name;
	@SerializedName("description")
	public String description;

	public RealmsDescriptionDto(@Nullable String string, String string2) {
		this.name = string;
		this.description = string2;
	}
}
