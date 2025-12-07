package net.minecraft.client.gui.components.debug;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public record DebugEntryCategory(Component label, float sortKey) {
	public static final DebugEntryCategory SCREEN_TEXT = new DebugEntryCategory(Component.translatable("debug.options.category.text"), 1.0F);
	public static final DebugEntryCategory RENDERER = new DebugEntryCategory(Component.translatable("debug.options.category.renderer"), 2.0F);
}
