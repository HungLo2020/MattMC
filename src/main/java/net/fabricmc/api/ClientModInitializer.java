package net.fabricmc.api;

/**
 * A mod initializer ran only on {@link EnvType#CLIENT}.
 *
 * <p>This entrypoint is suitable for setting up client-specific logic, such as rendering
 * or integrated server tweaks.</p>
 *
 * <p>In {@code fabric.mod.json}, the entrypoint is defined with {@code client} key.</p>
 *
 * @see ModInitializer
 * @see DedicatedServerModInitializer
 * @see net.fabricmc.loader.api.FabricLoader#getEntrypointContainers(String, Class)
 */
@FunctionalInterface
public interface ClientModInitializer {
	/**
	 * Runs the mod initializer on the client environment.
	 */
	void onInitializeClient();
}
