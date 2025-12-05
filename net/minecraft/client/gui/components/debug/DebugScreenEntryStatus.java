package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.StringRepresentable.EnumCodec;

@Environment(EnvType.CLIENT)
public enum DebugScreenEntryStatus implements StringRepresentable {
	ALWAYS_ON("alwaysOn"),
	IN_F3("inF3"),
	NEVER("never");

	public static final EnumCodec<DebugScreenEntryStatus> CODEC = StringRepresentable.fromEnum(DebugScreenEntryStatus::values);
	private final String name;

	private DebugScreenEntryStatus(final String string2) {
		this.name = string2;
	}

	public String getSerializedName() {
		return this.name;
	}
}
