package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.StringRepresentable.EnumCodec;

@Environment(EnvType.CLIENT)
public enum DebugScreenProfile implements StringRepresentable {
	DEFAULT("default", "debug.options.profile.default"),
	PERFORMANCE("performance", "debug.options.profile.performance");

	public static final EnumCodec<DebugScreenProfile> CODEC = StringRepresentable.fromEnum(DebugScreenProfile::values);
	private final String name;
	private final String translationKey;

	private DebugScreenProfile(final String string2, final String string3) {
		this.name = string2;
		this.translationKey = string3;
	}

	public String translationKey() {
		return this.translationKey;
	}

	public String getSerializedName() {
		return this.name;
	}
}
