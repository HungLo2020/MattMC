package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called immediately before Distant Horizons starts a rendering pass. <br>
 * At this point the GL state will be set up for DH to render. <br>
 * This event cannot be cancelled, use {@link DhApiBeforeRenderEvent} if you want to cancel rendering.
 * 
 * @author James Seibel
 * @version 2023-1-31
 * @since API 2.0.0
 * 
 * @see DhApiBeforeRenderEvent
 */
public abstract class DhApiBeforeRenderPassEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** 
	 * Fired immediately before Distant Horizons starts a rendering pass. <br>
	 * {@link DhApiRenderParam#renderPass} should either be {@link EDhApiRenderPass#OPAQUE} or {@link EDhApiRenderPass#TRANSPARENT}.
	 */
	public abstract void beforeRender(DhApiEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> event) { this.beforeRender(event); }
	
}