package net.minecraft.util.profiling.custom;

import net.minecraft.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Wraps a ProfilerFiller to capture hierarchical timing data.
 * This wrapper intercepts all push/pop calls to build a complete timing tree.
 */
public class ProfilerCollectorWrapper implements ProfilerFiller {
    private final ProfilerFiller delegate;
    private final Map<String, OperationRecord> operations;
    private final ThreadLocal<Deque<Long>> startTimes;
    private final ThreadLocal<Deque<String>> pathStack;
    private final ThreadLocal<StringBuilder> pathBuilder;
    
    public ProfilerCollectorWrapper(ProfilerFiller delegate) {
        this.delegate = delegate;
        this.operations = new ConcurrentHashMap<>();
        this.startTimes = ThreadLocal.withInitial(ArrayDeque::new);
        this.pathStack = ThreadLocal.withInitial(ArrayDeque::new);
        this.pathBuilder = ThreadLocal.withInitial(StringBuilder::new);
    }
    
    @Override
    public void startTick() {
        delegate.startTick();
    }
    
    @Override
    public void endTick() {
        delegate.endTick();
    }
    
    @Override
    public void push(String name) {
        delegate.push(name);
        
        // Record start time
        startTimes.get().push(Util.getNanos());
        
        // Build path
        Deque<String> stack = pathStack.get();
        stack.push(name);
        
        // We'll record timing on pop, not on push
    }
    
    @Override
    public void push(Supplier<String> nameSupplier) {
        push(nameSupplier.get());
    }
    
    @Override
    public void pop() {
        delegate.pop();
        
        Deque<Long> times = startTimes.get();
        Deque<String> stack = pathStack.get();
        
        if (!times.isEmpty() && !stack.isEmpty()) {
            // Calculate duration
            long duration = Util.getNanos() - times.pop();
            
            // Build full path
            String name = stack.pop();
            String fullPath = buildPath(stack, name);
            
            // Record timing
            operations.computeIfAbsent(fullPath, k -> new OperationRecord(k))
                .addSample(duration);
        }
    }
    
    @Override
    public void popPush(String name) {
        pop();
        push(name);
    }
    
    @Override
    public void popPush(Supplier<String> nameSupplier) {
        popPush(nameSupplier.get());
    }
    
    @Override
    public void markForCharting(MetricCategory metricCategory) {
        delegate.markForCharting(metricCategory);
    }
    
    @Override
    public void incrementCounter(String name, int amount) {
        delegate.incrementCounter(name, amount);
    }
    
    @Override
    public void incrementCounter(Supplier<String> nameSupplier, int amount) {
        delegate.incrementCounter(nameSupplier, amount);
    }
    
    @Override
    public void addZoneText(String text) {
        delegate.addZoneText(text);
    }
    
    @Override
    public void addZoneValue(long value) {
        delegate.addZoneValue(value);
    }
    
    @Override
    public void setZoneColor(int color) {
        delegate.setZoneColor(color);
    }
    
    /**
     * Build hierarchical path from stack and current name.
     * Creates paths like "root.tick.entities.regular"
     */
    private String buildPath(Deque<String> stack, String currentName) {
        StringBuilder sb = pathBuilder.get();
        sb.setLength(0); // Clear
        
        // Build path from bottom of stack to top
        if (stack.isEmpty()) {
            return currentName;
        }
        
        // Convert deque to array for iteration from bottom to top
        String[] elements = stack.toArray(new String[0]);
        
        // Iterate from oldest (bottom) to newest (top)
        for (int i = elements.length - 1; i >= 0; i--) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(elements[i]);
        }
        
        // Add current name
        if (sb.length() > 0) {
            sb.append('.');
        }
        sb.append(currentName);
        
        return sb.toString();
    }
    
    /**
     * Get all collected operations.
     */
    public Map<String, OperationRecord> getOperations() {
        return operations;
    }
    
    /**
     * Clear all collected data.
     */
    public void clear() {
        operations.clear();
        startTimes.get().clear();
        pathStack.get().clear();
    }
}
