package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

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
		// Rust VulkanicGAL owns DH's targets; there is no GL texture name to share.
		return DhApiResult.createFail("DH's depth texture is owned by the Rust renderer.", -1);
	}
	@Override
	public DhApiResult<Integer> getDhColorTextureId()
	{
		return DhApiResult.createFail("DH's color texture is owned by the Rust renderer.", -1);
	}
	
	
	@Override 
	public void setDeferTransparentRendering(boolean deferTransparentRendering) { this.deferTransparentRendering = deferTransparentRendering; }
	@Override 
	public boolean getDeferTransparentRendering() { return this.deferTransparentRendering; }
	
	@Override
	public float getNearClipPlaneDistanceInBlocks(float partialTicks) { return RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks); }
	
}
