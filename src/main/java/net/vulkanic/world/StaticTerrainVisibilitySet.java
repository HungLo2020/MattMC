package net.vulkanic.world;

import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Explicit frame-visibility replacement for persistent static-terrain assets.
 * This policy owns no GPU resource or renderer state: callers retain their
 * mesh resources and remove only instances omitted by the current semantic
 * visibility publication.
 */
final class StaticTerrainVisibilitySet {
	private StaticTerrainVisibilitySet() {
	}

	static int reconcile(Map<Long, ?> activeInstances, Set<Long> visibleMeshKeys) {
		if (activeInstances == null || visibleMeshKeys == null) {
			throw new IllegalArgumentException("static terrain visibility reconciliation requires non-null inputs");
		}
		int activeBefore = activeInstances.size();
		activeInstances.keySet().removeIf(meshKey -> !visibleMeshKeys.contains(meshKey));
		return activeBefore - activeInstances.size();
	}

	static int reconcile(Long2ObjectMap<?> activeInstances, LongSet visibleMeshKeys) {
		if (activeInstances == null || visibleMeshKeys == null) {
			throw new IllegalArgumentException("static terrain visibility reconciliation requires non-null inputs");
		}
		int activeBefore = activeInstances.size();
		var keys = activeInstances.keySet().iterator();
		while (keys.hasNext()) {
			if (!visibleMeshKeys.contains(keys.nextLong())) {
				keys.remove();
			}
		}
		return activeBefore - activeInstances.size();
	}

	/** Replacement and lookup retain admission order; new entries evict the eldest. */
	static <T> void rememberBounded(
		Long2ObjectLinkedOpenHashMap<T> activeInstances, long meshKey, T instance, int limit
	) {
		activeInstances.put(meshKey, instance);
		while (activeInstances.size() > limit) {
			activeInstances.removeFirst();
		}
	}

	static boolean isAcceptedGeneration(
		long requestedGeneration,
		Long uploadedGeneration,
		Long registeredGeneration,
		Long acknowledgedGeneration
	) {
		return uploadedGeneration != null
			&& uploadedGeneration.longValue() == requestedGeneration
			&& ((acknowledgedGeneration != null
					&& acknowledgedGeneration.longValue() == requestedGeneration)
				|| (registeredGeneration != null
					&& registeredGeneration.longValue() == requestedGeneration));
	}

	/** Records the frozen generation and rejects same-frame conflicts. */
	static void protectGeneration(
		it.unimi.dsi.fastutil.longs.Long2LongMap protectedGenerations,
		long meshKey,
		long meshGeneration
	) {
		long previous = protectedGenerations.getOrDefault(meshKey, meshGeneration);
		if (previous != meshGeneration) {
			throw new IllegalStateException("frozen world frame references multiple generations for mesh " + meshKey);
		}
		protectedGenerations.put(meshKey, meshGeneration);
	}

	/** A post-freeze upload may not replace a mesh referenced by that frame. */
	static boolean mayPublishGeneration(
		Map<Long, Long> protectedGenerations,
		long meshKey,
		long candidateGeneration
	) {
		Long protectedGeneration = protectedGenerations.get(meshKey);
		return protectedGeneration == null || protectedGeneration.longValue() == candidateGeneration;
	}
}
