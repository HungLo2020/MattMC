package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import net.distanthorizons.api.methods.events.interfaces.IDhApiCancelableEvent;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Called before Distant Horizons starts rendering a generic object. <br>
 * Canceling this event will prevent the triggering {@link IDhApiRenderableBoxGroup} from rendering this frame.
 *
 * @author James Seibel
 * @version 2024-7-11
 * @since API 3.0.0
 */
public abstract class DhApiBeforeGenericObjectRenderEvent implements IDhApiCancelableEvent<DhApiBeforeGenericObjectRenderEvent.EventParam>
{
	/** Fired before Distant Horizons renders a generic object. */
	public abstract void beforeRender(DhApiCancelableEventParam<EventParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiCancelableEventParam<EventParam> input) { this.beforeRender(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam extends DhApiRenderParam implements IDhApiEventParam
	{
		public final long boxGroupId;
		public final String resourceLocationNamespace;
		public final String resourceLocationPath;
		
		
		public EventParam(
				DhApiRenderParam renderParam,
				IDhApiRenderableBoxGroup boxGroup
			) 
		{
			super(renderParam); 
			
			this.boxGroupId = boxGroup.getId();
			this.resourceLocationNamespace = boxGroup.getResourceLocationNamespace();
			this.resourceLocationPath = boxGroup.getResourceLocationPath();
		}
		public EventParam(
				DhApiRenderParam renderParam,
				long boxGroupId, String resourceLocationNamespace, String resourceLocationPath
			)
		{
			super(renderParam);
			
			this.boxGroupId = boxGroupId;
			this.resourceLocationNamespace = resourceLocationNamespace;
			this.resourceLocationPath = resourceLocationPath;
		}
		
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
				this, 
				this.boxGroupId, this.resourceLocationNamespace, this.resourceLocationPath
			);
		}
	}
	
}