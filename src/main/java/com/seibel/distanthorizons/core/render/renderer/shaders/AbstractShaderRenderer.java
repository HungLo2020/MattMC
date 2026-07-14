package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public abstract class AbstractShaderRenderer
{
	protected static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	protected ShaderProgram shader;

	protected boolean init = false;
	
	
	protected AbstractShaderRenderer() {}
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		this.onInit();
	}
	
	public void render(float partialTicks)
	{
		this.init();
		CommandContext ctx = VulkanicAPI.getCommandContext();

		int width = MC_RENDER.getTargetFramebufferViewportWidth();
		int height = MC_RENDER.getTargetFramebufferViewportHeight();
		if (width <= 0 || height <= 0)
		{
			return;
		}

		if (!this.onPreRender(ctx, partialTicks))
		{
			return;
		}
		
		String shaderName = this.getClass().getSimpleName();
		try (VulkanicAPI.ShaderInputParityScope ignored = VulkanicAPI.beginShaderInputParitySemanticDraw(
			"dh-shader-renderer",
			"distant-horizons",
			"shader:" + shaderName,
			null,
			null,
			"distant-horizons:" + shaderName,
			"distant-horizons-framebuffer",
			false,
			0,
			0,
			0,
			0,
			1,
			0
		))
		{
			this.shader.bind(ctx);
			try
			{
				this.onApplyUniforms(ctx, partialTicks);
				VulkanicAPI.setViewport(ctx, 0, 0, width, height);
				this.onRender(ctx);
			}
			finally
			{
				this.shader.unbind(ctx);
			}
		}
	}
	
	public void free()
	{
		if (this.shader != null)
		{
			this.shader.free();
		}
	}
	
	protected void onInit() {}

	protected boolean onPreRender(CommandContext ctx, float partialTicks) { return true; }
	
	protected void onApplyUniforms(CommandContext ctx, float partialTicks) {}
	
	protected void onRender(CommandContext ctx) {}
}
