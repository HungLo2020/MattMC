package net.distanthorizons.core.generation.tasks;

import net.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Consumer;

/**
 * @author Leetom
 * @version 2022-11-25
 */
@Deprecated // TODO look into how these are used and if they should continue to be used
public final class WorldGenTaskGroup
{
	public final long pos;
	public byte dataDetail;
	/** Only accessed by the generator polling thread */
	public final LinkedList<WorldGenTask> worldGenTasks = new LinkedList<>();
	
	
	
	public WorldGenTaskGroup(long pos, byte dataDetail)
	{
		this.pos = pos;
		this.dataDetail = dataDetail;
	}
	
	public void consumeDataSource(FullDataSourceV2 dataSource)
	{
		Iterator<WorldGenTask> tasks = this.worldGenTasks.iterator();
		while (tasks.hasNext())
		{
			WorldGenTask task = tasks.next();
			Consumer<FullDataSourceV2> dataSourceConsumer = task.taskTracker.getDataSourceConsumer();
			if (dataSourceConsumer == null)
			{
				tasks.remove();
				task.future.complete(WorldGenResult.CreateFail());
			}
			else
			{
				dataSourceConsumer.accept(dataSource);
			}
		}
	}
	
}
