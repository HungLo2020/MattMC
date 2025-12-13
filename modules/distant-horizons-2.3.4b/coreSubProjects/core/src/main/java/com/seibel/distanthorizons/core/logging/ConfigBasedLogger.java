/*
 * Compatibility shim for ConfigBasedLogger
 */
package com.seibel.distanthorizons.core.logging;

import org.apache.logging.log4j.Logger;
import java.util.function.Supplier;

/**
 * Compatibility class for old wrapper code that expects ConfigBasedLogger.
 * Provides basic logging functionality.
 */
public class ConfigBasedLogger
{
    private final Logger logger;
    private final Supplier<Boolean> enabledSupplier;
    
    public ConfigBasedLogger(Logger logger, Supplier<Boolean> enabledSupplier)
    {
        this.logger = logger;
        this.enabledSupplier = enabledSupplier;
    }
    
    public ConfigBasedLogger(String name)
    {
        this.logger = org.apache.logging.log4j.LogManager.getLogger(name);
        this.enabledSupplier = () -> true;
    }
    
    public ConfigBasedLogger()
    {
        this.logger = org.apache.logging.log4j.LogManager.getLogger();
        this.enabledSupplier = () -> true;
    }
    
    private boolean isEnabled() { return enabledSupplier.get(); }
    
    public void trace(String message, Object... args) { if (isEnabled()) logger.trace(message, args); }
    public void debug(String message, Object... args) { if (isEnabled()) logger.debug(message, args); }
    public void info(String message, Object... args) { if (isEnabled()) logger.info(message, args); }
    public void warn(String message, Object... args) { if (isEnabled()) logger.warn(message, args); }
    public void error(String message, Object... args) { if (isEnabled()) logger.error(message, args); }
    public void error(String message, Throwable t) { if (isEnabled()) logger.error(message, t); }
    
    public boolean isTraceEnabled() { return isEnabled() && logger.isTraceEnabled(); }
    public boolean isDebugEnabled() { return isEnabled() && logger.isDebugEnabled(); }
    public boolean isInfoEnabled() { return isEnabled() && logger.isInfoEnabled(); }
    public boolean isWarnEnabled() { return isEnabled() && logger.isWarnEnabled(); }
    public boolean isErrorEnabled() { return isEnabled() && logger.isErrorEnabled(); }
}
