package net.distanthorizons.api.methods.events.abstractEvents;

import net.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import net.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/**
 * Called after Distant Horizons has finished unloading a world.
 *
 * @see IDhApiWorldProxy
 *
 * @author James Seibel
 * @version 2024-12-7
 * @since API 4.0.0
 */
public abstract class DhApiWorldUnloadEvent implements IDhApiEvent<DhApiWorldUnloadEvent.EventParam>
{
	/** Fired before Distant Horizons unloads a world. */
	public abstract void onWorldUnload(DhApiEventParam<EventParam> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<EventParam> input) { this.onWorldUnload(input); }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam implements IDhApiEventParam
	{
		public EventParam() { }
		
		
		@Override
		public DhApiWorldLoadEvent.EventParam copy() { return new DhApiWorldLoadEvent.EventParam(); }
	}
	
}