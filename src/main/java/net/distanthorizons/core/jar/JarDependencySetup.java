package net.distanthorizons.core.jar;

import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.jar.wrapperInterfaces.config.LangWrapper;
import net.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;

public class JarDependencySetup
{
	public static void createInitialBindings()
	{
		SingletonInjector.INSTANCE.bind(ILangWrapper.class, LangWrapper.INSTANCE);
		LangWrapper.init();
	}
	
}
