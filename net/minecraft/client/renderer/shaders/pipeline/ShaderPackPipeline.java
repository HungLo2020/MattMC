package net.minecraft.client.renderer.shaders.pipeline;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.gl.FullScreenQuadRenderer;
import net.minecraft.client.renderer.shaders.helpers.OptionalBoolean;
import net.minecraft.client.renderer.shaders.helpers.StringPair;
import net.minecraft.client.renderer.shaders.helpers.Tri;
import net.minecraft.client.renderer.shaders.option.ShaderPackOptions;
import net.minecraft.client.renderer.shaders.pack.*;
import net.minecraft.client.renderer.shaders.pipeline.transform.PatchShaderType;
import net.minecraft.client.renderer.shaders.pipeline.transform.TransformPatcher;
import net.minecraft.client.renderer.shaders.preprocessor.JcppProcessor;
import net.minecraft.client.renderer.shaders.preprocessor.StandardMacros;
import net.minecraft.client.renderer.shaders.program.Program;
import net.minecraft.client.renderer.shaders.program.ProgramBuilder;
import net.minecraft.client.renderer.shaders.program.ProgramSource;
import net.minecraft.client.renderer.shaders.program.ShaderCompileException;
import net.minecraft.client.renderer.shaders.shadows.PackShadowDirectives;
import net.minecraft.client.renderer.shaders.shadows.ShadowRenderer;
import net.minecraft.client.renderer.shaders.shadows.ShadowRenderTargets;
import net.minecraft.client.renderer.shaders.targets.GBufferManager;
import net.minecraft.client.renderer.shaders.targets.RenderTarget;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import net.minecraft.client.renderer.shaders.texture.TextureStage;
import net.minecraft.client.renderer.shaders.texture.TextureType;
import net.minecraft.client.renderer.shaders.uniform.providers.CapturedRenderingState;
import net.minecraft.client.renderer.shaders.uniform.providers.SystemTimeUniforms;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
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
	private boolean isBeforeTranslucent = true;
	private int cachedWidth = -1;
	private int cachedHeight = -1;
	
	// Shader programs (Step A5)
	private final Map<String, Program> programs = new HashMap<>();
	private final Map<String, net.minecraft.client.renderer.shaders.programs.ExtendedShader> extendedShaders = new HashMap<>();
	private Program gbuffersTerrainProgram;
	private Program gbuffersTexturedProgram;
	private Program gbuffersBasicProgram;
	private Program shadowProgram;  // Shadow pass terrain program
	private final List<Program> deferredPrograms = new ArrayList<>();  // Run before translucent
	private final List<Program> compositePrograms = new ArrayList<>(); // Run after translucent
	private Program finalProgram;
	private boolean programsCompiled = false;
	
	// Shadow rendering (Step A9)
	private ShadowRenderer shadowRenderer;
	private ShadowRenderTargets shadowRenderTargets;
	private PackShadowDirectives shadowDirectives;
	private boolean shadowsEnabled = false;
	
	// Cached uniform values (Step A10)
	private Matrix4f cachedModelViewMatrix = new Matrix4f();
	private Matrix4f cachedProjectionMatrix = new Matrix4f();
	private Matrix4f cachedShadowModelViewMatrix = new Matrix4f();
	private Matrix4f cachedShadowProjectionMatrix = new Matrix4f();
	
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
		// Find starting paths in the shaders/ directory AND dimension directories
		// Following IRIS pattern - search world0, world-1, world1 directories
		List<String> candidates = ShaderPackSourceNames.getPotentialStarts();
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		
		// Search base shaders directory
		startingPaths.addAll(ShaderPackSourceNames.findPresentSources(
			packSource, 
			"/shaders/", 
			candidates
		));
		
		// Search dimension-specific directories (IRIS pattern from ShaderPack.java)
		// Try all possible dimension directories - findPresentSources will only return files that exist
		String[] dimensionDirs = {"world0", "world-1", "world1"};
		for (String dimDir : dimensionDirs) {
			List<AbsolutePackPath> dimPaths = ShaderPackSourceNames.findPresentSources(
				packSource, 
				"/shaders/" + dimDir + "/", 
				candidates
			);
			startingPaths.addAll(dimPaths);
			if (!dimPaths.isEmpty()) {
				LOGGER.info("Found {} shader files in dimension directory: {}", dimPaths.size(), dimDir);
			}
		}
		
		LOGGER.info("Found {} starting shader files for pack: {}", startingPaths.size(), packName);
		
		this.sourceProvider = new ShaderSourceProvider(packSource, startingPaths);
		
		// Create shader pack options (Step 8)
		// For now, this creates an empty option set
		// Full option discovery from GLSL will be implemented in future enhancements
		this.shaderPackOptions = new ShaderPackOptions();
		LOGGER.debug("Initialized shader pack options for pack: {}", packName);
		
		// Initialize G-buffer manager with default settings
		// Will be resized in beginLevelRendering() based on actual screen size
		this.gBufferManager = new GBufferManager(1, 1, createDefaultTargetSettings());  // Will resize later
		
		// Initialize shadow rendering (Step A9)
		initializeShadowRendering();
		
		// Compile shader programs (Step A5)
		compilePrograms();
		
		LOGGER.info("Created shader pipeline for pack: {} in dimension: {}", packName, dimension);
	}
	
	/**
	 * Initializes shadow rendering resources (Step A9).
	 * Following IRIS's shadow initialization pattern.
	 * 
	 * IRIS Reference: IrisRenderingPipeline.java constructor
	 */
	private void initializeShadowRendering() {
		// Load shadow directives from shader properties
		this.shadowDirectives = new PackShadowDirectives();
		
		// Check if shadows are enabled (distance > 0)
		if (shadowDirectives.getDistance() > 0.0f) {
			try {
				// Create shadow render targets
				int resolution = shadowDirectives.getResolution();
				// Pass false for higherShadowcolor to use OptiFine limit (2 shadow color buffers)
				this.shadowRenderTargets = new ShadowRenderTargets(resolution, shadowDirectives, false);
				
				// Create shadow renderer
				this.shadowRenderer = new ShadowRenderer(shadowDirectives, shadowRenderTargets);
				this.shadowsEnabled = true;
				
				LOGGER.info("Initialized shadow rendering at {}x{} resolution", resolution, resolution);
			} catch (Exception e) {
				LOGGER.error("Failed to initialize shadow rendering", e);
				this.shadowsEnabled = false;
			}
		} else {
			this.shadowsEnabled = false;
			LOGGER.debug("Shadows disabled (distance = 0)");
		}
	}
	
	/**
	 * Compiles all shader programs from the shader pack (Step A5).
	 * Following IRIS's program creation pattern.
	 * 
	 * IRIS Reference: IrisRenderingPipeline.java createShader() method
	 */
	private void compilePrograms() {
		LOGGER.info("Compiling shader programs for pack: {}", packName);
		
		// Compile ALL gbuffers programs that exist in the shader pack
		// Following IRIS pattern: compile all available programs, fallback handled at runtime
		String[] gbuffersPrograms = {
			"gbuffers_basic",
			"gbuffers_line",
			"gbuffers_textured",
			"gbuffers_textured_lit",
			"gbuffers_skybasic",
			"gbuffers_skytextured",
			"gbuffers_clouds",
			"gbuffers_terrain",
			"gbuffers_terrain_solid",
			"gbuffers_terrain_cutout",
			"gbuffers_damagedblock",
			"gbuffers_block",
			"gbuffers_block_translucent",
			"gbuffers_beaconbeam",
			"gbuffers_item",
			"gbuffers_entities",
			"gbuffers_entities_translucent",
			"gbuffers_lightning",
			"gbuffers_particles",
			"gbuffers_particles_translucent",
			"gbuffers_entities_glowing",
			"gbuffers_armor_glint",
			"gbuffers_spidereyes",
			"gbuffers_hand",
			"gbuffers_hand_water",
			"gbuffers_weather",
			"gbuffers_water"
		};
		
		int gbuffersCompiled = 0;
		for (String programName : gbuffersPrograms) {
			Program program = tryCompileProgram(programName);
			if (program != null) {
				gbuffersCompiled++;
				// Store reference to key programs
				if ("gbuffers_terrain".equals(programName)) {
					gbuffersTerrainProgram = program;
				} else if ("gbuffers_textured".equals(programName)) {
					gbuffersTexturedProgram = program;
				} else if ("gbuffers_basic".equals(programName)) {
					gbuffersBasicProgram = program;
				}
			}
		}
		LOGGER.info("Compiled {} gbuffers programs", gbuffersCompiled);
		
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
		
		// Compile shadow program (Step A9)
		// IRIS: shadow pass uses shadow.vsh/fsh or falls back to gbuffers
		if (shadowsEnabled) {
			shadowProgram = tryCompileProgram("shadow");
			if (shadowProgram == null) {
				shadowProgram = tryCompileProgram("shadow_solid");
			}
			if (shadowProgram != null) {
				LOGGER.info("Compiled shadow program");
			} else {
				LOGGER.debug("No shadow program found, shadows will use depth-only rendering");
			}
		}
		
		programsCompiled = true;
		
		int compiledCount = programs.size();
		LOGGER.info("Compiled {} shader programs for pack: {} ({} gbuffers, {} deferred, {} composite)", 
			compiledCount, packName, gbuffersCompiled, deferredPrograms.size(), compositePrograms.size());
	}
	
	/**
	 * Attempts to compile a shader program from the pack.
	 * Returns null if the program doesn't exist or fails to compile.
	 * 
	 * Uses JcppProcessor to preprocess #define/#ifdef directives, then
	 * TransformPatcher to transform deprecated GLSL constructs to GLSL 330 Core
	 * profile compatible code - following IRIS pattern exactly.
	 * 
	 * @param programName The program name (e.g., "gbuffers_terrain")
	 * @return The compiled program, or null
	 */
	private Program tryCompileProgram(String programName) {
		try {
			// Get vertex and fragment sources with dimension awareness
			String vertexSource = sourceProvider.getShaderSource(programName + ".vsh", dimension);
			String fragmentSource = sourceProvider.getShaderSource(programName + ".fsh", dimension);
			
			// Check if both sources exist
			if (vertexSource == null || fragmentSource == null) {
				LOGGER.debug("Program {} not found (vsh={}, fsh={})", 
					programName, vertexSource != null, fragmentSource != null);
				return null;
			}
			
			// Get optional geometry source
			String geometrySource = sourceProvider.getShaderSource(programName + ".gsh", dimension);
			
			LOGGER.debug("Compiling program: {} (vsh={} chars, fsh={} chars, gsh={} chars)", 
				programName, vertexSource.length(), fragmentSource.length(), 
				geometrySource != null ? geometrySource.length() : 0);
			
			// Get standard environment defines for preprocessing
			ImmutableList<StringPair> environmentDefines = StandardMacros.createStandardEnvironmentDefines();
			
			// Step 1: Preprocess shader sources with JCPP to handle #define, #ifdef, #include, etc.
			// This MUST be done before passing to TransformPatcher
			String preprocessedVertex = JcppProcessor.glslPreprocessSource(vertexSource, environmentDefines);
			String preprocessedFragment = JcppProcessor.glslPreprocessSource(fragmentSource, environmentDefines);
			String preprocessedGeometry = geometrySource != null 
				? JcppProcessor.glslPreprocessSource(geometrySource, environmentDefines) 
				: null;
			
			LOGGER.debug("Preprocessed program: {} (vsh={} chars, fsh={} chars)", 
				programName, preprocessedVertex.length(), preprocessedFragment.length());
			
			// Determine the texture stage based on program name
			TextureStage textureStage = determineTextureStage(programName);
			
			// Create empty texture map (will be populated with actual texture bindings in future)
			Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap = new Object2ObjectOpenHashMap<>();
			
			// Step 2: Transform shader sources using TransformPatcher
			// This handles deprecated GLSL constructs (gl_TextureMatrix, gl_Vertex, gl_Color, etc.)
			// and converts them to GLSL 330 Core profile compatible code
			Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
				programName,
				preprocessedVertex,
				preprocessedGeometry,
				preprocessedFragment,
				textureStage,
				textureMap
			);
			
			if (transformed == null) {
				LOGGER.warn("TransformPatcher returned null for program: {}", programName);
				return null;
			}
			
			// Get transformed sources
			String transformedVertex = transformed.get(PatchShaderType.VERTEX);
			String transformedFragment = transformed.get(PatchShaderType.FRAGMENT);
			String transformedGeometry = transformed.get(PatchShaderType.GEOMETRY);
			
			if (transformedVertex == null || transformedFragment == null) {
				LOGGER.warn("Transformation produced null shaders for program: {}", programName);
				return null;
			}
			
			LOGGER.debug("Transformed program: {} (vsh={} chars, fsh={} chars)", 
				programName, transformedVertex.length(), transformedFragment.length());
			
			// Build the program using ProgramBuilder with transformed sources
			Program program = ProgramBuilder.begin(programName, transformedVertex, transformedGeometry, transformedFragment)
				.build();
			
			// Store in programs map
			programs.put(programName, program);
			
			// For gbuffers programs, also create ExtendedShader for proper uniform binding
			if (programName.startsWith("gbuffers_")) {
				try {
					net.minecraft.client.renderer.shaders.programs.ExtendedShader extendedShader = 
						net.minecraft.client.renderer.shaders.programs.ShaderCreator.createBasic(
							programName,
							transformedVertex,
							transformedFragment,
							this
						);
					extendedShaders.put(programName, extendedShader);
					LOGGER.info("Created ExtendedShader for: {}", programName);
				} catch (Exception e) {
					LOGGER.warn("Failed to create ExtendedShader for {} during shader pack compilation", programName, e);
				}
			}
			
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
	 * Determines the texture stage based on program name.
	 * Following IRIS's program categorization pattern.
	 */
	private TextureStage determineTextureStage(String programName) {
		if (programName.startsWith("composite") || programName.equals("final")) {
			return TextureStage.COMPOSITE_AND_FINAL;
		} else if (programName.startsWith("deferred")) {
			return TextureStage.DEFERRED;
		} else if (programName.startsWith("prepare")) {
			return TextureStage.PREPARE;
		} else if (programName.startsWith("shadowcomp")) {
			return TextureStage.SHADOWCOMP;
		} else if (programName.startsWith("begin")) {
			return TextureStage.BEGIN;
		} else if (programName.startsWith("setup")) {
			return TextureStage.SETUP;
		} else {
			// gbuffers_*, shadow, etc.
			return TextureStage.GBUFFERS_AND_SHADOW;
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
		
		// Capture matrices for uniforms (Step A10)
		captureRenderingState();
		
		// Render shadow pass first (Step A9)
		// IRIS pattern: shadows rendered before main geometry
		if (shadowsEnabled && shadowRenderer != null) {
			renderShadowPass();
		}
		
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
	 * Captures the current rendering state for uniforms (Step A10).
	 * Following IRIS's CapturedRenderingState pattern.
	 */
	private void captureRenderingState() {
		// Get matrices from CapturedRenderingState
		Matrix4fc modelView = CapturedRenderingState.INSTANCE.getGbufferModelView();
		Matrix4fc projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
		
		if (modelView != null) {
			cachedModelViewMatrix.set(modelView);
		}
		if (projection != null) {
			cachedProjectionMatrix.set(projection);
		}
		
		// Get shadow matrices if available
		if (shadowRenderer != null) {
			cachedShadowModelViewMatrix.set(shadowRenderer.getShadowModelViewMatrix());
			cachedShadowProjectionMatrix.set(shadowRenderer.getShadowProjectionMatrix());
		}
	}
	
	/**
	 * Renders the shadow pass before main geometry (Step A9).
	 * Following IRIS's ShadowRenderer.renderShadows() pattern.
	 * 
	 * IRIS Reference: ShadowRenderer.java:renderShadows()
	 */
	private void renderShadowPass() {
		LOGGER.debug("Rendering shadow pass");
		
		// Update shadow direction based on sun/moon position
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			float celestialAngle = mc.level.getTimeOfDay(CapturedRenderingState.INSTANCE.getTickDelta());
			shadowRenderer.updateShadowDirection(celestialAngle);
		}
		
		// Begin shadow rendering
		shadowRenderer.beginShadowRender(0.0f);
		
		// The actual shadow geometry rendering happens through the normal
		// rendering pipeline - the shadow framebuffer is bound and geometry
		// is rendered with shadow shaders. For now, we just set up and tear down.
		
		// End shadow rendering
		shadowRenderer.endShadowRender();
		
		LOGGER.debug("Shadow pass complete");
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
	 * Sets all uniforms for shader programs (Step A10).
	 * Full uniform support following IRIS pattern.
	 * 
	 * IRIS Reference: Uniforms package, all provider classes
	 * 
	 * @param programId The OpenGL program ID
	 */
	private void setBasicUniforms(int programId) {
		// === Sampler uniforms ===
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
		
		// gnormal, gaux1, gaux2, gaux3, gaux4 (alternative sampler names)
		setUniform1i(programId, "gnormal", 1);
		setUniform1i(programId, "gaux1", 2);
		setUniform1i(programId, "colortex3", 3);
		setUniform1i(programId, "colortex4", 4);
		setUniform1i(programId, "colortex5", 5);
		setUniform1i(programId, "colortex6", 6);
		setUniform1i(programId, "colortex7", 7);
		
		// Depth textures
		setUniform1i(programId, "depthtex0", 8);
		setUniform1i(programId, "depthtex1", 9);
		setUniform1i(programId, "depthtex2", 10);
		setUniform1i(programId, "gdepth", 8);
		
		// Shadow textures (if shadows are enabled)
		if (shadowsEnabled) {
			setUniform1i(programId, "shadowtex0", 11);
			setUniform1i(programId, "shadowtex1", 12);
			setUniform1i(programId, "shadowcolor0", 13);
			setUniform1i(programId, "shadowcolor1", 14);
			setUniform1i(programId, "shadow", 11);
		}
		
		// Noise texture
		setUniform1i(programId, "noisetex", 15);
		
		// === View/Screen uniforms (ViewportUniforms) ===
		setUniform1f(programId, "viewWidth", (float) cachedWidth);
		setUniform1f(programId, "viewHeight", (float) cachedHeight);
		setUniform1f(programId, "aspectRatio", (float) cachedWidth / (float) cachedHeight);
		
		// === Matrix uniforms (MatrixUniforms) ===
		setUniformMatrix4f(programId, "gbufferModelView", cachedModelViewMatrix);
		setUniformMatrix4f(programId, "gbufferProjection", cachedProjectionMatrix);
		
		// Inverse matrices
		Matrix4f modelViewInverse = new Matrix4f(cachedModelViewMatrix).invert();
		Matrix4f projectionInverse = new Matrix4f(cachedProjectionMatrix).invert();
		setUniformMatrix4f(programId, "gbufferModelViewInverse", modelViewInverse);
		setUniformMatrix4f(programId, "gbufferProjectionInverse", projectionInverse);
		
		// Shadow matrices (if shadows are enabled)
		if (shadowsEnabled && shadowRenderer != null) {
			setUniformMatrix4f(programId, "shadowModelView", cachedShadowModelViewMatrix);
			setUniformMatrix4f(programId, "shadowProjection", cachedShadowProjectionMatrix);
			
			Matrix4f shadowModelViewInverse = new Matrix4f(cachedShadowModelViewMatrix).invert();
			Matrix4f shadowProjectionInverse = new Matrix4f(cachedShadowProjectionMatrix).invert();
			setUniformMatrix4f(programId, "shadowModelViewInverse", shadowModelViewInverse);
			setUniformMatrix4f(programId, "shadowProjectionInverse", shadowProjectionInverse);
		}
		
		// === Time uniforms (SystemTimeUniforms, WorldTimeUniforms) ===
		float frameTimeCounter = SystemTimeUniforms.TIMER.getFrameTimeCounter();
		setUniform1f(programId, "frameTimeCounter", frameTimeCounter);
		
		// Frame counter
		setUniform1i(programId, "frameCounter", SystemTimeUniforms.COUNTER.getAsInt());
		
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			// World time uniforms
			long worldTime = mc.level.getDayTime();
			setUniform1i(programId, "worldTime", (int) (worldTime % 24000));
			setUniform1i(programId, "worldDay", (int) (worldTime / 24000));
			
			// Sun/moon angle
			float sunAngle = mc.level.getTimeOfDay(CapturedRenderingState.INSTANCE.getTickDelta());
			setUniform1f(programId, "sunAngle", sunAngle);
			
			// Moon phase
			setUniform1i(programId, "moonPhase", mc.level.getMoonPhase());
			
			// Rain/weather
			float rainStrength = mc.level.getRainLevel(CapturedRenderingState.INSTANCE.getTickDelta());
			setUniform1f(programId, "rainStrength", rainStrength);
			setUniform1f(programId, "wetness", rainStrength);  // Alias
		}
		
		// === Camera uniforms (CameraUniforms) ===
		Camera camera = mc.gameRenderer.getMainCamera();
		if (camera != null) {
			org.joml.Vector3d cameraPos = new org.joml.Vector3d(
				camera.getPosition().x,
				camera.getPosition().y,
				camera.getPosition().z
			);
			setUniform3f(programId, "cameraPosition", (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
			
			// Previous camera position (for motion blur, etc.)
			setUniform3f(programId, "previousCameraPosition", (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
		}
		
		// === Near/far planes ===
		setUniform1f(programId, "near", 0.05f);  // Minecraft default near plane
		setUniform1f(programId, "far", (float) (mc.options.getEffectiveRenderDistance() * 16));
		
		// === FOG uniforms (FogUniforms) ===
		Vector3d fogColor = CapturedRenderingState.INSTANCE.getFogColor();
		setUniform3f(programId, "fogColor", (float) fogColor.x, (float) fogColor.y, (float) fogColor.z);
		setUniform1f(programId, "fogDensity", CapturedRenderingState.INSTANCE.getFogDensity());
		
		// === Constants ===
		setUniform1f(programId, "pi", (float) Math.PI);
		setUniform1f(programId, "TAU", (float) (Math.PI * 2.0));
		setUniform1f(programId, "E", (float) Math.E);
		setUniform1f(programId, "GOLDEN_RATIO", 1.6180339887f);
		
		// === Screen brightness (gamma) ===
		setUniform1f(programId, "screenBrightness", mc.options.gamma().get().floatValue());
	}
	
	// Helper methods for setting uniforms
	private void setUniform1i(int programId, String name, int value) {
		int loc = GL20.glGetUniformLocation(programId, name);
		if (loc >= 0) {
			GL20.glUniform1i(loc, value);
		}
	}
	
	private void setUniform1f(int programId, String name, float value) {
		int loc = GL20.glGetUniformLocation(programId, name);
		if (loc >= 0) {
			GL20.glUniform1f(loc, value);
		}
	}
	
	private void setUniform3f(int programId, String name, float x, float y, float z) {
		int loc = GL20.glGetUniformLocation(programId, name);
		if (loc >= 0) {
			GL20.glUniform3f(loc, x, y, z);
		}
	}
	
	private void setUniformMatrix4f(int programId, String name, Matrix4f matrix) {
		int loc = GL20.glGetUniformLocation(programId, name);
		if (loc >= 0) {
			float[] values = new float[16];
			matrix.get(values);
			GL20.glUniformMatrix4fv(loc, false, values);
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
		
		// Destroy shadow resources (Step A9 cleanup)
		if (shadowProgram != null) {
			shadowProgram.destroy();
			shadowProgram = null;
		}
		if (shadowRenderer != null) {
			shadowRenderer.destroy();
			shadowRenderer = null;
		}
		if (shadowRenderTargets != null) {
			shadowRenderTargets.destroy();
			shadowRenderTargets = null;
		}
		shadowsEnabled = false;
		
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

	/**
	 * Checks if we are before the translucent rendering phase.
	 * @return true if before translucent rendering
	 */
	public boolean isBeforeTranslucent() {
		return isBeforeTranslucent;
	}

	/**
	 * Sets whether we are before the translucent rendering phase.
	 * @param beforeTranslucent true if before translucent rendering
	 */
	public void setBeforeTranslucent(boolean beforeTranslucent) {
		this.isBeforeTranslucent = beforeTranslucent;
	}
	
	/**
	 * Gets the shadow renderer.
	 * @return The shadow renderer, or null if shadows are disabled
	 */
	public ShadowRenderer getShadowRenderer() {
		return shadowRenderer;
	}
	
	/**
	 * Checks if shadows are enabled.
	 * @return true if shadows are enabled
	 */
	public boolean areShadowsEnabled() {
		return shadowsEnabled;
	}
	
	/**
	 * Gets the G-buffer manager.
	 * @return The G-buffer manager
	 */
	public GBufferManager getGBufferManager() {
		return gBufferManager;
	}
	
	/**
	 * Gets the shadow render targets.
	 * @return The shadow render targets, or null if shadows are disabled
	 */
	public ShadowRenderTargets getShadowRenderTargets() {
		return shadowRenderTargets;
	}
	
	/**
	 * Gets a compiled program by name.
	 * Used by the shader interception system to replace vanilla shaders with shader pack shaders.
	 * 
	 * @param programName The name of the program (e.g., "gbuffers_terrain")
	 * @return The compiled program, or null if not found
	 */
	public Program getProgram(String programName) {
		return programs.get(programName);
	}

	/**
	 * Gets an ExtendedShader by program name.
	 * ExtendedShaders have proper Iris-compatible uniform bindings.
	 * 
	 * @param programName The name of the program (e.g., "gbuffers_terrain")
	 * @return The ExtendedShader, or null if not found
	 */
	public net.minecraft.client.renderer.shaders.programs.ExtendedShader getExtendedShader(String programName) {
		return extendedShaders.get(programName);
	}

	/**
	 * Gets all ExtendedShaders.
	 * @return A map of program names to ExtendedShaders
	 */
	public Map<String, net.minecraft.client.renderer.shaders.programs.ExtendedShader> getExtendedShaders() {
		return extendedShaders;
	}
	
	/**
	 * Gets all compiled programs.
	 * @return A map of program names to compiled programs
	 */
	public Map<String, Program> getPrograms() {
		return programs;
	}
}
