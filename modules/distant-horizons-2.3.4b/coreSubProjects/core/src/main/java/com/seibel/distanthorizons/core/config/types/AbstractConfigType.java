/*
 * Compatibility shim for AbstractConfigType
 * Makes it extend AbstractConfigBase to maintain compatibility with old wrapper code
 */
package com.seibel.distanthorizons.core.config.types;

import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;

/**
 * Compatibility class for old wrapper code that expects AbstractConfigType.
 * This extends AbstractConfigBase to provide type compatibility.
 */
public class AbstractConfigType<T, SELF extends AbstractConfigType<T, SELF>> extends AbstractConfigBase<T>
{
    // The SELF parameter is ignored but kept for signature compatibility
    
    public AbstractConfigType() {
        super(EConfigEntryAppearance.ALL, null);
    }
    
    // Convenience methods for old API compatibility
    public String getComment() { return ""; }
    public boolean shouldShowInGui() { return true; }
    public String getDisplayName() { return this.name; }
    public String getTranslationKey() { return this.name; }
}
