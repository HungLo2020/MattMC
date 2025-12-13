/*
 * Compatibility shim for ConfigBasedSpamLogger
 */
package com.seibel.distanthorizons.core.logging;

import org.apache.logging.log4j.Logger;
import java.util.function.Supplier;

/**
 * Compatibility class for old wrapper code that expects ConfigBasedSpamLogger.
 * Provides spam-limited logging functionality.
 */
public class ConfigBasedSpamLogger
{
    private final Logger logger;
    private final Supplier<Boolean> enabledSupplier;
    private final int spamLimit;
    
    public ConfigBasedSpamLogger(Logger logger, Supplier<Boolean> enabledSupplier, int spamLimit)
    {
        this.logger = logger;
        this.enabledSupplier = enabledSupplier;
        this.spamLimit = spamLimit;
    }
    
    public ConfigBasedSpamLogger(String name)
    {
        this.logger = org.apache.logging.log4j.LogManager.getLogger(name);
        this.enabledSupplier = () -> true;
        this.spamLimit = 1;
    }
    
    public ConfigBasedSpamLogger()
    {
        this.logger = org.apache.logging.log4j.LogManager.getLogger();
        this.enabledSupplier = () -> true;
        this.spamLimit = 1;
    }
    
    private boolean isEnabled() { return enabledSupplier.get(); }
    
    public boolean canMaybeLog() { return isEnabled(); }
    
    public void trace(String message, Object... args) { if (isEnabled()) logger.trace(message, args); }
    public void debug(String message, Object... args) { if (isEnabled()) logger.debug(message, args); }
    public void debugInc(String message, Object... args) { if (isEnabled()) logger.debug(message, args); }
    public void info(String message, Object... args) { if (isEnabled()) logger.info(message, args); }
    public void infoInc(String message, Object... args) { if (isEnabled()) logger.info(message, args); }
    public void warn(String message, Object... args) { if (isEnabled()) logger.warn(message, args); }
    public void error(String message, Object... args) { if (isEnabled()) logger.error(message, args); }
    public void error(String message, Throwable t) { if (isEnabled()) logger.error(message, t); }
    
    public boolean isTraceEnabled() { return isEnabled() && logger.isTraceEnabled(); }
    public boolean isDebugEnabled() { return isEnabled() && logger.isDebugEnabled(); }
    public boolean isInfoEnabled() { return isEnabled() && logger.isInfoEnabled(); }
    public boolean isWarnEnabled() { return isEnabled() && logger.isWarnEnabled(); }
    public boolean isErrorEnabled() { return isEnabled() && logger.isErrorEnabled(); }
}
