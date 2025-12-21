package com.seibel.distanthorizons.common.wrappers.misc;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public interface IMixinServerPlayer
{
	@Nullable
	ServerLevel distantHorizons$getDimensionChangeDestination();
	
	
}
