package net.minecraft.client.renderer.sodium.console;

import net.minecraft.client.renderer.sodium.console.message.MessageLevel;
import org.jetbrains.annotations.NotNull;

public interface ConsoleSink {
    void logMessage(@NotNull MessageLevel level, @NotNull String text, boolean translatable, double duration);
}
