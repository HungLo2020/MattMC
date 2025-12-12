// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.programs;

import com.mojang.blaze3d.opengl.GlProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A specialized map mapping {@link ShaderKey} to {@link GlProgram}.
 * Avoids much of the complexity / overhead of an EnumMap while ultimately
 * fulfilling the same function.
 * 
 * Based on IRIS's ShaderMap class
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/programs/ShaderMap.java
 */
public class ShaderMap {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderMap.class);
	
	private final GlProgram[] shaders;

	public ShaderMap() {
		ShaderKey[] ids = ShaderKey.values();
		this.shaders = new GlProgram[ids.length];
	}

	/**
	 * Adds a shader to the map.
	 * 
	 * @param key The shader key
	 * @param shader The shader program
	 */
	public void put(ShaderKey key, GlProgram shader) {
		if (key != null && shader != null) {
			int ordinal = key.ordinal();
			if (shaders[ordinal] != null) {
				LOGGER.warn("Replacing existing shader for key: {}", key);
			}
			shaders[ordinal] = shader;
		}
	}

	/**
	 * Gets a shader from the map.
	 * 
	 * @param key The shader key
	 * @return The shader program, or null if not present
	 */
	public GlProgram getShader(ShaderKey key) {
		if (key == null) {
			return null;
		}
		return shaders[key.ordinal()];
	}

	/**
	 * Checks if a shader is present for the given key.
	 */
	public boolean hasShader(ShaderKey key) {
		return key != null && shaders[key.ordinal()] != null;
	}

	/**
	 * Gets the number of shaders in the map.
	 */
	public int size() {
		int count = 0;
		for (GlProgram shader : shaders) {
			if (shader != null) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Destroys all shaders in the map.
	 */
	public void destroy() {
		for (int i = 0; i < shaders.length; i++) {
			GlProgram shader = shaders[i];
			if (shader != null) {
				try {
					shader.close();
				} catch (Exception e) {
					LOGGER.error("Error destroying shader {}: {}", ShaderKey.values()[i], e.getMessage());
				}
				shaders[i] = null;
			}
		}
	}
}
