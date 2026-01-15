package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons starts the cleanup process done after rendering. <br>
 * This called after every render pass.
 *
 * @author James Seibel
 * @version 2024-1-31
 * @since API 2.0.0
 */
public abstract class DhApiBeforeRenderCleanupEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** Fired before Distant Horizons starts the cleanup process once rendering has finished. */
	public abstract void beforeCleanup(DhApiEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> event) { this.beforeCleanup(event); }
	
}