package net.fabricmc.loader.impl.metadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;

/**
 * Internal variant of the ModMetadata interface.
 */
@SuppressWarnings("deprecation")
public interface LoaderModMetadata extends net.fabricmc.loader.metadata.LoaderModMetadata {
	int getSchemaVersion();

	default String getOldStyleLanguageAdapter() {
		return "net.fabricmc.loader.language.JavaLanguageAdapter";
	}

	Map<String, String> getLanguageAdapterDefinitions();
	Collection<NestedJarEntry> getJars();
	Collection<String> getMixinConfigs(EnvType type);
	/* @Nullable */
	// NOTE: getClassTweaker() removed - access modifications already applied in source
	@Override
	boolean loadsInEnvironment(EnvType type);

	Collection<String> getOldInitializers();
	@Override
	List<EntrypointMetadata> getEntrypoints(String type);
	@Override
	Collection<String> getEntrypointKeys();

	void emitFormatWarnings();

	void setVersion(Version version);
	void setDependencies(Collection<ModDependency> dependencies);
}
