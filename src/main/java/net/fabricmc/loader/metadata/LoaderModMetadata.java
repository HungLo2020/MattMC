package net.fabricmc.loader.metadata;

import java.util.Collection;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * @deprecated Use {@link ModMetadata} instead
 */
@Deprecated
public interface LoaderModMetadata extends ModMetadata {
	boolean loadsInEnvironment(EnvType type);
	List<? extends EntrypointMetadata> getEntrypoints(String type);
	Collection<String> getEntrypointKeys();
}
