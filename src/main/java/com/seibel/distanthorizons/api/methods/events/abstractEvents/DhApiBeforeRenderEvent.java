package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons starts rendering a frame. <br>
 * Canceling the event will prevent DH from rendering that frame. <br> <br>
 * 
 * This is called before DH starts modifying the GL state.
 * If you want to inject into DH's rendering pass, use {@link DhApiBeforeRenderPassEvent} instead.
 *
 * @author James Seibel
 * @version 2023-6-23
 * @since API 1.0.0
 * 
 * @see DhApiBeforeRenderPassEvent
 */
public abstract class DhApiBeforeRenderEvent implements IDhApiCancelableEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons renders LODs. */
	public abstract void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<DhApiRenderParam> input) { this.beforeRender(input); }
	
}