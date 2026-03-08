package net.blaze3d.opengl;

import net.blaze3d.GpuOutOfMemoryException;
import net.blaze3d.GraphicsWorkarounds;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.preprocessor.GlslPreprocessor;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicAPI;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlDevice implements GpuDevice {
	private static final Logger LOGGER = LogUtils.getLogger();
	protected static boolean USE_GL_ARB_vertex_attrib_binding = true;
	protected static boolean USE_GL_KHR_debug = true;
	protected static boolean USE_GL_EXT_debug_label = true;
	protected static boolean USE_GL_ARB_debug_output = true;
	protected static boolean USE_GL_ARB_direct_state_access = true;
	protected static boolean USE_GL_ARB_buffer_storage = true;
	private final CommandEncoder encoder;
	@Nullable
	private final GlDebug debugLog;
	private final GlDebugLabel debugLabels;
	private final int maxSupportedTextureSize;
	private final DirectStateAccess directStateAccess;
	private final BiFunction<ResourceLocation, ShaderType, String> defaultShaderSource;
	private final Map<RenderPipeline, GlRenderPipeline> pipelineCache = new IdentityHashMap();
	private final Map<GlDevice.ShaderCompilationKey, GlShaderModule> shaderCache = new HashMap();
	private final VertexArrayCache vertexArrayCache;
	private final BufferStorage bufferStorage;
	private final Set<String> enabledExtensions = new HashSet();
	private final int uniformOffsetAlignment;
	// Iris: Track missing shaders to avoid log spam
	private Set<RenderPipeline> missingShaders = new HashSet();

	public GlDevice(long l, int i, boolean bl, BiFunction<ResourceLocation, ShaderType, String> biFunction, boolean bl2) {
		GLFW.glfwMakeContextCurrent(l);
		net.vulkanic.GraphicsCapabilities gLCapabilities = net.vulkanic.VulkanicAPI.initializeGraphicsCapabilities();
		int j = getMaxSupportedTextureSize();
		GLFW.glfwSetWindowSizeLimits(l, -1, -1, j, j);
		GraphicsWorkarounds graphicsWorkarounds = GraphicsWorkarounds.get(this);
		this.debugLog = GlDebug.enableDebugCallback(i, bl, this.enabledExtensions);
		this.debugLabels = GlDebugLabel.create(gLCapabilities, bl2, this.enabledExtensions);
		this.vertexArrayCache = VertexArrayCache.create(this.debugLabels, this.enabledExtensions);
		this.bufferStorage = BufferStorage.create(this.enabledExtensions);
		this.directStateAccess = DirectStateAccess.create(gLCapabilities, this.enabledExtensions, graphicsWorkarounds);
		this.maxSupportedTextureSize = j;
		this.defaultShaderSource = biFunction;
		this.encoder = new GlCommandEncoder(this);
		this.uniformOffsetAlignment = net.vulkanic.VulkanicAPI.getUniformBufferOffsetAlignment(net.vulkanic.VulkanicAPI.getImmediateContext());
		net.vulkanic.CommandContext ctx = net.vulkanic.VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.setProgramPointSizeEnabled(ctx, true);
		// Register this device with the Vulkanic backend so it can delegate
		// device-level operations (pipeline compilation, etc.) back to this GlDevice.
		net.vulkanic.VulkanicAPI.registerDevice(this);
	}

	public GlDebugLabel debugLabels() {
		return this.debugLabels;
	}

	@Override
	public CommandEncoder createCommandEncoder() {
		return this.encoder;
	}

	@Override
	public GpuTexture createTexture(@Nullable Supplier<String> supplier, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		return this.createTexture(this.debugLabels.exists() && supplier != null ? (String)supplier.get() : null, i, textureFormat, j, k, l, m);
	}

	@Override
	public GpuTexture createTexture(@Nullable String string, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		if (m < 1) {
			throw new IllegalArgumentException("mipLevels must be at least 1");
		} else if (l < 1) {
			throw new IllegalArgumentException("depthOrLayers must be at least 1");
		} else {
			boolean bl = (i & 16) != 0;
			if (bl) {
				if (j != k) {
					throw new IllegalArgumentException("Cubemap compatible textures must be square, but size is " + j + "x" + k);
				}

				if (l % 6 != 0) {
					throw new IllegalArgumentException("Cubemap compatible textures must have a layer count with a multiple of 6, was " + l);
				}

				if (l > 6) {
					throw new UnsupportedOperationException("Array textures are not yet supported");
				}
			} else if (l > 1) {
				throw new UnsupportedOperationException("Array or 3D textures are not yet supported");
			}

			while (net.vulkanic.VulkanicAPI.getError(net.vulkanic.VulkanicAPI.getImmediateContext()) != 0) {
			}
			int n = GlStateManager._genTexture();
			if (string == null) {
				string = String.valueOf(n);
			}

			net.vulkanic.CommandContext ctx = net.vulkanic.VulkanicAPI.getImmediateContext();

			int o;
			if (bl) {
				net.vulkanic.VulkanicAPI.bindCubemapTexture(ctx, n);
				o = net.vulkanic.VulkanicAPI.GL_TEXTURE_CUBE_MAP;
			} else {
				VulkanicAPI.bindTexture2D(VulkanicAPI.getImmediateContext(), n);
				o = net.vulkanic.VulkanicAPI.GL_TEXTURE_2D;
			}

			net.vulkanic.VulkanicAPI.setTextureMaxLevel(ctx, o, m - 1);
			net.vulkanic.VulkanicAPI.setTextureMinLod(ctx, o, 0);
			net.vulkanic.VulkanicAPI.setTextureMaxLod(ctx, o, m - 1);
			if (textureFormat.hasDepthAspect()) {
				net.vulkanic.VulkanicAPI.disableTextureCompareMode(ctx, o);
			}

			if (bl) {
				for (int p : GlConst.CUBEMAP_TARGETS) {
					for (int q = 0; q < m; q++) {
						GlStateManager._texImage2D(
							p, q, GlConst.toGlInternalId(textureFormat), j >> q, k >> q, 0, GlConst.toGlExternalId(textureFormat), GlConst.toGlType(textureFormat), null
						);
					}
				}
			} else {
				for (int r = 0; r < m; r++) {
					GlStateManager._texImage2D(
						o, r, GlConst.toGlInternalId(textureFormat), j >> r, k >> r, 0, GlConst.toGlExternalId(textureFormat), GlConst.toGlType(textureFormat), null
					);
				}
			}

			int r = net.vulkanic.VulkanicAPI.getError(net.vulkanic.VulkanicAPI.getImmediateContext());
			if (r == 1285) {
				throw new GpuOutOfMemoryException("Could not allocate texture of " + j + "x" + k + " for " + string);
			} else if (r != 0) {
				throw new IllegalStateException("OpenGL error " + r);
			} else {
				GlTexture glTexture = new GlTexture(i, string, textureFormat, j, k, l, m, n);
				this.debugLabels.applyLabel(glTexture);
				return glTexture;
			}
		}
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture) {
		return this.createTextureView(gpuTexture, 0, gpuTexture.getMipLevels());
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int j) {
		if (gpuTexture.isClosed()) {
			throw new IllegalArgumentException("Can't create texture view with closed texture");
		} else if (i >= 0 && i + j <= gpuTexture.getMipLevels()) {
			return new GlTextureView((GlTexture)gpuTexture, i, j);
		} else {
			throw new IllegalArgumentException(
				j + " mip levels starting from " + i + " would be out of range for texture with only " + gpuTexture.getMipLevels() + " mip levels"
			);
		}
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, int j) {
		if (j <= 0) {
			throw new IllegalArgumentException("Buffer size must be greater than zero");
		} else {
			while (net.vulkanic.VulkanicAPI.getError(net.vulkanic.VulkanicAPI.getImmediateContext()) != 0) {
			}
			GlBuffer glBuffer = this.bufferStorage.createBuffer(this.directStateAccess, supplier, i, j);
			int k = net.vulkanic.VulkanicAPI.getError(net.vulkanic.VulkanicAPI.getImmediateContext());
			if (k == 1285) {
				throw new GpuOutOfMemoryException("Could not allocate buffer of " + j + " for " + supplier);
			} else if (k != 0) {
				throw new IllegalStateException("OpenGL error " + k);
			} else {
				this.debugLabels.applyLabel(glBuffer);
				return glBuffer;
			}
		}
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
		if (!byteBuffer.hasRemaining()) {
			throw new IllegalArgumentException("Buffer source must not be empty");
		} else {
			while (net.vulkanic.VulkanicAPI.getError(net.vulkanic.VulkanicAPI.getImmediateContext()) != 0) {
			}
			long l = byteBuffer.remaining();
			GlBuffer glBuffer = this.bufferStorage.createBuffer(this.directStateAccess, supplier, i, byteBuffer);
			int j = net.vulkanic.VulkanicAPI.getError(net.vulkanic.VulkanicAPI.getImmediateContext());
			if (j == 1285) {
				throw new GpuOutOfMemoryException("Could not allocate buffer of " + l + " for " + supplier);
			} else if (j != 0) {
				throw new IllegalStateException("OpenGL error " + j);
			} else {
				this.debugLabels.applyLabel(glBuffer);
				return glBuffer;
			}
		}
	}

	@Override
	public String getImplementationInformation() {
		return GLFW.glfwGetCurrentContext() == 0L
			? "NO CONTEXT"
			: net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_RENDERER)
				+ " GL version "
				+ net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_VERSION)
				+ ", "
				+ net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_VENDOR);
	}

	@Override
	public List<String> getLastDebugMessages() {
		return this.debugLog == null ? Collections.emptyList() : this.debugLog.getLastOpenGlDebugMessages();
	}

	@Override
	public boolean isDebuggingEnabled() {
		return this.debugLog != null;
	}

	@Override
	public String getRenderer() {
		return net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_RENDERER);
	}

	@Override
	public String getVendor() {
		return net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_VENDOR);
	}

	@Override
	public String getBackendName() {
		return "OpenGL";
	}

	@Override
	public String getVersion() {
		return net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_VERSION);
	}

	private static int getMaxSupportedTextureSize() {
		int i = net.vulkanic.VulkanicAPI.getInteger(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_MAX_TEXTURE_SIZE);

		for (int j = Math.max(32768, i); j >= 1024; j >>= 1) {
			GlStateManager._texImage2D(
				net.vulkanic.VulkanicAPI.GL_PROXY_TEXTURE_2D,
				0,
				net.vulkanic.VulkanicAPI.GL_RGBA,
				j,
				j,
				0,
				net.vulkanic.VulkanicAPI.GL_RGBA,
				net.vulkanic.VulkanicAPI.GL_UNSIGNED_BYTE,
				null
			);
			int k = net.vulkanic.VulkanicAPI.getTextureLevelParameter(
				net.vulkanic.VulkanicAPI.getImmediateContext(),
				net.vulkanic.VulkanicAPI.GL_PROXY_TEXTURE_2D,
				0,
				net.vulkanic.VulkanicAPI.GL_TEXTURE_WIDTH
			);
			if (k != 0) {
				return j;
			}
		}

		int jx = Math.max(i, 1024);
		LOGGER.info("Failed to determine maximum texture size by probing, trying GL_MAX_TEXTURE_SIZE = {}", jx);
		return jx;
	}

	@Override
	public int getMaxTextureSize() {
		return this.maxSupportedTextureSize;
	}

	@Override
	public int getUniformOffsetAlignment() {
		return this.uniformOffsetAlignment;
	}

	@Override
	public void clearPipelineCache() {
		for (GlRenderPipeline glRenderPipeline : this.pipelineCache.values()) {
			if (glRenderPipeline.program() != GlProgram.INVALID_PROGRAM) {
				glRenderPipeline.program().close();
			}
		}

		this.pipelineCache.clear();

		for (GlShaderModule glShaderModule : this.shaderCache.values()) {
			if (glShaderModule != GlShaderModule.INVALID_SHADER) {
				glShaderModule.close();
			}
		}

		this.shaderCache.clear();
		String string = net.vulkanic.VulkanicAPI.getString(net.vulkanic.VulkanicAPI.getImmediateContext(), net.vulkanic.VulkanicAPI.GL_RENDERER);
		if (string.contains("AMD")) {
			sacrificeShaderToOpenGlAndAmd();
		}
	}

	private static void sacrificeShaderToOpenGlAndAmd() {
		int i = net.vulkanic.VulkanicAPI.createShader(net.vulkanic.VulkanicAPI.getImmediateContext(), 35633);
		int j = net.vulkanic.VulkanicAPI.createShaderProgram(net.vulkanic.VulkanicAPI.getImmediateContext());
		net.vulkanic.VulkanicAPI.attachShader(net.vulkanic.VulkanicAPI.getImmediateContext(), j, i);
		net.vulkanic.VulkanicAPI.deleteShader(net.vulkanic.VulkanicAPI.getImmediateContext(), i);
		net.vulkanic.VulkanicAPI.deleteProgram(net.vulkanic.VulkanicAPI.getImmediateContext(), j);
	}

	@Override
	public List<String> getEnabledExtensions() {
		return new ArrayList(this.enabledExtensions);
	}

	@Override
	public void close() {
		this.clearPipelineCache();
	}

	public DirectStateAccess directStateAccess() {
		return this.directStateAccess;
	}

	protected GlRenderPipeline getOrCompilePipeline(RenderPipeline renderPipeline) {
		// Iris: Check for shader overrides first
		if (renderPipeline != net.irisshaders.iris.pipeline.CompositeRenderer.COMPOSITE_PIPELINE) {
			net.irisshaders.iris.pipeline.WorldRenderingPipeline pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();

			if (pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline irisPipeline 
				&& irisPipeline.shouldOverrideShaders() 
				&& !net.irisshaders.iris.vertices.ImmediateState.bypass) {
				
				GlProgram program = overrideIrisProgram(irisPipeline, renderPipeline);

				if (program != null) {
					return new GlRenderPipeline(renderPipeline, program);
				} else if (missingShaders.add(renderPipeline)) {
					if (renderPipeline.getLocation().getNamespace().equals("minecraft")) {
						LOGGER.error("Missing program " + renderPipeline.getLocation() + " in Iris override list. This is likely an Iris bug!!!", new Throwable());
					} else {
						LOGGER.warn("Missing program " + renderPipeline.getLocation() + " in Iris override list. This is not a critical problem, but it could lead to weird rendering.", new Throwable());
					}
				}
			}
		}
		
		// Default vanilla behavior
		return (GlRenderPipeline)this.pipelineCache
			.computeIfAbsent(renderPipeline, renderPipeline2 -> this.compilePipeline(renderPipeline, this.defaultShaderSource));
	}

	// Iris: Override Iris program (merged from MixinShaderManager_Overrides)
	private static GlProgram overrideIrisProgram(net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline, RenderPipeline shaderProgram) {
		net.irisshaders.iris.pipeline.programs.ShaderKey shaderKey = net.irisshaders.iris.pipeline.IrisPipelines.getPipeline(pipeline, shaderProgram);
		return shaderKey == null ? null : pipeline.getShaderMap().getShader(shaderKey);
	}

	protected GlShaderModule getOrCompileShader(
		ResourceLocation resourceLocation, ShaderType shaderType, ShaderDefines shaderDefines, BiFunction<ResourceLocation, ShaderType, String> biFunction
	) {
		GlDevice.ShaderCompilationKey shaderCompilationKey = new GlDevice.ShaderCompilationKey(resourceLocation, shaderType, shaderDefines);
		return (GlShaderModule)this.shaderCache.computeIfAbsent(shaderCompilationKey, shaderCompilationKey2 -> this.compileShader(shaderCompilationKey, biFunction));
	}

	public GlRenderPipeline precompilePipeline(RenderPipeline renderPipeline, @Nullable BiFunction<ResourceLocation, ShaderType, String> biFunction) {
		BiFunction<ResourceLocation, ShaderType, String> biFunction2 = biFunction == null ? this.defaultShaderSource : biFunction;
		return (GlRenderPipeline)this.pipelineCache.computeIfAbsent(renderPipeline, renderPipeline2 -> this.compilePipeline(renderPipeline, biFunction2));
	}

	private GlShaderModule compileShader(GlDevice.ShaderCompilationKey shaderCompilationKey, BiFunction<ResourceLocation, ShaderType, String> biFunction) {
		String string = (String)biFunction.apply(shaderCompilationKey.id, shaderCompilationKey.type);
		if (string == null) {
			LOGGER.error("Couldn't find source for {} shader ({})", shaderCompilationKey.type, shaderCompilationKey.id);
			return GlShaderModule.INVALID_SHADER;
		} else {
			String string2 = GlslPreprocessor.injectDefines(string, shaderCompilationKey.defines);
			int i = net.vulkanic.VulkanicAPI.createShader(net.vulkanic.VulkanicAPI.getImmediateContext(), GlConst.toGl(shaderCompilationKey.type));
			net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(i, string2);
			net.vulkanic.VulkanicAPI.compileShader(net.vulkanic.VulkanicAPI.getImmediateContext(), i);
			if (net.vulkanic.VulkanicAPI.getShaderParameter(net.vulkanic.VulkanicAPI.getImmediateContext(), i, 35713) == 0) {
				String string3 = StringUtils.trim(net.vulkanic.VulkanicAPI.getShaderInfoLog(net.vulkanic.VulkanicAPI.getImmediateContext(), i));
				LOGGER.error("Couldn't compile {} shader ({}): {}", shaderCompilationKey.type.getName(), shaderCompilationKey.id, string3);
				return GlShaderModule.INVALID_SHADER;
			} else {
				GlShaderModule glShaderModule = new GlShaderModule(i, shaderCompilationKey.id, shaderCompilationKey.type);
				this.debugLabels.applyLabel(glShaderModule);
				return glShaderModule;
			}
		}
	}

	private GlRenderPipeline compilePipeline(RenderPipeline renderPipeline, BiFunction<ResourceLocation, ShaderType, String> biFunction) {
		GlShaderModule glShaderModule = this.getOrCompileShader(renderPipeline.getVertexShader(), ShaderType.VERTEX, renderPipeline.getShaderDefines(), biFunction);
		GlShaderModule glShaderModule2 = this.getOrCompileShader(
			renderPipeline.getFragmentShader(), ShaderType.FRAGMENT, renderPipeline.getShaderDefines(), biFunction
		);
		if (glShaderModule == GlShaderModule.INVALID_SHADER) {
			LOGGER.error("Couldn't compile pipeline {}: vertex shader {} was invalid", renderPipeline.getLocation(), renderPipeline.getVertexShader());
			return new GlRenderPipeline(renderPipeline, GlProgram.INVALID_PROGRAM);
		} else if (glShaderModule2 == GlShaderModule.INVALID_SHADER) {
			LOGGER.error("Couldn't compile pipeline {}: fragment shader {} was invalid", renderPipeline.getLocation(), renderPipeline.getFragmentShader());
			return new GlRenderPipeline(renderPipeline, GlProgram.INVALID_PROGRAM);
		} else {
			GlProgram glProgram;
			try {
				glProgram = GlProgram.link(glShaderModule, glShaderModule2, renderPipeline.getVertexFormat(), renderPipeline.getLocation().toString());
			} catch (ShaderManager.CompilationException var7) {
				LOGGER.error("Couldn't compile program for pipeline {}: {}", renderPipeline.getLocation(), var7);
				return new GlRenderPipeline(renderPipeline, GlProgram.INVALID_PROGRAM);
			}

			glProgram.setupUniforms(renderPipeline.getUniforms(), renderPipeline.getSamplers());
			this.debugLabels.applyLabel(glProgram);
			return new GlRenderPipeline(renderPipeline, glProgram);
		}
	}

	public VertexArrayCache vertexArrayCache() {
		return this.vertexArrayCache;
	}

	public BufferStorage getBufferStorage() {
		return this.bufferStorage;
	}

	@Environment(EnvType.CLIENT)
	record ShaderCompilationKey(ResourceLocation id, ShaderType type, ShaderDefines defines) {

		public String toString() {
			String string = this.id + " (" + this.type + ")";
			return !this.defines.isEmpty() ? string + " with " + this.defines : string;
		}
	}
}
