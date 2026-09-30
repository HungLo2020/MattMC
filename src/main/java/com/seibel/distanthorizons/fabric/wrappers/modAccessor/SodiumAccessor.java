package com.seibel.distanthorizons.fabric.wrappers.modAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import net.minecraft.client.Minecraft;



public class SodiumAccessor implements ISodiumAccessor
{
	//======================//
	// mod accessor methods //
	//======================//
	
	@Override
	public String getModName() { return "Sodium-Fabric"; }
	
	
	
	//================//
	// sodium methods //
	//================//
	
	/** An overwrite for a config in sodium 0.5 to fix their terrain from showing */
	@Override
	public void setFogOcclusion(boolean occlusionEnabled)
	{
		// in newer versions of Sodium this doesn't appear to be an issue so it can probably just be ignored
	}
	
}
