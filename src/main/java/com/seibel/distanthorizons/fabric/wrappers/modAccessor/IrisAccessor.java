package com.seibel.distanthorizons.fabric.wrappers.modAccessor;


import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;

import net.iris.api.v0.IrisApi;

public class IrisAccessor implements IIrisAccessor
{
	@Override
	public String getModName() { return "iris"; }
	
	@Override
	public boolean isShaderPackInUse() { return IrisApi.getInstance().isShaderPackInUse(); }
	
	@Override
	public boolean isRenderingShadowPass() { return IrisApi.getInstance().isRenderingShadowPass(); }
	
}

