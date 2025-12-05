package net.minecraft.client.gui.render.state.pip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiSkinRenderState(
	PlayerModel playerModel,
	ResourceLocation texture,
	float rotationX,
	float rotationY,
	float pivotY,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public GuiSkinRenderState(
		PlayerModel playerModel,
		ResourceLocation resourceLocation,
		float f,
		float g,
		float h,
		int i,
		int j,
		int k,
		int l,
		float m,
		@Nullable ScreenRectangle screenRectangle
	) {
		this(playerModel, resourceLocation, f, g, h, i, j, k, l, m, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle));
	}
}
