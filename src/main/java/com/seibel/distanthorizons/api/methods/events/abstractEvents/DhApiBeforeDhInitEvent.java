package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/**
 * Fired before Distant Horizons starts running its mod loader setup. <br>
 * IE this is called before Forge's initClient/initServer or Fabric's init method.
 *
 * @author James Seibel
 * @version 2023-6-23
 * @since API 1.0.0
 */
public abstract class DhApiBeforeDhInitEvent implements IDhApiEvent<Void>
{
	/** Fired before Distant Horizons starts its initial setup on Minecraft startup. */
	public abstract void beforeDistantHorizonsInit(DhApiEventParam<Void> input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<Void> input) { this.beforeDistantHorizonsInit(input); }
	
}