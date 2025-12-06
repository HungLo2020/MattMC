package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class GlShaderModule implements AutoCloseable {
	private static final int NOT_ALLOCATED = -1;
	public static final GlShaderModule INVALID_SHADER = new GlShaderModule(-1, ResourceLocation.withDefaultNamespace("invalid"), ShaderType.VERTEX);
	private final ResourceLocation id;
	private int shaderId;
	private final ShaderType type;

	public GlShaderModule(int i, ResourceLocation resourceLocation, ShaderType shaderType) {
		this.id = resourceLocation;
		this.shaderId = i;
		this.type = shaderType;
	}

	public void close() {
		if (this.shaderId == -1) {
			throw new IllegalStateException("Already closed");
		} else {
			RenderSystem.assertOnRenderThread();
			GlStateManager.glDeleteShader(this.shaderId);
			this.shaderId = -1;
		}
	}

	public ResourceLocation getId() {
		return this.id;
	}

	public int getShaderId() {
		return this.shaderId;
	}

	public String getDebugLabel() {
		return this.type.idConverter().idToFile(this.id).toString();
	}
}
