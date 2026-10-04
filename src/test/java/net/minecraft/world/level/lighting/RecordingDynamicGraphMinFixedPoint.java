package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.LongPredicate;
import net.minecraft.util.Mth;

public abstract class RecordingDynamicGraphMinFixedPoint {
	public static final long SOURCE = Long.MAX_VALUE;
	private static final int NO_COMPUTED_LEVEL = 255;
	protected final int levelCount;
	private final QueueTranscript.RecordingQueue priorityQueue;
	private final Long2ByteMap computedLevels;
	private volatile boolean hasWork;

	protected RecordingDynamicGraphMinFixedPoint(int i, int j, int k) {
		if (i >= 254) {
			throw new IllegalArgumentException("Level count must be < 254.");
		} else {
			this.levelCount = i;
			this.priorityQueue = new QueueTranscript.RecordingQueue(i, j);
			this.computedLevels = new Long2ByteOpenHashMap(k, 0.5F) {
				@Override
				protected void rehash(int i) {
					if (i > k) {
						super.rehash(i);
					}
				}
			};
			this.computedLevels.defaultReturnValue((byte)-1);
		}
	}

	protected void removeFromQueue(long l) {
		boolean acquired = this.priorityQueue.beginAccess();
		try {
			int i = this.computedLevels.remove(l) & 255;
			if (i != 255) {
				int j = this.getLevel(l);
				this.priorityQueue.cancelComputed(l, j, i);
				this.hasWork = !this.priorityQueue.isEmpty();
			}
		} finally {
			this.priorityQueue.endAccess(acquired);
		}
	}

	public void removeIf(LongPredicate longPredicate) {
		LongList longList = new LongArrayList();
		this.computedLevels.keySet().forEach(l -> {
			if (longPredicate.test(l)) {
				longList.add(l);
			}
		});
		longList.forEach(this::removeFromQueue);
	}

	protected void checkNode(long l) {
		this.checkEdge(l, l, this.levelCount - 1, false);
	}

	protected void checkEdge(long l, long m, int i, boolean bl) {
		boolean acquired = this.priorityQueue.beginAccess();
		try {
			this.checkEdge(l, m, i, this.getLevel(m), this.computedLevels.get(m) & 255, bl);
			this.hasWork = !this.priorityQueue.isEmpty();
		} finally {
			this.priorityQueue.endAccess(acquired);
		}
	}

	private void checkEdge(long l, long m, int i, int j, int k, boolean bl) {
		if (!this.isSource(m)) {
			i = Mth.clamp(i, 0, this.levelCount - 1);
			j = Mth.clamp(j, 0, this.levelCount - 1);
			boolean bl2 = k == 255;
			if (bl2) {
				k = j;
			}

			int n;
			if (bl) {
				n = Math.min(k, i);
			} else {
				n = Mth.clamp(this.getComputedLevel(m, l, i), 0, this.levelCount - 1);
			}

			if (j != n) {
				this.priorityQueue.reschedule(m, j, bl2 ? NO_COMPUTED_LEVEL : k, n);
				// Queue work is immediate; avoid rewriting an already identical pending value.
				// Compare unsigned bytes to the clamped int: absence (255) never equals n.
				if (k != n || (this.computedLevels.get(m) & 255) != n) {
					this.computedLevels.put(m, (byte)n);
				}
			} else if (!bl2) {
				this.priorityQueue.cancelComputed(m, j, k);
				this.computedLevels.remove(m);
			}
		}
	}

	protected final void checkNeighbor(long l, long m, int i, boolean bl) {
		int j = this.computedLevels.get(m) & 255;
		int k = Mth.clamp(this.computeLevelFromNeighbor(l, m, i), 0, this.levelCount - 1);
		if (bl) {
			this.checkEdge(l, m, k, this.getLevel(m), j, bl);
		} else {
			boolean bl2 = j == 255;
			int n;
			if (bl2) {
				n = Mth.clamp(this.getLevel(m), 0, this.levelCount - 1);
			} else {
				n = j;
			}

			if (k == n) {
				this.checkEdge(l, m, this.levelCount - 1, bl2 ? n : this.getLevel(m), j, bl);
			}
		}
	}

	protected final boolean hasWork() {
		return this.hasWork;
	}

	protected final int runUpdates(int i) {
		boolean acquired = this.priorityQueue.beginAccess();
		try {
			if (this.priorityQueue.isEmpty()) {
				return i;
			} else {
				while (!this.priorityQueue.isEmpty() && i > 0) {
					i--;
					long l = this.priorityQueue.removeFirstLong();
					int j = Mth.clamp(this.getLevel(l), 0, this.levelCount - 1);
					int k = this.computedLevels.remove(l) & 255;
					if (k < j) {
						this.setLevel(l, k);
						this.checkNeighborsAfterUpdate(l, k, true);
					} else if (k > j) {
						this.setLevel(l, this.levelCount - 1);
						if (k != this.levelCount - 1) {
							this.priorityQueue.enqueueComputed(l, this.levelCount - 1, k);
							this.computedLevels.put(l, (byte)k);
						}

						this.checkNeighborsAfterUpdate(l, j, false);
					}
				}

				this.hasWork = !this.priorityQueue.isEmpty();
				return i;
			}
		} finally {
			this.priorityQueue.endAccess(acquired);
		}
	}

	public int getQueueSize() {
		return this.computedLevels.size();
	}

	protected boolean isSource(long l) {
		return l == Long.MAX_VALUE;
	}

	protected abstract int getComputedLevel(long l, long m, int i);

	protected abstract void checkNeighborsAfterUpdate(long l, int i, boolean bl);

	protected abstract int getLevel(long l);

	protected abstract void setLevel(long l, int i);

	protected abstract int computeLevelFromNeighbor(long l, long m, int i);
}
