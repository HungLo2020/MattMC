/**
 * Advanced video options and configuration management.
 * 
 * <p>This package contains the unified configuration system for advanced rendering features,
 * consolidating settings from both the terrain optimization and shader systems into a cohesive
 * options interface.</p>
 * 
 * <ul>
 *   <li><b>Terrain Options</b> - Chunk rendering settings, performance toggles, quality options</li>
 *   <li><b>Shader Options</b> - Shader pack selection, shader-specific settings</li>
 *   <li><b>UI Integration</b> - Advanced video settings screens integrated with vanilla UI</li>
 *   <li><b>Config Migration</b> - Backward compatibility with Sodium/Iris config files</li>
 * </ul>
 * 
 * <p><b>Migration Path:</b></p>
 * <pre>
 * net.caffeinemc.mods.sodium.client.gui     → options.terrain (Sodium options UI)
 * net.irisshaders.iris.gui                  → options.shaders (Iris options UI)
 * sodium-options.json                       → merged into options.txt
 * iris.properties                           → merged into options.txt
 * </pre>
 * 
 * <p><b>Configuration Structure:</b></p>
 * <ul>
 *   <li>{@code Options.advancedRenderingOptions} - Terrain and performance settings</li>
 *   <li>{@code Options.shaderOptions} - Shader pack and shader settings</li>
 *   <li>Backward compatibility: Read old config files on first launch</li>
 *   <li>Forward migration: Save to unified {@code options.txt}</li>
 * </ul>
 * 
 * <p><b>UI Structure:</b></p>
 * <pre>
 * Video Settings (vanilla)
 * ├── Graphics Quality (vanilla options)
 * ├── Performance (vanilla options)
 * └── Advanced... (new button)
 *     ├── Chunk Rendering (terrain options)
 *     │   ├── Chunk Render Distance
 *     │   ├── Use Chunk Multithreading
 *     │   ├── Always Defer Chunk Updates
 *     │   └── ...
 *     ├── Terrain Quality (terrain options)
 *     │   ├── Terrain Quality
 *     │   ├── Use Block Face Culling
 *     │   └── ...
 *     └── Shader Packs (shader options)
 *         ├── Shader Pack Selection
 *         ├── Shader Options
 *         └── ...
 * </pre>
 * 
 * <p><b>Integration Points:</b></p>
 * <ul>
 *   <li>{@code Options} - Extend with advanced rendering option instances</li>
 *   <li>{@code VideoSettingsScreen} - Add "Advanced..." button</li>
 *   <li>Option persistence - Unified save/load through {@code options.txt}</li>
 * </ul>
 * 
 * @see net.minecraft.client.Options
 * @see net.minecraft.client.OptionInstance
 */
package net.minecraft.client.renderer.advanced.options;
