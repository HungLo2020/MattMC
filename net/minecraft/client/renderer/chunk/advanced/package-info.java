/**
 * Advanced chunk rendering implementation.
 * 
 * <p>Originally from Sodium mod by JellySquid and CaffeineMC.
 * Integrated into Minecraft core as part of MattMC's advanced rendering system.
 * 
 * <p>This package provides highly optimized chunk meshing, culling, and rendering
 * that significantly improves frame rates compared to vanilla Minecraft. Key features:
 * <ul>
 *   <li>Multi-threaded chunk mesh compilation</li>
 *   <li>Advanced frustum and occlusion culling</li>
 *   <li>Optimized vertex formats and buffer management</li>
 *   <li>Translucent geometry sorting (BSP tree and topological)</li>
 *   <li>Terrain render pass system</li>
 *   <li>Deferred chunk updates and prioritization</li>
 * </ul>
 * 
 * <p><b>Migration Notes:</b>
 * <ul>
 *   <li>Migrated from: {@code net.caffeinemc.mods.sodium.client.render.chunk}</li>
 *   <li>Migration Date: 2025-12-16</li>
 *   <li>INTEGRATION.md Step 7-8 (Phase 3, Step 14)</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.chunk.advanced.ChunkRenderer
 * @see net.minecraft.client.renderer.chunk.advanced.RenderSectionManager
 * @see net.minecraft.client.renderer.advanced.AdvancedRenderingConfig
 * @since MattMC 1.21.10
 */
package net.minecraft.client.renderer.chunk.advanced;
