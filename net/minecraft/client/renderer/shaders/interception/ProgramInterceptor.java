package net.minecraft.client.renderer.shaders.interception;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.shaders.gl.program.Program;
import net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline;
import net.minecraft.client.renderer.shaders.programs.ShaderKey;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Intercepts vanilla shader requests and redirects to shader pack programs.
 * Based on IRIS MixinShaderManager_Overrides.java.
 */
public class ProgramInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger("ProgramInterceptor");
    private final Set<RenderPipeline> missingShaders = new HashSet<>();
    
    /**
     * Attempts to override a vanilla pipeline with a shader pack program.
     * Returns null if no override is available.
     */
    @Nullable
    public Program override(ShaderRenderingPipeline pipeline, RenderPipeline renderPipeline) {
        if (pipeline == null || !pipeline.shouldOverrideShaders()) {
            return null;
        }

        ShaderKey shaderKey = ShaderPipelineMapper.getPipeline(pipeline, renderPipeline);

        if (shaderKey != null) {
            Program program = pipeline.getShaderMap().getShader(shaderKey);
            if (program != null) {
                return program;
            }
        }

        // Log missing shaders once per pipeline
        if (missingShaders.add(renderPipeline)) {
            if (renderPipeline.getLocation().getNamespace().equals("minecraft")) {
                LOGGER.error("Missing program " + renderPipeline.getLocation() + " in override list. This is likely a MattMC shader bug!");
            } else {
                LOGGER.warn("Missing program " + renderPipeline.getLocation() + " in override list. Not critical, but could lead to weird rendering.");
            }
        }

        return null;
    }

    /**
     * Clears the missing shaders cache.
     * Call this when reloading shaders.
     */
    public void clearMissingShaders() {
        missingShaders.clear();
    }

    /**
     * Gets the number of missing shaders reported.
     */
    public int getMissingShaderCount() {
        return missingShaders.size();
    }
}
