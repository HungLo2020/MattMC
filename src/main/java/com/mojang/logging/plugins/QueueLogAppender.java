package com.mojang.logging.plugins;

import com.mojang.logging.LogQueues;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.util.concurrent.BlockingQueue;

/**
 * Log4j2 appender that writes to a named BlockingQueue for GUI consumption.
 * This is a source-based replacement for com.mojang:logging:1.2.7.jar
 */
@Plugin(name = "Queue", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class QueueLogAppender extends AbstractAppender {
    private static final int MAX_CAPACITY = 250;
    private final BlockingQueue<String> queue;
    
    protected QueueLogAppender(String name, Filter filter, Layout<? extends Serializable> layout, 
                               boolean ignoreExceptions, BlockingQueue<String> queue) {
        super(name, filter, layout, ignoreExceptions, null);
        this.queue = queue;
    }
    
    @Override
    public void append(LogEvent event) {
        if (queue.size() >= MAX_CAPACITY) {
            queue.clear();
        }
        String message = new String(getLayout().toByteArray(event));
        queue.offer(message);
    }
    
    @PluginFactory
    public static QueueLogAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute("queueName") String queueName,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") Filter filter,
            @PluginAttribute("ignoreExceptions") String ignoreExceptionsStr) {
        
        if (name == null) {
            LOGGER.error("No name provided for QueueLogAppender");
            return null;
        }
        
        if (queueName == null) {
            queueName = name;
        }
        
        boolean ignoreExceptions = Boolean.parseBoolean(ignoreExceptionsStr);
        BlockingQueue<String> queue = LogQueues.getOrCreateQueue(queueName);
        
        return new QueueLogAppender(name, filter, layout, ignoreExceptions, queue);
    }
}
