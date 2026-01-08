package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.network.chat.Component;

import net.minecraft.client.gui.components.Button;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import net.minecraft.resources.ResourceLocation;

/**
 * Creates a button with a texture on it (and a background) that works with all mc versions
 *
 * @author coolGi
 * @version 2023-10-03
 */
public class TexturedButtonWidget extends Button
{
	public final boolean renderBackground;
	
	private final int u;
	private final int v;
	private final int hoveredVOffset;
	
	private final ResourceLocation textureResourceLocation;
	
	private final int textureWidth;
	private final int textureHeight;
	
	
	public TexturedButtonWidget(
		int x, int y, int width, int height, int u, int v, int hoveredVOffset, 
		ResourceLocation textureResourceLocation, 
		int textureWidth, int textureHeight, OnPress pressAction, Component text) 
	{
		this(x, y, width, height, u, v, hoveredVOffset, textureResourceLocation, textureWidth, textureHeight, pressAction, text, true);
	}
	public TexturedButtonWidget(
		int x, int y, int width, int height, int u, int v, int hoveredVOffset, 
		ResourceLocation textureResourceLocation, 
		int textureWidth, int textureHeight, OnPress pressAction, Component text, 
		boolean renderBackground)
	{
		// We don't pass on the text option as otherwise it will render (we normally pass it for narration)
		// TODO: Find a fix for it
		super(x, y, width, height, Component.empty(), pressAction, DEFAULT_NARRATION);
		
		this.u = u;
		this.v = v;
		this.hoveredVOffset = hoveredVOffset;
		
		this.textureResourceLocation = textureResourceLocation;
		
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		
		this.renderBackground = renderBackground;
	}
	
	@Override
	public void renderWidget(GuiGraphics matrices, int mouseX, int mouseY, float delta)
	{
		if (this.renderBackground)
		{
			matrices.blitSprite(
				RenderPipelines.GUI_TEXTURED,
				SPRITES.get(this.active, this.isHoveredOrFocused()),
				this.getX(), this.getY(),
				this.getWidth(), this.getHeight());

		}
		
		
		// Renders the sprite
		int i = 0;
		if (!this.active)
		{
			i = 2;
		}
		else if (this.isHovered)
		{
			i = 1;
		}
		
		matrices.blit(
				RenderPipelines.GUI_TEXTURED,
				this.textureResourceLocation,
				this.getX(), this.getY(),
				this.u, this.v + (this.hoveredVOffset * i),
				this.width, this.height,
				this.textureWidth, this.textureHeight);

	}
}
