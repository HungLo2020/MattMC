package com.seibel.distanthorizons.fabric.wrappers.modAccessor;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

public class ModChecker implements IModChecker
{
	public static final ModChecker INSTANCE = new ModChecker();
	
	@Override
	public boolean isModLoaded(String modid)
	{
		return FabricLoader.getInstance().isModLoaded(modid);
	}
	
	@Override
	public File modLocation(String modid)
	{
		return new File(FabricLoader.getInstance().getModContainer(modid).get().getOrigin().getPaths().get(0).toUri());
	}
	
}
