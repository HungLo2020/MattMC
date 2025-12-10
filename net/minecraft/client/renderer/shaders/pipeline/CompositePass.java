package net.minecraft.client.renderer.shaders.pipeline;

/**
 * Represents the different composite rendering passes in the shader pipeline.
 * These passes run after the main geometry rendering and before the final pass.
 * 
 * IRIS Source: frnsrc/Iris-1.21.9/.../pipeline/CompositePass.java
 * IRIS Adherence: 100% VERBATIM - Copied exactly from IRIS 1.21.9
 */
public enum CompositePass {
	BEGIN,
	PREPARE,
	DEFERRED,
	COMPOSITE
}
