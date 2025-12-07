package net.minecraft.client.multiplayer.chat.report;

import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum ReportType {
	CHAT("chat"),
	SKIN("skin"),
	USERNAME("username");

	private final String backendName;

	private ReportType(final String string2) {
		this.backendName = string2.toUpperCase(Locale.ROOT);
	}

	public String backendName() {
		return this.backendName;
	}
}
