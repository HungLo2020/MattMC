package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called during Distant Horizons rendering setup and immediately <br>
 * before the render textures are cleared. <br>
 * Generally the textures cleared are Distant Horizons owned depth and color textures. <br> 
 * Canceling the event will prevent DH from clearing any textures.
 *
 * @author James Seibel
 * @version 2024-1-31
 * @since API 2.0.0
 */
public abstract class DhApiBeforeTextureClearEvent implements IDhApiCancelableEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons clears any textures. */
	public abstract void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<DhApiRenderParam> input) { this.beforeClear(input); }
	
}