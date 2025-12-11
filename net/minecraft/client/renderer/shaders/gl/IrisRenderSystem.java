// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.shaders.gl.sampler.SamplerLimits;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GL46C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Abstraction layer for OpenGL calls with render thread assertions.
 * 
 * Based on IRIS's IrisRenderSystem class
 * Reference: frnsrc/Iris-1.21.9/.../gl/IrisRenderSystem.java
 */
public class IrisRenderSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(IrisRenderSystem.class);
	
	private static DSAAccess dsaState;
	private static boolean hasMultibind;
	private static boolean supportsCompute;
	private static boolean supportsTesselation;
	private static int[] samplers;
	private static int[] emptyArray;
	private static boolean initialized = false;
	
	public static void initRenderer() {
		if (initialized) return;
		
		if (GL.getCapabilities().OpenGL45) {
			dsaState = new DSACore();
			LOGGER.info("OpenGL 4.5 detected, enabling DSA.");
		} else if (GL.getCapabilities().GL_ARB_direct_state_access) {
			dsaState = new DSAARB();
			LOGGER.info("ARB_direct_state_access detected, enabling DSA.");
		} else {
			dsaState = new DSAUnsupported();
			LOGGER.info("DSA support not detected.");
		}

		hasMultibind = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_multi_bind;
		supportsCompute = GL.getCapabilities().glDispatchCompute != 0;
		supportsTesselation = GL.getCapabilities().GL_ARB_tessellation_shader || GL.getCapabilities().OpenGL40;

		// Limit sampler arrays to a reasonable size to prevent excessive memory allocation
		int maxUnits = Math.min(SamplerLimits.get().getMaxTextureUnits(), 128);
		samplers = new int[maxUnits];
		emptyArray = new int[maxUnits];
		
		initialized = true;
	}
	
	// ============ Uniform Methods ============

	public static void uniform1f(int location, float v0) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform1f(location, v0);
	}

	public static void uniform1i(int location, int v0) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform1i(location, v0);
	}

	public static void uniform2f(int location, float v0, float v1) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform2f(location, v0, v1);
	}

	public static void uniform2i(int location, int v0, int v1) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform2i(location, v0, v1);
	}

	public static void uniform3f(int location, float v0, float v1, float v2) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform3f(location, v0, v1, v2);
	}

	public static void uniform3i(int location, int v0, int v1, int v2) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform3i(location, v0, v1, v2);
	}

	public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform4f(location, v0, v1, v2, v3);
	}

	public static void uniform4i(int location, int v0, int v1, int v2, int v3) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniform4i(location, v0, v1, v2, v3);
	}

	public static void uniformMatrix3fv(int location, boolean transpose, FloatBuffer matrix) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniformMatrix3fv(location, transpose, matrix);
	}

	public static void uniformMatrix3fv(int location, boolean transpose, float[] matrix) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniformMatrix3fv(location, transpose, matrix);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrix) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniformMatrix4fv(location, transpose, matrix);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, float[] matrix) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniformMatrix4fv(location, transpose, matrix);
	}
	
	// ============ Query Methods ============

	public static String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeBuffer, IntBuffer typeBuffer) {
		RenderSystem.assertOnRenderThread();
		return GL32C.glGetActiveUniform(program, index, maxLength, sizeBuffer, typeBuffer);
	}

	public static void getIntegerv(int pname, int[] params) {
		RenderSystem.assertOnRenderThread();
		GL32C.glGetIntegerv(pname, params);
	}

	public static void getFloatv(int pname, float[] params) {
		RenderSystem.assertOnRenderThread();
		GL32C.glGetFloatv(pname, params);
	}
	
	// ============ Texture Methods ============

	public static void bindTextureToUnit(int target, int unit, int texture) {
		if (dsaState != null) {
			dsaState.bindTextureToUnit(target, unit, texture);
		} else {
			// Fallback for when DSA is not initialized
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
			GlStateManager._bindTexture(texture);
		}
	}

	public static void bindTextureForSetup(int target, int texture) {
		GL30.glBindTexture(target, texture);
	}

	public static int createTexture(int target) {
		if (dsaState != null) {
			return dsaState.createTexture(target);
		}
		return GL11.glGenTextures();
	}

	public static void generateMipmaps(int texture, int target) {
		if (dsaState != null) {
			dsaState.generateMipmaps(texture, target);
		} else {
			bindTextureForSetup(target, texture);
			GL30.glGenerateMipmap(target);
		}
	}
	
	// ============ Sampler Methods ============

	public static int genSampler() {
		return GL33C.glGenSamplers();
	}

	public static void destroySampler(int sampler) {
		GL33C.glDeleteSamplers(sampler);
	}

	public static void bindSampler(int unit, int sampler) {
		if (!initialized) return;
		GL33C.glBindSampler(unit, sampler);
		samplers[unit] = sampler;
	}

	public static void bindSamplerToUnit(int unit, int sampler) {
		bindSampler(unit, sampler);
	}

	public static void unbindAllSamplers() {
		if (!initialized || samplers == null) return;
		
		boolean usedASampler = false;
		for (int i = 0; i < samplers.length; i++) {
			if (samplers[i] != 0) {
				usedASampler = true;
				if (!hasMultibind) GL33C.glBindSampler(i, 0);
				samplers[i] = 0;
			}
		}
		if (usedASampler && hasMultibind) {
			GL45C.glBindSamplers(0, emptyArray);
		}
	}

	public static void samplerParameteri(int sampler, int pname, int param) {
		GL33C.glSamplerParameteri(sampler, pname, param);
	}

	public static void samplerParameterf(int sampler, int pname, float param) {
		GL33C.glSamplerParameterf(sampler, pname, param);
	}

	public static void samplerParameteriv(int sampler, int pname, int[] params) {
		GL33C.glSamplerParameteriv(sampler, pname, params);
	}
	
	// ============ Framebuffer Methods ============

	public static int createFramebuffer() {
		if (dsaState != null) {
			return dsaState.createFramebuffer();
		}
		return GL30.glGenFramebuffers();
	}

	public static void bindFramebuffer(int target, int framebuffer) {
		GL30.glBindFramebuffer(target, framebuffer);
	}

	public static void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
		GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
	}

	public static int checkFramebufferStatus(int target) {
		return GL30.glCheckFramebufferStatus(target);
	}

	public static void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1,
									   int dstX0, int dstY0, int dstX1, int dstY1,
									   int mask, int filter) {
		GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
	}

	public static void blitFramebuffer(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1,
									   int dstX0, int dstY0, int dstX1, int dstY1,
									   int mask, int filter) {
		if (dsaState != null) {
			dsaState.blitFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		} else {
			// Fallback
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dest);
			GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		}
	}
	
	// ============ Buffer Methods ============

	public static void deleteBuffers(int buffer) {
		RenderSystem.assertOnRenderThread();
		GL15.glDeleteBuffers(buffer);
	}

	public static void bufferData(int target, float[] data, int usage) {
		RenderSystem.assertOnRenderThread();
		GL15.glBufferData(target, data, usage);
	}
	
	// ============ Draw Methods ============

	public static void drawArrays(int mode, int first, int count) {
		RenderSystem.assertOnRenderThread();
		GL11.glDrawArrays(mode, first, count);
	}

	public static void drawElements(int mode, int count, int type, long indices) {
		RenderSystem.assertOnRenderThread();
		GL11.glDrawElements(mode, count, type, indices);
	}
	
	// ============ State Methods ============

	public static void setPolygonMode(int mode) {
		GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, mode);
	}

	public static void clearColor(float r, float g, float b, float a) {
		RenderSystem.assertOnRenderThread();
		GL11.glClearColor(r, g, b, a);
	}

	public static void clear(int mask) {
		RenderSystem.assertOnRenderThread();
		GL11.glClear(mask);
	}

	public static void viewport(int x, int y, int width, int height) {
		GL11.glViewport(x, y, width, height);
	}
	
	// ============ Program Methods ============

	public static void bindAttributeLocation(int program, int index, CharSequence name) {
		RenderSystem.assertOnRenderThread();
		GL20.glBindAttribLocation(program, index, name);
	}

	public static int getAttribLocation(int program, String name) {
		return GL20.glGetAttribLocation(program, name);
	}

	public static int getUniformBlockIndex(int program, String uniformBlockName) {
		RenderSystem.assertOnRenderThread();
		return GL32C.glGetUniformBlockIndex(program, uniformBlockName);
	}

	public static void uniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
		RenderSystem.assertOnRenderThread();
		GL32C.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
	}
	
	// ============ Compute Methods ============

	public static boolean supportsCompute() {
		return supportsCompute;
	}

	public static boolean supportsTesselation() {
		return supportsTesselation;
	}

	public static void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
		if (supportsCompute) {
			GL43C.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
		}
	}

	public static void memoryBarrier(int barriers) {
		if (supportsCompute) {
			GL43C.glMemoryBarrier(barriers);
		}
	}
	
	// ============ Blend Methods ============

	public static void disableBufferBlend(int buffer) {
		RenderSystem.assertOnRenderThread();
		GL32C.glDisablei(GL32C.GL_BLEND, buffer);
	}

	public static void enableBufferBlend(int buffer) {
		RenderSystem.assertOnRenderThread();
		GL32C.glEnablei(GL32C.GL_BLEND, buffer);
	}

	public static void blendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
		RenderSystem.assertOnRenderThread();
		if (GL.getCapabilities().GL_ARB_draw_buffers_blend) {
			org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
		} else if (GL.getCapabilities().OpenGL40) {
			org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
		}
	}
	
	// ============ Image Methods ============

	public static int getMaxImageUnits() {
		if (GL.getCapabilities().OpenGL42 || GL.getCapabilities().GL_ARB_shader_image_load_store) {
			return GL32C.glGetInteger(org.lwjgl.opengl.GL42C.GL_MAX_IMAGE_UNITS);
		}
		return 0;
	}

	public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
		if (GL.getCapabilities().OpenGL42) {
			org.lwjgl.opengl.GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
		} else if (GL.getCapabilities().GL_ARB_shader_image_load_store) {
			org.lwjgl.opengl.ARBShaderImageLoadStore.glBindImageTexture(unit, texture, level, layered, layer, access, format);
		}
	}
	
	// ============ DSA Interface ============

	public interface DSAAccess {
		void generateMipmaps(int texture, int target);
		void bindTextureToUnit(int target, int unit, int texture);
		int createTexture(int target);
		int createFramebuffer();
		void blitFramebuffer(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1,
							 int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
		void framebufferTexture2D(int framebuffer, int attachment, int textarget, int texture, int level);
		void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values);
		void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values);
		void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values);
		int bufferStorage(int target, float[] data, int usage);
	}
	
	// ============ DSA Implementations ============

	private static class DSACore implements DSAAccess {
		@Override
		public void generateMipmaps(int texture, int target) {
			GL45C.glGenerateTextureMipmap(texture);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			GL45C.glBindTextureUnit(unit, texture);
		}

		@Override
		public int createTexture(int target) {
			return GL45C.glCreateTextures(target);
		}

		@Override
		public int createFramebuffer() {
			return GL45C.glCreateFramebuffers();
		}

		@Override
		public void blitFramebuffer(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1,
									int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
			GL45C.glBlitNamedFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		}

		@Override
		public void framebufferTexture2D(int framebuffer, int attachment, int textarget, int texture, int level) {
			GL45C.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			GL45C.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			GL45C.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			GL45C.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			int buffer = GL45C.glCreateBuffers();
			GL45C.glNamedBufferStorage(buffer, data, usage);
			return buffer;
		}
	}

	private static class DSAARB implements DSAAccess {
		@Override
		public void generateMipmaps(int texture, int target) {
			ARBDirectStateAccess.glGenerateTextureMipmap(texture);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			ARBDirectStateAccess.glBindTextureUnit(unit, texture);
		}

		@Override
		public int createTexture(int target) {
			return ARBDirectStateAccess.glCreateTextures(target);
		}

		@Override
		public int createFramebuffer() {
			return ARBDirectStateAccess.glCreateFramebuffers();
		}

		@Override
		public void blitFramebuffer(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1,
									int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
			ARBDirectStateAccess.glBlitNamedFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		}

		@Override
		public void framebufferTexture2D(int framebuffer, int attachment, int textarget, int texture, int level) {
			ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			ARBDirectStateAccess.glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			ARBDirectStateAccess.glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			ARBDirectStateAccess.glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, values);
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			int buffer = ARBDirectStateAccess.glCreateBuffers();
			ARBDirectStateAccess.glNamedBufferStorage(buffer, data, usage);
			return buffer;
		}
	}

	private static class DSAUnsupported implements DSAAccess {
		@Override
		public void generateMipmaps(int texture, int target) {
			bindTextureForSetup(target, texture);
			GL30.glGenerateMipmap(target);
		}

		@Override
		public void bindTextureToUnit(int target, int unit, int texture) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
			GlStateManager._bindTexture(texture);
		}

		@Override
		public int createTexture(int target) {
			return GL11.glGenTextures();
		}

		@Override
		public int createFramebuffer() {
			return GL30.glGenFramebuffers();
		}

		@Override
		public void blitFramebuffer(int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1,
									int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dest);
			GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		}

		@Override
		public void framebufferTexture2D(int framebuffer, int attachment, int textarget, int texture, int level) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, textarget, texture, level);
		}

		@Override
		public void clearBufferfv(int framebuffer, int buffer, int drawbuffer, float[] values) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
			GL30.glClearBufferfv(buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
			GL30.glClearBufferiv(buffer, drawbuffer, values);
		}

		@Override
		public void clearBufferuiv(int framebuffer, int buffer, int drawbuffer, int[] values) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
			GL30.glClearBufferuiv(buffer, drawbuffer, values);
		}

		@Override
		public int bufferStorage(int target, float[] data, int usage) {
			int buffer = GL15.glGenBuffers();
			GL15.glBindBuffer(target, buffer);
			GL15.glBufferData(target, data, usage);
			return buffer;
		}
	}
}
