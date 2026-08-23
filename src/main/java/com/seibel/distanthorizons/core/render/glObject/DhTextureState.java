package com.seibel.distanthorizons.core.render.glObject;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public final class DhTextureState
{
	private DhTextureState() { }

	public static void setActiveTextureUnit(int textureId)
	{
		ensureJavaCompatibilityTextureState();
		setActiveTextureUnit(VulkanicAPI.getCommandContext(), textureId);
	}

	public static void setActiveTextureUnit(CommandContext ctx, int textureId)
	{
		ensureJavaCompatibilityTextureState();
		VulkanicAPI.setActiveTextureUnit(ctx, textureId);
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTexture(textureId);
	}

	public static void setActiveTextureUnitIndex(int unitIndex)
	{
		ensureJavaCompatibilityTextureState();
		setActiveTextureUnitIndex(VulkanicAPI.getCommandContext(), unitIndex);
	}

	public static void setActiveTextureUnitIndex(CommandContext ctx, int unitIndex)
	{
		ensureJavaCompatibilityTextureState();
		VulkanicAPI.setActiveTextureUnitIndex(ctx, unitIndex);
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(unitIndex);
	}

	public static void bindTexture2D(int textureId)
	{
		ensureJavaCompatibilityTextureState();
		bindTexture2D(VulkanicAPI.getCommandContext(), textureId);
	}

	public static void bindTexture2D(CommandContext ctx, int textureId)
	{
		ensureJavaCompatibilityTextureState();
		VulkanicAPI.bindTexture2D(ctx, textureId);
	}

	private static void ensureJavaCompatibilityTextureState()
	{
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java DH texture state is unavailable while Rust owns whole-frame presentation");
		}
	}
}
