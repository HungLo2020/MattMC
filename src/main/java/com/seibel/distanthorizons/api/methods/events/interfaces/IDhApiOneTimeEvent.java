package com.seibel.distanthorizons.api.methods.events.interfaces;

/**
 * If a {@link IDhApiEvent} implements this interface then the event will only ever be fired once. <Br>
 * An example of this would be initial setup methods, DH won't run its initial setup more than once. <br><br>
 *
 * If a handler is bound to a one time event after the event has been fired, the handler will immediately fire.
 *
 * @since API 1.0.0
 */
public interface IDhApiOneTimeEvent<T> extends IDhApiEvent<T>
{
	
}
