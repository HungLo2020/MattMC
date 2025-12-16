package net.minecraft.client.renderer.sodium.render.chunk.shader;

import net.minecraft.client.renderer.gl.advanced.shader.uniform.GlUniform;
import net.minecraft.client.renderer.gl.advanced.shader.uniform.GlUniformBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

public interface ShaderBindingContext {
    @NotNull
    <U extends GlUniform<?>> U bindUniform(String name, IntFunction<U> factory);

    @Nullable
    <U extends GlUniform<?>> U bindUniformOptional(String name, IntFunction<U> factory);

    @NotNull
    GlUniformBlock bindUniformBlock(String name, int bindingPoint);

    @Nullable
    GlUniformBlock bindUniformBlockOptional(String name, int bindingPoint);
}
