package net.fabricmc.loader.impl.game.minecraft;

import java.io.File;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class Hooks {
	public static final String INTERNAL_NAME = Hooks.class.getName().replace('.', '/');

	public static String appletMainClass;

	public static final String FABRIC = "fabric";
	public static final String VANILLA = "vanilla";

	public static String insertBranding(final String brand) {
		if (brand == null || brand.isEmpty()) {
			Log.warn(LogCategory.GAME_PROVIDER, "Null or empty branding found!", new IllegalStateException());
			return FABRIC;
		}

		return VANILLA.equals(brand) ? FABRIC : brand + ',' + FABRIC;
	}

	public static void startClient(File runDir, Object gameInstance) {
		if (runDir == null) {
			runDir = new File(".");
		}

		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
		loader.prepareModInit(runDir.toPath(), gameInstance);
		loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
		loader.invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
	}

	public static void startServer(File runDir, Object gameInstance) {
		if (runDir == null) {
			runDir = new File(".");
		}

		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
		loader.prepareModInit(runDir.toPath(), gameInstance);
		loader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
		loader.invokeEntrypoints("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
	}

	public static void setGameInstance(Object gameInstance) {
		FabricLoaderImpl.INSTANCE.setGameInstance(gameInstance);
	}
}
