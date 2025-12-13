/*
 * Compatibility shim for EventTimer
 */
package com.seibel.distanthorizons.core.util.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility class for old wrapper code that expects EventTimer.
 * Provides basic timing functionality.
 */
public class EventTimer
{
    private long startTime;
    private long endTime;
    private final String name;
    public final List<Event> events = new ArrayList<>();
    
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
    
    public void complete()
    {
        stop();
    }
    
    public void nextEvent(String eventName)
    {
        // Mark the end of current event and start of next
        long now = System.nanoTime();
        events.add(new Event(eventName, now - startTime));
        this.startTime = now;
    }
    
    public long getElapsedNanos()
    {
        return endTime - startTime;
    }
    
    public long getTotalTimeNs()
    {
        long total = 0;
        for (Event e : events) {
            total += e.timeNs;
        }
        return total;
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
    
    public static class Event
    {
        public final String name;
        public final long timeNs;
        
        public Event(String name, long timeNs)
        {
            this.name = name;
            this.timeNs = timeNs;
        }
    }
}
