package net.irisshaders.iris.gl;

import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.GraphicsFeature;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicCapability;
import net.vulkanic.VulkanicIntegerQuery;
import net.vulkanic.VulkanicPolygonFace;
import net.vulkanic.VulkanicPolygonMode;
import net.vulkanic.VulkanicResourceBarriers;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;
import net.vulkanic.VulkanicTextureTarget;
import net.vulkanic.VulkanicTextureSwizzleComponent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3i;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * This class is responsible for abstracting calls to OpenGL and asserting that calls are run on the render thread.
 */
public class IrisRenderSystem {
	private static final int[] emptyArray = new int[SamplerLimits.get().getMaxTextureUnits()];
	private static GpuBufferSlice backupProjection;
	private static PerspectiveProjectionMatrixBuffer perspectiveProjectionMatrixBuffer;
	private static ProjectionType backupProjectionType;
	private static DSAAccess dsaState;
	private static boolean hasMultibind;
	private static boolean supportsCompute;
	private static boolean supportsTesselation;
	private static int polygonMode = VulkanicAPI.GL_FILL;
	private static int backupPolygonMode = VulkanicAPI.GL_FILL;
	private static int[] samplers;
	private static final IntList textureToUnswizzle = new IntArrayList();
	private static int currentProgram;
	private static Runnable blendFuncListener;
	private static final int TEXTURE_UNIT_COUNT = 128;
	private static int activeTextureUnitIndex;
	private static final int[] textureBindings = new int[TEXTURE_UNIT_COUNT];
	private static final Plot PLOT_BUFFERS = TracyClient.createPlot("GPU Buffers");
	private static int numBuffers = 0;
	private static final Plot PLOT_TEXTURES = TracyClient.createPlot("GPU Textures");
	private static int numTextures = 0;
	private static final VulkanicResourceBarriers COMPUTE_WRITES_VISIBLE_TO_TEXTURE_SAMPLING =
		VulkanicResourceBarriers.computeWritesVisibleToTextureSampling();
	private static final VulkanicResourceBarriers IMAGE_WRITES_VISIBLE_TO_TEXTURE_SAMPLING =
		VulkanicResourceBarriers.of(
			VulkanicResourceBarriers.Barrier.SHADER_IMAGE_ACCESS,
			VulkanicResourceBarriers.Barrier.TEXTURE_FETCH
		);

	static {
		StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
	}

	public static void initRenderer() {
		GraphicsCapabilities capabilities = VulkanicAPI.getGraphicsCapabilities();

		if (capabilities.supportsCore(GraphicsFeature.DIRECT_STATE_ACCESS)) {
			dsaState = new DSACore();
			Iris.logger.info("OpenGL 4.5 detected, enabling DSA.");
		} else if (capabilities.supportsExtension(GraphicsFeature.DIRECT_STATE_ACCESS)) {
			dsaState = new DSAARB();
			Iris.logger.info("ARB_direct_state_access detected, enabling DSA.");
		} else {
			dsaState = new DSAUnsupported();
			Iris.logger.info("DSA support not detected.");
		}

		hasMultibind = capabilities.supports(GraphicsFeature.MULTI_BIND);
		perspectiveProjectionMatrixBuffer = new PerspectiveProjectionMatrixBuffer("Iris shadow map projection");

		supportsCompute = VulkanicAPI.checkFunctionAvailable("glDispatchCompute");
		supportsTesselation = capabilities.supports(GraphicsFeature.TESSELLATION_SHADER);

		samplers = new int[SamplerLimits.get().getMaxTextureUnits()];
	}

	public static void getIntegerv(int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.getIntegerv(VulkanicAPI.getCommandContext(), pname, params);
	}

	public static void getViewport(int[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.getViewport(VulkanicAPI.getCommandContext(), params);
	}

	public static void getFloatv(int pname, float[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.getFloatv(VulkanicAPI.getCommandContext(), pname, params);
	}

	public static void getClearColor(float[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.getClearColor(VulkanicAPI.getCommandContext(), params);
	}

	public static void generateMipmaps(int texture, int mipmapTarget) {
		RenderSystem.assertOnRenderThread();
		dsaState.generateMipmaps(texture, mipmapTarget);
	}

	public static void generateMipmaps(int texture, VulkanicTextureTarget mipmapTarget) {
		generateMipmaps(texture, mipmapTarget.toLegacyGlTarget());
	}

	public static void generateMipmaps(int texture) {
		generateMipmaps(texture, VulkanicTextureTarget.TEXTURE_2D);
	}

	public static void bindAttributeLocation(int program, int index, CharSequence name) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setAttributeLocation(VulkanicAPI.getCommandContext(), program, index, name);
	}

	public static void texImage1D(int texture, int target, int level, int internalformat, int width, int border, int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		VulkanicAPI.uploadTexture1D(VulkanicAPI.getCommandContext(), target, level, internalformat, width, border, format, type, pixels);
	}

	public static void texImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		VulkanicAPI.uploadTexture2D(VulkanicAPI.getCommandContext(), target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void texImage2D(int texture, VulkanicTextureTarget target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		texImage2D(texture, target.toLegacyGlTarget(), level, internalformat, width, height, border, format, type, pixels);
	}

	public static void texImage2D(int texture, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		texImage2D(texture, VulkanicTextureTarget.TEXTURE_2D, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void texImage3D(int texture, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		VulkanicAPI.uploadTexture3D(VulkanicAPI.getCommandContext(), target, level, internalformat, width, height, depth, border, format, type, pixels);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrix) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix4fv(VulkanicAPI.getCommandContext(), location, transpose, matrix);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, float[] matrix) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix4fv(VulkanicAPI.getCommandContext(), location, transpose, matrix);
	}

	public static void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.copyTexImage2D(VulkanicAPI.getCommandContext(), target, level, internalFormat, x, y, width, height, border);
	}

	public static void copyTexImage2D(int level, int internalFormat, int x, int y, int width, int height, int border) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.copyTexImage2D(VulkanicAPI.getCommandContext(), level, internalFormat, x, y, width, height, border);
	}

	public static void uniform1f(int location, float v0) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform1f(VulkanicAPI.getCommandContext(), location, v0);
	}

	public static void uniform2f(int location, float v0, float v1) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform2f(VulkanicAPI.getCommandContext(), location, v0, v1);
	}

	public static void uniform2i(int location, int v0, int v1) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform2i(VulkanicAPI.getCommandContext(), location, v0, v1);
	}

	public static void uniform3f(int location, float v0, float v1, float v2) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform3f(VulkanicAPI.getCommandContext(), location, v0, v1, v2);
	}

	public static void uniform3i(int location, int v0, int v1, int v2) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform3i(VulkanicAPI.getCommandContext(), location, v0, v1, v2);
	}

	public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform4f(VulkanicAPI.getCommandContext(), location, v0, v1, v2, v3);
	}

	public static void uniform4i(int location, int v0, int v1, int v2, int v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform4i(VulkanicAPI.getCommandContext(), location, v0, v1, v2, v3);
	}

	public static void texParameteriv(int texture, int target, int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		dsaState.texParameteriv(texture, target, pname, params);
	}

	public static void texParameteriv(int texture, VulkanicTextureTarget target, int pname, int[] params) {
		texParameteriv(texture, target.toLegacyGlTarget(), pname, params);
	}

	public static void texParameteriv(int texture, int pname, int[] params) {
		texParameteriv(texture, VulkanicTextureTarget.TEXTURE_2D, pname, params);
	}

	public static void texParameteriv(int texture, int target, VulkanicTextureParameterName pname, int[] params) {
		texParameteriv(texture, target, pname.toLegacyGlPName(), params);
	}

	public static void texParameteriv(int texture, VulkanicTextureTarget target, VulkanicTextureParameterName pname, int[] params) {
		texParameteriv(texture, target, pname.toLegacyGlPName(), params);
	}

	public static void texParameteriv(int texture, VulkanicTextureParameterName pname, int[] params) {
		texParameteriv(texture, VulkanicTextureTarget.TEXTURE_2D, pname, params);
	}

	public static void setTextureSwizzleRgba(
		int texture,
		int target,
		VulkanicTextureSwizzleComponent red,
		VulkanicTextureSwizzleComponent green,
		VulkanicTextureSwizzleComponent blue,
		VulkanicTextureSwizzleComponent alpha
	) {
		texParameteriv(
			texture,
			target,
			VulkanicTextureParameterName.SWIZZLE_RGBA,
			new int[] {
				red.toLegacyGlConstant(),
				green.toLegacyGlConstant(),
				blue.toLegacyGlConstant(),
				alpha.toLegacyGlConstant()
			}
		);
	}

	public static void setTextureSwizzleRgba(
		int texture,
		VulkanicTextureTarget target,
		VulkanicTextureSwizzleComponent red,
		VulkanicTextureSwizzleComponent green,
		VulkanicTextureSwizzleComponent blue,
		VulkanicTextureSwizzleComponent alpha
	) {
		setTextureSwizzleRgba(texture, target.toLegacyGlTarget(), red, green, blue, alpha);
	}

	public static void setTextureSwizzleRgba(
		int texture,
		VulkanicTextureSwizzleComponent red,
		VulkanicTextureSwizzleComponent green,
		VulkanicTextureSwizzleComponent blue,
		VulkanicTextureSwizzleComponent alpha
	) {
		setTextureSwizzleRgba(texture, VulkanicTextureTarget.TEXTURE_2D, red, green, blue, alpha);
	}

	/**
	 * Internal API for use when you don't know the target texture. Should use {@link IrisRenderSystem#texParameteriv(int, int, int, int[])} instead unless you know what you're doing!
	 */
	public static void texParameterivDirect(int target, int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.texParameteriv(VulkanicAPI.getCommandContext(), target, pname, params);
	}

	public static void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
		dsaState.copyTexSubImage2D(destTexture, target, i, i1, i2, i3, i4, width, height);
	}

	public static void copyTexSubImage2D(int destTexture, VulkanicTextureTarget target, int i, int i1, int i2, int i3, int i4, int width, int height) {
		copyTexSubImage2D(destTexture, target.toLegacyGlTarget(), i, i1, i2, i3, i4, width, height);
	}

	public static void copyTexSubImage2D(int destTexture, int level, int i1, int i2, int i3, int i4, int width, int height) {
		copyTexSubImage2D(destTexture, VulkanicTextureTarget.TEXTURE_2D, level, i1, i2, i3, i4, width, height);
	}

	public static void texParameteri(int texture, int target, int pname, int param) {
		RenderSystem.assertOnRenderThread();
		dsaState.texParameteri(texture, target, pname, param);
	}

	public static void texParameteri(int texture, VulkanicTextureTarget target, int pname, int param) {
		texParameteri(texture, target.toLegacyGlTarget(), pname, param);
	}

	public static void texParameteri(int texture, int pname, int param) {
		texParameteri(texture, VulkanicTextureTarget.TEXTURE_2D, pname, param);
	}

	public static void texParameteri(int texture, int target, VulkanicTextureParameterName pname, VulkanicTextureParameterValue param) {
		texParameteri(texture, target, pname.toLegacyGlPName(), param.toLegacyGlConstant());
	}

	public static void texParameteri(int texture, VulkanicTextureTarget target, VulkanicTextureParameterName pname, VulkanicTextureParameterValue param) {
		texParameteri(texture, target.toLegacyGlTarget(), pname, param);
	}

	public static void texParameteri(int texture, int target, VulkanicTextureParameterName pname, int param) {
		texParameteri(texture, target, pname.toLegacyGlPName(), param);
	}

	public static void texParameteri(int texture, VulkanicTextureTarget target, VulkanicTextureParameterName pname, int param) {
		texParameteri(texture, target.toLegacyGlTarget(), pname, param);
	}

	public static void texParameteri(int texture, VulkanicTextureParameterName pname, VulkanicTextureParameterValue param) {
		texParameteri(texture, VulkanicTextureTarget.TEXTURE_2D, pname, param);
	}

	public static void texParameteri(int texture, VulkanicTextureParameterName pname, int param) {
		texParameteri(texture, VulkanicTextureTarget.TEXTURE_2D, pname, param);
	}

	public static void texParameterf(int texture, int target, int pname, float param) {
		RenderSystem.assertOnRenderThread();
		dsaState.texParameterf(texture, target, pname, param);
	}

	public static void texParameterf(int texture, VulkanicTextureTarget target, int pname, float param) {
		texParameterf(texture, target.toLegacyGlTarget(), pname, param);
	}

	public static void texParameterf(int texture, int target, VulkanicTextureParameterName pname, float param) {
		texParameterf(texture, target, pname.toLegacyGlPName(), param);
	}

	public static void texParameterf(int texture, VulkanicTextureTarget target, VulkanicTextureParameterName pname, float param) {
		texParameterf(texture, target.toLegacyGlTarget(), pname.toLegacyGlPName(), param);
	}

	public static void texParameterf(int texture, int pname, float param) {
		texParameterf(texture, VulkanicTextureTarget.TEXTURE_2D, pname, param);
	}

	public static void texParameterf(int texture, VulkanicTextureParameterName pname, float param) {
		texParameterf(texture, VulkanicTextureTarget.TEXTURE_2D, pname, param);
	}

	public static void setTextureLinearFiltering(int texture) {
		texParameteri(texture, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.LINEAR);
		texParameteri(texture, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
	}

	public static void setTextureNearestFiltering(int texture) {
		texParameteri(texture, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.NEAREST);
		texParameteri(texture, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.NEAREST);
	}

	public static void setTextureWrapMode2D(int texture, boolean clampToEdge) {
		VulkanicTextureParameterValue wrapMode = clampToEdge ? VulkanicTextureParameterValue.CLAMP_TO_EDGE : VulkanicTextureParameterValue.REPEAT;
		texParameteri(texture, VulkanicTextureParameterName.WRAP_S, wrapMode);
		texParameteri(texture, VulkanicTextureParameterName.WRAP_T, wrapMode);
	}

	public static void resetTextureLodRangeToZero(int texture) {
		texParameteri(texture, VulkanicTextureParameterName.MAX_LEVEL, 0);
		texParameteri(texture, VulkanicTextureParameterName.MIN_LOD, 0);
		texParameteri(texture, VulkanicTextureParameterName.MAX_LOD, 0);
		texParameterf(texture, VulkanicTextureParameterName.LOD_BIAS, 0.0F);
	}

	public static String getProgramInfoLog(int program) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.getProgramInfoLog(VulkanicAPI.getCommandContext(), program);
	}

	public static String getShaderInfoLog(int shader) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.getShaderInfoLog(VulkanicAPI.getCommandContext(), shader);
	}

	public static void drawBuffers(int framebuffer, int[] buffers) {
		RenderSystem.assertOnRenderThread();
		dsaState.drawBuffers(framebuffer, buffers);
	}

	public static void readBuffer(int framebuffer, int buffer) {
		RenderSystem.assertOnRenderThread();
		dsaState.readBuffer(framebuffer, buffer);
	}

	public static void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
		VulkanicAPI.assertOnRenderThreadOrInit();
		dsaState.clearBufferfv(framebuffer, buffer, drawbuffer, values);
	}

	public static void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
		VulkanicAPI.assertOnRenderThreadOrInit();
		dsaState.clearBufferiv(framebuffer, buffer, drawbuffer, values);
	}

	public static void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
		VulkanicAPI.assertOnRenderThreadOrInit();
		dsaState.clearBufferuiv(framebuffer, buffer, drawbuffer, values);
	}

	/**
	 * @deprecated Prefer VulkanicAPI typed reflection metadata helpers
	 * ({@link VulkanicAPI#getActiveUniformInfo(net.vulkanic.CommandContext, int, int, int)} or
	 * {@link VulkanicAPI#getActiveUniforms(net.vulkanic.CommandContext, int, int)}).
	 */
	@Deprecated
	public static String getActiveUniform(int program, int index, int size, IntBuffer type, IntBuffer name) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.getActiveUniform(VulkanicAPI.getCommandContext(), program, index, size, type, name);
	}

	public static void readPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.readPixels(VulkanicAPI.getCommandContext(), x, y, width, height, format, type, pixels);
	}

	public static void bufferData(int target, float[] data, int usage) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bufferData(VulkanicAPI.getCommandContext(), target, data, usage);
	}

	public static int bufferStorage(int target, float[] data, int usage) {
		RenderSystem.assertOnRenderThread();
		return dsaState.bufferStorage(target, data, usage);
	}

	public static void bufferStorage(int target, long size, int flags) {
		RenderSystem.assertOnRenderThread();
		// The ARB version is identical to GL44 and redirects, so this should work on ARB as well.
		VulkanicAPI.bufferStorage(VulkanicAPI.getCommandContext(), target, size, flags);
	}

	public static void bufferStorage(VulkanicBufferTarget target, long size, int flags) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bufferStorage(VulkanicAPI.getCommandContext(), target, size, flags);
	}

	public static void bindBufferBase(int target, Integer index, int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bindBufferBase(VulkanicAPI.getCommandContext(), target, index, buffer);
	}

	public static void bindBufferBase(VulkanicBufferTarget target, Integer index, int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bindBufferBase(VulkanicAPI.getCommandContext(), target, index, buffer);
	}

	public static void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setVertexAttrib4f(VulkanicAPI.getCommandContext(), index, v0, v1, v2, v3);
	}

	public static void detachShader(int program, int shader) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.detachShader(VulkanicAPI.getCommandContext(), program, shader);
	}

	public static void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
		dsaState.framebufferTexture2D(fb, fbtarget, attachment, target, texture, levels);
	}

	public static void framebufferTexture2D(int fb, int fbtarget, int attachment, int texture, int levels) {
		dsaState.framebufferTexture2D(fb, fbtarget, attachment, VulkanicTextureTarget.TEXTURE_2D.toLegacyGlTarget(), texture, levels);
	}

	public static void framebufferTexture2D(int fb, int attachment, int texture, int levels) {
		framebufferTexture2D(fb, VulkanicAPI.GL_FRAMEBUFFER, attachment, texture, levels);
	}

	public static int getTexParameteri(int texture, int target, int pname) {
		RenderSystem.assertOnRenderThread();
		return dsaState.getTexParameteri(texture, target, pname);
	}

	public static int getTexParameteri(int texture, VulkanicTextureTarget target, int pname) {
		return getTexParameteri(texture, target.toLegacyGlTarget(), pname);
	}

	public static int getTexParameteri(int texture, int pname) {
		return getTexParameteri(texture, VulkanicTextureTarget.TEXTURE_2D, pname);
	}

	public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bindImageTexture(VulkanicAPI.getCommandContext(), unit, texture, level, layered, layer, access, format);
	}

	public static int getMaxImageUnits() {
		return VulkanicAPI.getMaxImageUnits(VulkanicAPI.getCommandContext());
	}

	public static boolean supportsSSBO() {
		GraphicsCapabilities capabilities = VulkanicAPI.getGraphicsCapabilities();
		return capabilities.supports(GraphicsFeature.SHADER_STORAGE_BUFFER)
			&& capabilities.supports(GraphicsFeature.BUFFER_STORAGE);
	}

	public static boolean supportsImageLoadStore() {
		GraphicsCapabilities capabilities = VulkanicAPI.getGraphicsCapabilities();
		return VulkanicAPI.checkFunctionAvailable("glBindImageTexture")
			|| capabilities.supportsCore(GraphicsFeature.IMAGE_LOAD_STORE)
			|| (capabilities.supportsExtension(GraphicsFeature.IMAGE_LOAD_STORE)
			&& capabilities.supports(GraphicsFeature.BUFFER_STORAGE));
	}

	public static void genBuffers(int[] buffers) {
		VulkanicAPI.createBuffers(VulkanicAPI.getCommandContext(), buffers);
	}

	public static void clearBufferSubData(int glShaderStorageBuffer, int glR8, long offset, long size, int glRed, int glByte, int[] ints) {
		VulkanicAPI.clearBufferSubData(VulkanicAPI.getCommandContext(), glShaderStorageBuffer, glR8, offset, size, glRed, glByte, ints);
	}

	public static void clearBufferSubData(VulkanicBufferTarget target, int internalFormat, long offset, long size, int format, int type, int[] data) {
		VulkanicAPI.clearBufferSubData(VulkanicAPI.getCommandContext(), target, internalFormat, offset, size, format, type, data);
	}

	public static void getProgramiv(int program, int value, int[] storage) {
		VulkanicAPI.getProgramiv(VulkanicAPI.getCommandContext(), program, value, storage);
	}

	public static void dispatchCompute(int workX, int workY, int workZ) {
		VulkanicAPI.dispatchCompute(VulkanicAPI.getCommandContext(), workX, workY, workZ);
	}

	public static void dispatchCompute(Vector3i workGroups) {
		VulkanicAPI.dispatchCompute(VulkanicAPI.getCommandContext(), workGroups.x, workGroups.y, workGroups.z);
	}

	public static void memoryBarrier(int barriers) {
		RenderSystem.assertOnRenderThread();

		if (supportsCompute) {
			VulkanicAPI.memoryBarrier(VulkanicAPI.getCommandContext(), barriers);
		}
	}

	public static void memoryBarrier(VulkanicResourceBarriers barriers) {
		RenderSystem.assertOnRenderThread();

		if (supportsCompute) {
			VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), barriers);
		}
	}

	public static void memoryBarrierComputeWritesVisibleToTextureSampling() {
		memoryBarrier(COMPUTE_WRITES_VISIBLE_TO_TEXTURE_SAMPLING);
	}

	public static void memoryBarrierImageWritesVisibleToTextureSampling() {
		memoryBarrier(IMAGE_WRITES_VISIBLE_TO_TEXTURE_SAMPLING);
	}

	public static boolean supportsBufferBlending() {
		return VulkanicAPI.getGraphicsCapabilities().supports(GraphicsFeature.DRAW_BUFFERS_BLEND);
	}

	public static void disableBufferBlend(int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setIndexedEnabled(VulkanicAPI.getCommandContext(), VulkanicCapability.BLEND, buffer, false);
		BlendModeStorage.markBlendStateUnknown();
	}

	public static void enableBufferBlend(int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setIndexedEnabled(VulkanicAPI.getCommandContext(), VulkanicCapability.BLEND, buffer, true);
		BlendModeStorage.markBlendStateUnknown();
	}

	public static void blendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.blendFuncSeparatei(VulkanicAPI.getCommandContext(), buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
	}

	// These functions are deprecated and unavailable in the core profile.

	public static void bindTextureToUnit(int target, int unit, int texture) {
		VulkanicTextureTarget.fromLegacyGlTarget(target)
			.ifPresentOrElse(
				typedTarget -> bindTextureToUnit(typedTarget, unit, texture),
				() -> dsaState.bindTextureToUnit(target, unit, texture)
			);
	}

	public static void bindTextureToUnit(VulkanicTextureTarget target, int unit, int texture) {
		dsaState.bindTextureToUnit(target.toLegacyGlTarget(), unit, texture);
	}

	public static void bindTextureToUnit(int unit, int texture) {
		bindTextureToUnit(VulkanicTextureTarget.TEXTURE_2D, unit, texture);
	}

	public static int getUniformBlockIndex(int program, String uniformBlockName) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.getUniformBlockIndex(VulkanicAPI.getCommandContext(), program, uniformBlockName);
	}

	public static void uniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.uniformBlockBinding(VulkanicAPI.getCommandContext(), program, uniformBlockIndex, uniformBlockBinding);
	}

	public static void setShadowProjection(Matrix4f shadowProjection) {
		backupProjection = VulkanicAPI.getProjectionMatrixBuffer();
		backupProjectionType = VulkanicAPI.getProjectionType();
		VulkanicAPI.setProjectionMatrix(perspectiveProjectionMatrixBuffer.getBuffer(shadowProjection), ProjectionType.ORTHOGRAPHIC);
	}

	public static void restorePlayerProjection() {
		VulkanicAPI.setProjectionMatrix(backupProjection, backupProjectionType);
		backupProjection = null;
		backupProjectionType = null;
	}

	public static void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
		dsaState.blitFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
	}

	public static void blitColorBufferNearest(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1) {
		blitFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, VulkanicAPI.GL_COLOR_BUFFER_BIT, VulkanicAPI.GL_NEAREST);
	}

	public static void blitDepthBufferNearest(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1) {
		blitFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, VulkanicAPI.GL_DEPTH_BUFFER_BIT, VulkanicAPI.GL_NEAREST);
	}

	public static void blitDepthAndStencilBuffersNearest(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1) {
		blitFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, VulkanicAPI.GL_DEPTH_BUFFER_BIT | VulkanicAPI.GL_STENCIL_BUFFER_BIT, VulkanicAPI.GL_NEAREST);
	}

	public static int createFramebuffer() {
		return dsaState.createFramebuffer();
	}

	public static int createTexture(int target) {
		return VulkanicTextureTarget.fromLegacyGlTarget(target)
			.map(IrisRenderSystem::createTexture)
			.orElseGet(() -> dsaState.createTexture(target));
	}

	public static int createTexture(VulkanicTextureTarget target) {
		return dsaState.createTexture(target.toLegacyGlTarget());
	}

	public static int createTexture2D() {
		return createTexture(VulkanicTextureTarget.TEXTURE_2D);
	}

	private static int lastTex = -1;

	public static void bindTextureForSetup(int glType, int glId) {
		VulkanicTextureTarget.fromLegacyGlTarget(glType)
			.ifPresentOrElse(
				typedTarget -> bindTextureForSetup(typedTarget, glId),
				() -> VulkanicAPI.bindTexture(VulkanicAPI.getCommandContext(), glType, glId)
			);
	}

	public static void bindTextureForSetup(VulkanicTextureTarget target, int glId) {
		if (target == VulkanicTextureTarget.TEXTURE_2D) {
			lastTex = getBoundTextureOnActiveUnit();
		}
		VulkanicAPI.bindTexture(VulkanicAPI.getCommandContext(), target.toLegacyGlTarget(), glId);
	}

	public static void restoreTexture() {
		if (lastTex != -1) {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.bindTexture2D(ctx, lastTex);
			lastTex = -1;
		}
	}

	public static boolean supportsCompute() {
		return supportsCompute;
	}

	public static boolean supportsTesselation() {
		return supportsTesselation;
	}

	public static int genSampler() {
		return VulkanicAPI.createSampler(VulkanicAPI.getCommandContext());
	}

	public static void destroySampler(int glId) {
		VulkanicAPI.deleteSampler(VulkanicAPI.getCommandContext(), glId);
	}

	public static void bindSamplerToUnit(int unit, int sampler) {
		if (samplers[unit] == sampler) {
			return;
		}

		VulkanicAPI.bindSampler(VulkanicAPI.getCommandContext(), unit, sampler);

		samplers[unit] = sampler;
	}

	public static void unbindAllSamplers() {
		boolean usedASampler = false;
		for (int i = 0; i < samplers.length; i++) {
			if (samplers[i] != 0) {
				usedASampler = true;
				if (!hasMultibind) VulkanicAPI.bindSampler(VulkanicAPI.getCommandContext(), i, 0);
				samplers[i] = 0;
			}
		}
		if (usedASampler && hasMultibind) {
			VulkanicAPI.bindSamplers(VulkanicAPI.getCommandContext(), 0, emptyArray);
		}
	}


	public static void samplerParameteri(int sampler, int pname, int param) {
		VulkanicAPI.setSamplerParameteri(VulkanicAPI.getCommandContext(), sampler, pname, param);
	}

	public static void samplerParameteri(int sampler, VulkanicTextureParameterName pname, int param) {
		VulkanicAPI.setSamplerParameteri(VulkanicAPI.getCommandContext(), sampler, pname, param);
	}

	public static void samplerParameteri(int sampler, VulkanicTextureParameterName pname, VulkanicTextureParameterValue param) {
		VulkanicAPI.setSamplerParameteri(VulkanicAPI.getCommandContext(), sampler, pname, param);
	}

	public static void samplerParameterf(int sampler, int pname, float param) {
		VulkanicAPI.setSamplerParameterf(VulkanicAPI.getCommandContext(), sampler, pname, param);
	}

	public static void samplerParameteriv(int sampler, int pname, int[] params) {
		VulkanicAPI.setSamplerParameteriv(VulkanicAPI.getCommandContext(), sampler, pname, params);
	}

	public static long getVRAM() {
		if (VulkanicAPI.getGraphicsCapabilities().supports(GraphicsFeature.GPU_MEMORY_INFO)) {
			CommandContext ctx = VulkanicAPI.getCommandContext();
			return VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L;
		} else {
			return 4294967296L;
		}
	}

	public static void deleteBuffers(int glId) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.deleteBuffer(VulkanicAPI.getCommandContext(), glId);
	}

	public static void setPolygonMode(int mode) {
		if (mode != polygonMode) {
			polygonMode = mode;
			VulkanicAPI.setPolygonMode(VulkanicAPI.getCommandContext(), VulkanicPolygonFace.FRONT_AND_BACK, mode);
		}
	}

	public static void setPolygonMode(VulkanicPolygonMode mode) {
		setPolygonMode(mode.toGlModeConstant());
	}

	public static void overridePolygonMode() {
		backupPolygonMode = polygonMode;
		setPolygonMode(VulkanicPolygonMode.FILL);
	}

	public static void restorePolygonMode() {
		setPolygonMode(backupPolygonMode);
		backupPolygonMode = VulkanicPolygonMode.FILL.toGlModeConstant();
	}

	public static void dispatchComputeIndirect(long offset) {
		VulkanicAPI.dispatchComputeIndirect(VulkanicAPI.getCommandContext(), offset);
	}

	public static void bindBuffer(int target, int buffer) {
		VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), target, buffer);
	}

	public static void bindBuffer(VulkanicBufferTarget target, int buffer) {
		VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), target, buffer);
	}

	public static int createBuffers() {
		return dsaState.createBuffers();
	}

	public static String getStringi(int glEnum, int index) {
		return VulkanicAPI.getString(VulkanicAPI.getCommandContext(), glEnum, index);
	}

	public static void copyImageSubData(int sourceTexture, int target, int mip, int srcX, int srcY, int srcZ, int destTexture, int dstTarget, int dstMip, int dstX, int dstY, int dstZ, int width, int height, int depth) {
		VulkanicAPI.copyImageSubData(VulkanicAPI.getCommandContext(), 
			sourceTexture, target, mip, srcX, srcY, srcZ, 
			destTexture, dstTarget, dstMip, dstX, dstY, dstZ, 
			width, height, depth);
  }

	public static void copyImageSubData(
		int sourceTexture,
		VulkanicTextureTarget sourceTarget,
		int mip,
		int srcX,
		int srcY,
		int srcZ,
		int destTexture,
		VulkanicTextureTarget destTarget,
		int dstMip,
		int dstX,
		int dstY,
		int dstZ,
		int width,
		int height,
		int depth
	) {
		VulkanicAPI.copyImageSubData(
			VulkanicAPI.getCommandContext(),
			sourceTexture,
			sourceTarget,
			mip,
			srcX,
			srcY,
			srcZ,
			destTexture,
			destTarget,
			dstMip,
			dstX,
			dstY,
			dstZ,
			width,
			height,
			depth
		);
	}

	public static void copyImageSubData2D(
		int sourceTexture,
		int mip,
		int srcX,
		int srcY,
		int srcZ,
		int destTexture,
		int dstMip,
		int dstX,
		int dstY,
		int dstZ,
		int width,
		int height,
		int depth
	) {
		copyImageSubData(
			sourceTexture,
			VulkanicTextureTarget.TEXTURE_2D,
			mip,
			srcX,
			srcY,
			srcZ,
			destTexture,
			VulkanicTextureTarget.TEXTURE_2D,
			dstMip,
			dstX,
			dstY,
			dstZ,
			width,
			height,
			depth
		);
	}


  private static boolean cullingState;

	public static void backupAndDisableCullingState(boolean b) {
		cullingState = Minecraft.getInstance().smartCull;
		Minecraft.getInstance().smartCull = Minecraft.getInstance().smartCull && !b;
	}

	public static void restoreCullingState() {
		Minecraft.getInstance().smartCull = cullingState;
		cullingState = true;
  }

	public static void onProgramUse() {
		for (int i = 0; i < textureToUnswizzle.size(); i++) {
			setTextureSwizzleRgba(
				textureToUnswizzle.getInt(i),
				VulkanicTextureSwizzleComponent.RED,
				VulkanicTextureSwizzleComponent.GREEN,
				VulkanicTextureSwizzleComponent.BLUE,
				VulkanicTextureSwizzleComponent.ALPHA
			);
		}
		textureToUnswizzle.clear();
	}

	public static void useProgram(int program) {
		RenderSystem.assertOnRenderThread();
		if (currentProgram == 0 && program == 0) {
			return;
		}

		onProgramUse();
		currentProgram = program;
		VulkanicAPI.bindShaderProgram(VulkanicAPI.getCommandContext(), program);
		net.irisshaders.iris.vertices.ImmediateState.usingTessellation = false;
	}

	public static void addUnswizzle(int shaderTexture) {
		textureToUnswizzle.add(shaderTexture);
	}

	public static void setActiveTexture(int textureUnit) {
		RenderSystem.assertOnRenderThread();
		int textureUnitIndex = VulkanicAPI.textureUnitToIndex(textureUnit);
		validateTextureUnitIndex(textureUnitIndex);

		if (activeTextureUnitIndex != textureUnitIndex) {
			activeTextureUnitIndex = textureUnitIndex;
			VulkanicAPI.setActiveTextureUnitIndex(VulkanicAPI.getCommandContext(), textureUnitIndex);
		}
	}

	public static void setActiveTextureUnitIndex(int textureUnitIndex) {
		RenderSystem.assertOnRenderThread();
		validateTextureUnitIndex(textureUnitIndex);

		if (activeTextureUnitIndex != textureUnitIndex) {
			activeTextureUnitIndex = textureUnitIndex;
			VulkanicAPI.setActiveTextureUnitIndex(VulkanicAPI.getCommandContext(), textureUnitIndex);
		}
	}

	public static int getActiveTextureUnitIndex() {
		return activeTextureUnitIndex;
	}

	public static int getTextureBinding(int textureUnitIndex) {
		validateTextureUnitIndex(textureUnitIndex);
		return textureBindings[textureUnitIndex];
	}

	public static int getBoundTextureOnActiveUnit() {
		return getTextureBinding(getActiveTextureUnitIndex());
	}

	public static void setTextureBinding(int textureUnitIndex, int textureId) {
		validateTextureUnitIndex(textureUnitIndex);
		textureBindings[textureUnitIndex] = textureId;
	}

	private static void validateTextureUnitIndex(int textureUnitIndex) {
		if (textureUnitIndex < 0 || textureUnitIndex >= textureBindings.length) {
			throw new IllegalArgumentException("Texture " + textureUnitIndex + " out of range");
		}
	}

	private static boolean isTexture2DTarget(int target) {
		return VulkanicTextureTarget.fromLegacyGlTarget(target)
			.map(typedTarget -> typedTarget == VulkanicTextureTarget.TEXTURE_2D)
			.orElse(false);
	}

	public static int createTextureId() {
		RenderSystem.assertOnRenderThread();
		incrementTrackedTextures();
		return VulkanicAPI.createTexture2D(VulkanicAPI.getCommandContext());
	}

	public static void incrementTrackedTextures() {
		numTextures++;
		PLOT_TEXTURES.setValue(numTextures);
	}

	public static void decrementTrackedTextures() {
		numTextures--;
		PLOT_TEXTURES.setValue(numTextures);
	}

	public static void incrementTrackedBuffers() {
		numBuffers++;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static void decrementTrackedBuffers() {
		numBuffers--;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static void notifyBlendFuncChanged() {
		if (blendFuncListener != null) {
			blendFuncListener.run();
		}
	}

	public static void deleteTextureId(int textureId) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.deleteTexture(VulkanicAPI.getCommandContext(), textureId);

		for (int textureUnitIndex = 0; textureUnitIndex < textureBindings.length; textureUnitIndex++) {
			if (textureBindings[textureUnitIndex] == textureId) {
				textureBindings[textureUnitIndex] = -1;
			}
		}

		decrementTrackedTextures();

		net.irisshaders.iris.pbr.TextureTracker.INSTANCE.onDeleteTexture(textureId);
		net.irisshaders.iris.pbr.TextureInfoCache.INSTANCE.onDeleteTexture(textureId);
		net.irisshaders.iris.pbr.texture.PBRTextureManager.INSTANCE.onDeleteTexture(textureId);
	}

	public static int checkFramebufferStatus(int glFramebuffer) {
		return VulkanicAPI.checkFramebufferStatus(VulkanicAPI.getCommandContext(), glFramebuffer);
	}

	public static int checkFramebufferStatus() {
		return VulkanicAPI.checkFramebufferStatus(VulkanicAPI.getCommandContext());
	}

	public static void uniformMatrix3fv(int index, boolean b, FloatBuffer buf) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix3fv(VulkanicAPI.getCommandContext(), index, b, buf);
	}

	public static void uniformMatrix3fv(int index, boolean b, float[] buf) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix3fv(VulkanicAPI.getCommandContext(), index, b, buf);
	}

	public static void clearColor(float v, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setClearColor(VulkanicAPI.getCommandContext(), v, v1, v2, v3);
	}

	public static int getAttribLocation(int handle, String irisNormal) {
		return VulkanicAPI.getAttributeLocation(VulkanicAPI.getCommandContext(), handle, irisNormal);
	}

	public interface DSAAccess {
		void generateMipmaps(int texture, int target);

		void texParameteri(int texture, int target, int pname, int param);

		void texParameterf(int texture, int target, int pname, float param);

		void texParameteriv(int texture, int target, int pname, int[] params);

		void readBuffer(int framebuffer, int buffer);

		void drawBuffers(int framebuffer, int[] buffers);

		void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values);

		void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values);

		void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values);

		int getTexParameteri(int texture, int target, int pname);

		void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height);

		void bindTextureToUnit(int target, int unit, int texture);

		int bufferStorage(int target, float[] data, int usage);

		void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter);

		void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels);

		int createFramebuffer();

		int createTexture(int target);

		int createBuffers();
	}

	public static class DSACore extends DSAARB {

	}

	public static class DSAARB extends DSAUnsupported {

		@Override
		public void generateMipmaps(int texture, int target) {
			VulkanicAPI.generateTextureMipmapDSA(VulkanicAPI.getCommandContext(), texture);
		}

		@Override
		public void texParameteri(int texture, int target, int pname, int param) {
			VulkanicAPI.textureParameteri(VulkanicAPI.getCommandContext(), texture, pname, param);
		}

		@Override
		public void texParameterf(int texture, int target, int pname, float param) {
			VulkanicAPI.textureParameterf(VulkanicAPI.getCommandContext(), texture, pname, param);
		}

		@Override
		public void texParameteriv(int texture, int target, int pname, int[] params) {
			VulkanicAPI.textureParameteriv(VulkanicAPI.getCommandContext(), texture, pname, params);
		}


		@Override
		public void readBuffer(int framebuffer, int buffer) {
			VulkanicAPI.namedFramebufferReadBuffer(VulkanicAPI.getCommandContext(), framebuffer, buffer);
		}

		@Override
		public void drawBuffers(int framebuffer, int[] buffers) {
			VulkanicAPI.namedFramebufferDrawBuffers(VulkanicAPI.getCommandContext(), framebuffer, buffers);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			VulkanicAPI.clearNamedFramebufferfv(VulkanicAPI.getCommandContext(), framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			VulkanicAPI.clearNamedFramebufferiv(VulkanicAPI.getCommandContext(), framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			VulkanicAPI.clearNamedFramebufferuiv(VulkanicAPI.getCommandContext(), framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public int getTexParameteri(int texture, int target, int pname) {
			return VulkanicAPI.getTextureParameteri(VulkanicAPI.getCommandContext(), texture, pname);
		}

		@Override
		public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
			VulkanicAPI.copyTextureSubImage2D(VulkanicAPI.getCommandContext(), destTexture, i, i1, i2, i3, i4, width, height);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			if (isTexture2DTarget(target)) {
				if (getTextureBinding(unit) == texture) {
					return;
				}

				VulkanicAPI.bindTextureUnit(VulkanicAPI.getCommandContext(), unit, texture);

				// Manually fix GLStateManager bindings...
				setTextureBinding(unit, texture);
			} else {
				VulkanicAPI.bindTextureUnit(VulkanicAPI.getCommandContext(), unit, texture);
			}
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			int buffer = VulkanicAPI.createBuffers(VulkanicAPI.getCommandContext());
			VulkanicAPI.namedBufferData(VulkanicAPI.getCommandContext(), buffer, data, usage);
			return buffer;
		}

		@Override
		public int createBuffers() {
			return VulkanicAPI.createBuffers(VulkanicAPI.getCommandContext());
		}

		@Override
		public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
			VulkanicAPI.blitNamedFramebuffer(VulkanicAPI.getCommandContext(), source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
		}

		@Override
		public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
			VulkanicAPI.namedFramebufferTexture(VulkanicAPI.getCommandContext(), fb, attachment, texture, levels);
		}

		@Override
		public int createFramebuffer() {
			return VulkanicAPI.createFramebuffers(VulkanicAPI.getCommandContext());
		}

		@Override
		public int createTexture(int target) {
			return VulkanicAPI.createTextures(VulkanicAPI.getCommandContext(), target);
		}
	}

	public static class DSAUnsupported implements DSAAccess {
		@Override
		public void generateMipmaps(int texture, int target) {
			int previous = getBoundTextureOnActiveUnit();
			VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), texture);
			VulkanicAPI.generateMipmap(VulkanicAPI.getCommandContext(), target);
			VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), previous);
		}

		@Override
		public void texParameteri(int texture, int target, int pname, int param) {
			bindTextureForSetup(target, texture);
			VulkanicAPI.setTextureParameter(VulkanicAPI.getCommandContext(), target, pname, param);
			restoreTexture();
		}

		@Override
		public void texParameterf(int texture, int target, int pname, float param) {
			bindTextureForSetup(target, texture);
			VulkanicAPI.texParameterf(VulkanicAPI.getCommandContext(), target, pname, param);
			restoreTexture();
		}

		@Override
		public void texParameteriv(int texture, int target, int pname, int[] params) {
			bindTextureForSetup(target, texture);
			VulkanicAPI.texParameteriv(VulkanicAPI.getCommandContext(), target, pname, params);
			restoreTexture();
		}

		@Override
		public void readBuffer(int framebuffer, int buffer) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
			VulkanicAPI.setReadBuffer(VulkanicAPI.getCommandContext(), buffer);
		}

		@Override
		public void drawBuffers(int framebuffer, int[] buffers) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
			VulkanicAPI.drawBuffers(VulkanicAPI.getCommandContext(), buffers);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
			VulkanicAPI.clearBufferfv(VulkanicAPI.getCommandContext(), buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
			VulkanicAPI.clearBufferiv(VulkanicAPI.getCommandContext(), buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
			VulkanicAPI.clearBufferuiv(VulkanicAPI.getCommandContext(), buffer, drawbuffer, values);
		}

		@Override
		public int getTexParameteri(int texture, int target, int pname) {
			bindTextureForSetup(target, texture);
			return VulkanicAPI.getTexParameteri(VulkanicAPI.getCommandContext(), target, pname);
		}

		@Override
		public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
			int previous = getBoundTextureOnActiveUnit();
			VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), destTexture);
			VulkanicAPI.copyTexSubImage2D(VulkanicAPI.getCommandContext(), target, i, i1, i2, i3, i4, width, height);
			VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), previous);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			int activeTexture = getActiveTextureUnitIndex();
			setActiveTextureUnitIndex(unit);
			VulkanicAPI.bindTexture(VulkanicAPI.getCommandContext(), target, texture);
			if (isTexture2DTarget(target)) {
				setTextureBinding(unit, texture);
			}
			setActiveTextureUnitIndex(activeTexture);
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			incrementTrackedBuffers();
			int buffer = VulkanicAPI.createBuffer(VulkanicAPI.getCommandContext());
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), target, buffer);
			bufferData(target, data, usage);
			VulkanicAPI.bindBuffer(VulkanicAPI.getCommandContext(), target, 0);

			return buffer;
		}

		@Override
		public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
			VulkanicAPI.blitNamedFramebuffer(VulkanicAPI.getCommandContext(), source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
		}

		@Override
		public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), fbtarget, fb);
			VulkanicAPI.framebufferTexture2D(VulkanicAPI.getCommandContext(), fbtarget, attachment, target, texture, levels);
		}

		@Override
		public int createFramebuffer() {
			int framebuffer = VulkanicAPI.createFramebuffer(VulkanicAPI.getCommandContext());
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), framebuffer);
			return framebuffer;
		}

		@Override
		public int createTexture(int target) {
			int texture = createTextureId();
			bindTextureForSetup(target, texture);
			restoreTexture();
			return texture;
		}

		@Override
		public int createBuffers() {
			incrementTrackedBuffers();
			return VulkanicAPI.createBuffer(VulkanicAPI.getCommandContext());
		}
	}

		/*
	public static void bindTextures(int startingTexture, int[] bindings) {
		if (hasMultibind) {
			ARBMultiBind.glBindTextures(startingTexture, bindings);
		} else if (dsaState != DSAState.NONE) {
			for (int binding : bindings) {
				ARBDirectStateAccess.glBindTextureUnit(startingTexture, binding);
				startingTexture++;
			}
		} else {
			for (int binding : bindings) {
				setActiveTexture(startingTexture);
				VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), binding);
				startingTexture++;
			}
		}
	}
	 */
}
