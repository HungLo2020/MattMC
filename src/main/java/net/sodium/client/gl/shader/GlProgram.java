package net.sodium.client.gl.shader;

import net.sodium.client.gl.GlObject;
import net.sodium.client.gl.shader.uniform.GlUniform;
import net.sodium.client.gl.shader.uniform.GlUniformBlock;
import net.sodium.client.render.chunk.shader.ShaderBindingContext;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * An OpenGL shader program.
 */
public class GlProgram<T> extends GlObject implements ShaderBindingContext {
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);

    private final T shaderInterface;

    protected GlProgram(int program, Function<ShaderBindingContext, T> interfaceFactory) {
        this.setHandle(program);
        this.shaderInterface = interfaceFactory.apply(this);
    }

    public T getInterface() {
        return this.shaderInterface;
    }

    public static Builder builder(ResourceLocation name) {
        return new Builder(name);
    }

    public void bind() {
        CommandContext ctx = VulkanicAPI.getImmediateContext();
        VulkanicAPI.bindShaderProgram(ctx, this.handle());
    }

    public void unbind() {
        CommandContext ctx = VulkanicAPI.getImmediateContext();
        VulkanicAPI.bindShaderProgram(ctx, 0);
    }

    public void delete() {
        VulkanicAPI.deleteProgram(VulkanicAPI.getImmediateContext(), this.handle());

        this.invalidateHandle();
    }

    @Override
    public <U extends GlUniform<?>> @NotNull U bindUniform(String name, IntFunction<U> factory) {
        int index = VulkanicAPI.getUniformLocation(VulkanicAPI.getImmediateContext(), this.handle(), name);

        if (index < 0) {
            throw new NullPointerException("No uniform exists with name: " + name);
        }

        return factory.apply(index);
    }

    @Override
    public <U extends GlUniform<?>> U bindUniformOptional(String name, IntFunction<U> factory) {
        int index = VulkanicAPI.getUniformLocation(VulkanicAPI.getImmediateContext(), this.handle(), name);

        if (index < 0) {
            return null;
        }

        return factory.apply(index);
    }

    @Override
    public @NotNull GlUniformBlock bindUniformBlock(String name, int bindingPoint) {
        int index = VulkanicAPI.locateUniformBlock(this.handle(), name);

        if (index < 0) {
            throw new NullPointerException("No uniform block exists with name: " + name);
        }

        VulkanicAPI.bindUniformBlock(this.handle(), index, bindingPoint);

        return new GlUniformBlock(bindingPoint);
    }

    @Override
    public GlUniformBlock bindUniformBlockOptional(String name, int bindingPoint) {
        int index = VulkanicAPI.locateUniformBlock(this.handle(), name);

        if (index < 0) {
            return null;
        }

        VulkanicAPI.bindUniformBlock(this.handle(), index, bindingPoint);

        return new GlUniformBlock(bindingPoint);
    }

    public static class Builder {
        private final ResourceLocation name;
        private final int program;

        public Builder(ResourceLocation name) {
            this.name = name;
            this.program = VulkanicAPI.createShaderProgram(VulkanicAPI.getImmediateContext());
        }

        public Builder attachShader(GlShader shader) {
            VulkanicAPI.attachShader(VulkanicAPI.getImmediateContext(), this.program, shader.handle());

            return this;
        }

        /**
         * Links the attached shaders to this program and returns a user-defined container which wraps the shader
         * program. This container can, for example, provide methods for updating the specific uniforms of that shader
         * set.
         *
         * @param factory The factory which will create the shader program's interface
         * @param <U> The interface type for the shader program
         * @return An instantiated shader container as provided by the factory
         */
        public <U> GlProgram<U> link(Function<ShaderBindingContext, U> factory) {
            VulkanicAPI.linkProgram(VulkanicAPI.getImmediateContext(), this.program);

            String log = VulkanicAPI.getProgramInfoLog(VulkanicAPI.getImmediateContext(), this.program);

            if (!log.isEmpty()) {
                LOGGER.warn("Program link log for " + this.name + ": " + log);
            }

            int result = VulkanicAPI.getProgramParameter(VulkanicAPI.getImmediateContext(), this.program, VulkanicAPI.GL_LINK_STATUS);

            if (result != 1) { // GL_TRUE
                throw new RuntimeException("Shader program linking failed, see log for details");
            }

            return new GlProgram<>(this.program, factory);
        }

        public Builder bindAttribute(String name, int index) {
            VulkanicAPI.setAttributeLocation(VulkanicAPI.getImmediateContext(), this.program, index, name);

            return this;
        }

        public Builder bindFragmentData(String name, int index) {
            VulkanicAPI.bindFragmentDataLocation(this.program, index, name);

            return this;
        }
    }
}
