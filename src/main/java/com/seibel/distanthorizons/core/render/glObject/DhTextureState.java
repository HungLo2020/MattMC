package com.seibel.distanthorizons.core.render.glObject;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public final class DhTextureState
{
	private DhTextureState() { }

	public static void setActiveTextureUnit(int textureId)
	{
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		VulkanicAPI.setActiveTextureUnit(ctx, textureId);
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTexture(textureId);
	}

	public static void bindTexture2D(int textureId)
	{
		VulkanicAPI.bindTexture2D(VulkanicAPI.getImmediateContext(), textureId);
	}
}
