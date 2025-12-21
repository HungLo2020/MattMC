package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;


public class GuiHelper
{
	/**
	 * Helper static methods for versional compat
	 */
	public static Button MakeBtn(Component base, int posX, int posZ, int width, int height, Button.OnPress action)
	{
		return Button.builder(base, action).bounds(posX, posZ, width, height).build();
	}
	
	public static MutableComponent TextOrLiteral(String text)
	{
		return Component.literal(text);
	}
	
	public static MutableComponent TextOrTranslatable(String text)
	{
		return Component.translatable(text);
	}
	
	public static MutableComponent Translatable(String text, Object... args)
	{
		return Component.translatable(text, args);
	}
	
	public static void SetX(AbstractWidget w, int x)
	{
		w.setX(x);
	}
	
	public static void SetY(AbstractWidget w, int y)
	{
		w.setY(y);
	}
	
}
