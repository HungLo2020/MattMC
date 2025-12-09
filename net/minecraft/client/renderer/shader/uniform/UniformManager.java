package net.minecraft.client.renderer.shader.uniform;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.shader.program.CompiledShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages uniform variables for shader programs.
 * Provides common uniforms like matrices, time, camera position, etc.
 */
@Environment(EnvType.CLIENT)
public class UniformManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private CompiledShaderProgram currentProgram;
    
    /**
     * Sets the current shader program for uniform updates.
     */
    public void setProgram(CompiledShaderProgram program) {
        this.currentProgram = program;
        this.uniformLocations.clear();
    }
    
    /**
     * Gets the location of a uniform variable in the current program.
     * Caches locations for performance.
     */
    private int getUniformLocation(String name) {
        if (currentProgram == null) {
            return -1;
        }
        
        return uniformLocations.computeIfAbsent(name, n -> {
            int location = currentProgram.getUniformLocation(n);
            if (location == -1) {
                LOGGER.debug("Uniform '{}' not found in shader program", n);
            }
            return location;
        });
    }
    
    /**
     * Sets a mat4 uniform.
     */
    public void setMatrix4f(String name, Matrix4f matrix) {
        int location = getUniformLocation(name);
        if (location != -1) {
            float[] values = new float[16];
            matrix.get(values);
            GL20.glUniformMatrix4fv(location, false, values);
        }
    }
    
    /**
     * Sets a float uniform.
     */
    public void setFloat(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }
    
    /**
     * Sets an int uniform.
     */
    public void setInt(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }
    
    /**
     * Sets a vec3 uniform.
     */
    public void setVec3(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }
    
    /**
     * Sets a vec4 uniform.
     */
    public void setVec4(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }
    
    /**
     * Sets a boolean uniform (as int).
     */
    public void setBoolean(String name, boolean value) {
        setInt(name, value ? 1 : 0);
    }
}
