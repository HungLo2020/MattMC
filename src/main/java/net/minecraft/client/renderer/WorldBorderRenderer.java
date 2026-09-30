package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WorldBorderRenderer {
	public static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
	private boolean needsRebuild = true;
	private double lastMinX;
	private double lastMinZ;
	private double lastBorderMinX;
	private double lastBorderMaxX;
	private double lastBorderMinZ;
	private double lastBorderMaxZ;
	@Nullable
	private GpuBuffer worldBorderBuffer;
	@Nullable
	private final VulkanicAPI.AutoStorageIndexBuffer indices;

	public WorldBorderRenderer() {
		this.indices = null;
		this.worldBorderBuffer = null;
	}

	/** Releases a compatibility buffer if Vulkan ownership begins after construction. */
	public void ensureRustSemanticRoute() {
		if (this.worldBorderBuffer != null) {
			this.worldBorderBuffer.close();
			this.worldBorderBuffer = null;
		}
	}

	public void extract(WorldBorder worldBorder, Vec3 vec3, double d, WorldBorderRenderState worldBorderRenderState) {
		worldBorderRenderState.minX = worldBorder.getMinX();
		worldBorderRenderState.maxX = worldBorder.getMaxX();
		worldBorderRenderState.minZ = worldBorder.getMinZ();
		worldBorderRenderState.maxZ = worldBorder.getMaxZ();
		if ((
				!(vec3.x < worldBorderRenderState.maxX - d)
					|| !(vec3.x > worldBorderRenderState.minX + d)
					|| !(vec3.z < worldBorderRenderState.maxZ - d)
					|| !(vec3.z > worldBorderRenderState.minZ + d)
			)
			&& !(vec3.x < worldBorderRenderState.minX - d)
			&& !(vec3.x > worldBorderRenderState.maxX + d)
			&& !(vec3.z < worldBorderRenderState.minZ - d)
			&& !(vec3.z > worldBorderRenderState.maxZ + d)) {
			worldBorderRenderState.alpha = 1.0 - worldBorder.getDistanceToBorder(vec3.x, vec3.z) / d;
			worldBorderRenderState.alpha = Math.pow(worldBorderRenderState.alpha, 4.0);
			worldBorderRenderState.alpha = Mth.clamp(worldBorderRenderState.alpha, 0.0, 1.0);
			worldBorderRenderState.tint = worldBorder.getStatus().getColor();
		} else {
			worldBorderRenderState.alpha = 0.0;
		}
		RustGalWorldPrimitiveRenderer.applyDiagnosticWorldBorderState(worldBorderRenderState, vec3, d);
	}

	public void render(WorldBorderRenderState worldBorderRenderState, Vec3 vec3, double d, double e) {
		if (RustGalWorldPrimitiveRenderer.enqueueWorldBorder(worldBorderRenderState, vec3, d, e)) {
			return;
		}
		throw new IllegalStateException("Java Vulkan world-border rendering is unavailable until the Rust semantic route is admitted");
	}

	public void invalidate() {
		this.needsRebuild = true;
	}

}
