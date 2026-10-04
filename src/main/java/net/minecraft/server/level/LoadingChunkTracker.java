package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.world.level.TicketStorage;

/** Java side of the native-owned loading ticket distance graph. Rust owns
 * propagation, the graph's level map and a mirror of each chunk's lowest
 * loading ticket level; each run replays the original ordered {@code setLevel}
 * calls into chunk scheduling. After any {@code setLevel(pos, i)} the holder
 * state reads back {@code i}, so Rust's level map equals {@link #getLevel}
 * provided the tracker exists before any holder, as DistanceManager creates it. */
class LoadingChunkTracker {
	private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;
	private final DistanceManager distanceManager;
	private final TicketStorage ticketStorage;
	private final TicketChunkDistance distance = new TicketChunkDistance(TicketChunkDistance.LOADING, MAX_LEVEL);
	private final PlayerChunkDistances.LevelSink levelSink = this::setLevel;

	public LoadingChunkTracker(DistanceManager distanceManager, TicketStorage ticketStorage) {
		this.distanceManager = distanceManager;
		this.ticketStorage = ticketStorage;
		// The original graph read ticket levels lazily, including tickets added
		// before this listener; seed those before any notification arrives.
		for (LongIterator iterator = ticketStorage.activeTicketChunks(); iterator.hasNext(); ) {
			long l = iterator.nextLong();
			this.distance.seed(l, ticketStorage.getTicketLevelAt(l, false));
		}
		ticketStorage.setLoadingChunkUpdatedListener(this::update);
	}

	public void update(long l, int i, boolean bl) {
		this.distance.update(l, this.ticketStorage.getTicketLevelAt(l, false), i, bl);
	}

	protected int getLevel(long l) {
		if (!this.distanceManager.isChunkToRemove(l)) {
			ChunkHolder chunkHolder = this.distanceManager.getChunk(l);
			if (chunkHolder != null) {
				return chunkHolder.getTicketLevel();
			}
		}

		return MAX_LEVEL;
	}

	protected void setLevel(long l, int i) {
		ChunkHolder chunkHolder = this.distanceManager.getChunk(l);
		int j = chunkHolder == null ? MAX_LEVEL : chunkHolder.getTicketLevel();
		if (j != i) {
			chunkHolder = this.distanceManager.updateChunkScheduling(l, i, chunkHolder, j);
			if (chunkHolder != null) {
				this.distanceManager.chunksToUpdateFutures.add(chunkHolder);
			}
		}
	}

	public int runDistanceUpdates(int i) {
		return this.distance.runUpdates(i, this.levelSink);
	}
}
