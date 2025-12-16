/**
 * Chunk mesh compilation and building.
 * 
 * <p>Handles the process of converting block data into optimized vertex meshes:
 * <ul>
 *   <li>Multi-threaded chunk build executor</li>
 *   <li>Task queue and prioritization</li>
 *   <li>Block and fluid rendering pipelines</li>
 *   <li>Upload duration estimation and budgeting</li>
 *   <li>Vertex buffer construction</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.chunk.advanced.compile.executor.ChunkBuilder
 * @see net.minecraft.client.renderer.chunk.advanced.compile.pipeline.BlockRenderer
 * @see net.minecraft.client.renderer.chunk.advanced.compile.pipeline.FluidRenderer
 */
package net.minecraft.client.renderer.chunk.advanced.compile;
