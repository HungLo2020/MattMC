package com.seibel.distanthorizons.core.network.exceptions;

/** Fired if the client attempts to request an LOD out of allowed range. */
public class RequestOutOfRangeException extends Exception
{
	public RequestOutOfRangeException(String message) { super(message); }
	
}
