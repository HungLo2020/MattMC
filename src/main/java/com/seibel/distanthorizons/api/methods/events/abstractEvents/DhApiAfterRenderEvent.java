package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Fired after Distant Horizons finishes rendering a frame. <br>
 * At this point DH will have also finished cleaning up any modifications it
 * did to the OpenGL state, so the state should be back to Minecraft's defaults. <br><br>
 *
 * Note: as of API v 3.0.0 no {@link DhApiRenderParam} is included in this event 
 * because the specific parameters may change
 * depending on whether deferred rendering is enabled or not.
 * 
 * @author James Seibel
 * @version 2024-7-14
 * @since API 1.0.0
 */
public abstract class DhApiAfterRenderEvent implements IDhApiEvent<Void>
{
	/** Fired after Distant Horizons finishes rendering fake chunks. */
	public abstract void afterRender(DhApiEventParam<Void> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<Void> event) { this.afterRender(event); }
	
}