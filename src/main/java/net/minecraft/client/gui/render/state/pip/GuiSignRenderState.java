package net.minecraft.client.gui.render.state.pip;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.Model;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiSignRenderState(
	Model.Simple signModel,
	WoodType woodType,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public GuiSignRenderState(Model.Simple simple, WoodType woodType, int i, int j, int k, int l, float f, @Nullable ScreenRectangle screenRectangle) {
		this(simple, woodType, i, j, k, l, f, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle));
	}
}
