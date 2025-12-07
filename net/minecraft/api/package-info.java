/**
 * Core API annotations for Minecraft.
 * <p>
 * This package contains annotation types used throughout the Minecraft codebase
 * to document code characteristics and requirements.
 * 
 * <h2>Environment Annotations</h2>
 * The {@link net.minecraft.api.Environment} annotation is used extensively to 
 * mark client-only or server-only code paths. While this annotation is retained
 * at runtime, it serves purely as documentation and is not checked or enforced
 * by the Minecraft runtime.
 * 
 * <h2>History</h2>
 * These annotations were originally provided by Fabric API during the 
 * decompilation process. This custom implementation removes the Fabric 
 * dependency while maintaining identical functionality.
 * 
 * @since Minecraft 1.21.10 (MattMC port)
 */
package net.minecraft.api;
