package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.gl.FullScreenQuadRenderer;
import net.minecraft.client.renderer.shaders.helpers.OptionalBoolean;
import net.minecraft.client.renderer.shaders.option.ShaderPackOptions;
import net.minecraft.client.renderer.shaders.pack.*;
import net.minecraft.client.renderer.shaders.program.Program;
import net.minecraft.client.renderer.shaders.program.ProgramBuilder;
import net.minecraft.client.renderer.shaders.program.ProgramSource;
import net.minecraft.client.renderer.shaders.program.ShaderCompileException;
import net.minecraft.client.renderer.shaders.targets.GBufferManager;
import net.minecraft.client.renderer.shaders.targets.RenderTarget;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shader pack rendering pipeline - represents rendering with a loaded shader pack.
 * 
 * Based on IRIS's IrisRenderingPipeline pattern.
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/IrisRenderingPipeline.java
 * 
 * This is the core shader rendering pipeline that:
 * - Manages G-buffers for MRT output
 * - Handles framebuffer binding during rendering phases
 * - Compiles shader programs from pack source (Step A5)
 * - Binds shader programs during rendering (Step A6)
 * - Executes composite passes (Step A7)
 * - Executes final pass to screen (Step A8)
 */
public class ShaderPackPipeline implements WorldRenderingPipeline {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderPackPipeline.class);
	
	private final String packName;
	private final String dimension;
	private final ShaderProperties shaderProperties;
	private final ShaderSourceProvider sourceProvider;
	private final ShaderPackOptions shaderPackOptions;
	private WorldRenderingPhase currentPhase = WorldRenderingPhase.NONE;
	
	// G-buffer management (Step A3)
	private GBufferManager gBufferManager;
	private GlFramebuffer gBufferFramebuffer;
	private boolean isRenderingWorld = false;
	private int cachedWidth = -1;
	private int cachedHeight = -1;
	
	// Shader programs (Step A5)
	private final Map<String, Program> programs = new HashMap<>();
	private Program gbuffersTerrainProgram;
	private Program gbuffersTexturedProgram;
	private Program gbuffersBasicProgram;
	private final List<Program> deferredPrograms = new ArrayList<>();  // Run before translucent
	private final List<Program> compositePrograms = new ArrayList<>(); // Run after translucent
	private Program finalProgram;
	private boolean programsCompiled = false;
	
	/**
	 * Creates a new shader pack pipeline.
	 * 
	 * @param packName The name of the shader pack
	 * @param dimension The dimension ID (e.g., "minecraft:overworld")
	 * @param packSource The shader pack source to load properties from
	 */
	public ShaderPackPipeline(String packName, String dimension, ShaderPackSource packSource) {
		this.packName = packName;
		this.dimension = dimension;
		
		// Load shader properties - following IRIS pattern
		ShaderProperties props = null;
		try {
			props = ShaderProperties.load(packSource);
		} catch (IOException e) {
			LOGGER.error("Failed to load shader properties for pack: {}", packName, e);
			props = ShaderProperties.empty();
		}
		this.shaderProperties = props;
		
		// Create shader source provider (Step 7)
		// Find starting paths in the shaders/ directory
		List<String> candidates = ShaderPackSourceNames.getPotentialStarts();
		List<AbsolutePackPath> startingPaths = ShaderPackSourceNames.findPresentSources(
			packSource, 
			"/shaders/", 
			candidates
		);
		
		LOGGER.debug("Found {} starting shader files for pack: {}", startingPaths.size(), packName);
		
		this.sourceProvider = new ShaderSourceProvider(packSource, startingPaths);
		
		// Create shader pack options (Step 8)
		// For now, this creates an empty option set
		// Full option discovery from GLSL will be implemented in future enhancements
		this.shaderPackOptions = new ShaderPackOptions();
		LOGGER.debug("Initialized shader pack options for pack: {}", packName);
		
		// Initialize G-buffer manager with default settings
		// Will be resized in beginLevelRendering() based on actual screen size
		this.gBufferManager = new GBufferManager(1, 1, createDefaultTargetSettings());  // Will resize later
		
		// Compile shader programs (Step A5)
		compilePrograms();
		
		LOGGER.info("Created shader pipeline for pack: {} in dimension: {}", packName, dimension);
	}
	
	/**
	 * Compiles all shader programs from the shader pack (Step A5).
	 * Following IRIS's program creation pattern.
	 * 
	 * IRIS Reference: IrisRenderingPipeline.java createShader() method
	 */
	private void compilePrograms() {
		LOGGER.info("Compiling shader programs for pack: {}", packName);
		
		// Try to compile gbuffers_terrain (most important for terrain rendering)
		gbuffersTerrainProgram = tryCompileProgram("gbuffers_terrain");
		
		// Try to compile fallback programs
		if (gbuffersTerrainProgram == null) {
			gbuffersTexturedProgram = tryCompileProgram("gbuffers_textured");
		}
		if (gbuffersTerrainProgram == null && gbuffersTexturedProgram == null) {
			gbuffersBasicProgram = tryCompileProgram("gbuffers_basic");
		}
		
		// Compile deferred programs (run before translucent rendering)
		// IRIS: deferred passes run after opaque geometry but before translucent
		for (int i = 0; i <= 7; i++) {
			String name = i == 0 ? "deferred" : "deferred" + i;
			Program deferred = tryCompileProgram(name);
			if (deferred != null) {
				deferredPrograms.add(deferred);
				LOGGER.info("Compiled deferred program: {}", name);
			}
		}
		
		// Compile composite programs (run after translucent rendering)
		// IRIS: composite passes run after all geometry
		for (int i = 0; i <= 7; i++) {
			String name = i == 0 ? "composite" : "composite" + i;
			Program composite = tryCompileProgram(name);
			if (composite != null) {
				compositePrograms.add(composite);
				LOGGER.info("Compiled composite program: {}", name);
			}
		}
		
		// Compile final program
		finalProgram = tryCompileProgram("final");
		
		programsCompiled = true;
		
		int compiledCount = programs.size();
		LOGGER.info("Compiled {} shader programs for pack: {} ({} deferred, {} composite)", 
			compiledCount, packName, deferredPrograms.size(), compositePrograms.size());
	}
	
	/**
	 * Attempts to compile a shader program from the pack.
	 * Returns null if the program doesn't exist or fails to compile.
	 * 
	 * @param programName The program name (e.g., "gbuffers_terrain")
	 * @return The compiled program, or null
	 */
	private Program tryCompileProgram(String programName) {
		try {
			// Get vertex and fragment sources
			String vertexSource = sourceProvider.getShaderSource(programName + ".vsh");
			String fragmentSource = sourceProvider.getShaderSource(programName + ".fsh");
			
			// Check if both sources exist
			if (vertexSource == null || fragmentSource == null) {
				LOGGER.debug("Program {} not found (vsh={}, fsh={})", 
					programName, vertexSource != null, fragmentSource != null);
				return null;
			}
			
			// Get optional geometry source
			String geometrySource = sourceProvider.getShaderSource(programName + ".gsh");
			
			LOGGER.debug("Compiling program: {} (vsh={} chars, fsh={} chars)", 
				programName, vertexSource.length(), fragmentSource.length());
			
			// Build the program using ProgramBuilder
			Program program = ProgramBuilder.begin(programName, vertexSource, geometrySource, fragmentSource)
				.build();
			
			// Store in programs map
			programs.put(programName, program);
			
			LOGGER.info("Successfully compiled program: {}", programName);
			return program;
			
		} catch (ShaderCompileException e) {
			LOGGER.error("Failed to compile program {}: {}", programName, e.getMessage());
			return null;
		} catch (Exception e) {
			LOGGER.error("Error compiling program {}", programName, e);
			return null;
		}
	}
	
	/**
	 * Creates the default G-buffer target settings.
	 * Extracted to eliminate code duplication.
	 * 
	 * @return Map of render target settings
	 */
	private static Map<Integer, GBufferManager.RenderTargetSettings> createDefaultTargetSettings() {
		Map<Integer, GBufferManager.RenderTargetSettings> targetSettings = new HashMap<>();
		// Default colortex0 with RGBA8 format
		targetSettings.put(0, new GBufferManager.RenderTargetSettings(InternalTextureFormat.RGBA8));
		// colortex1 for normals (common shader pack pattern)
		targetSettings.put(1, new GBufferManager.RenderTargetSettings(InternalTextureFormat.RGBA16F));
		// colortex2 for specular/PBR data (common shader pack pattern)
		targetSettings.put(2, new GBufferManager.RenderTargetSettings(InternalTextureFormat.RGBA8));
		return targetSettings;
	}
	
	@Override
	public void beginLevelRendering() {
		isRenderingWorld = true;
		
		LOGGER.debug("Begin level rendering with shader pack: {}", packName);
		
		// Get current window dimensions
		Minecraft mc = Minecraft.getInstance();
		com.mojang.blaze3d.pipeline.RenderTarget mainTarget = mc.getMainRenderTarget();
		int width = mainTarget.width;
		int height = mainTarget.height;
		
		// Resize G-buffers if needed (IRIS pattern: renderTargets.resizeIfNeeded())
		if (width != cachedWidth || height != cachedHeight) {
			cachedWidth = width;
			cachedHeight = height;
			
			// Destroy old G-buffer manager and create new one with correct size
			if (gBufferManager != null) {
				gBufferManager.destroy();
			}
			
			gBufferManager = new GBufferManager(width, height, createDefaultTargetSettings());
			
			// Create G-buffer framebuffer
			createGBufferFramebuffer();
			
			LOGGER.info("Resized G-buffers to {}x{}", width, height);
		}
		
		// Bind G-buffer framebuffer for MRT output (IRIS pattern)
		if (gBufferFramebuffer != null) {
			gBufferFramebuffer.bind();
			
			// Set draw buffers for MRT (Multiple Render Targets)
			// This tells OpenGL to write to multiple color attachments
			int[] drawBuffers = new int[]{
				GL30.GL_COLOR_ATTACHMENT0,  // colortex0
				GL30.GL_COLOR_ATTACHMENT1,  // colortex1  
				GL30.GL_COLOR_ATTACHMENT2   // colortex2
			};
			GL20.glDrawBuffers(drawBuffers);
			
			// Clear all buffers
			GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		} else {
			LOGGER.warn("G-buffer framebuffer not initialized, using default framebuffer");
		}
	}
	
	/**
	 * Creates the G-buffer framebuffer with MRT attachments.
	 * Following IRIS RenderTargets.createGbufferFramebuffer() pattern.
	 */
	private void createGBufferFramebuffer() {
		if (gBufferFramebuffer != null) {
			gBufferFramebuffer.destroy();
		}
		
		gBufferFramebuffer = new GlFramebuffer();
		
		// Attach color textures for MRT
		RenderTarget colorTex0 = gBufferManager.getOrCreate(0);
		RenderTarget colorTex1 = gBufferManager.getOrCreate(1);
		RenderTarget colorTex2 = gBufferManager.getOrCreate(2);
		
		gBufferFramebuffer.addColorAttachment(0, colorTex0.getMainTexture());
		gBufferFramebuffer.addColorAttachment(1, colorTex1.getMainTexture());
		gBufferFramebuffer.addColorAttachment(2, colorTex2.getMainTexture());
		
		// Attach depth texture from colortex0's depth or create separate
		// For now, we'll use a simple depth renderbuffer
		gBufferFramebuffer.addDepthAttachment(cachedWidth, cachedHeight);
		
		// Check framebuffer completeness
		if (!gBufferFramebuffer.isComplete()) {
			LOGGER.error("G-buffer framebuffer is not complete!");
		} else {
			LOGGER.debug("G-buffer framebuffer created successfully");
		}
	}
	
	@Override
	public void finalizeLevelRendering() {
		LOGGER.debug("Finalize level rendering with shader pack: {}", packName);
		
		isRenderingWorld = false;
		
		// Unbind G-buffer framebuffer
		GlFramebuffer.unbind();
		
		// Execute composite passes (Step A7)
		executeCompositePasses();
		
		// Execute final pass (Step A8)
		executeFinalPass();
	}
	
	/**
	 * Executes composite post-processing passes (Step A7).
	 * This runs both deferred and composite passes in order.
	 * Following IRIS's CompositeRenderer.renderAll() pattern.
	 * 
	 * IRIS Reference: CompositeRenderer.java:273-363
	 */
	private void executeCompositePasses() {
		int totalPasses = deferredPrograms.size() + compositePrograms.size();
		if (totalPasses == 0) {
			LOGGER.debug("No composite/deferred programs to execute");
			return;
		}
		
		LOGGER.debug("Executing {} deferred + {} composite passes", 
			deferredPrograms.size(), compositePrograms.size());
		
		// Execute deferred passes first (before translucent)
		for (int i = 0; i < deferredPrograms.size(); i++) {
			executePostProcessPass(deferredPrograms.get(i), "deferred", i);
		}
		
		// Execute composite passes (after translucent)
		for (int i = 0; i < compositePrograms.size(); i++) {
			executePostProcessPass(compositePrograms.get(i), "composite", i);
		}
		
		// Unbind program
		Program.unbind();
		
		// Unbind framebuffer
		GlFramebuffer.unbind();
		
		LOGGER.debug("Post-processing passes complete");
	}
	
	/**
	 * Executes a single post-processing pass (composite or deferred).
	 * 
	 * @param program The shader program to use
	 * @param type Pass type ("composite" or "deferred") for logging
	 * @param index Pass index for logging
	 */
	private void executePostProcessPass(Program program, String type, int index) {
		if (program == null) return;
		
		// For each pass:
		// 1. Bind appropriate framebuffer (ping-pong between buffers)
		// 2. Bind input textures (previous pass output)
		// 3. Use the program
		// 4. Render full-screen quad
		
		// Bind output framebuffer (alternate between colortex0/1 for ping-pong)
		if (gBufferFramebuffer != null) {
			gBufferFramebuffer.bind();
			
			// For ping-pong, we'd alternate draw buffers
			// For now, just write to colortex0
			int[] drawBuffers = new int[]{GL30.GL_COLOR_ATTACHMENT0};
			GL20.glDrawBuffers(drawBuffers);
		}
		
		// Set viewport
		GL11.glViewport(0, 0, cachedWidth, cachedHeight);
		
		// Bind input textures (G-buffer outputs from geometry pass)
		bindGBufferTextures();
		
		// Use the program
		program.use();
		
		// Set basic uniforms (Step A10 will add full uniform support)
		setBasicUniforms(program.getProgramId());
		
		// Render full-screen quad
		FullScreenQuadRenderer.INSTANCE.render();
		
		LOGGER.trace("Executed {} pass {}", type, index);
		
		LOGGER.debug("Composite passes complete");
	}
	
	/**
	 * Executes the final pass to output to screen (Step A8).
	 * Following IRIS's FinalPassRenderer.renderFinalPass() pattern.
	 * 
	 * IRIS Reference: FinalPassRenderer.java:207-331
	 */
	private void executeFinalPass() {
		LOGGER.debug("Executing final pass (program={})", finalProgram != null ? "present" : "absent");
		
		Minecraft mc = Minecraft.getInstance();
		com.mojang.blaze3d.pipeline.RenderTarget mainTarget = mc.getMainRenderTarget();
		
		if (mainTarget == null) {
			LOGGER.warn("Main render target is null, cannot execute final pass");
			return;
		}
		
		if (finalProgram != null) {
			// Bind default framebuffer (screen)
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			
			// Set viewport to screen size
			GL11.glViewport(0, 0, mainTarget.width, mainTarget.height);
			
			// Bind G-buffer textures as input
			bindGBufferTextures();
			
			// Use final program
			finalProgram.use();
			
			// Set basic uniforms
			setBasicUniforms(finalProgram.getProgramId());
			
			// Render full-screen quad
			FullScreenQuadRenderer.INSTANCE.render();
			
			// Unbind program
			Program.unbind();
			
			LOGGER.debug("Final pass executed with shader");
		} else {
			// No final shader - copy colortex0 directly to screen
			copyColorToScreen();
			LOGGER.debug("Final pass executed via direct copy (no final shader)");
		}
		
		// Unbind all textures
		for (int i = 0; i < 8; i++) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
			GlStateManager._bindTexture(0);
		}
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
	}
	
	/**
	 * Binds G-buffer textures for reading in composite/final passes.
	 * Following IRIS's texture binding pattern.
	 */
	private void bindGBufferTextures() {
		// Bind colortex0 to texture unit 0
		RenderTarget colorTex0 = gBufferManager.get(0);
		if (colorTex0 != null) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0);
			GlStateManager._bindTexture(colorTex0.getMainTexture());
		}
		
		// Bind colortex1 to texture unit 1
		RenderTarget colorTex1 = gBufferManager.get(1);
		if (colorTex1 != null) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE1);
			GlStateManager._bindTexture(colorTex1.getMainTexture());
		}
		
		// Bind colortex2 to texture unit 2
		RenderTarget colorTex2 = gBufferManager.get(2);
		if (colorTex2 != null) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE2);
			GlStateManager._bindTexture(colorTex2.getMainTexture());
		}
		
		// Reset to texture unit 0
		GlStateManager._activeTexture(GL13.GL_TEXTURE0);
	}
	
	/**
	 * Sets basic uniforms for shader programs.
	 * Full uniform support will be implemented in Step A10.
	 * 
	 * @param programId The OpenGL program ID
	 */
	private void setBasicUniforms(int programId) {
		// colortex0 sampler
		int colortex0Loc = GL20.glGetUniformLocation(programId, "colortex0");
		if (colortex0Loc >= 0) {
			GL20.glUniform1i(colortex0Loc, 0);
		}
		
		// Alternative names for colortex0
		int gcolor = GL20.glGetUniformLocation(programId, "gcolor");
		if (gcolor >= 0) {
			GL20.glUniform1i(gcolor, 0);
		}
		
		// colortex1 sampler
		int colortex1Loc = GL20.glGetUniformLocation(programId, "colortex1");
		if (colortex1Loc >= 0) {
			GL20.glUniform1i(colortex1Loc, 1);
		}
		
		// colortex2 sampler
		int colortex2Loc = GL20.glGetUniformLocation(programId, "colortex2");
		if (colortex2Loc >= 0) {
			GL20.glUniform1i(colortex2Loc, 2);
		}
		
		// Screen dimensions (cast to float for glUniform1f)
		int viewWidthLoc = GL20.glGetUniformLocation(programId, "viewWidth");
		if (viewWidthLoc >= 0) {
			GL20.glUniform1f(viewWidthLoc, (float) cachedWidth);
		}
		
		int viewHeightLoc = GL20.glGetUniformLocation(programId, "viewHeight");
		if (viewHeightLoc >= 0) {
			GL20.glUniform1f(viewHeightLoc, (float) cachedHeight);
		}
	}
	
	/**
	 * Copies colortex0 to the main framebuffer.
	 * This is a temporary implementation until composite/final passes are implemented.
	 * Following IRIS's final pass pattern.
	 */
	private void copyColorToScreen() {
		Minecraft mc = Minecraft.getInstance();
		com.mojang.blaze3d.pipeline.RenderTarget mainTarget = mc.getMainRenderTarget();
		
		if (mainTarget == null) {
			LOGGER.warn("Main render target is null, cannot copy to screen");
			return;
		}
		
		// Get the colortex0 render target
		RenderTarget colorTex0 = gBufferManager.get(0);
		if (colorTex0 == null) {
			LOGGER.warn("colortex0 is null, cannot copy to screen");
			return;
		}
		
		// Bind G-buffer framebuffer for reading
		if (gBufferFramebuffer != null) {
			// Bind G-buffer as read framebuffer
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, gBufferFramebuffer.getId());
			GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);  // Read from colortex0
			
			// Bind default framebuffer (0) as draw framebuffer
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
			
			// Blit (copy) from G-buffer to main framebuffer
			// Use GL_LINEAR for better visual quality during scaling
			GL30.glBlitFramebuffer(
				0, 0, cachedWidth, cachedHeight,      // Source rectangle
				0, 0, mainTarget.width, mainTarget.height,  // Destination rectangle
				GL11.GL_COLOR_BUFFER_BIT,                    // Copy color only
				GL11.GL_LINEAR                               // Linear filtering for scaling
			);
			
			// Unbind framebuffers
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
		}
	}
	
	@Override
	public WorldRenderingPhase getPhase() {
		return currentPhase;
	}
	
	@Override
	public void setPhase(WorldRenderingPhase phase) {
		this.currentPhase = phase;
		// Phase-specific shader program switching would happen here
		LOGGER.trace("Phase set to: {}", phase);
	}
	
	@Override
	public boolean shouldDisableFrustumCulling() {
		// Stub for Step 5 - will be determined by shader properties in later steps
		// For now, use default behavior
		return false;
	}
	
	@Override
	public boolean shouldDisableOcclusionCulling() {
		// Stub for Step 5 - will be determined by shader properties in later steps
		// For now, use default behavior
		return false;
	}
	
	@Override
	public boolean shouldRenderUnderwaterOverlay() {
		// Use shader properties following IRIS pattern
		// OptionalBoolean.orElse(default) returns the value or default if DEFAULT
		return shaderProperties.getUnderwaterOverlay().orElse(true);
	}
	
	@Override
	public boolean shouldRenderVignette() {
		// Use shader properties following IRIS pattern
		return shaderProperties.getVignette().orElse(true);
	}
	
	@Override
	public boolean shouldRenderSun() {
		// Use shader properties following IRIS pattern
		return shaderProperties.getSun().orElse(true);
	}
	
	@Override
	public boolean shouldRenderMoon() {
		// Use shader properties following IRIS pattern
		return shaderProperties.getMoon().orElse(true);
	}
	
	@Override
	public boolean shouldRenderWeather() {
		// Use shader properties following IRIS pattern
		return shaderProperties.getWeather().orElse(true);
	}
	
	@Override
	public void destroy() {
		LOGGER.info("Destroying shader pipeline for pack: {}", packName);
		
		// Destroy all compiled programs (Step A5 cleanup)
		for (Program program : programs.values()) {
			if (program != null) {
				program.destroy();
			}
		}
		programs.clear();
		deferredPrograms.clear();
		compositePrograms.clear();
		gbuffersTerrainProgram = null;
		gbuffersTexturedProgram = null;
		gbuffersBasicProgram = null;
		finalProgram = null;
		programsCompiled = false;
		
		if (gBufferFramebuffer != null) {
			gBufferFramebuffer.destroy();
			gBufferFramebuffer = null;
		}
		
		if (gBufferManager != null) {
			gBufferManager.destroy();
			gBufferManager = null;
		}
	}
	
	/**
	 * Gets the pack name.
	 * @return The shader pack name
	 */
	public String getPackName() {
		return packName;
	}
	
	/**
	 * Gets the dimension ID.
	 * @return The dimension ID
	 */
	public String getDimension() {
		return dimension;
	}
	
	/**
	 * Gets the shader source provider.
	 * @return The source provider
	 */
	public ShaderSourceProvider getSourceProvider() {
		return sourceProvider;
	}
	
	/**
	 * Gets the shader pack options.
	 * @return The shader pack options
	 */
	public ShaderPackOptions getShaderPackOptions() {
		return shaderPackOptions;
	}
	
	/**
	 * Checks if the pipeline is currently rendering the world.
	 * @return true if rendering
	 */
	public boolean isRenderingWorld() {
		return isRenderingWorld;
	}
}
