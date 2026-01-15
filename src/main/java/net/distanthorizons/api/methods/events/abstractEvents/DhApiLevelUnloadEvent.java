package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import net.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/**
 * Called after Distant Horizons has finished unloading a level.
 *
 * @author James Seibel
 * @version 2023-6-23
 * @since API 1.0.0
 */
public abstract class DhApiLevelUnloadEvent implements IDhApiEvent<DhApiLevelUnloadEvent.EventParam>
{
	/** Fired before Distant Horizons unloads a level. */
	public abstract void onLevelUnload(DhApiEventParam<EventParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> input) { this.onLevelUnload(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam implements IDhApiEventParam
	{
		/** The recently unloaded level. */
		public final IDhApiLevelWrapper levelWrapper;
		
		public EventParam(IDhApiLevelWrapper newLevelWrapper) { this.levelWrapper = newLevelWrapper; }
		
		
		@Override
		public EventParam copy() { return new EventParam(this.levelWrapper); }
	}
	
}