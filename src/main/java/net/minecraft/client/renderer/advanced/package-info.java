/**
 * Advanced rendering subsystems for MattMC.
 * 
 * <p>This package contains the deeply-integrated rendering enhancements that were previously
 * provided by Sodium and Iris as external mods. These components are now first-class citizens
 * of the MattMC rendering architecture.</p>
 * 
 * <p>Key subsystems:</p>
 * <ul>
 *   <li>{@link net.minecraft.client.renderer.advanced.terrain} - Advanced chunk rendering
 *       optimizations (formerly Sodium)</li>
 *   <li>{@link net.minecraft.client.renderer.advanced.shaders} - Shader pack support and
 *       pipeline management (formerly Iris)</li>
 *   <li>{@link net.minecraft.client.renderer.advanced.options} - Advanced video options
 *       and configuration</li>
 * </ul>
 * 
 * <p>These subsystems are designed to be:</p>
 * <ul>
 *   <li><b>Optional</b> - Can be disabled via configuration flags</li>
 *   <li><b>Fallback-aware</b> - Degrade gracefully to vanilla rendering when disabled</li>
 *   <li><b>Interoperable</b> - Work together seamlessly (Sodium + Iris compatibility)</li>
 *   <li><b>Maintainable</b> - Clear boundaries and documented interfaces</li>
 * </ul>
 * 
 * @see net.minecraft.client.renderer.LevelRenderer
 * @see net.minecraft.client.renderer.GameRenderer
 */
package net.minecraft.client.renderer.advanced;
