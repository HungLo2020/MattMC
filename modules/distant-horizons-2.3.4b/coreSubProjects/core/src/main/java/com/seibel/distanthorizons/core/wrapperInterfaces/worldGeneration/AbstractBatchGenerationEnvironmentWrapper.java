/*
 * Compatibility shim - AbstractBatchGenerationEnvironmentWrapper is an alias for IBatchGeneratorEnvironmentWrapper
 */
package com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration;

/**
 * Compatibility alias for the old AbstractBatchGenerationEnvironmentWrapper class name.
 * The new API uses IBatchGeneratorEnvironmentWrapper.
 */
public abstract class AbstractBatchGenerationEnvironmentWrapper implements IBatchGeneratorEnvironmentWrapper
{
    // This is an abstract adapter class that bridges the old API to the new interface
}
