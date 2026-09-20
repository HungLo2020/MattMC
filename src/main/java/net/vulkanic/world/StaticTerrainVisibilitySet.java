package net.vulkanic.world;

import java.util.Map;
import java.util.Set;

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
