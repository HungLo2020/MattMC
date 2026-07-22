package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShaderProgram;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.glObject.texture.*;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.render.renderer.shaders.*;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicBlendEquation;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicCapability;
import net.vulkanic.VulkanicDepthCompareOp;
import net.vulkanic.VulkanicPolygonFace;
import net.vulkanic.VulkanicPolygonMode;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicResourceBarriers;
import org.jetbrains.annotations.Nullable;

/**
 * This is where all the magic happens. <br>
 * This is where LODs are draw to the world.
 */
public class LodRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererEventToFile)
			.build();
	public static final DhLogger RATE_LIMITED_LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererEventToFile)
			.maxCountPerSecond(4)
			.build();
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	private static final VulkanicResourceBarriers OFFSCREEN_LOD_WRITES_VISIBLE_TO_TEXTURE_FETCH = VulkanicResourceBarriers.of(
			VulkanicResourceBarriers.Barrier.TEXTURE_FETCH);
	
	public static final LodRenderer INSTANCE = new LodRenderer();
	
	
	
	// these ID's either what any render is currently using (since only one renderer can be active at a time), or just used previously
	private int activeFramebufferId = -1;
	private int activeColorTextureId = -1;
	private int activeDepthTextureId = -1;
	private IDhApiFramebuffer activeFramebuffer;
	private int textureWidth;
	private int textureHeight;
	private EDhDepthBufferFormat depthTextureFormat = EDhDepthBufferFormat.DEPTH32F;
	
	
	private IDhApiShaderProgram lodRenderProgram = null;
	public QuadElementBuffer quadIBO = null;
	private boolean renderObjectsCreated = false;
	
	// framebuffer and texture ID's for this renderer
	private IDhApiFramebuffer framebuffer;
	/** will be null if MC's framebuffer is being used since MC already has a color texture */
	@Nullable
	private DhColorTexture nullableColorTexture;
	private DHDepthTexture depthTexture;
	/** 
	 * If true the {@link LodRenderer#framebuffer} is the same as MC's.
	 * This should only be true in the case of Optifine so LODs won't be overwritten when shaders are enabled.
	 */
	private boolean usingMcFramebuffer = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private LodRenderer() { }
	
	
	
	//===========//
	// rendering //
	//===========//
	
	/**
	 * This will draw both opaque and transparent LODs if 
	 * {@link DhApiRenderProxy#getDeferTransparentRendering()} is false,
	 * otherwise it will only render opaque LODs.
	 */
	public void render(RenderParams renderParams, IProfilerWrapper profiler)
	{  this.renderLodPass(renderParams, profiler, false);  }
	
	/**
	 * This method is designed for Iris to be able 
	 * to draw water in a deferred rendering context. 
	 * It needs to be updated with any major changes, 
	 * but shouldn't be activated as per deferWaterRendering.
	 */
	public void renderDeferred(RenderParams renderParams, IProfilerWrapper profiler)
	{ this.renderLodPass(renderParams, profiler, true); }
	
	private void renderLodPass(RenderParams renderParams, IProfilerWrapper profiler, boolean runningDeferredPass)
	{
		//====================//
		// validate rendering //
		//====================//
		
		boolean deferTransparentRendering = DhApiRenderProxy.INSTANCE.getDeferTransparentRendering();
		if (runningDeferredPass 
			&& !deferTransparentRendering)
		{
			return;
		}
		boolean firstPass = !runningDeferredPass;
		
		// RenderParams parameter validation should be done before this
		if (!renderParams.validationRun)
		{
			throw new IllegalArgumentException("Render parameters validation");
		}
		
		RenderBufferHandler renderBufferHandler = renderParams.renderBufferHandler;
		GenericObjectRenderer genericRenderer = renderParams.genericRenderer;
		ILightMapWrapper lightmap = renderParams.lightmap;
		
		
		
		//=================//
		// rendering setup //
		//=================//
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderSetupEvent.class, renderParams);
		profiler.push("LOD GL setup");
		
		if (!this.renderObjectsCreated)
		{
			boolean setupSuccess = this.createRenderObjects();
			if (!setupSuccess)
			{
				// shouldn't normally happen, but just in case
				return;
			}
			
			// only do this once, that way they can still be reverted if desired
			if (Config.Client.Advanced.Graphics.overrideVanillaGraphicsSettings.get())
			{
				MC.disableVanillaClouds();
				MC.disableVanillaChunkFadeIn();
			}
			
			this.renderObjectsCreated = true;
		}
		
		this.setGLState(renderParams, firstPass);
		
		lightmap.bind();
		this.quadIBO.bind();
		
		if (firstPass)
		{
			// we only need to sort/cull the LODs during the first frame 
			profiler.popPush("LOD build render list");
			renderBufferHandler.buildRenderList(renderParams);
		}
		
		IDhApiShaderProgram lodShaderProgram = this.lodRenderProgram;
		IDhApiShaderProgram lodShaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
		if (lodShaderProgramOverride != null && lodShaderProgramOverride.overrideThisFrame())
		{
			lodShaderProgram = lodShaderProgramOverride;
		}
		
		
		
		//===========//
		// rendering //
		//===========//
		
		if (!runningDeferredPass)
		{
			//=========================//
			// opaque and non-deferred //
			// transparent rendering   //
			//=========================//
			
			// opaque LODs
			profiler.popPush("LOD Opaque");
			this.renderLodPass(lodShaderProgram, renderBufferHandler, renderParams, /*opaquePass*/ true);
			
			// custom objects with SSAO
			if (Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get())
			{
				profiler.popPush("Custom Objects");
				genericRenderer.render(renderParams, profiler, true, this.activeFramebufferId, this.activeFramebufferHasDepthAttachment(), this.getActiveDhFramebuffer());
			}
			
			// SSAO
			if (Config.Client.Advanced.Graphics.Ssao.enableSsao.get())
			{
				profiler.popPush("LOD SSAO");
				SSAORenderer.INSTANCE.render(new Mat4f(renderParams.dhProjectionMatrix), renderParams.partialTicks);
			}
			
			// custom objects without SSAO
			if (Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get())
			{
				profiler.popPush("Custom Objects");
				genericRenderer.render(renderParams, profiler, false, this.activeFramebufferId, this.activeFramebufferHasDepthAttachment(), this.getActiveDhFramebuffer());
			}
			
			// combined pass transparent rendering
			if (!deferTransparentRendering
				&& Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled)
			{
				profiler.popPush("LOD Transparent");
				this.renderLodPass(lodShaderProgram, renderBufferHandler, renderParams, /*opaquePass*/ false);
			}
			
			// far plane clip fading
			if (Config.Client.Advanced.Graphics.Quality.dhFadeFarClipPlane.get()
				// the fade shader messes with the GL state in a way Iris doesn't like,
				// so skip it if a shader is active
				&& (IRIS_ACCESSOR == null || !IRIS_ACCESSOR.isShaderPackInUse()))
			{
				profiler.popPush("Fade Far Clip Fade");
				DhFadeRenderer.INSTANCE.render(
						new Mat4f(renderParams.mcModelViewMatrix), new Mat4f(renderParams.mcProjectionMatrix),
						renderParams.partialTicks, profiler);
			}
			
			// fog
			if (Config.Client.Advanced.Graphics.Fog.enableDhFog.get())
			{
				profiler.popPush("LOD Fog");
				
				Mat4f combinedMatrix = new Mat4f(renderParams.dhProjectionMatrix);
				combinedMatrix.multiply(renderParams.dhModelViewMatrix);

				if (VulkanicAPI.isVulkanBackendSelected())
				{
					VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), OFFSCREEN_LOD_WRITES_VISIBLE_TO_TEXTURE_FETCH);
				}
				
				FogRenderer.INSTANCE.render(combinedMatrix, renderParams.partialTicks);
			}
			
			
			
			//=================//
			// debug rendering //
			//=================//
			
			if (Config.Client.Advanced.Debugging.DebugWireframe.enableRendering.get())
			{
				profiler.popPush("Debug wireframes");
				
				Mat4f combinedMatrix = new Mat4f(renderParams.dhProjectionMatrix);
				combinedMatrix.multiply(renderParams.dhModelViewMatrix);
				
				// Note: this can be very slow if a lot of boxes are being rendered 
				DebugRenderer.INSTANCE.render(combinedMatrix);
			}
			
			
			
			//===================//
			// optifine clean up //
			//===================//
			
			if (this.usingMcFramebuffer)
			{
				// If MC's framebuffer is being used the depth needs to be cleared to prevent rendering on top of MC.
				// This should only happen when Optifine shaders are being used.
				CommandContext ctx = VulkanicAPI.getCommandContext();
				VulkanicAPI.clearDepthBuffer(ctx);
			}
			
			
			
			//=============================//
			// Apply to the MC Framebuffer //
			//=============================//
			
			boolean cancelApplyShader = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeApplyShaderRenderEvent.class, renderParams);
			if (!cancelApplyShader)
			{
				profiler.popPush("LOD Apply");
				if (VulkanicAPI.isVulkanBackendSelected())
				{
					VulkanicAPI.applyResourceBarriers(VulkanicAPI.getCommandContext(), OFFSCREEN_LOD_WRITES_VISIBLE_TO_TEXTURE_FETCH);
				}
				
				// Copy the LOD framebuffer to Minecraft's framebuffer
				DhApplyShader.INSTANCE.render(renderParams.partialTicks);
			}
		}
		else
		{
			//====================//
			// deferred rendering //
			//====================//
			
			if (Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled)
			{
				profiler.popPush("LOD Transparent");
				this.renderLodPass(lodShaderProgram, renderBufferHandler, renderParams, /*opaquePass*/ false);
				
				
				if (Config.Client.Advanced.Graphics.Fog.enableDhFog.get())
				{
					profiler.popPush("LOD Fog");
					
					Mat4f combinedMatrix = new Mat4f(renderParams.dhProjectionMatrix);
					combinedMatrix.multiply(renderParams.dhModelViewMatrix);
					
					FogRenderer.INSTANCE.render(combinedMatrix, renderParams.partialTicks);
				}
			}
		}
		//================//
		// render cleanup //
		//================//
		
		profiler.popPush("LOD cleanup");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderCleanupEvent.class, renderParams);
		
		lightmap.unbind();
		this.quadIBO.unbind();
		lodShaderProgram.unbind();
		
		
		// end of internal LOD profiling
		profiler.pop();
	}
	
	
	
	//=================//
	// Setup Functions //
	//=================//
	
	private void setGLState(
			DhApiRenderParam renderEventParam,
			boolean firstPass)
	{
		//===================//
		// framebuffer setup //
		//===================//
		
		// get the active framebuffer
		IDhApiFramebuffer framebuffer = this.framebuffer;
		IDhApiFramebuffer framebufferOverride = OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class);
		if (framebufferOverride != null && framebufferOverride.overrideThisFrame())
		{
			framebuffer = framebufferOverride;
		}
		this.activeFramebuffer = framebuffer;
		this.activeFramebufferId = framebuffer.getId();
		framebuffer.bind();
		
		
		
		//==========//
		// bindings //
		//==========//
		CommandContext ctx = VulkanicAPI.getCommandContext();
		
		// by default draw everything as triangles
		VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, VulkanicPolygonMode.FILL);
		VulkanicAPI.setCullFaceEnabled(ctx, true);
		
		VulkanicAPI.blendFunc(ctx, VulkanicBlendFactor.SRC_ALPHA, VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA);
		VulkanicAPI.setBlendFunction(
			ctx,
			VulkanicBlendFactor.SRC_ALPHA,
			VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
			VulkanicBlendFactor.ONE,
			VulkanicBlendFactor.ZERO
		);
		
		VulkanicAPI.setCapabilityEnabled(ctx, VulkanicCapability.SCISSOR_TEST, false);

		VulkanicAPI.setColorMask(ctx, true, true, true, true);

		// Enable depth test and depth mask
		VulkanicAPI.setDepthTestEnabled(ctx, true);
		VulkanicAPI.setDepthFunc(ctx, VulkanicDepthCompareOp.LESS);
		VulkanicAPI.setDepthWriteMask(ctx, true);
		
		// This is required for MC versions 1.21.5+
		// due to MC updating the lightmap by changing the viewport size
		VulkanicAPI.setDynamicViewport(ctx, 0, 0, this.textureWidth, this.textureHeight);
		
		this.lodRenderProgram.bind();
		
		
		
		//==========//
		// uniforms //
		//==========//
		
		IDhApiShaderProgram shaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
		if (shaderProgramOverride != null && shaderProgramOverride.overrideThisFrame())
		{
			shaderProgramOverride.fillUniformData(renderEventParam);
		}
		
		this.lodRenderProgram.fillUniformData(renderEventParam);
		
		
		
		
		//===============//
		// texture setup //
		//===============//
		
		// resize the textures if needed
		EDhDepthBufferFormat targetDepthTextureFormat = this.getTargetDepthTextureFormat();
		if (MC_RENDER.getTargetFramebufferViewportWidth() != this.textureWidth
				|| MC_RENDER.getTargetFramebufferViewportHeight() != this.textureHeight
				|| targetDepthTextureFormat != this.depthTextureFormat)
		{
			// just resizing the textures doesn't work when Optifine is present,
			// so recreate the textures with the new size instead
			this.createAndBindTextures(targetDepthTextureFormat);
		}
		
		
		// set the active textures
		this.activeDepthTextureId = this.depthTexture.getTextureId();
		
		if (this.nullableColorTexture != null)
		{
			this.activeColorTextureId = this.nullableColorTexture.getTextureId();
		}
		else
		{
			// get MC's color texture
			this.activeColorTextureId = VulkanicAPI.getFramebufferColorAttachment0ObjectName(ctx);
		}
		
		
		// needs to be fired after all the textures have been created/bound
		boolean clearTextures = !ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeTextureClearEvent.class, renderEventParam);
		if (clearTextures)
		{
			VulkanicAPI.setClearDepth(ctx, 1.0);
			
			float[] clearColorValues = new float[4];
			VulkanicAPI.getClearColor(ctx, clearColorValues);
			VulkanicAPI.setClearColor(ctx, clearColorValues[0], clearColorValues[1], clearColorValues[2], 0.0f);
			
			if (this.usingMcFramebuffer && framebufferOverride == null)
			{
				// Due to using MC/Optifine's framebuffer we need to re-bind the depth texture,
				// otherwise we'll be writing to MC/Optifine's depth texture which causes rendering issues
				framebuffer.addDepthAttachment(this.depthTexture.getTextureId(), this.depthTextureFormat.isCombinedStencil());
				
				
				// don't clear the color texture, that removes the sky
				VulkanicAPI.clearDepthBuffer(ctx);
			}
			else if (firstPass)
			{
				VulkanicAPI.clearColorAndDepthBuffers(ctx);
			}
		}
		
		
	}
	
	private boolean createRenderObjects()
	{
		if (this.renderObjectsCreated)
		{
			LOGGER.warn("Renderer setup called but it has already completed setup!");
			return false;
		}
		
		// GLProxy should have already been created by this point, but just in case create it now
		GLProxy.getInstance();
		
		
		
		LOGGER.info("Setting up renderer");
		this.lodRenderProgram = new DhTerrainShaderProgram();
		
		this.quadIBO = new QuadElementBuffer();
		this.quadIBO.reserve(LodBufferContainer.MAX_QUADS_PER_BUFFER);
		
		
		// create or get the frame buffer
		// normal use case
		this.framebuffer = new DhFramebuffer();
		this.usingMcFramebuffer = false;
		
		// create and bind the necessary textures
		this.createAndBindTextures();
		
		if(!VulkanicAPI.isFramebufferComplete(this.framebuffer.getStatus()))
		{
			// This generally means something wasn't bound, IE missing either the color or depth texture
			LOGGER.warn("Framebuffer ["+this.framebuffer.getId()+"] isn't complete.");
			return false;
		}
		
		
		
		LOGGER.info("Renderer setup complete");
		return true;
	}
	
	@SuppressWarnings( "deprecation" )
	private void createAndBindTextures()
	{
		this.createAndBindTextures(this.getTargetDepthTextureFormat());
	}

	@SuppressWarnings( "deprecation" )
	private void createAndBindTextures(EDhDepthBufferFormat depthTextureFormat)
	{
		int oldWidth = this.textureWidth;
		int oldHeight = this.textureHeight;
		this.textureWidth = MC_RENDER.getTargetFramebufferViewportWidth();
		this.textureHeight = MC_RENDER.getTargetFramebufferViewportHeight();
		this.depthTextureFormat = depthTextureFormat;
		
		DhApiTextureCreatedParam textureCreatedParam = new DhApiTextureCreatedParam(
				oldWidth, oldHeight,
				this.textureWidth, this.textureHeight
		);
		
		
		// DhApiColorDepthTextureCreatedEvent needs to be kept around since old versions of Iris need it
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiColorDepthTextureCreatedEvent.class, new DhApiColorDepthTextureCreatedEvent.EventParam(textureCreatedParam));
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeColorDepthTextureCreatedEvent.class, textureCreatedParam);
		
		
		// also update the framebuffer override if present
		IDhApiFramebuffer framebufferOverride = OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class);
		
		
		this.depthTexture = new DHDepthTexture(this.textureWidth, this.textureHeight, this.depthTextureFormat);
		this.framebuffer.addDepthAttachment(this.depthTexture.getTextureId(), this.depthTextureFormat.isCombinedStencil());
		if (framebufferOverride != null)
		{
			framebufferOverride.addDepthAttachment(this.depthTexture.getTextureId(), this.depthTextureFormat.isCombinedStencil());
		}
		
		// if we are using MC's frame buffer, a color texture is already present and shouldn't need to be bound
		if (!this.usingMcFramebuffer)
		{
			this.nullableColorTexture = DhColorTexture.builder().setDimensions(this.textureWidth, this.textureHeight)
					.setInternalFormat(EDhInternalTextureFormat.RGBA8)
					.setPixelType(EDhPixelType.UNSIGNED_BYTE)
					.setPixelFormat(EDhPixelFormat.RGBA)
					.build();
			
			this.framebuffer.addColorAttachment(0, this.nullableColorTexture.getTextureId());
			if (framebufferOverride != null)
			{
				framebufferOverride.addColorAttachment(0, this.nullableColorTexture.getTextureId());
			}
		}
		else
		{
			this.nullableColorTexture = null;
		}
		
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterColorDepthTextureCreatedEvent.class, textureCreatedParam);
	}

	private EDhDepthBufferFormat getTargetDepthTextureFormat()
	{
		net.blaze3d.pipeline.RenderTarget mainRenderTarget = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
		if (mainRenderTarget == null)
		{
			return EDhDepthBufferFormat.DEPTH32F;
		}

		GpuTexture depthTexture = mainRenderTarget.getDepthTexture();
		if (depthTexture == null)
		{
			return EDhDepthBufferFormat.DEPTH32F;
		}

		return switch (depthTexture.getFormat())
		{
			case DEPTH32 -> EDhDepthBufferFormat.DEPTH32;
			case DEPTH24_STENCIL8 -> EDhDepthBufferFormat.DEPTH24_STENCIL8;
			case DEPTH32F_STENCIL8 -> EDhDepthBufferFormat.DEPTH32F_STENCIL8;
			default -> EDhDepthBufferFormat.DEPTH32F;
		};
	}
	
	
	
	//===============//
	// LOD rendering //
	//===============//
	
	private void renderLodPass(IDhApiShaderProgram shaderProgram, RenderBufferHandler lodBufferHandler, RenderParams renderEventParam, boolean opaquePass)
	{
		CommandContext ctx = VulkanicAPI.getCommandContext();
		DhLodRenderPlan renderPlan = this.createLodRenderPlan(opaquePass);
		SortedArraySet<LodBufferContainer> lodBufferContainers = lodBufferHandler.getColumnRenderBuffers();
		traceDhLodRenderPlan(
				"begin",
				shaderProgram,
				renderPlan,
				opaquePass,
				lodBufferContainers,
				"framebufferPending=true");

		boolean renderWireframe = Config.Client.Advanced.Debugging.renderWireframe.get();
		if (renderWireframe)
		{
			VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, VulkanicPolygonMode.LINE);
			VulkanicAPI.setCullFaceEnabled(ctx, false);
		}
		else
		{
			VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, VulkanicPolygonMode.FILL);
			VulkanicAPI.setCullFaceEnabled(ctx, true);
		}

		renderPlan.initialState().apply(ctx);

		//===========//
		// rendering //
		//===========//
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderPassEvent.class, renderEventParam);

		if (IRIS_ACCESSOR != null)
		{
			// done to fix a bug with Iris where face culling isn't properly set or reverted in the MC state manager
			// which causes Sodium to render some water chunks with their normal inverted
			// https://github.com/IrisShaders/Iris/issues/2582
			// https://github.com/IrisShaders/Iris/blob/1.21.9/common/src/main/java/net/irisshaders/iris/compat/dh/LodRendererEvents.java#L346
			VulkanicAPI.setCullFaceEnabled(ctx, true);
		}

		if (!this.isCurrentDrawFramebufferComplete(ctx, opaquePass))
		{
			traceDhLodRenderPlan(
					"skip-framebuffer-incomplete",
					shaderProgram,
					renderPlan,
					opaquePass,
					lodBufferContainers,
					"activeFramebuffer=" + this.activeFramebufferId
							+ ":drawFramebuffer=" + VulkanicAPI.getDrawFramebufferBinding());
			renderPlan.cleanupState().applyIfPresent(ctx);
			if (renderWireframe)
			{
				VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, VulkanicPolygonMode.FILL);
				VulkanicAPI.setCullFaceEnabled(ctx, true);
			}
			return;
		}
		
		
		if (lodBufferContainers != null)
		{
			traceDhLodRenderPlan("ready", shaderProgram, renderPlan, opaquePass, lodBufferContainers, "framebufferComplete=true");
			try
			{
				try (RenderPass ignored = this.createVulkanCompatibilityRenderPass("Distant Horizons LOD"))
				{
					for (DhLodRenderPhase phase : renderPlan.phases())
					{
						phase.stateOverride().applyIfPresent(ctx);
						this.renderLodBuffers(lodBufferContainers, shaderProgram, renderEventParam, phase);
					}
					renderPlan.renderPassExitState().applyIfPresent(ctx);
				}
			}
			finally
			{
				renderPlan.cleanupState().applyIfPresent(ctx);
			}
		}
		else
		{
			traceDhLodRenderPlan("skip-no-containers", shaderProgram, renderPlan, opaquePass, null, "framebufferComplete=true");
		}
		renderPlan.cleanupState().applyIfPresent(ctx);
		
		
		
		//=========================//
		// debug wireframe cleanup //
		//=========================//
		
		if (renderWireframe)
		{
			// default back to GL_FILL since all other rendering uses it 
			VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, VulkanicPolygonMode.FILL);
			VulkanicAPI.setCullFaceEnabled(ctx, true);
		}
	}

	private boolean isCurrentDrawFramebufferComplete(CommandContext ctx, boolean opaquePass)
	{
		int framebufferStatus = VulkanicAPI.checkFramebufferStatus(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER);
		if (VulkanicAPI.isFramebufferComplete(framebufferStatus))
		{
			return true;
		}

		RATE_LIMITED_LOGGER.warn(
				"Skipping Distant Horizons {} LOD pass because draw framebuffer [{}] is incomplete after render-pass setup. Status [0x{}].",
				opaquePass ? "opaque" : "transparent",
				VulkanicAPI.getDrawFramebufferBinding(),
				Integer.toHexString(framebufferStatus));
		return false;
	}

	private DhLodRenderPlan createLodRenderPlan(boolean opaquePass)
	{
		boolean useVulkanNoShaderWaterOrdering = this.shouldUseVulkanNoShaderWaterOrdering();
		if (opaquePass && useVulkanNoShaderWaterOrdering)
		{
			return DhLodRenderPlan.opaqueVulkanNoShaderWaterPlan();
		}
		if (!opaquePass && useVulkanNoShaderWaterOrdering)
		{
			return DhLodRenderPlan.transparentVulkanNoShaderWaterPlan();
		}
		return opaquePass ? DhLodRenderPlan.opaqueDefaultPlan() : DhLodRenderPlan.transparentDefaultPlan();
	}

	private enum DhLodBufferBucket
	{
		OPAQUE(container -> container.vbos),
		TRANSPARENT_SIDE(container -> container.vbosTransparent),
		TRANSPARENT_UP(container -> container.vbosTransparentUp),
		TRANSPARENT_WATER_UP(container -> container.vbosTransparentWaterUp);

		private final java.util.function.Function<LodBufferContainer, GLVertexBuffer[]> selector;

		DhLodBufferBucket(java.util.function.Function<LodBufferContainer, GLVertexBuffer[]> selector)
		{
			this.selector = selector;
		}

		private java.util.function.Function<LodBufferContainer, GLVertexBuffer[]> selector()
		{
			return this.selector;
		}
	}

	private record DhLodRenderPlan(
			DhLodRenderState initialState,
			DhLodRenderPhase[] phases,
			DhOptionalLodRenderState renderPassExitState,
			DhOptionalLodRenderState cleanupState)
	{
		private static DhLodRenderPlan opaqueDefaultPlan()
		{
			return new DhLodRenderPlan(
					DhLodRenderState.OPAQUE,
					new DhLodRenderPhase[] {
							DhLodRenderPhase.inherited(DhLodBufferBucket.OPAQUE)
					},
					DhOptionalLodRenderState.empty(),
					DhOptionalLodRenderState.empty());
		}

		private static DhLodRenderPlan transparentDefaultPlan()
		{
			return new DhLodRenderPlan(
					DhLodRenderState.TRANSPARENT,
					new DhLodRenderPhase[] {
							DhLodRenderPhase.inherited(DhLodBufferBucket.TRANSPARENT_SIDE),
							DhLodRenderPhase.inherited(DhLodBufferBucket.TRANSPARENT_UP),
							DhLodRenderPhase.inherited(DhLodBufferBucket.TRANSPARENT_WATER_UP)
					},
					DhOptionalLodRenderState.empty(),
					DhOptionalLodRenderState.of(DhLodRenderState.TRANSPARENT));
		}

		private static DhLodRenderPlan opaqueVulkanNoShaderWaterPlan()
		{
			return new DhLodRenderPlan(
					DhLodRenderState.OPAQUE,
					new DhLodRenderPhase[] {
							DhLodRenderPhase.inherited(DhLodBufferBucket.OPAQUE),
							DhLodRenderPhase.withState(DhLodRenderState.WATER_SURFACE, DhLodBufferBucket.TRANSPARENT_WATER_UP)
					},
					DhOptionalLodRenderState.of(DhLodRenderState.OPAQUE),
					DhOptionalLodRenderState.of(DhLodRenderState.OPAQUE));
		}

		private static DhLodRenderPlan transparentVulkanNoShaderWaterPlan()
		{
			return new DhLodRenderPlan(
					DhLodRenderState.TRANSPARENT,
					new DhLodRenderPhase[] {
							DhLodRenderPhase.withState(DhLodRenderState.TRANSPARENT_DETAIL, DhLodBufferBucket.TRANSPARENT_SIDE),
							DhLodRenderPhase.inherited(DhLodBufferBucket.TRANSPARENT_UP),
							DhLodRenderPhase.withState(DhLodRenderState.WATER_SURFACE, DhLodBufferBucket.TRANSPARENT_WATER_UP)
					},
					DhOptionalLodRenderState.empty(),
					DhOptionalLodRenderState.of(DhLodRenderState.TRANSPARENT));
		}
	}

	private record DhLodRenderPhase(
			DhOptionalLodRenderState stateOverride,
			DhLodBufferBucket bufferBucket)
	{
		private static DhLodRenderPhase inherited(DhLodBufferBucket bufferBucket)
		{
			return new DhLodRenderPhase(DhOptionalLodRenderState.empty(), bufferBucket);
		}

		private static DhLodRenderPhase withState(DhLodRenderState state, DhLodBufferBucket bufferBucket)
		{
			return new DhLodRenderPhase(DhOptionalLodRenderState.of(state), bufferBucket);
		}
	}

	private record DhOptionalLodRenderState(@Nullable DhLodRenderState state)
	{
		private static DhOptionalLodRenderState empty()
		{
			return new DhOptionalLodRenderState(null);
		}

		private static DhOptionalLodRenderState of(DhLodRenderState state)
		{
			return new DhOptionalLodRenderState(state);
		}

		private void applyIfPresent(CommandContext ctx)
		{
			if (this.state != null)
			{
				this.state.apply(ctx);
			}
		}
	}

	private enum DhLodRenderState
	{
		OPAQUE(true, true, true, VulkanicDepthCompareOp.LESS, true, false, false),
		TRANSPARENT(true, true, true, VulkanicDepthCompareOp.LESS, true, true, true),
		TRANSPARENT_DETAIL(true, true, true, VulkanicDepthCompareOp.LESS, false, true, false),
		WATER_SURFACE(true, false, true, VulkanicDepthCompareOp.ALWAYS, true, true, false);

		private final boolean colorWrite;
		private final boolean cullFace;
		private final boolean depthTest;
		private final VulkanicDepthCompareOp depthFunc;
		private final boolean depthWrite;
		private final boolean blend;
		private final boolean configureTransparentBlend;

		DhLodRenderState(
				boolean colorWrite,
				boolean cullFace,
				boolean depthTest,
				VulkanicDepthCompareOp depthFunc,
				boolean depthWrite,
				boolean blend,
				boolean configureTransparentBlend)
		{
			this.colorWrite = colorWrite;
			this.cullFace = cullFace;
			this.depthTest = depthTest;
			this.depthFunc = depthFunc;
			this.depthWrite = depthWrite;
			this.blend = blend;
			this.configureTransparentBlend = configureTransparentBlend;
		}

		private void apply(CommandContext ctx)
		{
			VulkanicAPI.setColorMask(ctx, this.colorWrite, this.colorWrite, this.colorWrite, this.colorWrite);
			VulkanicAPI.setCullFaceEnabled(ctx, this.cullFace);
			VulkanicAPI.setDepthTestEnabled(ctx, this.depthTest);
			VulkanicAPI.setDepthFunc(ctx, this.depthFunc);
			VulkanicAPI.setDepthWriteMask(ctx, this.depthWrite);
			VulkanicAPI.setBlendEnabled(ctx, this.blend);
			if (this.configureTransparentBlend)
			{
				VulkanicAPI.setBlendEquation(ctx, VulkanicBlendEquation.ADD);
				VulkanicAPI.setBlendFunction(
					ctx,
					VulkanicBlendFactor.SRC_ALPHA,
					VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
					VulkanicBlendFactor.ONE,
					VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA
				);
			}
		}
	}

	private void renderLodBuffers(
			SortedArraySet<LodBufferContainer> lodBufferContainers,
			IDhApiShaderProgram shaderProgram,
			RenderParams renderEventParam,
			DhLodRenderPhase phase)
	{
		CommandContext ctx = VulkanicAPI.getCommandContext();
		int submittedVbos = 0;
		int submittedVertices = 0;
		int submittedIndices = 0;
		for (int lodIndex = 0; lodIndex < lodBufferContainers.size(); lodIndex++)
		{
			LodBufferContainer bufferContainer = lodBufferContainers.get(lodIndex);
			this.setShaderProgramMvmOffset(bufferContainer.minCornerBlockPos, shaderProgram, renderEventParam);

			GLVertexBuffer[] vbos = phase.bufferBucket().selector().apply(bufferContainer);
			for (int vboIndex = 0; vboIndex < vbos.length; vboIndex++)
			{
				GLVertexBuffer vbo = vbos[vboIndex];
				if (vbo == null)
				{
					continue;
				}

				if (vbo.getVertexCount() == 0)
				{
					continue;
				}

				vbo.bind();
				shaderProgram.bindVertexBuffer(vbo.getId());
				this.quadIBO.bind();
				int indexCount = (vbo.getVertexCount() / 4) * 6; // 4 vertices per DH quad, 6 indices per rendered quad.
				submittedVbos++;
				submittedVertices += vbo.getVertexCount();
				submittedIndices += indexCount;
				String bucketName = phase.bufferBucket().name().toLowerCase(java.util.Locale.ROOT);
				String lodWorkIdentity = "lod:" + bucketName
						+ ":lodIndex=" + lodIndex
						+ ":vboIndex=" + vboIndex
						+ ":vertices=" + vbo.getVertexCount()
						+ ":indices=" + indexCount;
				VulkanicAPI.recordShaderInputParitySubmittedWorkIdentity(
						"distant-horizons",
						lodWorkIdentity);
				try (VulkanicAPI.ShaderInputParityScope ignored = VulkanicAPI.beginShaderInputParitySemanticDraw(
						"dh-lod-terrain-draw",
						"distant-horizons",
						lodWorkIdentity,
						null,
						null,
						"distant-horizons:lod-terrain:" + shaderProgram.getClass().getSimpleName(),
						"distant-horizons-framebuffer",
						true,
						0,
						vbo.getVertexCount(),
						0,
						indexCount,
						1,
						0))
				{
					traceDhLodTerrainResources(shaderProgram, phase, bufferContainer, lodIndex, vboIndex);
					VulkanicAPI.traceShaderInputParityDhLodGeometry(
							"dh-lod-terrain-geometry",
							vbo.getId(),
							vbo.getVertexCount(),
							this.quadIBO.getId(),
							indexCount,
							this.quadIBO.getType(),
							com.seibel.distanthorizons.core.util.LodUtil.LOD_VERTEX_FORMAT.getByteSize(),
							com.seibel.distanthorizons.core.util.LodUtil.LOD_VERTEX_FORMAT.toString());
					VulkanicAPI.drawElements(
							ctx,
							VulkanicPrimitiveMode.TRIANGLES,
							indexCount,
							this.quadIBO.getType(), 0);
				}
				vbo.unbind();
			}
		}
		VulkanicAPI.recordShaderInputParitySubmittedWorkIdentity(
				"distant-horizons",
				"lod-phase:" + phase.bufferBucket().name().toLowerCase(java.util.Locale.ROOT)
						+ ":containers=" + lodBufferContainers.size()
						+ ":submittedVbos=" + submittedVbos
						+ ":submittedVertices=" + submittedVertices
						+ ":submittedIndices=" + submittedIndices);
		traceDhLodPhaseSummary(shaderProgram, phase, lodBufferContainers.size(), submittedVbos, submittedVertices, submittedIndices);
	}

	private static void traceDhLodRenderPlan(
			String status,
			IDhApiShaderProgram shaderProgram,
			DhLodRenderPlan renderPlan,
			boolean opaquePass,
			@Nullable SortedArraySet<LodBufferContainer> lodBufferContainers,
			String detail)
	{
		if (!VulkanicAPI.isShaderInputParityTracingEnabled())
		{
			return;
		}

		int containerCount = lodBufferContainers == null ? -1 : lodBufferContainers.size();
		VulkanicAPI.traceShaderInputParityOrdering(
				"dh-lod-plan",
				"distant-horizons-lod-renderer",
				"status=" + status
						+ ":shader=" + shaderProgram.getClass().getSimpleName()
						+ ":opaque=" + opaquePass
						+ ":containers=" + containerCount
						+ ":phases=" + renderPlan.phases().length
						+ ":" + detail);
	}

	private static void traceDhLodPhaseSummary(
			IDhApiShaderProgram shaderProgram,
			DhLodRenderPhase phase,
			int containerCount,
			int submittedVbos,
			int submittedVertices,
			int submittedIndices)
	{
		if (!VulkanicAPI.isShaderInputParityTracingEnabled())
		{
			return;
		}

		VulkanicAPI.traceShaderInputParityOrdering(
				"dh-lod-phase-summary",
				"distant-horizons-lod-renderer",
				"shader=" + shaderProgram.getClass().getSimpleName()
						+ ":bucket=" + phase.bufferBucket().name().toLowerCase(java.util.Locale.ROOT)
						+ ":containers=" + containerCount
						+ ":submittedVbos=" + submittedVbos
						+ ":submittedVertices=" + submittedVertices
						+ ":submittedIndices=" + submittedIndices);
	}

	private static void traceDhLodTerrainResources(
			IDhApiShaderProgram shaderProgram,
			DhLodRenderPhase phase,
			LodBufferContainer bufferContainer,
			int lodIndex,
			int vboIndex)
	{
		if (!VulkanicAPI.isShaderInputParityTracingEnabled())
		{
			return;
		}

		int lightmapTextureUnit = VulkanicAPI.isVulkanBackendSelected()
				? ILightMapWrapper.VULKAN_LIGHTMAP_TEXTURE_UNIT
				: ILightMapWrapper.OPENGL_LIGHTMAP_TEXTURE_UNIT;
		GpuTextureView lightmap = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView();
		java.util.List<String> resources = java.util.List.of(
				VulkanicAPI.shaderInputParitySamplerResource("uLightMap", lightmapTextureUnit, lightmap)
		);
		String bucketName = phase.bufferBucket().name().toLowerCase(java.util.Locale.ROOT);
		VulkanicAPI.traceShaderInputParitySyntheticResources(
				"dh-lod-terrain-resources",
				"distant-horizons:lod-terrain:" + bucketName,
				"dh:" + shaderProgram.getClass().getSimpleName() + ":vertex",
				"dh:" + shaderProgram.getClass().getSimpleName() + ":fragment",
				"distant-horizons:lod-terrain:" + bucketName
						+ ":region=" + bufferContainer.pos
						+ ":lod=" + lodIndex
						+ ":vbo=" + vboIndex,
				resources
		);
	}

	private boolean shouldUseVulkanNoShaderWaterOrdering()
	{
		return VulkanicAPI.isVulkanBackendSelected()
			&& !isIrisShaderRenderingEnabled();
	}

	private static boolean isIrisShaderRenderingEnabled()
	{
		return IRIS_ACCESSOR != null
			&& IRIS_ACCESSOR.areShadersEnabled()
			&& IRIS_ACCESSOR.isShaderPackInUse();
	}

	private RenderPass createVulkanCompatibilityRenderPass(String label)
	{
		if (!VulkanicAPI.isVulkanBackendSelected())
		{
			return null;
		}

		CommandContext ctx = VulkanicAPI.getCommandContext();
		int framebufferId = this.activeFramebufferId;
		boolean framebufferHasDepthAttachment = this.activeFramebufferHasDepthAttachment();
		int drawFramebufferId = VulkanicAPI.getDrawFramebufferBinding();
		if (drawFramebufferId > 0 && VulkanicAPI.getFramebufferColorAttachment0ObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0)
		{
			framebufferId = drawFramebufferId;
			framebufferHasDepthAttachment = VulkanicAPI.getFramebufferDepthAttachmentObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0;
		}
		if (framebufferId < 0)
		{
			return null;
		}

		DhFramebuffer descriptorOwner = this.getActiveDhFramebuffer();
		if (descriptorOwner != null
				&& descriptorOwner.getId() == framebufferId
				&& descriptorOwner.canCreateRenderTargetDescriptor())
		{
			boolean preferDescriptor = true;
			return VulkanicAPI.createCommandEncoder().createRenderPass(
					descriptorOwner.createRenderTargetDescriptor(() -> label),
					framebufferId,
					preferDescriptor);
		}

		return VulkanicAPI.createRenderPass(() -> label, framebufferId, framebufferHasDepthAttachment);
	}

	private boolean activeFramebufferHasDepthAttachment()
	{
		if (this.activeFramebuffer instanceof DhFramebuffer dhFramebuffer)
		{
			return dhFramebuffer.hasDepthAttachment();
		}

		return this.framebuffer instanceof DhFramebuffer dhFramebuffer && dhFramebuffer.hasDepthAttachment();
	}
		
	/**
	 * the MVM offset is needed so LODs can be rendered anywhere in the MC world
	 * without running into floating point percision loss.
	 */
	private void setShaderProgramMvmOffset(DhBlockPos pos, IDhApiShaderProgram shaderProgram, RenderParams renderEventParam) throws IllegalStateException
	{
		Vec3d camPos = renderEventParam.exactCameraPosition;
		Vec3f modelPos = new Vec3f(
				(float) (pos.getX() - camPos.x),
				(float) (pos.getY() - camPos.y),
				(float) (pos.getZ() - camPos.z));
		
		shaderProgram.bind();
		shaderProgram.setModelOffsetPos(modelPos);
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent.EventParam(renderEventParam, modelPos));
	}
	
	
	
	//===============//
	// API functions //
	//===============//
	
	/** @return -1 if no frame buffer has been bound yet */
	public int getActiveFramebufferId() { return this.activeFramebufferId; }

	public boolean hasActiveRenderTarget() { return this.activeFramebuffer != null && this.activeFramebufferId != -1; }

	public boolean bindActiveRenderTarget()
	{
		if (!this.hasActiveRenderTarget())
		{
			return false;
		}

		this.activeFramebuffer.bind();
		return true;
	}

	@Nullable
	public DhFramebuffer getActiveDhFramebuffer()
	{
		return this.activeFramebuffer instanceof DhFramebuffer dhFramebuffer ? dhFramebuffer : null;
	}
	
	/** @return -1 if no texture has been bound yet */
	public int getActiveColorTextureId() { return this.activeColorTextureId; }
	
	/** @return -1 if no texture has been bound yet */
	public int getActiveDepthTextureId() { return this.activeDepthTextureId; }
	
	
	
}
