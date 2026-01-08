package net.fabricmc.loader;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import net.fabricmc.loader.impl.FabricLoaderImpl;

/**
 * The main class for mod loading operations.
 *
 * @deprecated Use {@link net.fabricmc.loader.api.FabricLoader}
 */
@Deprecated
public abstract class FabricLoader implements net.fabricmc.loader.api.FabricLoader {
	/**
	 * @deprecated Use {@link net.fabricmc.loader.api.FabricLoader#getInstance()} where possible,
	 * report missing areas as an issue.
	 */
	@Deprecated
	public static final FabricLoader INSTANCE = FabricLoaderImpl.InitHelper.get();

	@Override
	public abstract <T> List<T> getEntrypoints(String key, Class<T> type);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Collection<ModContainer> getModContainers() {
		return (Collection) getAllMods();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<ModContainer> getMods() {
		return (List) getAllMods();
	}
}
