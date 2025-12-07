package net.minecraft.client.telemetry;

import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface TelemetryEventSender {
	TelemetryEventSender DISABLED = (telemetryEventType, consumer) -> {};

	default TelemetryEventSender decorate(Consumer<TelemetryPropertyMap.Builder> consumer) {
		return (telemetryEventType, consumer2) -> this.send(telemetryEventType, builder -> {
			consumer2.accept(builder);
			consumer.accept(builder);
		});
	}

	void send(TelemetryEventType telemetryEventType, Consumer<TelemetryPropertyMap.Builder> consumer);
}
