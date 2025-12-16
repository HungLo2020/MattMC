/**
 * Sodium's supporting infrastructure integrated into Minecraft core.
 * <p>
 * This package contains all of Sodium's utility, platform, and support code that has been
 * integrated into Minecraft's core rendering system. These files provide the foundational
 * infrastructure that enables Sodium's advanced rendering capabilities.
 * </p>
 * 
 * <h2>Package Contents</h2>
 * 
 * <h3>Core Support (203 files)</h3>
 * <ul>
 *   <li>{@link net.minecraft.client.renderer.sodium.SodiumClientMod} - Main Sodium client initialization</li>
 * </ul>
 * 
 * <h3>Major Subpackages</h3>
 * 
 * <h4>1. Utility Systems (36 files) - {@link net.minecraft.client.renderer.sodium.util}</h4>
 * <ul>
 *   <li><b>collections/</b> - Specialized collections (BitArray, DoubleBufferedQueue)</li>
 *   <li><b>iterator/</b> - Custom iterators (ByteIterator, ReversibleObjectArrayIterator)</li>
 *   <li><b>color/</b> - Color processing utilities (ColorSRGB, BoxBlur, FastCubicSampler)</li>
 *   <li><b>sorting/</b> - Sorting algorithms (RadixSort, VertexSorters)</li>
 *   <li><b>interval_tree/</b> - Interval tree data structures</li>
 *   <li><b>task/</b> - Task execution utilities</li>
 *   <li>Math utilities (MathUtil, UInt32)</li>
 * </ul>
 * 
 * <h4>2. Model System (45 files) - {@link net.minecraft.client.renderer.sodium.model}</h4>
 * <ul>
 *   <li><b>color/</b> - Color providers and registry for block/item coloring</li>
 *   <li><b>light/</b> - Advanced lighting pipeline and light data caching</li>
 *   <li><b>quad/</b> - Model quad representations, properties, and blending</li>
 * </ul>
 * 
 * <h4>3. Render Support (35 files) - {@link net.minecraft.client.renderer.sodium.render}</h4>
 * <ul>
 *   <li><b>viewport/</b> - Camera transforms and viewport management</li>
 *   <li><b>vertex/</b> - Vertex format attributes and serialization</li>
 *   <li><b>texture/</b> - Texture and sprite utilities</li>
 *   <li><b>util/</b> - Render assertions and helper utilities</li>
 *   <li><b>frapi/</b> - Fabric Rendering API compatibility layer</li>
 *   <li><b>immediate/</b> - Immediate mode rendering support</li>
 * </ul>
 * 
 * <h4>4. Platform Abstraction (8 files) - {@link net.minecraft.client.renderer.sodium.services}</h4>
 * <ul>
 *   <li>Service interfaces for platform-specific implementations</li>
 *   <li>Fluid renderer factory</li>
 *   <li>Platform block and level access</li>
 *   <li>Mixin override configuration</li>
 * </ul>
 * 
 * <h4>5. Compatibility Layer (25 files) - {@link net.minecraft.client.renderer.sodium.compatibility}</h4>
 * <ul>
 *   <li><b>environment/</b> - Graphics adapter detection and environment probing</li>
 *   <li><b>workarounds/</b> - GPU driver workarounds (Intel, NVIDIA)</li>
 *   <li><b>checks/</b> - System compatibility checks</li>
 * </ul>
 * 
 * <h4>6. Platform Integration (20 files) - {@link net.minecraft.client.renderer.sodium.platform}</h4>
 * <ul>
 *   <li><b>windows/</b> - Windows-specific APIs (D3DKMT, Shell32, User32, Kernel32)</li>
 *   <li><b>unix/</b> - Unix-specific APIs (Libc)</li>
 *   <li>MessageBox, PlatformHelper, NativeWindowHandle</li>
 * </ul>
 * 
 * <h4>7. GUI Components (8 files) - {@link net.minecraft.client.renderer.sodium.gui}</h4>
 * <ul>
 *   <li>SodiumGameOptions - Sodium's options management</li>
 *   <li><b>console/</b> - Debug console rendering (also in {@link net.minecraft.client.renderer.sodium.console})</li>
 *   <li><b>options/</b> - Options UI controls and bindings</li>
 *   <li><b>widgets/</b> - Custom UI widgets</li>
 * </ul>
 * 
 * <h4>8. Data Management (8 files) - {@link net.minecraft.client.renderer.sodium.data}</h4>
 * <ul>
 *   <li><b>config/</b> - Configuration system (MixinConfig, MixinOption)</li>
 *   <li><b>fingerprint/</b> - System fingerprinting for compatibility</li>
 * </ul>
 * 
 * <h4>9. World Integration (15 files) - {@link net.minecraft.client.renderer.sodium.world}</h4>
 * <ul>
 *   <li><b>biome/</b> - Biome color caching and color maps</li>
 *   <li><b>cloned/</b> - Cloned chunk sections for thread-safe access</li>
 *   <li>Level slice management</li>
 * </ul>
 * 
 * <h2>Integration with Minecraft Core</h2>
 * <p>
 * This supporting infrastructure provides the foundation for the advanced rendering features
 * migrated in Steps 13-14 (GL abstraction and chunk rendering). It includes:
 * </p>
 * <ul>
 *   <li><b>Platform Independence:</b> Abstraction layer for Windows/Unix platform APIs</li>
 *   <li><b>GPU Compatibility:</b> Workarounds for Intel and NVIDIA driver issues</li>
 *   <li><b>Performance Utilities:</b> Optimized data structures and algorithms</li>
 *   <li><b>Rendering Helpers:</b> Texture, model, and lighting support systems</li>
 *   <li><b>Configuration:</b> Options management and mixin configuration</li>
 * </ul>
 * 
 * <h2>Migration History</h2>
 * <p>
 * Migrated from {@code net.caffeinemc.mods.sodium.client.*} to
 * {@code net.minecraft.client.renderer.sodium.*} as part of Phase 3, Step 16
 * of the Sodium integration plan (STEP7-8PLAN.md).
 * </p>
 * 
 * <p>
 * <b>Original Source:</b> Sodium mod by JellySquid<br>
 * <b>Initial Migration:</b> Steps 13-14 (December 2025)<br>
 * <b>Directory Correction:</b> Step 16 (December 2025)<br>
 * <b>Files Migrated:</b> 203 Java files across 9 major subsystems
 * </p>
 * 
 * <h2>Related Packages</h2>
 * <ul>
 *   <li>{@link net.minecraft.client.renderer.gl.advanced} - GL abstraction layer (Step 13)</li>
 *   <li>{@link net.minecraft.client.renderer.chunk.advanced} - Chunk rendering (Step 14)</li>
 *   <li>{@link net.minecraft.client.renderer.advanced.vertex} - Vertex API (Step 6)</li>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced} - Vertex implementation (Step 15)</li>
 * </ul>
 * 
 * @since Sodium Integration Phase 3, Steps 13-14 & 16
 * @see net.minecraft.client.renderer.sodium.SodiumClientMod Main entry point
 */
package net.minecraft.client.renderer.sodium;
