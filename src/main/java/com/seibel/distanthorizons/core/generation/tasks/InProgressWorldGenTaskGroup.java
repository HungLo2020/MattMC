package com.seibel.distanthorizons.core.generation.tasks;

import java.util.concurrent.CompletableFuture;

/**
 * @author Leetom
 * @version 2022-11-25
 */
public final class InProgressWorldGenTaskGroup
{
	public final WorldGenTaskGroup group;
	public CompletableFuture<Void> genFuture = null;
	
	
	public InProgressWorldGenTaskGroup(WorldGenTaskGroup group)
	{
		this.group = group;
	}
	
}
