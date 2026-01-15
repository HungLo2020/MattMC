package net.distanthorizons.core.generation.tasks;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

public class WorldGenResult
{
	/** true if terrain was generated */
	public final boolean success;
	/** the position that was generated, will be null if nothing was generated */
	public final long pos;
	/** if a position is too high detail for world generator to handle it, these futures are for its 4 children positions after being split up. */
	public final LinkedList<CompletableFuture<WorldGenResult>> childFutures = new LinkedList<>();
	
	
	public static WorldGenResult CreateSplit(Collection<CompletableFuture<WorldGenResult>> siblingFutures) { return new WorldGenResult(false, 0, siblingFutures); }
	public static WorldGenResult CreateFail() { return new WorldGenResult(false, 0, null); }
	public static WorldGenResult CreateSuccess(long pos) { return new WorldGenResult(true, pos, null); }
	private WorldGenResult(boolean success, long pos, Collection<CompletableFuture<WorldGenResult>> childFutures)
	{
		this.success = success;
		this.pos = pos;
		
		if (childFutures != null)
		{
			this.childFutures.addAll(childFutures);
		}
	}
	
	
}
