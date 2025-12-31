package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.joml.Vector2ic;

@Environment(EnvType.CLIENT)
public interface ClientTooltipPositioner {
	Vector2ic positionTooltip(int i, int j, int k, int l, int m, int n);
}
