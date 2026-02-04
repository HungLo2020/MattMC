/**
 * Vulkanic - Rendering Abstraction Layer
 * 
 * <p>Vulkanic provides a unified interface for all rendering operations, allowing the game
 * to support multiple graphics backends without requiring code changes outside of this package.</p>
 * 
 * <h2>Usage</h2>
 * <p>Initialize Vulkanic during game startup:</p>
 * <pre>{@code
 * // Initialize with default backend (OpenGL)
 * Vulkanic.initialize();
 * 
 * // Or specify a backend explicitly
 * Vulkanic.initialize(BackendType.OPENGL);
 * 
 * // Get the device
 * VulkanicDevice device = Vulkanic.getDevice();
 * 
 * // Create rendering resources
 * VulkanicCommandBuffer cmd = device.createCommandBuffer();
 * VulkanicShader shader = device.createShader(vertexSource, fragmentSource);
 * VulkanicBuffer buffer = device.createBuffer(1024);
 * }</pre>
 * 
 * <h2>Rules</h2>
 * <ul>
 *   <li><strong>DO NOT</strong> make direct OpenGL calls outside the {@code vulkanic/backends} package</li>
 *   <li><strong>DO NOT</strong> directly import classes from {@code vulkanic/backends} in game code</li>
 *   <li><strong>DO</strong> use only the public API classes in the {@code vulkanic} package</li>
 * </ul>
 * 
 * @see net.vulkanic.Vulkanic
 * @see net.vulkanic.VulkanicDevice
 */
package net.vulkanic;
