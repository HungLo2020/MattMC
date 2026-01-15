package net.distanthorizons.fabric.wrappers.modAccessor;

import net.distanthorizons.core.wrapperInterfaces.modAccessor.IBCLibAccessor;

public class BCLibAccessor implements IBCLibAccessor
{
	@Override
	public String getModName() { return "BCLib"; }
	
	public void setRenderCustomFog(boolean newValue)
	{
		// only some MC versions have BCLib and require this fix
	}
	
}