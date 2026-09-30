package com.seibel.distanthorizons.common.wrappers.misc;

import net.blaze3d.platform.NativeImage;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;
import net.minecraft.client.Minecraft;
import net.vulkanic.VulkanicAPI;

public class LightMapWrapper implements ILightMapWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private int textureId = 0;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LightMapWrapper() { }
	
	
	
	//==================//
	// lightmap syncing //
	//==================//
	
	public void uploadLightmap(NativeImage image)
	{
		throw new UnsupportedOperationException("setLightmapId should be used for MC versions after 1.21.5"); // TODO that MC version number is wrong, when did we actually start using setLightmapId()?
	}
	
	public void setLightmapId(int minecraftLightmapTetxureId)
	{
		// just use the MC texture ID
		this.textureId = minecraftLightmapTetxureId;
	}
	
	
	
	//==============//
	// lightmap use //
	//==============//
	
	@Override
	public void bind()
	{
		throw new IllegalStateException("Java Distant Horizons lightmap binding is unavailable while Rust owns whole-frame presentation");
	}
	
	@Override
	public void unbind()
	{
		throw new IllegalStateException("Java Distant Horizons lightmap unbinding is unavailable while Rust owns whole-frame presentation");
	}

}
