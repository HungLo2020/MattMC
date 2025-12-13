/*
 * Compatibility shim for EventTimer
 */
package com.seibel.distanthorizons.core.util.objects;

/**
 * Compatibility class for old wrapper code that expects EventTimer.
 * Provides basic timing functionality.
 */
public class EventTimer
{
    private long startTime;
    private long endTime;
    private final String name;
    
    public EventTimer(String name)
    {
        this.name = name;
        this.startTime = System.nanoTime();
    }
    
    public void start()
    {
        this.startTime = System.nanoTime();
    }
    
    public void stop()
    {
        this.endTime = System.nanoTime();
    }
    
    public void nextEvent(String eventName)
    {
        // Mark the end of current event and start of next
        this.endTime = System.nanoTime();
        // Could log the transition but for compatibility just update time
        this.startTime = System.nanoTime();
    }
    
    public long getElapsedNanos()
    {
        return endTime - startTime;
    }
    
    public long getElapsedMillis()
    {
        return (endTime - startTime) / 1_000_000;
    }
    
    public String getName()
    {
        return name;
    }
    
    @Override
    public String toString()
    {
        return name + ": " + getElapsedMillis() + "ms";
    }
}
