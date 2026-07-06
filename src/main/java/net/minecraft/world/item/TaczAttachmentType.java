package net.minecraft.world.item;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum TaczAttachmentType implements StringRepresentable {
	NONE("none"),
	SCOPE("scope"),
	MUZZLE("muzzle"),
	STOCK("stock"),
	GRIP("grip"),
	LASER("laser"),
	EXTENDED_MAG("extended_mag"),
	AMMO_MOD("ammo_mod");

	private final String serializedName;

	TaczAttachmentType(String serializedName) {
		this.serializedName = serializedName;
	}

	public static TaczAttachmentType byId(int id) {
		TaczAttachmentType[] values = values();
		return id >= 0 && id < values.length ? values[id] : NONE;
	}

	public static TaczAttachmentType byName(String name) {
		for (TaczAttachmentType type : values()) {
			if (type.serializedName.equals(name.toLowerCase(Locale.ROOT))) {
				return type;
			}
		}
		return NONE;
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}
}
