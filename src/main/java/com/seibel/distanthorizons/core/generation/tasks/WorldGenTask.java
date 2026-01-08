package com.seibel.distanthorizons.core.generation.tasks;

import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;

import java.util.concurrent.CompletableFuture;

/**
 * @author Leetom
 * @version 2022-11-25
 */
public final class WorldGenTask
{
	public final long pos;
	public final byte dataDetailLevel;
	public final IWorldGenTaskTracker taskTracker;
	public final CompletableFuture<WorldGenResult> future;
	
	
	
	public WorldGenTask(long pos, byte dataDetail, IWorldGenTaskTracker taskTracker, CompletableFuture<WorldGenResult> future)
	{
		this.dataDetailLevel = dataDetail;
		this.pos = pos;
		this.taskTracker = taskTracker;
		this.future = future;
	}
	
}
