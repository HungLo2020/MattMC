package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons has started setting up OpenGL objects for rendering. <br>
 * If you want to modify already bound DH OpenGL objects try using {@link DhApiBeforeRenderPassEvent}.
 * 
 * @author James Seibel
 * @version 2024-1-31
 * @since API 2.0.0
 * 
 * @see DhApiBeforeRenderPassEvent
 */
public abstract class DhApiBeforeRenderSetupEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons has started setting up OpenGL objects for rendering. */
	public abstract void beforeSetup(DhApiEventParam<DhApiRenderParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> input) { this.beforeSetup(input); }
	
	
}