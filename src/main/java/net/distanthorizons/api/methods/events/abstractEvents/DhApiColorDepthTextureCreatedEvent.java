package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam;

/**
 * Called before Distant Horizons (re)creates 
 * the color and depth textures it renders to. <br>
 * 
 * @author James Seibel
 * @version 2024-3-2
 * @since API 2.0.0
 * @deprecated Replaced by {@link DhApiBeforeColorDepthTextureCreatedEvent} since this event's name isn't obvious when it fires.
 */
@Deprecated // internal notes: this method must be kept around due to Iris using it and we don't want to break old Iris support
public abstract class DhApiColorDepthTextureCreatedEvent implements IDhApiEvent<DhApiColorDepthTextureCreatedEvent.EventParam>
{
	/** Fired before Distant Horizons creates. */
	public abstract void onResize(DhApiEventParam<EventParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> event) { this.onResize(event); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam implements IDhApiEventParam
	{
		/** Measured in pixels */
		public final int previousWidth;
		/** Measured in pixels */
		public final int previousHeight;
		
		/** Measured in pixels */
		public final int newWidth;
		/** Measured in pixels */
		public final int newHeight;
		
		
		public EventParam(
				int previousWidth, int previousHeight,
				int newWidth, int newHeight)
		{
			this.previousWidth = previousWidth;
			this.previousHeight = previousHeight;
			
			this.newWidth = newWidth;
			this.newHeight = newHeight;
			
		}
		public EventParam(DhApiTextureCreatedParam textureCreatedParam)
		{
			this.previousWidth = textureCreatedParam.previousWidth;
			this.previousHeight = textureCreatedParam.previousHeight;
			
			this.newWidth = textureCreatedParam.newWidth;
			this.newHeight = textureCreatedParam.newHeight;
			
		}
		
		
		@Override
		public EventParam copy()
		{
			return new EventParam(
					this.previousWidth, this.previousHeight,
					this.newWidth, this.newHeight
			);
		}
	}
	
}