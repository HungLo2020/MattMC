package com.seibel.distanthorizons.api.methods.events.sharedParameterObjects;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;

/** 
 * Wraps the event parameter to allow for additional control over the event 
 *
 * @since API 1.0.0
 */
public class DhApiEventParam<T>
{
	/** Depending on the {@link IDhApiEvent} this can be null. */
	public final T value;
	
	
	public DhApiEventParam(T value) { this.value = value; }
	
}
