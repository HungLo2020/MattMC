package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons has started setting up OpenGL objects for rendering generic objects. <br>
 * If you want to modify already bound DH OpenGL objects try using {@link DhApiBeforeGenericObjectRenderEvent}.
 * 
 * @author James Seibel
 * @version 2024-7-12
 * @since API 3.0.0
 * 
 * @see DhApiBeforeGenericObjectRenderEvent
 */
public abstract class DhApiBeforeGenericRenderSetupEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons has started setting up OpenGL objects for rendering generic objects. */
	public abstract void beforeSetup(DhApiEventParam<DhApiRenderParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> input) { this.beforeSetup(input); }
	
	
}