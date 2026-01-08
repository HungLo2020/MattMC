package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface IClientLevelWrapper extends ILevelWrapper
{
	
	@Nullable
	IServerLevelWrapper tryGetServerSideWrapper();
	
	int getBlockColor(DhBlockPos pos, IBiomeWrapper biome, FullDataSourceV2 fullDataSource, IBlockStateWrapper blockState);
	/** @return -1 if there was a problem getting the color */
	int getDirtBlockColor();
	void clearBlockColorCache();
	
	Color getCloudColor(float tickDelta);
	
}
