package com.seibel.distanthorizons.core.util.objects;

import com.seibel.distanthorizons.core.util.ExceptionUtil;

import java.util.concurrent.CompletionException;

public class UncheckedInterruptedException extends RuntimeException
{
	public UncheckedInterruptedException(String message)
	{
		super(message);
	}
	public UncheckedInterruptedException(Throwable cause)
	{
		super(cause);
	}
	public UncheckedInterruptedException(String message, Throwable cause)
	{
		super(message, cause);
	}
	public UncheckedInterruptedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	public UncheckedInterruptedException()
	{
		super();
	}
	
	
	public static UncheckedInterruptedException convert(InterruptedException e)
	{
		return new UncheckedInterruptedException(e);
	}
	
	public static boolean isInterrupt(Throwable t)
	{
		Throwable unwrapped = ExceptionUtil.ensureUnwrap(t);
		return unwrapped instanceof InterruptedException || unwrapped instanceof UncheckedInterruptedException;
	}
	
}
