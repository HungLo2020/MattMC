/**
 * Advanced vertex handling implementation migrated from Sodium.
 * <p>
 * This package contains Sodium's optimized vertex processing code that has been integrated
 * into Minecraft's core rendering system. It provides efficient vertex format handling,
 * vertex consumer tracking, and vertex serialization utilities.
 * </p>
 * 
 * <h2>Package Contents</h2>
 * <ul>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced.VertexConsumerTracker} - Tracks and logs vertex consumers that don't support optimized code paths</li>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced.VertexConsumerUtils} - Utility methods for vertex consumer operations</li>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced.VertexFormatAttribute} - Vertex format attribute definitions</li>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced.VertexFormatRegistryImpl} - Implementation of vertex format registry for global ID allocation</li>
 * </ul>
 * 
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced.buffer} - Buffer extensions for optimized vertex data management</li>
 *   <li>{@link net.minecraft.client.renderer.vertex.advanced.serializers} - Runtime bytecode generation for vertex format conversions</li>
 * </ul>
 * 
 * <h2>Integration with Minecraft Core</h2>
 * <p>
 * This implementation integrates with the vertex format caching added in Step 9 (Inline Vertex Format Mixins).
 * The {@link com.mojang.blaze3d.vertex.VertexFormat#getVertexSize()} method uses Sodium's cached stride
 * optimization when advanced rendering is enabled, avoiding redundant field accesses.
 * </p>
 * 
 * <h2>Migration History</h2>
 * <p>
 * Migrated from {@code net.caffeinemc.mods.sodium.client.render.vertex} to
 * {@code net.minecraft.client.renderer.vertex.advanced} as part of Phase 3, Step 15
 * of the Sodium integration plan (STEP7-8PLAN.md).
 * </p>
 * 
 * <p>
 * <b>Original Source:</b> Sodium mod by JellySquid<br>
 * <b>Integration Date:</b> 2025-12-16<br>
 * <b>Files Migrated:</b> 11 (4 core + 1 buffer + 6 serializers)
 * </p>
 * 
 * @since Sodium Integration Phase 3, Step 15
 * @see net.minecraft.client.renderer.advanced.vertex Vertex API (migrated in Step 6)
 * @see com.mojang.blaze3d.vertex.VertexFormat Core vertex format with Sodium caching
 */
package net.minecraft.client.renderer.vertex.advanced;
