package net.distanthorizons.core.render;

import net.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import net.distanthorizons.api.objects.DhApiResult;
import net.distanthorizons.core.api.internal.SharedApi;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.level.IDhClientLevel;
import net.distanthorizons.core.level.IDhLevel;
import net.distanthorizons.core.render.renderer.LodRenderer;
import net.distanthorizons.core.util.RenderUtil;
import net.distanthorizons.core.world.AbstractDhWorld;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

/**
 * Used to interact with Distant Horizons' rendering systems.
 *
 * @author James Seibel
 * @version 2023-2-8
 */
public class DhApiRenderProxy implements IDhApiRenderProxy
{
	public static final DhApiRenderProxy INSTANCE = new DhApiRenderProxy();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private boolean deferTransparentRendering = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private DhApiRenderProxy() { }
	
	
	
	//=========//
	// methods //
	//=========//
	
	public DhApiResult<Boolean> clearRenderDataCache()
	{
		// make sure this is a valid time to run the method
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world == null)
		{
			return DhApiResult.createFail("No world loaded");
		}
		
		
		// clear the render caches for each level
		Iterable<? extends IDhLevel> loadedLevels = world.getAllLoadedLevels();
		for (IDhLevel level : loadedLevels)
		{
			if (level instanceof IDhClientLevel)
			{
				((IDhClientLevel) level).clearRenderCache();
			}
		}
		
		return DhApiResult.createSuccess();
	}
	
	
	@Override
	public DhApiResult<Integer> getDhDepthTextureId()
	{
		int activeTexture = LodRenderer.INSTANCE.getActiveDepthTextureId();
		return (activeTexture == -1) ? DhApiResult.createFail("DH's depth texture hasn't been created and/or bound yet.", -1) : DhApiResult.createSuccess(activeTexture);
	}
	@Override
	public DhApiResult<Integer> getDhColorTextureId()
	{
		int activeTexture = LodRenderer.INSTANCE.getActiveColorTextureId();
		return (activeTexture == -1) ? DhApiResult.createFail("DH's color texture hasn't been created and/or bound yet.", -1) : DhApiResult.createSuccess(activeTexture);
	}
	
	
	@Override 
	public void setDeferTransparentRendering(boolean deferTransparentRendering) { this.deferTransparentRendering = deferTransparentRendering; }
	@Override 
	public boolean getDeferTransparentRendering() { return this.deferTransparentRendering; }
	
	@Override
	public float getNearClipPlaneDistanceInBlocks(float partialTicks) { return RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks); }
	
}
