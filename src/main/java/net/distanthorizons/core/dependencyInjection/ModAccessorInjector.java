package net.distanthorizons.core.dependencyInjection;

import net.distanthorizons.coreapi.DependencyInjection.DependencyInjector;
import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import net.distanthorizons.core.logging.DhLogger;

/**
 * This class takes care of dependency injection for mod accessors. (for mod compatibility
 * support).  <Br> <Br>
 *
 * If a IModAccessor returns null either that means the mod isn't loaded in the game
 * or an Accessor hasn't been implemented for the given Minecraft version.
 *
 * @author James Seibel
 * @author Leetom
 * @version 2022-7-18
 */
public class ModAccessorInjector extends DependencyInjector<IModAccessor>
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static final ModAccessorInjector INSTANCE = new ModAccessorInjector(IModAccessor.class);
	
	
	public ModAccessorInjector(Class<IModAccessor> newBindableInterface)
	{
		super(newBindableInterface, false);
	}
	
	
	/**
	 * Go to {@link DependencyInjector#bind(Class, IBindable)} DependencyHandler.bind()}
	 * for this method's javadocs.
	 */
	@Override
	public void bind(Class<? extends IModAccessor> interfaceClass, IModAccessor modAccessor)
			throws IllegalStateException, IllegalArgumentException
	{
		super.bind(interfaceClass, modAccessor);
		LOGGER.info("Registered mod compatibility accessor for: [" + modAccessor.getModName() + "].");
	}
	
	
}
