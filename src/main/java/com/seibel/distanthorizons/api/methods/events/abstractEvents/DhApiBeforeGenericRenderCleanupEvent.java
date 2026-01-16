package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons starts the cleanup process done after rendering generic objects. <br>
 * This is called after all generic objects have finished rendering.
 *
 * @author James Seibel
 * @version 2024-7-13
 * @since API 3.0.0
 */
public abstract class DhApiBeforeGenericRenderCleanupEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons starts the cleanup process once rendering has finished. */
	public abstract void beforeCleanup(DhApiEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> event) { this.beforeCleanup(event); }
	
}