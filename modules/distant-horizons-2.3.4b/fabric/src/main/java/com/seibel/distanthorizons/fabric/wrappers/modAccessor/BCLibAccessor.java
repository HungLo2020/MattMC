package com.seibel.distanthorizons.fabric.wrappers.modAccessor;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IBCLibAccessor;

/**
 * BCLib integration stub - BCLib is an optional dependency.
 * This stub prevents compilation errors when BCLib is not available.
 * Note: Fog compatibility tweaks are disabled when BCLib is not present.
 */
public class BCLibAccessor implements IBCLibAccessor
{
	@Override
	public String getModName() { return "BCLib"; }
	
	@Override
	public void setRenderCustomFog(boolean newValue)
	{
		// No-op stub - BCLib not available in this build
		// If BCLib is installed at runtime, this won't be called due to
		// the conditional check in FabricMain.initializeModCompat()
	}
	
}