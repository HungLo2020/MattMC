package net.minecraft.client.renderer.sodium.gui.prompt;

import net.minecraft.client.renderer.sodium.util.Dim2i;
import org.jetbrains.annotations.Nullable;

public interface ScreenPromptable {
    void setPrompt(@Nullable ScreenPrompt prompt);

    @Nullable ScreenPrompt getPrompt();

    Dim2i getDimensions();
}
