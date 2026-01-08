package com.seibel.distanthorizons.core.dependencyInjection;

import com.seibel.distanthorizons.coreapi.DependencyInjection.DependencyInjector;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * This class takes care of dependency injection
 * for singletons.
 *
 * @author James Seibel
 * @version 2022-7-16
 */
public class SingletonInjector extends DependencyInjector<IBindable>
{
	public static final SingletonInjector INSTANCE = new SingletonInjector(IBindable.class);
	
	
	public SingletonInjector(Class<IBindable> newBindableInterface) { super(newBindableInterface, false); }
	
}
