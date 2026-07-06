package net.minecraft.world.item;

import java.util.Locale;

public enum TaczFireMode {
	SEMI("semi"),
	AUTO("auto"),
	BURST("burst");

	private final String serializedName;

	TaczFireMode(String serializedName) {
		this.serializedName = serializedName;
	}

	public static TaczFireMode byName(String name) {
		for (TaczFireMode mode : values()) {
			if (mode.serializedName.equals(name.toLowerCase(Locale.ROOT))) {
				return mode;
			}
		}
		return SEMI;
	}

	public String getSerializedName() {
		return this.serializedName;
	}
}
