package net.minecraft.server.level;

import net.minecraft.world.level.TicketStorage;

/** Native-owned propagation with live Java source and holder effects. Each
 * query and ordered setter runs at the original graph's evaluation point;
 * ChunkMap's scheduling method applies holder changes through its native registry. */
class LoadingChunkTracker {
	private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;
	private final DistanceManager distanceManager;
	private final TicketStorage ticketStorage;
	private final TicketChunkDistance distance = new TicketChunkDistance(TicketChunkDistance.LOADING, MAX_LEVEL);
	private final TicketChunkDistance.Effects effects = new TicketChunkDistance.Effects() {
		public int level(long position) { return LoadingChunkTracker.this.getLevel(position); }
		public int source(long position) { return LoadingChunkTracker.this.getLevelFromSource(position); }
		public void set(long position, int level) { LoadingChunkTracker.this.setLevel(position, level); }
	};

	public LoadingChunkTracker(DistanceManager distanceManager, TicketStorage ticketStorage) {
		this.distanceManager = distanceManager;
		this.ticketStorage = ticketStorage;
		ticketStorage.setLoadingChunkUpdatedListener(this::update);
	}

	public void update(long l, int i, boolean bl) {
		this.distance.updateEffects(l, i, bl, this.effects);
	}

	protected int getLevelFromSource(long position) {
		return this.ticketStorage.getTicketLevelAt(position, false);
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
		return this.distance.runEffects(i, this.effects);
	}
}
