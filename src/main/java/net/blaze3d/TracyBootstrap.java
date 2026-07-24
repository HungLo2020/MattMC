package net.blaze3d;

import net.minecraft.util.profiling.TracyCompat;
import net.logging.LogListeners;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.slf4j.event.Level;

@Environment(EnvType.CLIENT)
public class TracyBootstrap {
	private static boolean setup;

	public static void setup() {
		if (!setup) {
			TracyCompat.load();
			if (TracyCompat.isAvailable()) {
				LogListeners.addListener("Tracy", (string, level) -> TracyCompat.message(string, messageColor(level)));
				setup = true;
			}
		}
	}

	private static int messageColor(Level level) {
		return switch (level) {
			case DEBUG -> 11184810;
			case WARN -> 16777130;
			case ERROR -> 16755370;
			default -> 16777215;
		};
	}
}
