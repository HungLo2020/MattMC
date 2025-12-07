package net.minecraft.client.telemetry;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface TelemetryEventLogger {
	void log(TelemetryEventInstance telemetryEventInstance);
}
