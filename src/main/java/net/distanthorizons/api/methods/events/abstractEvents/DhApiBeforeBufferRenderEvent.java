package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import net.distanthorizons.api.objects.math.DhApiVec3f;

/**
 * Called before Distant Horizons starts rendering a buffer. <br>
 * This event cannot be cancelled, use {@link DhApiBeforeRenderEvent} if you want to cancel rendering.
 * 
 * @author James Seibel
 * @version 2023-1-31
 * @since API 2.0.0
 * 
 * @see DhApiBeforeRenderEvent
 */
public abstract class DhApiBeforeBufferRenderEvent implements IDhApiEvent<DhApiBeforeBufferRenderEvent.EventParam>
{
	/** Fired immediately before Distant Horizons starts rendering a buffer. */
	public abstract void beforeRender(DhApiEventParam<EventParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> input) { this.beforeRender(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam extends DhApiRenderParam implements IDhApiEventParam
	{
		/** 
		 * Measured in blocks.
		 * Should be applied to the model view matrix to move the buffer into its proper place. 
		 */
		public final DhApiVec3f modelPos;
		
		
		public EventParam(DhApiRenderParam parent, DhApiVec3f modelPos)
		{
			super(parent);
			this.modelPos = modelPos;
		}
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
					this, this.modelPos.copy()
			);
		}
	}
	
}