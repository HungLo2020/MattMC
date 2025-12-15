/**
 * Advanced vertex format and processing APIs.
 * 
 * <p>This package contains the core vertex handling infrastructure migrated from Sodium,
 * now integrated as first-class Minecraft APIs. It provides:</p>
 * 
 * <ul>
 *   <li><b>Vertex Formats</b> - Common vertex formats for different rendering contexts</li>
 *   <li><b>Vertex Attributes</b> - Standard vertex attributes (position, color, normal, etc.)</li>
 *   <li><b>Vertex Buffers</b> - Efficient vertex buffer writing interfaces</li>
 *   <li><b>Vertex Serialization</b> - Registry and serializers for vertex data</li>
 * </ul>
 * 
 * <p><b>Migration Note:</b> Originally from {@code net.caffeinemc.mods.sodium.api.vertex},
 * migrated to Minecraft core in Step 6 of the integration plan.</p>
 * 
 * @since Step 6: Sodium Core API Migration
 */
package net.minecraft.client.renderer.advanced.vertex;

import org.jetbrains.annotations.ApiStatus;
