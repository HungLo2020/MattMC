/*
 * Compatibility shim for AbstractConfigType
 * This bridges the old wrapper code API to the new config system
 */
package com.seibel.distanthorizons.core.config.types;

import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * Compatibility class that serves as the base for config entries.
 * The old wrapper code expects AbstractConfigType which can be cast to ConfigEntry.
 * This class sits between AbstractConfigBase and ConfigEntry in the hierarchy.
 */
public abstract class AbstractConfigType<T, SELF extends AbstractConfigType<T, SELF>> extends AbstractConfigBase<T>
{
    protected AbstractConfigType(EConfigEntryAppearance appearance, T defaultValue) {
        super(appearance, defaultValue);
    }
    
    // Methods expected by old wrapper code - subclasses implement
    public String getComment() { return ""; }
    public boolean shouldShowInGui() { return true; }
    public String getDisplayName() { return this.name; }
    public String getTranslationKey() { return this.name; }
}
