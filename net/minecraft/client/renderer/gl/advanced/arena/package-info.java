/**
 * Buffer arena allocator for efficient memory management.
 * 
 * <p>Provides sub-allocation within large OpenGL buffers to reduce:
 * <ul>
 *   <li>Number of buffer objects</li>
 *   <li>Driver overhead from buffer creation/deletion</li>
 *   <li>Memory fragmentation</li>
 * </ul>
 * 
 * <p>Includes staging buffer support for asynchronous uploads.
 * 
 * @see net.minecraft.client.renderer.gl.advanced.arena.GlBufferArena
 * @see net.minecraft.client.renderer.gl.advanced.arena.GlBufferSegment
 * @see net.minecraft.client.renderer.gl.advanced.arena.staging.StagingBuffer
 */
package net.minecraft.client.renderer.gl.advanced.arena;
