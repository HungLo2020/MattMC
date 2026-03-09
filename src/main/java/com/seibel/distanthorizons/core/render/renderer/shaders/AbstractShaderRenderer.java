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
		
		this.shader.bind(ctx);
		
		this.onApplyUniforms(ctx, partialTicks);
		
		int width = MC_RENDER.getTargetFramebufferViewportWidth();
		int height = MC_RENDER.getTargetFramebufferViewportHeight();
		VulkanicAPI.setViewport(ctx, 0, 0, width, height);
		
		this.onRender(ctx);
		
		this.shader.unbind(ctx);
	}
	
	public void free()
	{
		if (this.shader != null)
		{
			this.shader.free();
		}
	}
	
	protected void onInit() {}
	
	protected void onApplyUniforms(CommandContext ctx, float partialTicks) {}
	
	protected void onRender(CommandContext ctx) {}
}
