package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
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
		
		this.shader.bind();
		
		this.onApplyUniforms(partialTicks);
		
		int width = MC_RENDER.getTargetFramebufferViewportWidth();
		int height = MC_RENDER.getTargetFramebufferViewportHeight();
		VulkanicAPI.setViewport(VulkanicAPI.getImmediateContext(), 0, 0, width, height);
		
		this.onRender();
		
		this.shader.unbind();
	}
	
	public void free()
	{
		if (this.shader != null)
		{
			this.shader.free();
		}
	}
	
	protected void onInit() {}
	
	protected void onApplyUniforms(float partialTicks) {}
	
	protected void onRender() {}
}
