package net.irisshaders.iris.gl;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.opengl.GlStateManager;
import net.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.texture.TextureType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

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

	public static void initRenderer() {
		if (VulkanicAPI.getGraphicsCapabilities().OpenGL45) {
			dsaState = new DSACore();
			Iris.logger.info("OpenGL 4.5 detected, enabling DSA.");
		} else if (VulkanicAPI.getGraphicsCapabilities().GL_ARB_direct_state_access) {
			dsaState = new DSAARB();
			Iris.logger.info("ARB_direct_state_access detected, enabling DSA.");
		} else {
			dsaState = new DSAUnsupported();
			Iris.logger.info("DSA support not detected.");
		}

		hasMultibind = VulkanicAPI.getGraphicsCapabilities().OpenGL45 || VulkanicAPI.getGraphicsCapabilities().GL_ARB_multi_bind;
		perspectiveProjectionMatrixBuffer = new PerspectiveProjectionMatrixBuffer("Iris shadow map projection");

		supportsCompute = VulkanicAPI.checkFunctionAvailable("glDispatchCompute");
		supportsTesselation = VulkanicAPI.getGraphicsCapabilities().GL_ARB_tessellation_shader || VulkanicAPI.getGraphicsCapabilities().OpenGL40;

		samplers = new int[SamplerLimits.get().getMaxTextureUnits()];
	}

	public static void getIntegerv(int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.getIntegerv(VulkanicAPI.getImmediateContext(), pname, params);
	}

	public static void getFloatv(int pname, float[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.getFloatv(VulkanicAPI.getImmediateContext(), pname, params);
	}

	public static void generateMipmaps(int texture, int mipmapTarget) {
		RenderSystem.assertOnRenderThread();
		dsaState.generateMipmaps(texture, mipmapTarget);
	}

	public static void bindAttributeLocation(int program, int index, CharSequence name) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setAttributeLocation(VulkanicAPI.getImmediateContext(), program, index, name);
	}

	public static void texImage1D(int texture, int target, int level, int internalformat, int width, int border, int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		VulkanicAPI.uploadTexture1D(VulkanicAPI.getImmediateContext(), target, level, internalformat, width, border, format, type, pixels);
	}

	public static void texImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		VulkanicAPI.uploadTexture2D(VulkanicAPI.getImmediateContext(), target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void texImage3D(int texture, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		VulkanicAPI.uploadTexture3D(VulkanicAPI.getImmediateContext(), target, level, internalformat, width, height, depth, border, format, type, pixels);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrix) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix4fv(VulkanicAPI.getImmediateContext(), location, transpose, matrix);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, float[] matrix) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix4fv(VulkanicAPI.getImmediateContext(), location, transpose, matrix);
	}

	public static void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.copyTexImage2D(VulkanicAPI.getImmediateContext(), target, level, internalFormat, x, y, width, height, border);
	}

	public static void uniform1f(int location, float v0) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform1f(VulkanicAPI.getImmediateContext(), location, v0);
	}

	public static void uniform2f(int location, float v0, float v1) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform2f(VulkanicAPI.getImmediateContext(), location, v0, v1);
	}

	public static void uniform2i(int location, int v0, int v1) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform2i(VulkanicAPI.getImmediateContext(), location, v0, v1);
	}

	public static void uniform3f(int location, float v0, float v1, float v2) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform3f(VulkanicAPI.getImmediateContext(), location, v0, v1, v2);
	}

	public static void uniform3i(int location, int v0, int v1, int v2) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform3i(VulkanicAPI.getImmediateContext(), location, v0, v1, v2);
	}

	public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform4f(VulkanicAPI.getImmediateContext(), location, v0, v1, v2, v3);
	}

	public static void uniform4i(int location, int v0, int v1, int v2, int v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniform4i(VulkanicAPI.getImmediateContext(), location, v0, v1, v2, v3);
	}

	public static void texParameteriv(int texture, int target, int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		dsaState.texParameteriv(texture, target, pname, params);
	}

	/**
	 * Internal API for use when you don't know the target texture. Should use {@link IrisRenderSystem#texParameteriv(int, int, int, int[])} instead unless you know what you're doing!
	 */
	public static void texParameterivDirect(int target, int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.glTexParameteriv(target, pname, params);
	}

	public static void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
		dsaState.copyTexSubImage2D(destTexture, target, i, i1, i2, i3, i4, width, height);
	}

	public static void texParameteri(int texture, int target, int pname, int param) {
		RenderSystem.assertOnRenderThread();
		dsaState.texParameteri(texture, target, pname, param);
	}

	public static void texParameterf(int texture, int target, int pname, float param) {
		RenderSystem.assertOnRenderThread();
		dsaState.texParameterf(texture, target, pname, param);
	}

	public static String getProgramInfoLog(int program) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.glGetProgramInfoLog(program);
	}

	public static String getShaderInfoLog(int shader) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.glGetShaderInfoLog(shader);
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
		RenderSystem.assertOnRenderThreadOrInit();
		dsaState.clearBufferfv(framebuffer, buffer, drawbuffer, values);
	}

	public static void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
		RenderSystem.assertOnRenderThreadOrInit();
		dsaState.clearBufferiv(framebuffer, buffer, drawbuffer, values);
	}

	public static void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
		RenderSystem.assertOnRenderThreadOrInit();
		dsaState.clearBufferuiv(framebuffer, buffer, drawbuffer, values);
	}

	public static String getActiveUniform(int program, int index, int size, IntBuffer type, IntBuffer name) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.glGetActiveUniform(program, index, size, type, name);
	}

	public static void readPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.readPixels(VulkanicAPI.getImmediateContext(), x, y, width, height, format, type, pixels);
	}

	public static void bufferData(int target, float[] data, int usage) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bufferData(VulkanicAPI.getImmediateContext(), target, data, usage);
	}

	public static int bufferStorage(int target, float[] data, int usage) {
		RenderSystem.assertOnRenderThread();
		return dsaState.bufferStorage(target, data, usage);
	}

	public static void bufferStorage(int target, long size, int flags) {
		RenderSystem.assertOnRenderThread();
		// The ARB version is identical to GL44 and redirects, so this should work on ARB as well.
		VulkanicAPI.glBufferStorage(target, size, flags);
	}

	public static void bindBufferBase(int target, Integer index, int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.bindBufferBase(VulkanicAPI.getImmediateContext(), target, index, buffer);
	}

	public static void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.glVertexAttrib4f(index, v0, v1, v2, v3);
	}

	public static void detachShader(int program, int shader) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.detachShader(VulkanicAPI.getImmediateContext(), program, shader);
	}

	public static void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
		dsaState.framebufferTexture2D(fb, fbtarget, attachment, target, texture, levels);
	}

	public static int getTexParameteri(int texture, int target, int pname) {
		RenderSystem.assertOnRenderThread();
		return dsaState.getTexParameteri(texture, target, pname);
	}

	public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.glBindImageTexture(unit, texture, level, layered, layer, access, format);
	}

	public static int getMaxImageUnits() {
		return VulkanicAPI.glGetMaxImageUnits();
	}

	public static boolean supportsSSBO() {
		return VulkanicAPI.getGraphicsCapabilities().OpenGL44 || (VulkanicAPI.getGraphicsCapabilities().GL_ARB_shader_storage_buffer_object && VulkanicAPI.getGraphicsCapabilities().GL_ARB_buffer_storage);
	}

	public static boolean supportsImageLoadStore() {
		return VulkanicAPI.checkFunctionAvailable("glBindImageTexture") || VulkanicAPI.getGraphicsCapabilities().OpenGL42 || ((VulkanicAPI.getGraphicsCapabilities().GL_ARB_shader_image_load_store || VulkanicAPI.getGraphicsCapabilities().GL_EXT_shader_image_load_store) && VulkanicAPI.getGraphicsCapabilities().GL_ARB_buffer_storage);
	}

	public static void genBuffers(int[] buffers) {
		VulkanicAPI.glGenBuffers(buffers);
	}

	public static void clearBufferSubData(int glShaderStorageBuffer, int glR8, long offset, long size, int glRed, int glByte, int[] ints) {
		VulkanicAPI.glClearBufferSubData(glShaderStorageBuffer, glR8, offset, size, glRed, glByte, ints);
	}

	public static void getProgramiv(int program, int value, int[] storage) {
		VulkanicAPI.glGetProgramiv(program, value, storage);
	}

	public static void dispatchCompute(int workX, int workY, int workZ) {
		VulkanicAPI.dispatchCompute(VulkanicAPI.getImmediateContext(), workX, workY, workZ);
	}

	public static void dispatchCompute(Vector3i workGroups) {
		VulkanicAPI.dispatchCompute(VulkanicAPI.getImmediateContext(), workGroups.x, workGroups.y, workGroups.z);
	}

	public static void memoryBarrier(int barriers) {
		RenderSystem.assertOnRenderThread();

		if (supportsCompute) {
			VulkanicAPI.glMemoryBarrier(barriers);
		}
	}

	public static boolean supportsBufferBlending() {
		return VulkanicAPI.getGraphicsCapabilities().GL_ARB_draw_buffers_blend || VulkanicAPI.getGraphicsCapabilities().OpenGL40;
	}

	public static void disableBufferBlend(int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setIndexedEnabled(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_BLEND, buffer, false);
		((BooleanStateExtended) GlStateManager.BLEND.mode).setUnknownState();
	}

	public static void enableBufferBlend(int buffer) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setIndexedEnabled(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_BLEND, buffer, true);
		((BooleanStateExtended) GlStateManager.BLEND.mode).setUnknownState();
	}

	public static void blendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
	}

	// These functions are deprecated and unavailable in the core profile.

	public static void bindTextureToUnit(int target, int unit, int texture) {
		dsaState.bindTextureToUnit(target, unit, texture);
	}

	public static int getUniformBlockIndex(int program, String uniformBlockName) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.getUniformBlockIndex(VulkanicAPI.getImmediateContext(), program, uniformBlockName);
	}

	public static void uniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.uniformBlockBinding(VulkanicAPI.getImmediateContext(), program, uniformBlockIndex, uniformBlockBinding);
	}

	public static void setShadowProjection(Matrix4f shadowProjection) {
		backupProjection = RenderSystem.getProjectionMatrixBuffer();
		backupProjectionType = RenderSystem.getProjectionType();
		RenderSystem.setProjectionMatrix(perspectiveProjectionMatrixBuffer.getBuffer(shadowProjection), ProjectionType.ORTHOGRAPHIC);
	}

	public static void restorePlayerProjection() {
		RenderSystem.setProjectionMatrix(backupProjection, backupProjectionType);
		backupProjection = null;
		backupProjectionType = null;
	}

	public static void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
		dsaState.blitFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
	}

	public static int createFramebuffer() {
		return dsaState.createFramebuffer();
	}

	public static int createTexture(int target) {
		return dsaState.createTexture(target);
	}

	private static int lastTex = -1;

	public static void bindTextureForSetup(int glType, int glId) {
		if (glType == VulkanicAPI.GL_TEXTURE_2D) {
			lastTex = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding;
		}
		VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), glType, glId);
	}

	public static void restoreTexture() {
		if (lastTex != -1) {
			CommandContext ctx = VulkanicAPI.getImmediateContext();
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
		return VulkanicAPI.glGenSamplers();
	}

	public static void destroySampler(int glId) {
		VulkanicAPI.glDeleteSamplers(glId);
	}

	public static void bindSamplerToUnit(int unit, int sampler) {
		if (samplers[unit] == sampler) {
			return;
		}

		VulkanicAPI.bindSampler(VulkanicAPI.getImmediateContext(), unit, sampler);

		samplers[unit] = sampler;
	}

	public static void unbindAllSamplers() {
		boolean usedASampler = false;
		for (int i = 0; i < samplers.length; i++) {
			if (samplers[i] != 0) {
				usedASampler = true;
				if (!hasMultibind) VulkanicAPI.bindSampler(VulkanicAPI.getImmediateContext(), i, 0);
				samplers[i] = 0;
			}
		}
		if (usedASampler && hasMultibind) {
			VulkanicAPI.glBindSamplers(0, emptyArray);
		}
	}


	public static void samplerParameteri(int sampler, int pname, int param) {
		VulkanicAPI.glSamplerParameteri(sampler, pname, param);
	}

	public static void samplerParameterf(int sampler, int pname, float param) {
		VulkanicAPI.glSamplerParameterf(sampler, pname, param);
	}

	public static void samplerParameteriv(int sampler, int pname, int[] params) {
		VulkanicAPI.glSamplerParameteriv(sampler, pname, params);
	}

	public static long getVRAM() {
		if (VulkanicAPI.getGraphicsCapabilities().GL_NVX_gpu_memory_info) {
			return VulkanicAPI.glGetInteger(VulkanicAPI.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L;
		} else {
			return 4294967296L;
		}
	}

	public static void deleteBuffers(int glId) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.glDeleteBuffers(glId);
	}

	public static void setPolygonMode(int mode) {
		if (mode != polygonMode) {
			polygonMode = mode;
			VulkanicAPI.setPolygonMode(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRONT_AND_BACK, mode);
		}
	}

	public static void overridePolygonMode() {
		backupPolygonMode = polygonMode;
		setPolygonMode(VulkanicAPI.GL_FILL);
	}

	public static void restorePolygonMode() {
		setPolygonMode(backupPolygonMode);
		backupPolygonMode = VulkanicAPI.GL_FILL;
	}

	public static void dispatchComputeIndirect(long offset) {
		VulkanicAPI.glDispatchComputeIndirect(offset);
	}

	public static void bindBuffer(int target, int buffer) {
		VulkanicAPI.bindBuffer(VulkanicAPI.getImmediateContext(), target, buffer);
	}

	public static int createBuffers() {
		return dsaState.createBuffers();
	}

	public static String getStringi(int glEnum, int index) {
		return VulkanicAPI.getString(VulkanicAPI.getImmediateContext(), glEnum, index);
	}

	public static void copyImageSubData(int sourceTexture, int target, int mip, int srcX, int srcY, int srcZ, int destTexture, int dstTarget, int dstMip, int dstX, int dstY, int dstZ, int width, int height, int depth) {
		VulkanicAPI.glCopyImageSubData(sourceTexture, target, mip, srcX, srcY, srcZ, destTexture, dstTarget, dstMip, dstX, dstY, dstZ, width, height, depth);
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
			texParameteriv(textureToUnswizzle.getInt(i), TextureType.TEXTURE_2D.getGlType(), VulkanicAPI.GL_TEXTURE_SWIZZLE_RGBA,
				new int[]{VulkanicAPI.GL_RED, VulkanicAPI.GL_GREEN, VulkanicAPI.GL_BLUE, VulkanicAPI.GL_ALPHA});
		}
		textureToUnswizzle.clear();
	}

	public static void addUnswizzle(int shaderTexture) {
		textureToUnswizzle.add(shaderTexture);
	}

	public static int checkFramebufferStatus(int glFramebuffer) {
		return VulkanicAPI.glCheckFramebufferStatus(glFramebuffer);
	}

	public static void uniformMatrix3fv(int index, boolean b, FloatBuffer buf) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix3fv(VulkanicAPI.getImmediateContext(), index, b, buf);
	}

	public static void uniformMatrix3fv(int index, boolean b, float[] buf) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.setUniformMatrix3fv(VulkanicAPI.getImmediateContext(), index, b, buf);
	}

	public static void clearColor(float v, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.glClearColor(v, v1, v2, v3);
	}

	public static int getAttribLocation(int handle, String irisNormal) {
		return VulkanicAPI.getAttributeLocation(VulkanicAPI.getImmediateContext(), handle, irisNormal);
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
			VulkanicAPI.glGenerateTextureMipmap(texture);
		}

		@Override
		public void texParameteri(int texture, int target, int pname, int param) {
			VulkanicAPI.glTextureParameteri(texture, pname, param);
		}

		@Override
		public void texParameterf(int texture, int target, int pname, float param) {
			VulkanicAPI.glTextureParameterf(texture, pname, param);
		}

		@Override
		public void texParameteriv(int texture, int target, int pname, int[] params) {
			VulkanicAPI.glTextureParameteriv(texture, pname, params);
		}


		@Override
		public void readBuffer(int framebuffer, int buffer) {
			VulkanicAPI.glNamedFramebufferReadBuffer(framebuffer, buffer);
		}

		@Override
		public void drawBuffers(int framebuffer, int[] buffers) {
			VulkanicAPI.glNamedFramebufferDrawBuffers(framebuffer, buffers);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			VulkanicAPI.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			VulkanicAPI.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			VulkanicAPI.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public int getTexParameteri(int texture, int target, int pname) {
			return VulkanicAPI.glGetTextureParameteri(texture, pname);
		}

		@Override
		public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
			VulkanicAPI.glCopyTextureSubImage2D(destTexture, i, i1, i2, i3, i4, width, height);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			if (target == VulkanicAPI.GL_TEXTURE_2D) {
				if (GlStateManager.TEXTURES[unit].binding == texture) {
					return;
				}

				VulkanicAPI.glBindTextureUnit(unit, texture);

				// Manually fix GLStateManager bindings...
				GlStateManager.TEXTURES[unit].binding = texture;
			} else {
				VulkanicAPI.glBindTextureUnit(unit, texture);
			}
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			int buffer = VulkanicAPI.glCreateBuffers();
			VulkanicAPI.glNamedBufferData(buffer, data, usage);
			return buffer;
		}

		@Override
		public int createBuffers() {
			return VulkanicAPI.glCreateBuffers();
		}

		@Override
		public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
			VulkanicAPI.glBlitNamedFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
		}

		@Override
		public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
			VulkanicAPI.glNamedFramebufferTexture(fb, attachment, texture, levels);
		}

		@Override
		public int createFramebuffer() {
			return VulkanicAPI.glCreateFramebuffers();
		}

		@Override
		public int createTexture(int target) {
			return VulkanicAPI.glCreateTextures(target);
		}
	}

	public static class DSAUnsupported implements DSAAccess {
		@Override
		public void generateMipmaps(int texture, int target) {
			int previous = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding;
			GlStateManager._bindTexture(texture);
			VulkanicAPI.glGenerateMipmap(target);
			GlStateManager._bindTexture(previous);
		}

		@Override
		public void texParameteri(int texture, int target, int pname, int param) {
			bindTextureForSetup(target, texture);
			VulkanicAPI.setTextureParameter(VulkanicAPI.getImmediateContext(), target, pname, param);
			restoreTexture();
		}

		@Override
		public void texParameterf(int texture, int target, int pname, float param) {
			bindTextureForSetup(target, texture);
			VulkanicAPI.glTexParameterf(target, pname, param);
			restoreTexture();
		}

		@Override
		public void texParameteriv(int texture, int target, int pname, int[] params) {
			bindTextureForSetup(target, texture);
			VulkanicAPI.texParameteriv(VulkanicAPI.getImmediateContext(), target, pname, params);
			restoreTexture();
		}

		@Override
		public void readBuffer(int framebuffer, int buffer) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
			VulkanicAPI.setReadBuffer(VulkanicAPI.getImmediateContext(), buffer);
		}

		@Override
		public void drawBuffers(int framebuffer, int[] buffers) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
			VulkanicAPI.drawBuffers(VulkanicAPI.getImmediateContext(), buffers);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
			VulkanicAPI.glClearBufferfv(buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
			VulkanicAPI.glClearBufferiv(buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
			VulkanicAPI.glClearBufferuiv(buffer, drawbuffer, values);
		}

		@Override
		public int getTexParameteri(int texture, int target, int pname) {
			bindTextureForSetup(target, texture);
			return VulkanicAPI.glGetTexParameteri(target, pname);
		}

		@Override
		public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
			int previous = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding;
			GlStateManager._bindTexture(destTexture);
			VulkanicAPI.glCopyTexSubImage2D(target, i, i1, i2, i3, i4, width, height);
			GlStateManager._bindTexture(previous);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			int activeTexture = GlStateManager.activeTexture;
			GlStateManager._activeTexture(VulkanicAPI.GL_TEXTURE0 + unit);
			VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), target, texture);
			if (target == VulkanicAPI.GL_TEXTURE_2D) {
				GlStateManager.TEXTURES[unit].binding = texture;
			}
			GlStateManager._activeTexture(VulkanicAPI.GL_TEXTURE0 + activeTexture);
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			int buffer = GlStateManager._glGenBuffers();
			GlStateManager._glBindBuffer(target, buffer);
			bufferData(target, data, usage);
			GlStateManager._glBindBuffer(target, 0);

			return buffer;
		}

		@Override
		public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_READ_FRAMEBUFFER, source);
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_DRAW_FRAMEBUFFER, dest);
			VulkanicAPI.glBlitFramebuffer(offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
		}

		@Override
		public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
			GlStateManager._glBindFramebuffer(fbtarget, fb);
			VulkanicAPI.framebufferTexture2D(VulkanicAPI.getImmediateContext(), fbtarget, attachment, target, texture, levels);
		}

		@Override
		public int createFramebuffer() {
			int framebuffer = GlStateManager.glGenFramebuffers();
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
			return framebuffer;
		}

		@Override
		public int createTexture(int target) {
			int texture = GlStateManager._genTexture();
			bindTextureForSetup(target, texture);
			restoreTexture();
			return texture;
		}

		@Override
		public int createBuffers() {
			return GlStateManager._glGenBuffers();
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
				GlStateManager._activeTexture(startingTexture);
				GlStateManager._bindTexture(binding);
				startingTexture++;
			}
		}
	}
	 */
}
