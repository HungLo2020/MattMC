package com.seibel.distanthorizons.core.render.glObject;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public final class DhTextureState
{
	private DhTextureState() { }

	public static void setActiveTextureUnit(int textureId)
	{
		setActiveTextureUnit(VulkanicAPI.getCommandContext(), textureId);
	}

	public static void setActiveTextureUnit(CommandContext ctx, int textureId)
	{
		VulkanicAPI.setActiveTextureUnit(ctx, textureId);
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTexture(textureId);
	}

	public static void setActiveTextureUnitIndex(int unitIndex)
	{
		setActiveTextureUnitIndex(VulkanicAPI.getCommandContext(), unitIndex);
	}

	public static void setActiveTextureUnitIndex(CommandContext ctx, int unitIndex)
	{
		VulkanicAPI.setActiveTextureUnitIndex(ctx, unitIndex);
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(unitIndex);
	}

	public static void bindTexture2D(int textureId)
	{
		bindTexture2D(VulkanicAPI.getCommandContext(), textureId);
	}

	public static void bindTexture2D(CommandContext ctx, int textureId)
	{
		VulkanicAPI.bindTexture2D(ctx, textureId);
	}
}
