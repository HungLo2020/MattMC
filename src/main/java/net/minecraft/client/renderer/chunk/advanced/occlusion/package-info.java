/**
 * Frustum and occlusion culling for chunk visibility.
 * 
 * <p>Determines which chunks are visible to avoid rendering off-screen geometry:
 * <ul>
 *   <li>Graph-based visibility propagation</li>
 *   <li>Direction-based occlusion</li>
 *   <li>Visibility encoding for efficient storage</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.chunk.advanced.occlusion.OcclusionCuller
 * @see net.minecraft.client.renderer.chunk.advanced.occlusion.GraphDirection
 */
package net.minecraft.client.renderer.chunk.advanced.occlusion;
