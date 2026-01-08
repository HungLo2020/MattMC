package com.seibel.distanthorizons.core.jar;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.wrapperInterfaces.config.LangWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;

public class JarDependencySetup
{
	public static void createInitialBindings()
	{
		SingletonInjector.INSTANCE.bind(ILangWrapper.class, LangWrapper.INSTANCE);
		LangWrapper.init();
	}
	
}
