package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DhScreen extends Screen
{
	
	protected DhScreen(Component $$0)
	{
		super($$0);
	}
	
	// addRenderableWidget in 1.17 and over
	// addButton in 1.16 and below
	protected Button addBtn(Button button)
	{
		return this.addRenderableWidget(button);
	}
	
	protected void DhDrawCenteredString(GuiGraphics guiStack, Font font, Component text, int x, int y, int color)
	{
		guiStack.drawCenteredString(font, text, x, y, color);
	}
	protected void DhDrawString(GuiGraphics guiStack, Font font, Component text, int x, int y, int color)
	{
		guiStack.drawString(font, text, x, y, color);
	}
	//protected void DhRenderTooltip(GuiGraphics guiStack, Font font, List<? extends net.minecraft.util.FormattedCharSequence> text, int x, int y)
	//{
	//	//guiStack.renderTooltip(font, text, x, y);
	//}
	protected void DhRenderComponentTooltip(GuiGraphics guiStack, Font font, List<Component> comp, int x, int y)
	{
		guiStack.setComponentTooltipForNextFrame(font, comp, x, y);
	}
	protected void DhRenderTooltip(GuiGraphics guiStack, Font font, Component text, int x, int y)
	{
		guiStack.setTooltipForNextFrame(font, text, x, y);
	}
	
	
	
}
