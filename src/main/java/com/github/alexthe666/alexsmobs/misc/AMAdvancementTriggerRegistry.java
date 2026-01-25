package com.github.alexthe666.alexsmobs.misc;

public class AMAdvancementTriggerRegistry {
    
    /**
     * Deferred holder stub for advancement triggers
     */
    public static class DeferredTriggerHolder {
        public void trigger(Object player) {
            // No-op for now - advancements not implemented
        }
        
        public DeferredTriggerHolder get() {
            return this;
        }
    }
    
    // Stub triggers
    public static final DeferredTriggerHolder ELEPHANT_SWAG = new DeferredTriggerHolder();
    public static final DeferredTriggerHolder UNDERMINE_UNDERMINER = new DeferredTriggerHolder();
    
    // Legacy method for compatibility
    public static void triggerSeagullSteal(Object player) {
        // No-op for now - advancements not implemented
    }
}
