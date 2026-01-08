package net.fabricmc.loader.launch.server;

/**
 * @deprecated Use {@link net.fabricmc.loader.impl.launch.server.FabricServerLauncher} instead
 */
@Deprecated
public final class FabricServerLauncher {
	public static void main(String[] args) {
		net.fabricmc.loader.impl.launch.server.FabricServerLauncher.main(args);
	}
}
