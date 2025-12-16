/**
 * Render device abstraction for batched draw calls.
 * 
 * <p>Provides high-level rendering interface that:
 * <ul>
 *   <li>Batches multiple draw calls into efficient multi-draw commands</li>
 *   <li>Manages render state and bindings</li>
 *   <li>Abstracts OpenGL command submission</li>
 *   <li>Optimizes draw call overhead</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.gl.advanced.device.RenderDevice
 * @see net.minecraft.client.renderer.gl.advanced.device.CommandList
 * @see net.minecraft.client.renderer.gl.advanced.device.DrawCommandList
 */
package net.minecraft.client.renderer.gl.advanced.device;
