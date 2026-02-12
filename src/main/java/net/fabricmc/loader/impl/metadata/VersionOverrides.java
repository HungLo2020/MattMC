package net.fabricmc.loader.impl.metadata;

import java.util.Collection;
import java.util.Collections;

/**
 * Simplified version overrides for integrated mod approach.
 * No version conflicts in single integrated mod - overrides not needed.
 */
public final class VersionOverrides {
	public VersionOverrides() {
		// No-op - single integrated mod has no version conflicts to override
	}

	public void apply(LoaderModMetadata metadata) {
		// No-op - no overrides to apply
	}

	public Collection<String> getAffectedModIds() {
		return Collections.emptySet();
	}
}
