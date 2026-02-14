package net.vulkanic;

/**
 * Defines comparison operations for depth and stencil tests.
 * 
 * Backend-agnostic enum that replaces GL depth/stencil comparison functions.
 */
public enum CompareOp {
    /**
     * Test never passes
     */
    NEVER,
    
    /**
     * Test passes if source < destination
     */
    LESS,
    
    /**
     * Test passes if source == destination
     */
    EQUAL,
    
    /**
     * Test passes if source <= destination
     */
    LESS_EQUAL,
    
    /**
     * Test passes if source > destination
     */
    GREATER,
    
    /**
     * Test passes if source != destination
     */
    NOT_EQUAL,
    
    /**
     * Test passes if source >= destination
     */
    GREATER_EQUAL,
    
    /**
     * Test always passes
     */
    ALWAYS
}
