package net.minecraft.client.gui.render.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ScreenArea {
	@Nullable
	ScreenRectangle bounds();
}
