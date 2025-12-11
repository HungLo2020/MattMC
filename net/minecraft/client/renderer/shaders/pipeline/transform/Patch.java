package net.minecraft.client.renderer.shaders.pipeline.transform;

/**
 * Patch types for shader transformation.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/Patch.java
 */
public enum Patch {
	VANILLA,
	DH_TERRAIN,
	DH_GENERIC,
	SODIUM,
	COMPOSITE,
	COMPUTE
}
