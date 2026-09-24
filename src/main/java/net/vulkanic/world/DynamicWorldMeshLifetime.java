package net.vulkanic.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks frame-local mesh generations copied from animated models, items, and
 * projected overlays. These assets are immutable, but their content keys can
 * change every frame; keeping every historical key until a world reset would
 * eventually exhaust the bounded world-mesh registry.
 * Rollback marks use a frame-bounded undo log so each model submit does not
 * copy the entire live registry. Rare removals retain an ordered snapshot.
 */
final class DynamicWorldMeshLifetime {
	private static final long IDLE_FRAME_GRACE = 1L;
	private final Map<Long, Usage> usages = new LinkedHashMap<>();
	private final List<Change> changes = new ArrayList<>();
	private long checkpointFrame;

	Checkpoint checkpoint(long semanticFrame) {
		if (semanticFrame <= 0L) throw new IllegalArgumentException("dynamic mesh checkpoint frame must be positive");
		if (this.checkpointFrame != semanticFrame) {
			this.changes.clear();
			this.checkpointFrame = semanticFrame;
		}
		return new Checkpoint(semanticFrame, this.changes.size());
	}

	void discardCheckpointHistory() {
		this.changes.clear();
		this.checkpointFrame = 0L;
	}

	void rollbackTo(Checkpoint checkpoint) {
		if (checkpoint == null || checkpoint.frame() != this.checkpointFrame
			|| checkpoint.changeCount() < 0 || checkpoint.changeCount() > this.changes.size()) {
			throw new IllegalArgumentException("dynamic mesh checkpoint is outside the current semantic frame");
		}
		for (int index = this.changes.size() - 1; index >= checkpoint.changeCount(); index--) {
			Change change = this.changes.get(index);
			if (change.snapshot() != null) {
				this.usages.clear();
				this.usages.putAll(change.snapshot());
			} else if (change.previous() == null) {
				this.usages.remove(change.meshKey());
			} else {
				this.usages.put(change.meshKey(), change.previous());
			}
		}
		this.changes.subList(checkpoint.changeCount(), this.changes.size()).clear();
	}

	void observe(long meshKey, long meshGeneration, long semanticFrame) {
		if (meshKey == 0L || meshGeneration == 0L || semanticFrame <= 0L) {
			throw new IllegalArgumentException("dynamic mesh lifetime identity must be positive");
		}
		Usage next = new Usage(meshGeneration, semanticFrame);
		Usage previous = this.usages.get(meshKey);
		if (next.equals(previous)) return;
		if (this.checkpointFrame == semanticFrame) this.changes.add(new Change(meshKey, previous, null));
		this.usages.put(meshKey, next);
	}

	List<Retirement> retireBeforeFrame(long semanticFrame) {
		if (semanticFrame <= 0L) {
			throw new IllegalArgumentException("dynamic mesh lifetime frame must be positive");
		}
		if (this.checkpointFrame != 0L && this.checkpointFrame != semanticFrame) {
			this.discardCheckpointHistory();
		} else if (this.checkpointFrame == semanticFrame) {
			this.changes.add(new Change(0L, null, new LinkedHashMap<>(this.usages)));
		}
		long minimumRetainedFrame = Math.max(1L, semanticFrame - IDLE_FRAME_GRACE);
		List<Retirement> retirements = new ArrayList<>();
		this.usages.entrySet().removeIf(entry -> {
			if (entry.getValue().lastUsedFrame() >= minimumRetainedFrame) {
				return false;
			}
			retirements.add(new Retirement(entry.getKey(), entry.getValue().meshGeneration()));
			return true;
		});
		return retirements;
	}

	void forget(long meshKey) {
		if (this.checkpointFrame != 0L && this.usages.containsKey(meshKey)) {
			this.changes.add(new Change(0L, null, new LinkedHashMap<>(this.usages)));
		}
		this.usages.remove(meshKey);
	}

	int size() {
		return this.usages.size();
	}

	record Retirement(long meshKey, long meshGeneration) {}
	record Checkpoint(long frame, int changeCount) {}
	private record Change(long meshKey, Usage previous, Map<Long, Usage> snapshot) {}
	private record Usage(long meshGeneration, long lastUsedFrame) {}
}
