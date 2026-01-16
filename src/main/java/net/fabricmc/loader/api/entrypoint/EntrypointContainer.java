package net.fabricmc.loader.api.entrypoint;

import net.fabricmc.loader.api.ModContainer;

/**
 * A container holding both an entrypoint instance and the {@link ModContainer} which has provided the entrypoint.
 *
 * @param <T> The type of the entrypoint
 * @see net.fabricmc.loader.api.FabricLoader#getEntrypointContainers(String, Class)
 */
public interface EntrypointContainer<T> {
	/**
	 * Returns the entrypoint instance. It will be constructed the first time you call this method.
	 */
	T getEntrypoint();

	/**
	 * Returns the mod that provided this entrypoint.
	 */
	ModContainer getProvider();

	/**
	 * Returns a string representation of the entrypoint's definition.
	 */
	default String getDefinition() {
		return "";
	}
}
