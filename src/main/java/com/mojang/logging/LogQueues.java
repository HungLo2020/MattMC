package com.mojang.logging;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe log message queue management for GUI logging.
 * This is a source-based replacement for com.mojang:logging:1.2.7.jar
 */
public class LogQueues {
    private static final Map<String, BlockingQueue<String>> QUEUES = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock QUEUE_LOCK = new ReentrantReadWriteLock();
    
    /**
     * Get or create a log queue by name.
     */
    public static BlockingQueue<String> getOrCreateQueue(String name) {
        QUEUE_LOCK.readLock().lock();
        try {
            BlockingQueue<String> queue = QUEUES.get(name);
            if (queue != null) {
                return queue;
            }
        } finally {
            QUEUE_LOCK.readLock().unlock();
        }
        
        QUEUE_LOCK.writeLock().lock();
        try {
            return QUEUES.computeIfAbsent(name, k -> new LinkedBlockingQueue<>());
        } finally {
            QUEUE_LOCK.writeLock().unlock();
        }
    }
    
    /**
     * Get the next log event from a named queue (non-blocking poll).
     * Returns null if the queue is empty.
     */
    public static String getNextLogEvent(String name) {
        BlockingQueue<String> queue = getOrCreateQueue(name);
        return queue.poll();
    }
}
