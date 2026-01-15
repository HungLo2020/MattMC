package net.distanthorizons.core.generation.tasks;

import net.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;

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
	
	CompletableFuture<Boolean> shouldGenerateSplitChild(long pos);
	
}
