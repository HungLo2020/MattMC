/**
 * Advanced shader pack support and pipeline management (Iris integration).
 * 
 * <p>This package contains the shader pack loading and rendering pipeline system that enables
 * OptiFine/Iris compatible shaders in MattMC. It includes:</p>
 * 
 * <ul>
 *   <li><b>Shader Pack Loading</b> - Parse and load OptiFine/Iris shader pack formats</li>
 *   <li><b>Pipeline Replacement</b> - Replace Minecraft's rendering pipeline with
 *       shader-driven rendering</li>
 *   <li><b>Shadow Rendering</b> - Dedicated shadow map passes with configurable resolution</li>
 *   <li><b>Post-Processing</b> - Deferred rendering and multi-pass post-processing effects</li>
 *   <li><b>Framebuffer Management</b> - Complex multi-target framebuffer setup</li>
 *   <li><b>Uniform System</b> - Expose game state to shaders (time, camera, weather, etc.)</li>
 * </ul>
 * 
 * <p><b>Migration Path from Iris:</b></p>
 * <pre>
 * net.irisshaders.iris.shaderpack     → shaders.pack
 * net.irisshaders.iris.pipeline       → shaders.pipeline
 * net.irisshaders.iris.uniforms       → shaders.uniforms
 * net.irisshaders.iris.targets        → shaders.framebuffers
 * net.irisshaders.iris.api            → shaders.api
 * </pre>
 * 
 * <p><b>Pipeline Architecture:</b></p>
 * <pre>
 * Shadow Pass → GBuffer Pass → Composite Passes → Final Output
 *      ↓             ↓                ↓                ↓
 *   Shadow Map   Color/Depth      Post-Effects     Screen
 * </pre>
 * 
 * <p><b>Initialization Order:</b></p>
 * <ol>
 *   <li>Load shader pack from {@code shaderpacks/} directory</li>
 *   <li>Parse shader programs and pipeline configuration</li>
 *   <li>Create framebuffers and render targets</li>
 *   <li>Compile shader programs and set up uniforms</li>
 *   <li>Initialize pipeline state and activate</li>
 * </ol>
 * 
 * <p><b>Integration Points:</b></p>
 * <ul>
 *   <li>{@code LevelRenderer.addMainPass()} - Inject shader passes around terrain rendering</li>
 *   <li>{@code GameRenderer} - Replace shader program loading and management</li>
 *   <li>Advanced terrain renderer - Wrap with shader pipeline for compatibility</li>
 * </ul>
 * 
 * <p><b>Compatibility Notes:</b></p>
 * <ul>
 *   <li>Designed to work seamlessly with advanced terrain rendering</li>
 *   <li>Can fall back to vanilla rendering when shader pack is disabled</li>
 *   <li>Supports both standalone use and combined use with terrain optimizations</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.GameRenderer
 * @see net.minecraft.client.renderer.LevelRenderer
 * @see net.minecraft.client.renderer.advanced.terrain
 */
package net.minecraft.client.renderer.advanced.shaders;
