package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.io.Closeable;
import java.util.List;

/**
 * Represents an entire world (aka server) and
 * contains every level in that world.
 */
public abstract class AbstractDhWorld implements IDhWorld, Closeable
{
	protected static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public final EWorldEnvironment environment;
	
	
	
	// constructor //
	
	protected AbstractDhWorld(EWorldEnvironment environment) { this.environment = environment; }
	
	
	
	// abstract methods //
	
	// removes the "throws IOException"
	@Override
	public abstract void close();
	
	
	
	// helper methods //
	
	/** 
	 * This method mutates a list so other lines can be easily added
	 * by overriding children.
	 */
	public void addDebugMenuStringsToList(List<String> messageList) 
	{
		EWorldEnvironment environment = this.environment;
		String levelCountStr = F3Screen.NUMBER_FORMAT.format(this.getLoadedLevelCount());
		
		String readOnlyStr = "";
		if (DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			readOnlyStr += " - ReadOnly";
		}
		
		String message = environment+" World with "+levelCountStr+" levels"+readOnlyStr;
		messageList.add(message);
	}
	
}
