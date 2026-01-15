package net.distanthorizons.api.enums.config;

import org.apache.logging.log4j.Level;

/**
 * ALL
 * DEBUG
 * INFO
 * WARN
 * ERROR
 * DISABLED
 * 
 * @since API 5.0.0
 * @version 2024-4-6
 */
public enum EDhApiLoggerLevel
{
	// ordered from most to least broad
	ALL(Level.ALL),
	DEBUG(Level.DEBUG),
	INFO(Level.INFO),
	WARN(Level.WARN),
	ERROR(Level.ERROR),
	DISABLED(Level.OFF),
	;
	
	public final Level level;
	
	EDhApiLoggerLevel(Level level)
	{ this.level = level; }
	
	
	
}
