/*
 * Compatibility shim - AbstractBatchGenerationEnvironmentWrapper is an alias for IBatchGeneratorEnvironmentWrapper
 */
package com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDhServerLevel;

/**
 * Compatibility alias for the old AbstractBatchGenerationEnvironmentWrapper class name.
 * The new API uses IBatchGeneratorEnvironmentWrapper.
 */
public abstract class AbstractBatchGenerationEnvironmentWrapper implements IBatchGeneratorEnvironmentWrapper
{
    protected final IDhServerLevel serverLevel;
    
    protected AbstractBatchGenerationEnvironmentWrapper(IDhServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }
}
