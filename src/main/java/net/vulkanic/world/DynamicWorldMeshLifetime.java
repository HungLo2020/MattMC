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
 */
final class DynamicWorldMeshLifetime {
	private static final long IDLE_FRAME_GRACE = 1L;
	private final Map<Long, Usage> usages = new LinkedHashMap<>();

	void observe(long meshKey, long meshGeneration, long semanticFrame) {
		if (meshKey == 0L || meshGeneration == 0L || semanticFrame <= 0L) {
			throw new IllegalArgumentException("dynamic mesh lifetime identity must be positive");
		}
		this.usages.put(meshKey, new Usage(meshGeneration, semanticFrame));
	}

	List<Retirement> retireBeforeFrame(long semanticFrame) {
		if (semanticFrame <= 0L) {
			throw new IllegalArgumentException("dynamic mesh lifetime frame must be positive");
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
		this.usages.remove(meshKey);
	}

	DynamicWorldMeshLifetime copy() {
		DynamicWorldMeshLifetime snapshot = new DynamicWorldMeshLifetime();
		snapshot.usages.putAll(this.usages);
		return snapshot;
	}

	void restore(DynamicWorldMeshLifetime snapshot) {
		if (snapshot == null) {
			throw new IllegalArgumentException("dynamic mesh lifetime snapshot is null");
		}
		this.usages.clear();
		this.usages.putAll(snapshot.usages);
	}

	int size() {
		return this.usages.size();
	}

	record Retirement(long meshKey, long meshGeneration) {}
	private record Usage(long meshGeneration, long lastUsedFrame) {}
}
