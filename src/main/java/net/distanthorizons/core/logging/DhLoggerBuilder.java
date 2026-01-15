package net.distanthorizons.core.logging;

import net.distanthorizons.api.enums.config.EDhApiLoggerLevel;
import net.distanthorizons.core.config.types.ConfigEntry;
import net.distanthorizons.coreapi.ModInfo;
import org.jetbrains.annotations.Nullable;

/**
 * @see DhLogger
 */
public class DhLoggerBuilder
{
	private String name;
	private @Nullable ConfigEntry<EDhApiLoggerLevel> chatLevelConfig;
	private @Nullable ConfigEntry<EDhApiLoggerLevel> fileLevelConfig;
	private int maxLogPerSec = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhLoggerBuilder() { this.name = ModInfo.NAME + "-" + getCallingClassName(); }
	/** @return "??" if no name could be found */
	private static String getCallingClassName()
	{
		StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
		String callerClassName = "??";
		for (int i = 1; i < stElements.length; i++)
		{
			StackTraceElement ste = stElements[i];
			if (!ste.getClassName().equals(DhLoggerBuilder.class.getName())
					&& ste.getClassName().indexOf("java.lang.Thread") != 0)
			{
				callerClassName = ste.getClassName();
				break;
			}
		}
		
		return callerClassName;
	}
	
	
	
	//===========//
	// variables //
	//===========//
	
	public DhLoggerBuilder name(String name)
	{
		this.name = name;
		return this;
	}
	
	public DhLoggerBuilder chatLevelConfig(ConfigEntry<EDhApiLoggerLevel> chatLevelConfig)
	{
		this.chatLevelConfig = chatLevelConfig;
		return this;
	}
	
	public DhLoggerBuilder fileLevelConfig(ConfigEntry<EDhApiLoggerLevel> fileLevelConfig)
	{
		this.fileLevelConfig = fileLevelConfig;
		return this;
	}
	
	public DhLoggerBuilder maxCountPerSecond(int maxLogPerSec)
	{
		this.maxLogPerSec = maxLogPerSec;
		return this;
	}
	
	
	
	//=======//
	// build //
	//=======//
	
	public DhLogger build()
	{
		try
		{
			return new DhLogger(
					this.name,
					this.chatLevelConfig, this.fileLevelConfig,
					this.maxLogPerSec
			);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
}
