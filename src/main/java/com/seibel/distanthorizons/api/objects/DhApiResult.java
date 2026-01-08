package com.seibel.distanthorizons.api.objects;

/**
 * Allows for more descriptive non-critical failure states.
 *
 * @param <T> The payload type this result contains, can be Void if the result is just used to notify success/failure.
 * @author James Seibel
 * @version 2022-11-24
 * @since API 1.0.0
 */
public class DhApiResult<T>
{
	/** True if the action succeeded, false otherwise. */
	public final boolean success;
	
	/**
	 * If the action failed this will contain the reason as to why. <br>
	 * If the action was successful this will generally be the empty string.
	 */
	public final String message;
	
	/**
	 * Whatever object the API Method generated/returned. <br>
	 * Will be null/Void if this result is just used to notify success/failure.
	 */
	public final T payload;
	
	
	
	// these constructors are private because the create... methods below are easier to understand
	private DhApiResult(boolean success, String message) { this(success, message, null); }
	private DhApiResult(boolean success, String message, T payload)
	{
		this.success = success;
		// don't allow null messages, in the case of a null message return the empty string to prevent potential null pointers
		this.message = (message == null) ? "" : message;
		this.payload = payload;
	}
	
	
	
	public static <Pt> DhApiResult<Pt> createSuccess() { return new DhApiResult<>(true, ""); }
	public static <Pt> DhApiResult<Pt> createSuccess(Pt payload) { return new DhApiResult<Pt>(true, "", payload); }
	// There is no createSuccess(String message) method because it would be too easy to confuse with createSuccess(Pt payload) when returning null
	public static <Pt> DhApiResult<Pt> createSuccess(String message, Pt payload) { return new DhApiResult<Pt>(true, message, payload); }
	
	// there is no createFail() since all fail results should give a reason for their failure
	public static <Pt> DhApiResult<Pt> createFail(String message) { return new DhApiResult<>(false, message); }
	public static <Pt> DhApiResult<Pt> createFail(String message, Pt payload) { return new DhApiResult<Pt>(false, message, payload); }
	
}
