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
import net.vulkanic.resources.VulkanicTextureFormat;
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
		this.uniformOffsetAlignment = net.vulkanic.VulkanicAPI.getInteger(net.vulkanic.VulkanicAPI.getImmediateContext(), 35380); // GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT
		net.vulkanic.CommandContext ctx = net.vulkanic.VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.setCapabilityEnabled(ctx, 34895, true); // GL_PROGRAM_POINT_SIZE
		// Register with the OpenGLBackend so it can delegate buffer/texture
		// creation back here (using our BufferStorage, DirectStateAccess, etc.)
		// rather than duplicating that logic.
		if (net.vulkanic.VulkanicAPI.getBackend() instanceof net.vulkanic.backends.opengl.OpenGLBackend openGLBackend) {
			openGLBackend.setGlDevice(this);
		}
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
		String label = this.debugLabels.exists() && supplier != null ? supplier.get() : null;
		return this.createTexture(label, i, textureFormat, j, k, l, m);
	}

	@Override
	public GpuTexture createTexture(@Nullable String label, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		// Delegate to Vulkanic — GlDevice is a thin facade for texture creation.
		// VulkanicAPI dispatches to OpenGLBackend which calls createGlTexture() below.
		VulkanicTextureFormat vkFmt = switch (textureFormat) {
			case RGBA8   -> VulkanicTextureFormat.RGBA8;
			case RED8    -> VulkanicTextureFormat.RED8;
			case RED8I   -> VulkanicTextureFormat.RED8I;
			case DEPTH32 -> VulkanicTextureFormat.DEPTH32;
		};
		return (GpuTexture) net.vulkanic.VulkanicAPI.createVulkanicTexture(label, i, vkFmt, j, k, l, m);
	}

	/**
	 * Raw GL texture allocation called by {@link net.vulkanic.backends.opengl.OpenGLBackend}
	 * to avoid a circular call through {@link net.vulkanic.VulkanicAPI}.
	 *
	 * <p>This method is intentionally kept accessible so that only the
	 * OpenGL backend can reach it.
	 */
	public GlTexture createGlTexture(@Nullable String label, int usage, VulkanicTextureFormat format,
	                                   int width, int height, int depthOrLayers, int mipLevels) {
		if (mipLevels < 1) {
			throw new IllegalArgumentException("mipLevels must be at least 1");
		} else if (depthOrLayers < 1) {
			throw new IllegalArgumentException("depthOrLayers must be at least 1");
		} else {
			boolean cubemap = (usage & 16) != 0;
			if (cubemap) {
				if (width != height) {
					throw new IllegalArgumentException("Cubemap compatible textures must be square, but size is " + width + "x" + height);
				}
				if (depthOrLayers % 6 != 0) {
					throw new IllegalArgumentException("Cubemap compatible textures must have a layer count with a multiple of 6, was " + depthOrLayers);
				}
				if (depthOrLayers > 6) {
					throw new UnsupportedOperationException("Array textures are not yet supported");
				}
			} else if (depthOrLayers > 1) {
				throw new UnsupportedOperationException("Array or 3D textures are not yet supported");
			}

			GlStateManager.clearGlErrors();
			int n = GlStateManager._genTexture();
			if (label == null) {
				label = String.valueOf(n);
			}

			int target;
			if (cubemap) {
				net.vulkanic.VulkanicAPI.bindTexture(net.vulkanic.VulkanicAPI.getImmediateContext(), 34067, n);
				target = 34067; // GL_TEXTURE_CUBE_MAP
			} else {
				GlStateManager._bindTexture(n);
				target = 3553; // GL_TEXTURE_2D
			}

			GlStateManager._texParameter(target, 33085, mipLevels - 1);
			GlStateManager._texParameter(target, 33082, 0);
			GlStateManager._texParameter(target, 33083, mipLevels - 1);
			if (format.hasDepthAspect()) {
				GlStateManager._texParameter(target, 34892, 0);
			}

			int internalFmt = GlConst.toGlInternalId(format);
			int externalFmt = GlConst.toGlExternalId(format);
			int glType      = GlConst.toGlType(format);

			if (cubemap) {
				for (int face : GlConst.CUBEMAP_TARGETS) {
					for (int mip = 0; mip < mipLevels; mip++) {
						GlStateManager._texImage2D(face, mip, internalFmt, width >> mip, height >> mip, 0, externalFmt, glType, null);
					}
				}
			} else {
				for (int mip = 0; mip < mipLevels; mip++) {
					GlStateManager._texImage2D(target, mip, internalFmt, width >> mip, height >> mip, 0, externalFmt, glType, null);
				}
			}

			int err = GlStateManager._getError();
			if (err == 1285) {
				throw new GpuOutOfMemoryException("Could not allocate texture of " + width + "x" + height + " for " + label);
			} else if (err != 0) {
				throw new IllegalStateException("OpenGL error " + err);
			} else {
				// Map VulkanicTextureFormat back to the legacy TextureFormat that GlTexture requires.
				TextureFormat legacyFmt = switch (format) {
					case RGBA8   -> TextureFormat.RGBA8;
					case RED8    -> TextureFormat.RED8;
					case RED8I   -> TextureFormat.RED8I;
					case DEPTH32 -> TextureFormat.DEPTH32;
				};
				GlTexture glTexture = new GlTexture(usage, label, legacyFmt, width, height, depthOrLayers, mipLevels, n);
				this.debugLabels.applyLabel(glTexture);
				return glTexture;
			}
		}
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture) {
		return (GpuTextureView) net.vulkanic.VulkanicAPI.createVulkanicTextureView(
				(net.vulkanic.resources.VulkanicTexture) gpuTexture);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int j) {
		return (GpuTextureView) net.vulkanic.VulkanicAPI.createVulkanicTextureView(
				(net.vulkanic.resources.VulkanicTexture) gpuTexture, i, j);
	}


	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, int j) {
		if (j <= 0) {
			throw new IllegalArgumentException("Buffer size must be greater than zero");
		} else {
			GlStateManager.clearGlErrors();
			// Delegate to Vulkanic — GlDevice is now a thin facade for buffer creation.
			// VulkanicAPI dispatches to OpenGLBackend, which uses our BufferStorage
			// (set via OpenGLBackend.setGlDevice) to create the buffer with DSA and
			// persistent-mapping support.  The returned object is an instanceof GlBuffer
			// so all downstream GlCommandEncoder casts continue to work.
			GlBuffer glBuffer = (GlBuffer) net.vulkanic.VulkanicAPI.createVulkanicBuffer(i, j);
			int k = GlStateManager._getError();
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
			GlStateManager.clearGlErrors();
			long l = byteBuffer.remaining();
			GlBuffer glBuffer = (GlBuffer) net.vulkanic.VulkanicAPI.createVulkanicBuffer(i, byteBuffer);
			int j = GlStateManager._getError();
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
			: GlStateManager._getString(7937) + " GL version " + GlStateManager._getString(7938) + ", " + GlStateManager._getString(7936);
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
		return GlStateManager._getString(7937);
	}

	@Override
	public String getVendor() {
		return GlStateManager._getString(7936);
	}

	@Override
	public String getBackendName() {
		return "OpenGL";
	}

	@Override
	public String getVersion() {
		return GlStateManager._getString(7938);
	}

	private static int getMaxSupportedTextureSize() {
		int i = GlStateManager._getInteger(3379);

		for (int j = Math.max(32768, i); j >= 1024; j >>= 1) {
			GlStateManager._texImage2D(32868, 0, 6408, j, j, 0, 6408, 5121, null);
			int k = GlStateManager._getTexLevelParameter(32868, 0, 4096);
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
		String string = GlStateManager._getString(7937);
		if (string.contains("AMD")) {
			sacrificeShaderToOpenGlAndAmd();
		}
	}

	private static void sacrificeShaderToOpenGlAndAmd() {
		int i = GlStateManager.glCreateShader(35633);
		int j = GlStateManager.glCreateProgram();
		GlStateManager.glAttachShader(j, i);
		GlStateManager.glDeleteShader(i);
		GlStateManager.glDeleteProgram(j);
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
			int i = GlStateManager.glCreateShader(GlConst.toGl(shaderCompilationKey.type));
			GlStateManager.glShaderSource(i, string2);
			GlStateManager.glCompileShader(i);
			if (GlStateManager.glGetShaderi(i, 35713) == 0) {
				String string3 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
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
