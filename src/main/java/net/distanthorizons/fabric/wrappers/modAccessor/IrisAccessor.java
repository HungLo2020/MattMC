package net.distanthorizons.fabric.wrappers.modAccessor;


import net.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;

import net.irisshaders.api.v0.IrisApi;

public class IrisAccessor implements IIrisAccessor
{
	@Override
	public String getModName() { return "iris"; }
	
	@Override
	public boolean isShaderPackInUse() { return IrisApi.getInstance().isShaderPackInUse(); }
	
	@Override
	public boolean isRenderingShadowPass() { return IrisApi.getInstance().isRenderingShadowPass(); }
	
}

