package net.distanthorizons.core.network.exceptions;

/** Fired if the client attempts to queue more tasks than the server is willing to handle. */
public class RateLimitedException extends Exception
{
	public RateLimitedException(String message) { super(message); }
	
}
