/**
 * Advanced terrain rendering system (Sodium integration).
 * 
 * <p>This package contains the optimized chunk rendering system that provides significant
 * performance improvements over vanilla Minecraft's terrain renderer. It includes:</p>
 * 
 * <ul>
 *   <li><b>Chunk Mesh Building</b> - Multi-threaded, optimized chunk meshing with compact
 *       vertex formats</li>
 *   <li><b>Render Graph</b> - Modern frame graph architecture for efficient render pass
 *       scheduling</li>
 *   <li><b>Occlusion Culling</b> - Advanced frustum and occlusion culling to skip
 *       non-visible chunks</li>
 *   <li><b>GL Abstractions</b> - Direct OpenGL command buffer management for minimal overhead</li>
 *   <li><b>Vertex Optimization</b> - Compact vertex formats to reduce memory bandwidth</li>
 * </ul>
 * 
 * <p><b>Migration Path from Sodium:</b></p>
 * <pre>
 * net.caffeinemc.mods.sodium.client.render.chunk     → terrain.chunk
 * net.caffeinemc.mods.sodium.client.render.vertex    → terrain.vertex
 * net.caffeinemc.mods.sodium.client.gl               → terrain.gl
 * net.caffeinemc.mods.sodium.api.vertex              → terrain.api.vertex
 * </pre>
 * 
 * <p><b>Initialization Order:</b></p>
 * <ol>
 *   <li>Register terrain renderer during {@code LevelRenderer} initialization</li>
 *   <li>Initialize chunk build pipeline and worker threads</li>
 *   <li>Set up GL state and vertex formats</li>
 *   <li>Begin accepting chunk build requests</li>
 * </ol>
 * 
 * <p><b>Integration Points:</b></p>
 * <ul>
 *   <li>{@code LevelRenderer.addMainPass()} - Replaces terrain rendering</li>
 *   <li>{@code GameRenderer.render()} - Setup and teardown hooks</li>
 *   <li>{@code BlockRenderDispatcher} - Custom block model rendering</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.LevelRenderer
 * @see net.minecraft.client.renderer.chunk.SectionRenderDispatcher
 */
package net.minecraft.client.renderer.advanced.terrain;
