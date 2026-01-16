package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.LodQuadTree;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Used to track what full data sources the system currently
 * wants but doesn't have. <br>
 * IE, what sections should be generated via the world generator. <br><br>
 * 
 * Note: <br>
 * This won't contain every position that needs to be retrieved 
 * (due to causing issues at extreme render distances).
 * TODO does that mean this object isn't necessary or 
 *      should just be renamed since it isn't the full queue <br><br>
 * 
 * Use by both world gen and server networking.
 * 
 * @see LodQuadTree
 */
public interface IFullDataSourceRetrievalQueue extends Closeable
{
	//=========//
	// getters //
	//=========//
	
	/** 
	 * The largest numerical detail level. <br>
	 * Detail level is absolute, not section;
	 * IE 0 = Block, 1 = 2x2 blocks, etc.
	 */
	byte lowestDataDetail();
	/** 
	 * The smallest numerical detail level. <br>
	 * Detail level is absolute, not section;
	 * IE 0 = Block, 1 = 2x2 blocks, etc.
	 */
	byte highestDataDetail();
	
	
	
	//=======//
	// setup //
	//=======//
	
	/** 
	 * Starts the retrieval process if not already running,
	 * and if running updates the target position.
	 * 
	 * @param targetPos the position that retrieval should be centered around, 
	 *                  generally this will be the player's position. 
	 * */
	void startAndSetTargetPos(DhBlockPos2D targetPos);
	
	
	
	//===============//
	// task handling //
	//===============//
	
	/** 
	 * Generally the retrieval queue should be fairly small, so its faster to iterate over the existing list
	 * and check if each one is valid vs dumbly attempting to remove every position that just went out of range.
	 */
	void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf);
	
	CompletableFuture<WorldGenResult> submitRetrievalTask(long pos, byte requiredDataDetail, IWorldGenTaskTracker tracker);
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	/** Can be used to let any lingering generation requests finish before fully shutting down the system */
	CompletableFuture<Void> startClosingAsync(boolean cancelCurrentGeneration, boolean alsoInterruptRunning);
	
	@Override
	void close();
	
	
	
	//===============//
	// debug display //
	//===============//
	
	int getWaitingTaskCount();
	int getInProgressTaskCount();
	
	/** returns how many chunks are currently queued for retrieval */
	int getQueuedChunkCount();
	
	/** used for rendering to the F3 menu */
	int getEstimatedRemainingTaskCount();
	void setEstimatedRemainingTaskCount(int newEstimate);
	
	/** used for displaying a progress update to the user */
	int getRetrievalEstimatedRemainingChunkCount();
	void setRetrievalEstimatedRemainingChunkCount(int newEstimate);

	void addDebugMenuStringsToList(List<String> messageList);
	
	/** Can be used to determine roughly how fast the world generator is running. */
	RollingAverage getRollingAverageChunkGenTimeInMs();
	
	
	
}
