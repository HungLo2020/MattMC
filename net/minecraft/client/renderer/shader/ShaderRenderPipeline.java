package net.minecraft.client.renderer.shader;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shader.gbuffer.GBufferManager;
import net.minecraft.client.renderer.shader.pack.ShaderPack;
import net.minecraft.client.renderer.shader.program.CompiledShaderProgram;
import net.minecraft.client.renderer.shader.program.ShaderCompiler;
import net.minecraft.client.renderer.shader.program.ShaderProgramType;
import net.minecraft.client.renderer.shader.shadow.ShadowMapManager;
import net.minecraft.client.renderer.shader.uniform.CameraUniforms;
import net.minecraft.client.renderer.shader.uniform.UniformManager;
import net.minecraft.client.renderer.shader.uniform.WorldStateUniforms;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Orchestrates the shader rendering pipeline.
 * Manages G-buffers, shadow maps, and multi-pass rendering.
 */
@Environment(EnvType.CLIENT)
public class ShaderRenderPipeline implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final ShaderPack shaderPack;
    private final GBufferManager gBufferManager;
    private final ShadowMapManager shadowMapManager;
    private final UniformManager uniformManager;
    private final WorldStateUniforms worldStateUniforms;
    private final CameraUniforms cameraUniforms;
    private ShaderPassExecutor passExecutor;
    private boolean initialized = false;
    
    public ShaderRenderPipeline(ShaderPack shaderPack) {
        this.shaderPack = shaderPack;
        this.gBufferManager = new GBufferManager();
        this.shadowMapManager = new ShadowMapManager();
        this.uniformManager = new UniformManager();
        this.worldStateUniforms = new WorldStateUniforms(Minecraft.getInstance());
        this.cameraUniforms = new CameraUniforms(Minecraft.getInstance());
    }
    
    /**
     * Initializes the rendering pipeline.
     */
    public void initialize(int width, int height) {
        try {
            LOGGER.info("Initializing shader render pipeline for: {}", 
                ShaderDebugHelper.getShaderPackSummary(shaderPack));
            
            // Log diagnostic information
            ShaderDebugHelper.logShaderPackInfo(shaderPack);
            
            // Validate shader pack
            if (!ShaderDebugHelper.validateShaderPack(shaderPack)) {
                LOGGER.warn("Shader pack validation warnings detected");
            }
            
            // Initialize G-buffers
            gBufferManager.initialize(width, height);
            
            // Initialize shadow maps
            int shadowMapSize = shaderPack.getProperties().getInt("shadowMapSize", 2048);
            shadowMapManager.initialize(shadowMapSize);
            
            // Compile shader programs that exist in the pack
            compileShaderPrograms();
            
            // Initialize pass executor
            this.passExecutor = new ShaderPassExecutor(
                shaderPack,
                gBufferManager,
                shadowMapManager,
                uniformManager,
                worldStateUniforms,
                cameraUniforms
            );
            this.passExecutor.initialize();
            
            initialized = true;
            LOGGER.info("Shader render pipeline initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize shader render pipeline", e);
            close();
        }
    }
    
    /**
     * Compiles shader programs from the shader pack.
     */
    private void compileShaderPrograms() {
        // Compile terrain shader if present
        compileProgram(ShaderProgramType.GBUFFERS_TERRAIN);
        
        // Compile other common programs
        compileProgram(ShaderProgramType.GBUFFERS_WATER);
        compileProgram(ShaderProgramType.GBUFFERS_ENTITIES);
        compileProgram(ShaderProgramType.GBUFFERS_SKYBASIC);
        compileProgram(ShaderProgramType.COMPOSITE);
        compileProgram(ShaderProgramType.FINAL);
        
        LOGGER.info("Compiled shader programs for pack: {}", shaderPack.getName());
    }
    
    /**
     * Compiles a single shader program if it exists in the pack.
     */
    private void compileProgram(ShaderProgramType type) {
        String programName = type.getName();
        String vertexSource = shaderPack.getVertexSource(programName);
        String fragmentSource = shaderPack.getFragmentSource(programName);
        
        if (vertexSource != null || fragmentSource != null) {
            try {
                CompiledShaderProgram program = ShaderCompiler.compile(type, vertexSource, fragmentSource);
                shaderPack.setCompiledProgram(type, program);
                LOGGER.debug("Compiled shader program: {}", programName);
            } catch (Exception e) {
                LOGGER.error("Failed to compile shader program: {}", programName, e);
            }
        }
    }
    
    /**
     * Executes the complete rendering pipeline.
     * 
     * @param camera The game camera
     * @param viewMatrix The view transformation matrix
     * @param projectionMatrix The projection matrix
     */
    public void render(Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!initialized || passExecutor == null) {
            return;
        }
        
        LOGGER.debug("Executing shader render pipeline for pack: {}", shaderPack.getName());
        
        try {
            // Execute the complete render pass sequence
            passExecutor.executeRenderPipeline(camera, viewMatrix, projectionMatrix);
        } catch (Exception e) {
            LOGGER.error("Error executing shader render pipeline", e);
        }
    }
    
    /**
     * Gets the pass executor for direct access if needed.
     */
    public ShaderPassExecutor getPassExecutor() {
        return passExecutor;
    }
    
    /**
     * Resizes the pipeline buffers.
     */
    public void resize(int width, int height) {
        if (initialized) {
            gBufferManager.resize(width, height);
        }
    }
    
    /**
     * Gets the G-buffer manager.
     */
    public GBufferManager getGBufferManager() {
        return gBufferManager;
    }
    
    /**
     * Gets the uniform manager.
     */
    public UniformManager getUniformManager() {
        return uniformManager;
    }
    
    /**
     * Gets the shadow map manager.
     */
    public ShadowMapManager getShadowMapManager() {
        return shadowMapManager;
    }
    
    /**
     * Gets the world state uniforms provider.
     */
    public WorldStateUniforms getWorldStateUniforms() {
        return worldStateUniforms;
    }
    
    /**
     * Gets the camera uniforms provider.
     */
    public CameraUniforms getCameraUniforms() {
        return cameraUniforms;
    }
    
    /**
     * Checks if the pipeline is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    @Override
    public void close() {
        if (passExecutor != null) {
            passExecutor.close();
        }
        gBufferManager.close();
        shadowMapManager.close();
        initialized = false;
        LOGGER.info("Shader render pipeline closed for pack: {}", shaderPack.getName());
    }
}
