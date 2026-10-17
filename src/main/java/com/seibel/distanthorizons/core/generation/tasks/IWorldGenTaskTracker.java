package com.seibel.distanthorizons.core.generation.tasks;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author Leetom
 * @version 2022-11-25
 */
public interface IWorldGenTaskTracker
{
	@Nullable
	Consumer<FullDataSourceV2> getDataSourceConsumer();
	
	/**
	 * Optionally consumes a generated chunk's native columns directly, without a
	 * producer {@link FullDataSourceV2}; returns false to receive that data source
	 * through {@link #getDataSourceConsumer()} instead. Null (the default) always does.
	 */
	@Nullable
	default java.util.function.Predicate<net.minecraft.world.level.chunk.NativeDhColumns> getNativeColumnsConsumer() { return null; }
	
	CompletableFuture<Boolean> shouldGenerateSplitChild(long pos);
	
}
