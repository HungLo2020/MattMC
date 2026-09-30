package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.resource.GraphicsResourceAllocator;
import net.blaze3d.resource.ResourceHandle;
import net.blaze3d.shaders.UniformType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PostChain implements AutoCloseable {
	public static final ResourceLocation MAIN_TARGET_ID = ResourceLocation.withDefaultNamespace("main");
	private final List<PostPass> passes;
	private final Map<ResourceLocation, PostChainConfig.InternalTarget> internalTargets;
	private final Set<ResourceLocation> externalTargets;
	private final Map<ResourceLocation, RenderTarget> persistentTargets = new HashMap();
	private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;

	private PostChain(
		List<PostPass> list,
		Map<ResourceLocation, PostChainConfig.InternalTarget> map,
		Set<ResourceLocation> set,
		CachedOrthoProjectionMatrixBuffer cachedOrthoProjectionMatrixBuffer
	) {
		this.passes = list;
		this.internalTargets = map;
		this.externalTargets = set;
		this.projectionMatrixBuffer = cachedOrthoProjectionMatrixBuffer;
	}

	public void close() {
		this.persistentTargets.values().forEach(RenderTarget::destroyBuffers);
		this.persistentTargets.clear();

		for (PostPass postPass : this.passes) {
			postPass.close();
		}
	}

	@Environment(EnvType.CLIENT)
	public interface TargetBundle {
		static PostChain.TargetBundle of(ResourceLocation resourceLocation, ResourceHandle<RenderTarget> resourceHandle) {
			return new PostChain.TargetBundle() {
				private ResourceHandle<RenderTarget> handle = resourceHandle;

				@Override
				public void replace(ResourceLocation resourceLocation, ResourceHandle<RenderTarget> resourceHandle) {
					if (resourceLocation.equals(resourceLocation)) {
						this.handle = resourceHandle;
					} else {
						throw new IllegalArgumentException("No target with id " + resourceLocation);
					}
				}

				@Nullable
				@Override
				public ResourceHandle<RenderTarget> get(ResourceLocation resourceLocation) {
					return resourceLocation.equals(resourceLocation) ? this.handle : null;
				}
			};
		}

		void replace(ResourceLocation resourceLocation, ResourceHandle<RenderTarget> resourceHandle);

		@Nullable
		ResourceHandle<RenderTarget> get(ResourceLocation resourceLocation);

		default ResourceHandle<RenderTarget> getOrThrow(ResourceLocation resourceLocation) {
			ResourceHandle<RenderTarget> resourceHandle = this.get(resourceLocation);
			if (resourceHandle == null) {
				throw new IllegalArgumentException("Missing target with id " + resourceLocation);
			} else {
				return resourceHandle;
			}
		}
	}
}
