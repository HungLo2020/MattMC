package net.minecraft.client;

import com.mojang.serialization.Codec;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.OptionEnum;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public enum PanoramaTheme implements OptionEnum, StringRepresentable {
	AQUATIC(0, "aquatic", "options.panoramaTheme.aquatic"),
	COPPER_AGE(1, "copper_age", "options.panoramaTheme.copperAge");

	public static final Codec<PanoramaTheme> CODEC = StringRepresentable.fromEnum(PanoramaTheme::values);
	private final int id;
	private final String serializeName;
	private final String key;

	private PanoramaTheme(final int id, final String serializeName, final String key) {
		this.id = id;
		this.serializeName = serializeName;
		this.key = key;
	}

	public String getSerializedName() {
		return this.serializeName;
	}

	public int getId() {
		return this.id;
	}

	public String getKey() {
		return this.key;
	}

	public String getPath() {
		// Return lowercase path matching directory structure
		// ResourceLocation requires lowercase paths
		return this.serializeName;
	}
}
