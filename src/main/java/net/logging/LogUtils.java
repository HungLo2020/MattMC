package net.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LifeCycle;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.function.Supplier;

/**
 * Mojang logging utilities wrapper around SLF4J and Log4j2.
 * This is a source-based replacement for com.mojang:logging:1.2.7.jar
 */
public class LogUtils {
    public static final String FATAL_MARKER_ID = "FATAL";
    public static final Marker FATAL_MARKER = MarkerFactory.getMarker(FATAL_MARKER_ID);
    
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    
    /**
     * Get a logger for the calling class automatically using StackWalker.
     * This is the primary method used throughout the Minecraft codebase.
     */
    public static Logger getLogger() {
        return LoggerFactory.getLogger(STACK_WALKER.getCallerClass());
    }
    
    /**
     * Check if the logging system is active (not stopped).
     */
    public static boolean isLoggerActive() {
        var context = LogManager.getContext();
        if (context instanceof LifeCycle) {
            LifeCycle lifeCycle = (LifeCycle) context;
            return !lifeCycle.isStopped();
        }
        return true;
    }
    
    /**
     * Configure the root logging level at runtime.
     */
    public static void configureRootLoggingLevel(org.slf4j.event.Level level) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("");
        loggerConfig.setLevel(convertLevel(level));
        context.updateLoggers();
    }
    
    /**
     * Convert SLF4J level to Log4j2 level.
     */
    private static Level convertLevel(org.slf4j.event.Level level) {
        return switch (level) {
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case DEBUG -> Level.DEBUG;
            case ERROR -> Level.ERROR;
            case TRACE -> Level.TRACE;
        };
    }
    
    /**
     * Defer evaluation of an object for lazy logging.
     * This allows expensive toString() operations to be skipped if the log level is not enabled.
     */
    public static Object defer(Supplier<Object> supplier) {
        return new ToString(supplier);
    }
    
    /**
     * Helper class for deferred toString() evaluation.
     */
    private static class ToString {
        private final Supplier<Object> supplier;
        
        ToString(Supplier<Object> supplier) {
            this.supplier = supplier;
        }
        
        @Override
        public String toString() {
            Object obj = supplier.get();
            return obj != null ? obj.toString() : "null";
        }
    }
}
