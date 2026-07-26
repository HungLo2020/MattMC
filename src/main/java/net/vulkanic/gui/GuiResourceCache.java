package net.vulkanic.gui;

import net.vulkanic.bridge.VulkanicGalBridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiResourceCache {
	private final Map<CacheKey, CachedResources> caches = new HashMap<>();

	GuiResourceCache() {
	}

	Lookup resourcesFor(VulkanicGalBridge bridge, long generation, RustGalGuiRenderer.TextureGroup textureGroup, Recorder recorder) {
		CacheKey key = new CacheKey(textureGroup.cacheKind, textureGroup.semanticId, generation);
		CachedResources resources = caches.get(key);
		if (resources != null) {
			return new Lookup(resources, true, 0L);
		}
		long createStarted = System.nanoTime();
		CachedResources created = createGuiSpriteResources(bridge, textureGroup, key, recorder);
		caches.put(key, created);
		return new Lookup(created, false, Math.max(0L, System.nanoTime() - createStarted));
	}

	void clearAtlasesAndCaches() {
		caches.clear();
		GuiSpriteAtlas.clear();
	}

	void clearCachesOnly() {
		caches.clear();
	}

	void destroyCachedResources(VulkanicGalBridge bridge, Recorder recorder) {
		if (bridge == null || caches.isEmpty()) {
			return;
		}
		List<CachedResources> resources = new ArrayList<>(caches.values());
		resources.sort(Comparator.comparing(resource -> resource.key().semanticId()));
		for (CachedResources resource : resources) {
			VulkanicGalBridge.ResourceBatchBuilder destroy = bridge.resourceBatchBuilder();
			for (HandleToDestroy handle : resource.handlesInDestroyOrder()) {
				destroy.destroy(handle.handle(), handle.kind());
				recorder.resourceDestroyed();
			}
			recorder.recordResourceBatch(bridge.resourceBatch(destroy.build()));
		}
	}

	private CachedResources createGuiSpriteResources(
		VulkanicGalBridge bridge,
		RustGalGuiRenderer.TextureGroup textureGroup,
		CacheKey key,
		Recorder recorder
	) {
		List<HandleToDestroy> created = new ArrayList<>();
		try {
			GuiSpriteAtlas.TextureAtlas atlas = GuiSpriteAtlas.atlasFor(textureGroup);
			VulkanicGalBridge.ResourceResults base = bridge.resourceBatch(
				bridge.resourceBatchBuilder()
					.buffer(1, key.label("texture-upload"), atlas.bytes().length, VulkanicGalBridge.MEMORY_UPLOAD,
						VulkanicGalBridge.BUFFER_TRANSFER_SRC | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
					.buffer(2, key.label("index"), GuiBatchBuilder.INDEX_BYTES.length, VulkanicGalBridge.MEMORY_UPLOAD,
						VulkanicGalBridge.BUFFER_INDEX | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
					.buffer(3, key.label("uniform"), GuiBatchBuilder.PACKED_UNIFORM_BYTES, VulkanicGalBridge.MEMORY_UPLOAD,
						VulkanicGalBridge.BUFFER_UNIFORM | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
					.texture(4, key.label("texture"), VulkanicGalBridge.FORMAT_RGBA8, atlas.width(), atlas.height(),
						VulkanicGalBridge.TEXTURE_SAMPLED | VulkanicGalBridge.TEXTURE_TRANSFER_DST)
					.sampler(5, key.label("sampler"))
					.shader(6, key.label("vertex"), VulkanicGalBridge.SHADER_VERTEX, GuiPipelineLibrary.VERTEX_SHADER_OPENGL)
					.shader(7, key.label("fragment"), VulkanicGalBridge.SHADER_FRAGMENT, GuiPipelineLibrary.FRAGMENT_SHADER_OPENGL)
					.build());
			recorder.recordResourceBatch(base);
			long uploadBuffer = base.handle(0);
			long indexBuffer = base.handle(1);
			long uniformBuffer = base.handle(2);
			long texture = base.handle(3);
			long sampler = base.handle(4);
			long vertex = base.handle(5);
			long fragment = base.handle(6);
			created.add(new HandleToDestroy(uploadBuffer, VulkanicGalBridge.HANDLE_BUFFER));
			created.add(new HandleToDestroy(indexBuffer, VulkanicGalBridge.HANDLE_BUFFER));
			created.add(new HandleToDestroy(uniformBuffer, VulkanicGalBridge.HANDLE_BUFFER));
			created.add(new HandleToDestroy(texture, VulkanicGalBridge.HANDLE_TEXTURE));
			created.add(new HandleToDestroy(sampler, VulkanicGalBridge.HANDLE_SAMPLER));
			created.add(new HandleToDestroy(vertex, VulkanicGalBridge.HANDLE_SHADER_MODULE));
			created.add(new HandleToDestroy(fragment, VulkanicGalBridge.HANDLE_SHADER_MODULE));
			VulkanicGalBridge.ResourceResults dependent = bridge.resourceBatch(
				bridge.resourceBatchBuilder()
					.textureView(10, key.label("texture-view"), texture, VulkanicGalBridge.FORMAT_RGBA8)
					.resourceLayout(20, key.label("resource-layout"),
						new VulkanicGalBridge.BindingDesc(0, VulkanicGalBridge.BINDING_UNIFORM_BUFFER, 1, false),
						new VulkanicGalBridge.BindingDesc(1, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE, 1, false),
						new VulkanicGalBridge.BindingDesc(2, VulkanicGalBridge.BINDING_SAMPLER, 1, false))
					.build());
			recorder.recordResourceBatch(dependent);
			long textureView = dependent.handle(0);
			long resourceLayout = dependent.handle(1);
			created.add(new HandleToDestroy(textureView, VulkanicGalBridge.HANDLE_TEXTURE_VIEW));
			created.add(new HandleToDestroy(resourceLayout, VulkanicGalBridge.HANDLE_RESOURCE_LAYOUT));
			VulkanicGalBridge.ResourceResults set = bridge.resourceBatch(
				bridge.resourceBatchBuilder()
					.resourceSet(21, key.label("resource-set"), resourceLayout,
						new VulkanicGalBridge.Binding(0, 0, uniformBuffer, VulkanicGalBridge.BINDING_UNIFORM_BUFFER),
						new VulkanicGalBridge.Binding(1, 0, textureView, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE),
						new VulkanicGalBridge.Binding(2, 0, sampler, VulkanicGalBridge.BINDING_SAMPLER))
					.pipelineLayout(30, key.label("pipeline-layout"), resourceLayout)
					.build());
			recorder.recordResourceBatch(set);
			long resourceSet = set.handle(0);
			long pipelineLayout = set.handle(1);
			created.add(new HandleToDestroy(resourceSet, VulkanicGalBridge.HANDLE_RESOURCE_SET));
			created.add(new HandleToDestroy(pipelineLayout, VulkanicGalBridge.HANDLE_PIPELINE_LAYOUT));
			VulkanicGalBridge.ResourceResults pipeline = bridge.resourceBatch(
				GuiPipelineLibrary.pipeline(bridge.resourceBatchBuilder(), textureGroup, 31, key.label("pipeline"), pipelineLayout, vertex, fragment)
					.build());
			recorder.recordResourceBatch(pipeline);
			long graphicsPipeline = pipeline.handle(0);
			created.add(new HandleToDestroy(graphicsPipeline, VulkanicGalBridge.HANDLE_GRAPHICS_PIPELINE));
			CachedResources resources = new CachedResources(
				key,
				uploadBuffer,
				indexBuffer,
				uniformBuffer,
				texture,
				sampler,
				vertex,
				fragment,
				textureView,
				resourceLayout,
				resourceSet,
				pipelineLayout,
				graphicsPipeline
			);
			uploadPersistentResources(bridge, textureGroup, resources, recorder);
			recorder.resourcesCreated(resources.handlesInDestroyOrder().size());
			return resources;
		} catch (RuntimeException error) {
			try {
				destroyHandles(bridge, created, recorder);
			} catch (RuntimeException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			throw error;
		}
	}

	private void uploadPersistentResources(
		VulkanicGalBridge bridge,
		RustGalGuiRenderer.TextureGroup textureGroup,
		CachedResources resources,
		Recorder recorder
	) {
		GuiSpriteAtlas.TextureAtlas atlas = GuiSpriteAtlas.atlasFor(textureGroup);
		VulkanicGalBridge.Status upload = bridge.submit(
			bridge.submissionBatchBuilder(resources.key().label("upload"))
				.hostWrite(resources.uploadBuffer(), 0, atlas.bytes())
				.barrier(resources.uploadBuffer(), VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_TRANSFER_SRC, false)
				.hostWrite(resources.indexBuffer(), 0, GuiBatchBuilder.INDEX_BYTES)
				.barrier(resources.indexBuffer(), VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
				.barrier(resources.texture(), VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_TRANSFER_DST, true)
				.copyBufferToTexture(resources.uploadBuffer(), resources.texture(), atlas.width(), atlas.height())
				.barrier(resources.texture(), VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, true)
				.build());
		recorder.recordUploadStatus(upload);
	}

	private void destroyHandles(VulkanicGalBridge bridge, List<HandleToDestroy> handles, Recorder recorder) {
		if (bridge == null || handles.isEmpty()) {
			return;
		}
		VulkanicGalBridge.ResourceBatchBuilder destroy = bridge.resourceBatchBuilder();
		for (int i = handles.size() - 1; i >= 0; i--) {
			HandleToDestroy handle = handles.get(i);
			destroy.destroy(handle.handle(), handle.kind());
			recorder.resourceDestroyed();
		}
		recorder.recordResourceBatch(bridge.resourceBatch(destroy.build()));
	}

	interface Recorder {
		void recordResourceBatch(VulkanicGalBridge.ResourceResults results);

		void recordUploadStatus(VulkanicGalBridge.Status status);

		void resourcesCreated(int count);

		void resourceDestroyed();
	}

	record Lookup(CachedResources resources, boolean cacheHit, long createNanos) {
	}

	record CacheKey(String kind, String semanticId, long generation) {
		String label(String suffix) {
			return kind + "." + semanticId + ".gen" + generation + "." + suffix;
		}
	}

	record CachedResources(
		CacheKey key,
		long uploadBuffer,
		long indexBuffer,
		long uniformBuffer,
		long texture,
		long sampler,
		long vertexShader,
		long fragmentShader,
		long textureView,
		long resourceLayout,
		long resourceSet,
		long pipelineLayout,
		long pipeline
	) {
		boolean sameBindingsAs(CachedResources other) {
			return this.pipeline == other.pipeline
				&& this.pipelineLayout == other.pipelineLayout
				&& this.resourceSet == other.resourceSet
				&& this.indexBuffer == other.indexBuffer
				&& this.uniformBuffer == other.uniformBuffer;
		}

		List<HandleToDestroy> handlesInDestroyOrder() {
			return List.of(
				new HandleToDestroy(pipeline, VulkanicGalBridge.HANDLE_GRAPHICS_PIPELINE),
				new HandleToDestroy(pipelineLayout, VulkanicGalBridge.HANDLE_PIPELINE_LAYOUT),
				new HandleToDestroy(resourceSet, VulkanicGalBridge.HANDLE_RESOURCE_SET),
				new HandleToDestroy(resourceLayout, VulkanicGalBridge.HANDLE_RESOURCE_LAYOUT),
				new HandleToDestroy(textureView, VulkanicGalBridge.HANDLE_TEXTURE_VIEW),
				new HandleToDestroy(fragmentShader, VulkanicGalBridge.HANDLE_SHADER_MODULE),
				new HandleToDestroy(vertexShader, VulkanicGalBridge.HANDLE_SHADER_MODULE),
				new HandleToDestroy(sampler, VulkanicGalBridge.HANDLE_SAMPLER),
				new HandleToDestroy(texture, VulkanicGalBridge.HANDLE_TEXTURE),
				new HandleToDestroy(uniformBuffer, VulkanicGalBridge.HANDLE_BUFFER),
				new HandleToDestroy(indexBuffer, VulkanicGalBridge.HANDLE_BUFFER),
				new HandleToDestroy(uploadBuffer, VulkanicGalBridge.HANDLE_BUFFER)
			);
		}
	}

	private record HandleToDestroy(long handle, int kind) {
	}
}
