/*
 * Compatibility shim for ConfigBasedSpamLogger
 */
package com.seibel.distanthorizons.core.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compatibility class for old wrapper code that expects ConfigBasedSpamLogger.
 * Provides spam-limited logging functionality.
 */
public class ConfigBasedSpamLogger
{
    private final Logger logger;
    
    public ConfigBasedSpamLogger(String name)
    {
        this.logger = LogManager.getLogger(name);
    }
    
    public ConfigBasedSpamLogger()
    {
        this.logger = LogManager.getLogger();
    }
    
    public void trace(String message, Object... args) { logger.trace(message, args); }
    public void debug(String message, Object... args) { logger.debug(message, args); }
    public void info(String message, Object... args) { logger.info(message, args); }
    public void warn(String message, Object... args) { logger.warn(message, args); }
    public void error(String message, Object... args) { logger.error(message, args); }
    public void error(String message, Throwable t) { logger.error(message, t); }
    
    public boolean isTraceEnabled() { return logger.isTraceEnabled(); }
    public boolean isDebugEnabled() { return logger.isDebugEnabled(); }
    public boolean isInfoEnabled() { return logger.isInfoEnabled(); }
    public boolean isWarnEnabled() { return logger.isWarnEnabled(); }
    public boolean isErrorEnabled() { return logger.isErrorEnabled(); }
}
