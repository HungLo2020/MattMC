package com.seibel.distanthorizons.api.methods.events.sharedParameterObjects;

/** 
 * Extension of {@link DhApiEventParam} that allows the event to be canceled. 
 * 
 * @since API 1.0.0
 */
public class DhApiCancelableEventParam<T> extends DhApiEventParam<T>
{
	public DhApiCancelableEventParam(T value) { super(value); }
	
	private boolean eventCanceled = false;
	/** Prevents the DH event from completing after all bound event handlers have been fired. */
	public void cancelEvent() { this.eventCanceled = true; }
	/** @return if this DH event has been canceled, either by this event handler or a previous one. */
	public boolean isEventCanceled() { return this.eventCanceled; }
	
}
