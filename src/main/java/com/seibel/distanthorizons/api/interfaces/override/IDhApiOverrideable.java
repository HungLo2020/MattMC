package com.seibel.distanthorizons.api.interfaces.override;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;

/**
 * Implemented by all DhApi objects that can be overridden.
 *
 * @author James Seibel
 * @version 2022-9-5
 * @since API 1.0.0
 */
public interface IDhApiOverrideable extends IBindable
{
	/**
	 * Higher (larger numerical) priorities override lower (smaller numerical) priorities. <br>
	 * For most developers this can be left at the default.
	 * 
	 * @return The priority of this interface, the lowest legal value is {@link IOverrideInjector#MIN_NON_CORE_OVERRIDE_PRIORITY}.
	 */
	default int getPriority() { return IOverrideInjector.DEFAULT_NON_CORE_OVERRIDE_PRIORITY; }
	
}
