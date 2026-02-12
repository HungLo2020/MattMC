package net.fabricmc.loader.impl.metadata;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Simplified dependency overrides for integrated mod approach.
 * No dependency conflicts in single integrated mod - overrides not needed.
 */
public final class DependencyOverrides {
public DependencyOverrides(Path configDir) {
// No-op - single integrated mod has no dependency conflicts to override
}

public void apply(LoaderModMetadata metadata) {
// No-op - no overrides to apply
}

public Collection<String> getAffectedModIds() {
return Collections.emptySet();
}
}
