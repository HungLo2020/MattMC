package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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

	public static PostChain load(
		PostChainConfig postChainConfig,
		TextureManager textureManager,
		Set<ResourceLocation> set,
		ResourceLocation resourceLocation,
		CachedOrthoProjectionMatrixBuffer cachedOrthoProjectionMatrixBuffer
	) throws ShaderManager.CompilationException {
		Stream<ResourceLocation> stream = postChainConfig.passes().stream().flatMap(PostChainConfig.Pass::referencedTargets);
		Set<ResourceLocation> set2 = (Set<ResourceLocation>)stream.filter(resourceLocationx -> !postChainConfig.internalTargets().containsKey(resourceLocationx))
			.collect(Collectors.toSet());
		Set<ResourceLocation> set3 = Sets.<ResourceLocation>difference(set2, set);
		if (!set3.isEmpty()) {
			throw new ShaderManager.CompilationException("Referenced external targets are not available in this context: " + set3);
		} else {
			Builder<PostPass> builder = ImmutableList.builder();

			for (int i = 0; i < postChainConfig.passes().size(); i++) {
				PostChainConfig.Pass pass = (PostChainConfig.Pass)postChainConfig.passes().get(i);
				builder.add(createPass(textureManager, pass, resourceLocation.withSuffix("/" + i)));
			}

			return new PostChain(builder.build(), postChainConfig.internalTargets(), set2, cachedOrthoProjectionMatrixBuffer);
		}
	}

	private static PostPass createPass(TextureManager textureManager, PostChainConfig.Pass pass, ResourceLocation resourceLocation) throws ShaderManager.CompilationException {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
			.withFragmentShader(pass.fragmentShaderId())
			.withVertexShader(pass.vertexShaderId())
			.withLocation(resourceLocation);

		for (PostChainConfig.Input input : pass.inputs()) {
			builder.withSampler(input.samplerName() + "Sampler");
		}

		builder.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);

		for (String string : pass.uniforms().keySet()) {
			builder.withUniform(string, UniformType.UNIFORM_BUFFER);
		}

		RenderPipeline renderPipeline = builder.build();
		List<PostPass.Input> list = new ArrayList();

		for (PostChainConfig.Input input2 : pass.inputs()) {
			switch (input2) {
				case PostChainConfig.TextureInput(String var35, ResourceLocation var36, int var37, int var38, boolean var39):
					AbstractTexture abstractTexture = textureManager.getTexture(var36.withPath(string -> "textures/effect/" + string + ".png"));
					abstractTexture.setFilter(var39, false);
					list.add(new PostPass.TextureInput(var35, abstractTexture, var37, var38));
					break;
				case PostChainConfig.TargetInput(String var21, ResourceLocation var41, boolean var42, boolean var43):
					list.add(new PostPass.TargetInput(var21, var41, var42, var43));
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		return new PostPass(renderPipeline, pass.outputTarget(), pass.uniforms(), list);
	}

	public void addToFrame(FrameGraphBuilder frameGraphBuilder, int i, int j, PostChain.TargetBundle targetBundle) {
		GpuBufferSlice gpuBufferSlice = this.projectionMatrixBuffer.getBuffer(i, j);
		Map<ResourceLocation, ResourceHandle<RenderTarget>> map = new HashMap(this.internalTargets.size() + this.externalTargets.size());

		for (ResourceLocation resourceLocation : this.externalTargets) {
			map.put(resourceLocation, targetBundle.getOrThrow(resourceLocation));
		}

		for (Entry<ResourceLocation, PostChainConfig.InternalTarget> entry : this.internalTargets.entrySet()) {
			ResourceLocation resourceLocation2 = (ResourceLocation)entry.getKey();
			PostChainConfig.InternalTarget internalTarget = (PostChainConfig.InternalTarget)entry.getValue();
			RenderTargetDescriptor renderTargetDescriptor = new RenderTargetDescriptor(
				(Integer)internalTarget.width().orElse(i), (Integer)internalTarget.height().orElse(j), true, internalTarget.clearColor()
			);
			if (internalTarget.persistent()) {
				RenderTarget renderTarget = this.getOrCreatePersistentTarget(resourceLocation2, renderTargetDescriptor);
				map.put(resourceLocation2, frameGraphBuilder.importExternal(resourceLocation2.toString(), renderTarget));
			} else {
				map.put(resourceLocation2, frameGraphBuilder.createInternal(resourceLocation2.toString(), renderTargetDescriptor));
			}
		}

		for (PostPass postPass : this.passes) {
			postPass.addToFrame(frameGraphBuilder, map, gpuBufferSlice);
		}

		for (ResourceLocation resourceLocation : this.externalTargets) {
			targetBundle.replace(resourceLocation, (ResourceHandle<RenderTarget>)map.get(resourceLocation));
		}
	}

	@Deprecated
	public void process(RenderTarget renderTarget, GraphicsResourceAllocator graphicsResourceAllocator) {
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		PostChain.TargetBundle targetBundle = PostChain.TargetBundle.of(MAIN_TARGET_ID, frameGraphBuilder.importExternal("main", renderTarget));
		this.addToFrame(frameGraphBuilder, renderTarget.width, renderTarget.height, targetBundle);
		frameGraphBuilder.execute(graphicsResourceAllocator);
	}

	private RenderTarget getOrCreatePersistentTarget(ResourceLocation resourceLocation, RenderTargetDescriptor renderTargetDescriptor) {
		RenderTarget renderTarget = (RenderTarget)this.persistentTargets.get(resourceLocation);
		if (renderTarget == null || renderTarget.width != renderTargetDescriptor.width() || renderTarget.height != renderTargetDescriptor.height()) {
			if (renderTarget != null) {
				renderTarget.destroyBuffers();
			}

			renderTarget = renderTargetDescriptor.allocate();
			renderTargetDescriptor.prepare(renderTarget);
			this.persistentTargets.put(resourceLocation, renderTarget);
		}

		return renderTarget;
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
