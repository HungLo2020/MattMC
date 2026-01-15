package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Fired before DH runs its apply shader.
 * The apply shader is a shader that copies over everything DH has rendered
 * for this pass into MC's framebuffers so it can be rendered to the screen.
 * Canceling this event prevents the apply shader from running.
 * 
 * @author James Seibel
 * @version 2024-1-31
 * @since API 2.0.0
 */
public abstract class DhApiBeforeApplyShaderRenderEvent implements IDhApiCancelableEvent<DhApiRenderParam>
{
	/** Fired before the apply shader is run. */
	public abstract void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<DhApiRenderParam> event) { this.beforeRender(event); }
	
}