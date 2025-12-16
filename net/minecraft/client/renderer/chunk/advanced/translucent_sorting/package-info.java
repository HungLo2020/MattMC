/**
 * Translucent geometry sorting for correct transparency rendering.
 * 
 * <p>Implements multiple sorting strategies:
 * <ul>
 *   <li>BSP tree sorting for complex geometry</li>
 *   <li>Topological sorting for simpler cases</li>
 *   <li>Dynamic sorting based on camera position</li>
 *   <li>Sort triggering for performance optimization</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.chunk.advanced.translucent_sorting.SortType
 * @see net.minecraft.client.renderer.chunk.advanced.translucent_sorting.bsp_tree.BSPNode
 * @see net.minecraft.client.renderer.chunk.advanced.translucent_sorting.data.TopoGraphSorting
 */
package net.minecraft.client.renderer.chunk.advanced.translucent_sorting;
