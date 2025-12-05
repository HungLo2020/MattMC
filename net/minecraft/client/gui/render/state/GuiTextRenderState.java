package net.minecraft.client.gui.render.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

@Environment(EnvType.CLIENT)
public final class GuiTextRenderState implements ScreenArea {
	public final Font font;
	public final FormattedCharSequence text;
	public final Matrix3x2f pose;
	public final int x;
	public final int y;
	public final int color;
	public final int backgroundColor;
	public final boolean dropShadow;
	@Nullable
	public final ScreenRectangle scissor;
	@Nullable
	private Font.PreparedText preparedText;
	@Nullable
	private ScreenRectangle bounds;

	public GuiTextRenderState(
		Font font,
		FormattedCharSequence formattedCharSequence,
		Matrix3x2f matrix3x2f,
		int i,
		int j,
		int k,
		int l,
		boolean bl,
		@Nullable ScreenRectangle screenRectangle
	) {
		this.font = font;
		this.text = formattedCharSequence;
		this.pose = matrix3x2f;
		this.x = i;
		this.y = j;
		this.color = k;
		this.backgroundColor = l;
		this.dropShadow = bl;
		this.scissor = screenRectangle;
	}

	public Font.PreparedText ensurePrepared() {
		if (this.preparedText == null) {
			this.preparedText = this.font.prepareText(this.text, (float)this.x, (float)this.y, this.color, this.dropShadow, this.backgroundColor);
			ScreenRectangle screenRectangle = this.preparedText.bounds();
			if (screenRectangle != null) {
				screenRectangle = screenRectangle.transformMaxBounds(this.pose);
				this.bounds = this.scissor != null ? this.scissor.intersection(screenRectangle) : screenRectangle;
			}
		}

		return this.preparedText;
	}

	@Nullable
	@Override
	public ScreenRectangle bounds() {
		this.ensurePrepared();
		return this.bounds;
	}
}
