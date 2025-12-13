/*
 * Compatibility shim for AbstractConfigType
 */
package com.seibel.distanthorizons.core.config.types;

/**
 * Compatibility class for old wrapper code that expects AbstractConfigType.
 * The new API uses different config type classes.
 */
public abstract class AbstractConfigType<T, SELF extends AbstractConfigType<T, SELF>>
{
    protected T value;
    protected String name;
    protected String comment;
    
    public T get() { return value; }
    public void set(T value) { this.value = value; }
    public String getName() { return name; }
    public String getComment() { return comment; }
    
    // GUI-related methods
    public boolean shouldShowInGui() { return true; }
    public String getDisplayName() { return name; }
    public String getTranslationKey() { return name; }
}
