package net.minecraft.client.renderer.shaders.vertices;

/**
 * Interface for BufferBuilders that support block-sensitive rendering.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/BlockSensitiveBufferBuilder.java
 */
public interface BlockSensitiveBufferBuilder {
	/**
	 * Called before rendering a block to set block-specific data.
	 * 
	 * @param block The block ID
	 * @param renderType The render type
	 * @param blockEmission The block emission
	 * @param localPosX The local X position within the section
	 * @param localPosY The local Y position within the section
	 * @param localPosZ The local Z position within the section
	 */
	void beginBlock(int block, byte renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ);
	
	/**
	 * Called after rendering a block to clear block-specific data.
	 */
	void endBlock();
}
