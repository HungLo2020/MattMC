package net.blaze3d.opengl;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.ScissorState;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.VulkanicTextureView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class GlRenderPass implements RenderPass {
	protected static final int MAX_VERTEX_BUFFERS = 1;
	public static final boolean VALIDATION = SharedConstants.IS_RUNNING_IN_IDE;
	private static final Logger LOGGER = LoggerFactory.getLogger(GlRenderPass.class);
	private final GlCommandEncoder encoder;
	private final boolean hasDepthTexture;
	private final int framebuffer;
	@Nullable
	private final VulkanicRenderTargetDescriptor renderTargetDescriptor;
	private boolean closed;
	@Nullable
	public GlRenderPipeline pipeline;
	protected final GpuBuffer[] vertexBuffers = new GpuBuffer[1];
	@Nullable
	protected GpuBuffer indexBuffer;
	protected VertexFormat.IndexType indexType = VertexFormat.IndexType.INT;
	private final ScissorState scissorState = new ScissorState();
	protected final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
	private final HashMap<String, net.vulkanic.VulkanicBufferSlice> uniformResourceSlices = new HashMap<>();
	public final HashMap<String, GpuTextureView> samplers = new HashMap<>();
	private final HashMap<String, VulkanicTextureView> samplerResourceViews = new HashMap<>();
	protected final Set<String> dirtyUniforms = new HashSet();
	protected int pushedDebugGroups;

	public GlRenderPass(GlCommandEncoder glCommandEncoder, boolean bl, int framebuffer) {
		this(glCommandEncoder, bl, framebuffer, null);
	}

	public GlRenderPass(GlCommandEncoder glCommandEncoder, boolean bl, int framebuffer, @Nullable VulkanicRenderTargetDescriptor renderTargetDescriptor) {
		this.encoder = glCommandEncoder;
		this.hasDepthTexture = bl;
		this.framebuffer = framebuffer;
		this.renderTargetDescriptor = renderTargetDescriptor;
	}

	public boolean hasDepthTexture() {
		return this.hasDepthTexture;
	}

	public int getFramebuffer() {
		return this.framebuffer;
	}

	@Nullable
	public VulkanicRenderTargetDescriptor getRenderTargetDescriptor() {
		return this.renderTargetDescriptor;
	}

	@Override
	public void pushDebugGroup(Supplier<String> supplier) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.pushedDebugGroups++;
			this.encoder.getDevice().debugLabels().pushDebugGroup(supplier);
		}
	}

	@Override
	public void popDebugGroup() {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else if (this.pushedDebugGroups == 0) {
			throw new IllegalStateException("Can't pop more debug groups than was pushed!");
		} else {
			this.pushedDebugGroups--;
			this.encoder.getDevice().debugLabels().popDebugGroup();
		}
	}

	@Override
	public void setPipeline(RenderPipeline renderPipeline) {
		if (this.pipeline == null || this.pipeline.info() != renderPipeline) {
			this.dirtyUniforms.addAll(this.uniforms.keySet());
			this.dirtyUniforms.addAll(this.samplers.keySet());
		}

		this.pipeline = this.encoder.getDevice().getOrCompilePipeline(renderPipeline);
	}

	@Override
	public void bindSampler(String string, @Nullable GpuTextureView gpuTextureView) {
		this.closeSamplerResourceView(string);
		if (gpuTextureView == null) {
			this.samplers.remove(string);
		} else {
			this.samplers.put(string, gpuTextureView);
			this.samplerResourceViews.put(string, this.encoder.createSamplerResourceView(gpuTextureView));
		}

		VulkanicAPI.recordScopedCompositeColortex0RenderPassBinding(
			this.pipeline == null ? null : this.pipeline.info(),
			string,
			gpuTextureView,
			-1,
			"opengl-renderpass-bindSampler"
		);
		this.dirtyUniforms.add(string);
	}

	public boolean bindLegacySampler(String string, int textureId) {
		this.closeSamplerResourceView(string);
		this.samplers.remove(string);
		if (textureId <= 0) {
			this.dirtyUniforms.add(string);
			return false;
		}

		VulkanicTextureView resourceView = this.encoder.createLegacySamplerResourceView(textureId);
		if (resourceView == null) {
			this.dirtyUniforms.add(string);
			return false;
		}

		this.samplerResourceViews.put(string, resourceView);
		VulkanicAPI.recordScopedCompositeColortex0RenderPassLegacyBinding(
			this.pipeline == null ? null : this.pipeline.info(),
			string,
			textureId,
			-1,
			"opengl-renderpass-bindLegacySampler"
		);
		this.dirtyUniforms.add(string);
		return true;
	}

	@Nullable
	VulkanicTextureView getSamplerResourceView(String string) {
		VulkanicTextureView resourceView = this.samplerResourceViews.get(string);
		if (resourceView != null) {
			return resourceView;
		}

		GpuTextureView recoveredView = this.encoder.recoverSamplerView(string);
		if (recoveredView == null) {
			return null;
		}

		this.samplers.putIfAbsent(string, recoveredView);
		VulkanicTextureView managedView = this.encoder.createSamplerResourceView(recoveredView);
		VulkanicTextureView existingView = this.samplerResourceViews.putIfAbsent(string, managedView);
		if (existingView != null) {
			managedView.close();
			return existingView;
		}

		return managedView;
	}

	private void closeSamplerResourceView(String name) {
		VulkanicTextureView previousView = this.samplerResourceViews.remove(name);
		if (previousView != null) {
			previousView.close();
		}
	}

	private void closeSamplerResourceViews() {
		for (VulkanicTextureView view : this.samplerResourceViews.values()) {
			view.close();
		}
		this.samplerResourceViews.clear();
	}

	@Override
	public void setUniform(String string, GpuBuffer gpuBuffer) {
		this.setUniform(string, gpuBuffer.slice());
	}

	@Override
	public void setUniform(String string, GpuBufferSlice gpuBufferSlice) {
		int i = this.encoder.getDevice().getUniformOffsetAlignment();
		if (gpuBufferSlice.offset() % i > 0) {
			throw new IllegalArgumentException("Uniform buffer offset must be aligned to " + i);
		} else {
			this.uniforms.put(string, gpuBufferSlice);
			this.uniformResourceSlices.put(
				string,
				new net.vulkanic.VulkanicBufferSlice(
					VulkanicAPI.resolveVulkanicBuffer(gpuBufferSlice.buffer()),
					gpuBufferSlice.offset(),
					gpuBufferSlice.length()
				)
			);
			if (VulkanicAPI.shouldTraceStandaloneUniform(string)) {
				LOGGER.info(
					"StandaloneLookupKeyTrace stage=renderpass-store lookupType=resource-name key={} keyHash={} mapSize={} containsExactKey=yes availableKeys={} note=render-pass-uniform-slice-stored",
					string,
					Integer.toHexString(string.hashCode()),
					this.uniformResourceSlices.size(),
					this.describeUniformResourceSliceKeys()
				);
			}
			this.dirtyUniforms.add(string);
		}
	}

	public void setUniform(String string, net.vulkanic.VulkanicBufferSlice vulkanicBufferSlice) {
		this.uniformResourceSlices.put(string, vulkanicBufferSlice);
		if (VulkanicAPI.shouldTraceStandaloneUniform(string)) {
			LOGGER.info(
				"StandaloneLookupKeyTrace stage=renderpass-store renderPassId={} lookupType=resource-name key={} keyHash={} mapSize={} containsExactKey=yes availableKeys={} note=standalone-buffer-slice-stored",
				System.identityHashCode(this),
				string,
				Integer.toHexString(string.hashCode()),
				this.uniformResourceSlices.size(),
				this.describeUniformResourceSliceKeys()
			);
		}
		this.dirtyUniforms.add(string);
	}

	@Nullable
	net.vulkanic.VulkanicBufferSlice getUniformResourceSlice(String string) {
		net.vulkanic.VulkanicBufferSlice slice = this.uniformResourceSlices.get(string);
		if (VulkanicAPI.shouldTraceStandaloneUniform(string)) {
			LOGGER.info(
				"StandaloneLookupKeyTrace stage=renderpass-lookup renderPassId={} lookupType=resource-name key={} keyHash={} mapSize={} containsExactKey={} availableKeys={} sliceAvailable={} note=uniformResourceSlices-get",
				System.identityHashCode(this),
				string,
				Integer.toHexString(string.hashCode()),
				this.uniformResourceSlices.size(),
				this.uniformResourceSlices.containsKey(string) ? "yes" : "no",
				this.describeUniformResourceSliceKeys(),
				slice != null ? "yes" : "no"
			);
		}
		return slice;
	}

	private String describeUniformResourceSliceKeys() {
		java.util.ArrayList<String> keys = new java.util.ArrayList<>(this.uniformResourceSlices.keySet());
		java.util.Collections.sort(keys);
		if (keys.size() > 8) {
			return keys.subList(0, 8) + "...(" + keys.size() + " total)";
		}
		return keys.toString();
	}

	@Override
	public void enableScissor(int i, int j, int k, int l) {
		this.scissorState.enable(i, j, k, l);
	}

	@Override
	public void disableScissor() {
		this.scissorState.disable();
	}

	public boolean isScissorEnabled() {
		return this.scissorState.enabled();
	}

	public int getScissorX() {
		return this.scissorState.x();
	}

	public int getScissorY() {
		return this.scissorState.y();
	}

	public int getScissorWidth() {
		return this.scissorState.width();
	}

	public int getScissorHeight() {
		return this.scissorState.height();
	}

	@Override
	public void setVertexBuffer(int i, GpuBuffer gpuBuffer) {
		if (i >= 0 && i < 1) {
			this.vertexBuffers[i] = gpuBuffer;
		} else {
			throw new IllegalArgumentException("Vertex buffer slot is out of range: " + i);
		}
	}

	@Override
	public void setIndexBuffer(@Nullable GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
		this.indexBuffer = gpuBuffer;
		this.indexType = indexType;
	}

	@Override
	public void drawIndexed(int i, int j, int k, int l) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.encoder.executeDraw(this, i, j, k, this.indexType, l);
		}
	}

	@Override
	public <T> void drawMultipleIndexed(
		Collection<RenderPass.Draw<T>> collection,
		@Nullable GpuBuffer gpuBuffer,
		@Nullable VertexFormat.IndexType indexType,
		Collection<String> collection2,
		T object
	) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.encoder.executeDrawMultiple(this, collection, gpuBuffer, indexType, collection2, object);
		}
	}

	@Override
	public void draw(int i, int j) {
		if (this.closed) {
			throw new IllegalStateException("Can't use a closed render pass");
		} else {
			this.encoder.executeDraw(this, i, 0, j, null, 1);
		}
	}

	@Override
	public void close() {
		if (!this.closed) {
			if (this.pushedDebugGroups > 0) {
				throw new IllegalStateException("Render pass had debug groups left open!");
			}

				this.closed = true;
				try {
					this.encoder.finishRenderPass();
					VulkanicAPI.recordScopedCompositeColortex0ProducerCompletion(
						this.renderTargetDescriptor,
						this.framebuffer,
						this.pipeline == null ? null : this.pipeline.info(),
						this.irisCustomPass,
						"opengl-renderpass-close"
					);
				} finally {
					this.closeSamplerResourceViews();
				}
			}
	}
	
	// Iris compatibility
	private net.irisshaders.iris.mixinterface.CustomPass irisCustomPass;
	
	public void iris$setCustomPass(net.irisshaders.iris.mixinterface.CustomPass pass) {
		this.irisCustomPass = pass;
	}
	
	public net.irisshaders.iris.mixinterface.CustomPass iris$getCustomPass() {
		return this.irisCustomPass;
	}
}
