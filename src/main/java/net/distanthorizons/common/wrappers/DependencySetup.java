package net.distanthorizons.common.wrappers;

import net.distanthorizons.common.wrappers.gui.ClassicConfigGUI;
import net.distanthorizons.common.wrappers.gui.LangWrapper;
import net.distanthorizons.common.wrappers.level.KeyedClientLevelManager;
import net.distanthorizons.common.wrappers.minecraft.MinecraftGLWrapper;
import net.distanthorizons.common.wrappers.minecraft.MinecraftServerWrapper;
import net.distanthorizons.core.level.IKeyedClientLevelManager;
import net.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import net.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import net.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper;
import net.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import net.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;

/**
 * Binds all necessary dependencies, so we
 * can access them in Core. <br>
 * This needs to be called before any Core classes
 * are loaded.
 *
 * @author James Seibel
 * @author Ran
 * @version 12-1-2021
 */
public class DependencySetup
{
	
	public static void createSharedBindings()
	{
		SingletonInjector.INSTANCE.bind(ILangWrapper.class, LangWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IVersionConstants.class, VersionConstants.INSTANCE);
		SingletonInjector.INSTANCE.bind(IWrapperFactory.class, WrapperFactory.INSTANCE);
		SingletonInjector.INSTANCE.bind(IKeyedClientLevelManager.class, KeyedClientLevelManager.INSTANCE);
	}
	
	public static void createServerBindings()
	{ SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, MinecraftServerWrapper.INSTANCE); }
	
	public static void createClientBindings()
	{
		SingletonInjector.INSTANCE.bind(IMinecraftClientWrapper.class, MinecraftClientWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, MinecraftClientWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class, MinecraftRenderWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftGLWrapper.class, MinecraftGLWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IConfigGui.class, ClassicConfigGUI.CONFIG_CORE_INTERFACE);
	}
	
}
