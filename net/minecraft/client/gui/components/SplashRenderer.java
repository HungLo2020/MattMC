package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class SplashRenderer {
	public static final SplashRenderer CHRISTMAS = new SplashRenderer("Merry X-mas!");
	public static final SplashRenderer NEW_YEAR = new SplashRenderer("Happy new year!");
	public static final SplashRenderer HALLOWEEN = new SplashRenderer("OOoooOOOoooo! Spooky!");
	private static final int WIDTH_OFFSET = 123;
	private static final int HEIGH_OFFSET = 69;
	private final String splash;

	public SplashRenderer(String string) {
		this.splash = string;
	}

	public void render(GuiGraphics guiGraphics, int i, Font font, float f) {
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().translate(i / 2.0F + 123.0F, 69.0F);
		guiGraphics.pose().rotate((float) (-Math.PI / 9));
		float g = 1.8F - Mth.abs(Mth.sin((float)(Util.getMillis() % 1000L) / 1000.0F * (float) (Math.PI * 2)) * 0.1F);
		g = g * 100.0F / (font.width(this.splash) + 32);
		guiGraphics.pose().scale(g, g);
		guiGraphics.drawCenteredString(font, this.splash, 0, -8, ARGB.color(f, -256));
		guiGraphics.pose().popMatrix();
	}
}
