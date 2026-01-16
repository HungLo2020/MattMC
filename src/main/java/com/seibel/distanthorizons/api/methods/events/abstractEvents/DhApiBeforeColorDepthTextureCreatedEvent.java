package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam;

/**
 * Called before Distant Horizons (re)creates 
 * the color and depth textures it renders to. <br>
 * 
 * @author James Seibel
 * @version 2025-6-9
 * @since API 4.1.0
 */
public abstract class DhApiBeforeColorDepthTextureCreatedEvent implements IDhApiEvent<DhApiTextureCreatedParam>
{
	/** Fired before Distant Horizons creates. */
	public abstract void onResize(DhApiEventParam<DhApiTextureCreatedParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiTextureCreatedParam> event) { this.onResize(event); }
	
	
}