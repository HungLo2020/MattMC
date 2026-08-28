package net.vulkanic;

import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;

import java.util.Objects;

/**
 * Backend-neutral frontend API surface.
 *
 * <p>This is the preferred entrypoint for new callsites that should avoid raw GL
 * integer knobs at the Vulkanic frontend boundary.</p>
 */
public final class VulkanicCoreAPI {

    private VulkanicCoreAPI() {
    }

    public static void bindTexture(CommandContext ctx, VulkanicTextureTarget target, int textureId) {
        VulkanicAPI.bindTexture(ctx, target, textureId);
    }

    public static void uploadTexture2D(
        CommandContext ctx,
        VulkanicTextureTarget target,
        int level,
        VulkanicTextureUploadFormat uploadFormat,
        int width,
        int height,
        int border,
        java.nio.ByteBuffer pixels
    ) {
        VulkanicAPI.uploadTexture2D(ctx, target, level, uploadFormat, width, height, border, pixels);
    }

    public static void uploadTexture2D(
        CommandContext ctx,
        int level,
        VulkanicTextureUploadFormat uploadFormat,
        int width,
        int height,
        int border,
        java.nio.ByteBuffer pixels
    ) {
        VulkanicAPI.uploadTexture2D(ctx, level, uploadFormat, width, height, border, pixels);
    }

    public static void bindBuffer(CommandContext ctx, VulkanicBufferTarget target, int buffer) {
        VulkanicAPI.bindBuffer(ctx, target, buffer);
    }

    public static void bufferData(CommandContext ctx, VulkanicBufferTarget target, java.nio.ByteBuffer data, int usage) {
        VulkanicAPI.bufferData(ctx, target.toLegacyGlTarget(), data, usage);
    }

    public static void bufferData(CommandContext ctx, VulkanicBufferTarget target, long size, int usage) {
        VulkanicAPI.bufferData(ctx, target.toLegacyGlTarget(), size, usage);
    }

    public static void bufferSubData(CommandContext ctx, VulkanicBufferTarget target, long offset, java.nio.ByteBuffer data) {
        VulkanicAPI.bufferSubData(ctx, target, offset, data);
    }

    public static void bufferStorage(CommandContext ctx, VulkanicBufferTarget target, java.nio.ByteBuffer data, int flags) {
        VulkanicAPI.bufferStorage(ctx, target, data, flags);
    }

    public static void bufferStorage(CommandContext ctx, VulkanicBufferTarget target, long size, int flags) {
        VulkanicAPI.bufferStorage(ctx, target, size, flags);
    }

    public static java.nio.ByteBuffer mapBufferRange(CommandContext ctx, VulkanicBufferTarget target, long offset, long length, int access) {
        return VulkanicAPI.mapBuffer(ctx, target.toLegacyGlTarget(), offset, length, access);
    }

    public static void unmapBuffer(CommandContext ctx, VulkanicBufferTarget target) {
        VulkanicAPI.unmapBuffer(ctx, target.toLegacyGlTarget());
    }

    public static void flushMappedBufferRange(CommandContext ctx, VulkanicBufferTarget target, long offset, long length) {
        VulkanicAPI.flushMappedBufferRange(ctx, target.toLegacyGlTarget(), offset, length);
    }

    public static int textureId(GpuTexture texture) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
            || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException(
                "legacy Java texture handles are unavailable while Rust owns whole-frame Vulkan presentation"
            );
        }
        return VulkanicAPI.getTextureHandle(texture);
    }

    public static int textureId(GpuTextureView textureView) {
        if (textureView == null) {
            throw new IllegalArgumentException("textureView must not be null");
        }
        return textureId(textureView.texture());
    }

    public static int bufferId(net.blaze3d.buffers.GpuBuffer buffer) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
            || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException(
                "legacy Java buffer handles are unavailable while Rust owns whole-frame Vulkan presentation"
            );
        }
        return VulkanicAPI.getBufferHandle(buffer);
    }

    public static void bindTextureUnit(CommandContext ctx, int unit, GpuTextureView textureView) {
        VulkanicAPI.bindTextureUnit(ctx, unit, textureView);
    }

    public static void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
            || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException(
                "Java Vulkan presentation is unavailable while Rust owns whole-frame presentation"
            );
        }
        VulkanicAPI.presentTextureToScreen(ctx, textureView);
    }

    public static void setCapabilityEnabled(CommandContext ctx, VulkanicCapability capability, boolean enabled) {
        VulkanicAPI.setCapabilityEnabled(ctx, capability, enabled);
    }

    public static boolean isEnabled(CommandContext ctx, VulkanicCapability capability) {
        return VulkanicAPI.isEnabled(ctx, capability);
    }

    public static void setCullFaceMode(CommandContext ctx, VulkanicCullFaceMode mode) {
        VulkanicAPI.setCullFaceMode(ctx, mode);
    }

    public static void setDepthFunc(CommandContext ctx, VulkanicDepthCompareOp op) {
        VulkanicAPI.setDepthFunc(ctx, op);
    }

    public static void setBlendFunction(
        CommandContext ctx,
        VulkanicBlendFactor srcRgb,
        VulkanicBlendFactor dstRgb,
        VulkanicBlendFactor srcAlpha,
        VulkanicBlendFactor dstAlpha
    ) {
        VulkanicAPI.setBlendFunction(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void setBlendEquation(CommandContext ctx, VulkanicBlendEquation equation) {
        VulkanicAPI.setBlendEquation(ctx, equation);
    }

    public static void blendFunc(CommandContext ctx, VulkanicBlendFactor sfactor, VulkanicBlendFactor dfactor) {
        VulkanicAPI.blendFunc(ctx, sfactor, dfactor);
    }

    public static void setStencilFunc(CommandContext ctx, VulkanicStencilCompareOp func, int ref, int mask) {
        VulkanicAPI.setStencilFunc(ctx, func, ref, mask);
    }

    public static void setStencilOp(
        CommandContext ctx,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        VulkanicAPI.setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp);
    }

    public static void setStencilWriteMask(CommandContext ctx, int mask) {
        VulkanicAPI.setStencilWriteMask(ctx, mask);
    }

    public static void setStencilFuncSeparate(CommandContext ctx, VulkanicStencilFace face, VulkanicStencilCompareOp func, int ref, int mask) {
        VulkanicAPI.setStencilFuncSeparate(ctx, face, func, ref, mask);
    }

    public static void setStencilOpSeparate(
        CommandContext ctx,
        VulkanicStencilFace face,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        VulkanicAPI.setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp);
    }

    public static void setStencilWriteMaskSeparate(CommandContext ctx, VulkanicStencilFace face, int mask) {
        VulkanicAPI.setStencilWriteMaskSeparate(ctx, face, mask);
    }

    public static void clearBuffers(CommandContext ctx, VulkanicClearBuffer... buffers) {
        VulkanicAPI.clearBuffers(ctx, buffers);
    }

    public static void setLogicOp(CommandContext ctx, VulkanicLogicOp opcode) {
        VulkanicAPI.setLogicOp(ctx, opcode);
    }

    public static void bindUniformBufferRange(
        CommandContext ctx,
        VulkanicBufferTarget target,
        int index,
        int buffer,
        long offset,
        long size
    ) {
        VulkanicAPI.bindUniformBufferRange(ctx, target, index, buffer, offset, size);
    }

    public static void texBuffer(CommandContext ctx, VulkanicTextureTarget target, int internalFormat, int buffer) {
        VulkanicAPI.texBuffer(ctx, target, internalFormat, buffer);
    }

    public static VulkanicSpirvModule compileSpirvModule(
        CommandContext ctx,
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName,
        String entryPoint
    ) {
        return VulkanicAPI.compileSpirvModule(ctx, shaderStage, glslSource, sourceName, entryPoint);
    }

    public static VulkanicSpirvModule compileSpirvModule(
        CommandContext ctx,
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName
    ) {
        return VulkanicAPI.compileSpirvModule(ctx, shaderStage, glslSource, sourceName);
    }

    public static java.util.Optional<VulkanicSpirvModule> getCompiledSpirvModule(CommandContext ctx, int shader) {
        return VulkanicAPI.getCompiledSpirvModule(ctx, shader);
    }

    public static java.util.Optional<VulkanicSpirvModule> getCompiledSpirvModule(CommandContext ctx, VulkanicShaderHandle shader) {
        return VulkanicAPI.getCompiledSpirvModule(ctx, shader);
    }

    public static VulkanicShaderHandle createShaderHandle(CommandContext ctx, VulkanicShaderStage shaderStage) {
        return VulkanicAPI.createShaderHandle(ctx, shaderStage);
    }

    public static VulkanicProgramHandle createShaderProgramHandle(CommandContext ctx) {
        return VulkanicAPI.createShaderProgramHandle(ctx);
    }

    public static void compileShader(CommandContext ctx, VulkanicShaderHandle shader) {
        VulkanicAPI.compileShader(ctx, shader);
    }

    public static void attachShader(CommandContext ctx, VulkanicProgramHandle program, VulkanicShaderHandle shader) {
        VulkanicAPI.attachShader(ctx, program, shader);
    }

    public static void detachShader(CommandContext ctx, VulkanicProgramHandle program, VulkanicShaderHandle shader) {
        VulkanicAPI.detachShader(ctx, program, shader);
    }

    public static void linkProgram(CommandContext ctx, VulkanicProgramHandle program) {
        VulkanicAPI.linkProgram(ctx, program);
    }

    public static boolean isProgramLinkSuccessful(CommandContext ctx, VulkanicProgramHandle program) {
        return VulkanicAPI.isProgramLinkSuccessful(ctx, program);
    }

    public static boolean isShaderCompileSuccessful(CommandContext ctx, VulkanicShaderHandle shader) {
        return VulkanicAPI.isShaderCompileSuccessful(ctx, shader);
    }

    public static String getProgramInfoLog(CommandContext ctx, VulkanicProgramHandle program) {
        return VulkanicAPI.getProgramInfoLog(ctx, program);
    }

    public static String getShaderInfoLog(CommandContext ctx, VulkanicShaderHandle shader) {
        return VulkanicAPI.getShaderInfoLog(ctx, shader);
    }

    public static void deleteShader(CommandContext ctx, VulkanicShaderHandle shader) {
        VulkanicAPI.deleteShader(ctx, shader);
    }

    public static void deleteProgram(CommandContext ctx, VulkanicProgramHandle program) {
        VulkanicAPI.deleteProgram(ctx, program);
    }

    public static void uploadShaderSource(CommandContext ctx, int shader, CharSequence source) {
        VulkanicAPI.uploadShaderSource(ctx, shader, source);
    }

    public static void uploadShaderSource(CommandContext ctx, VulkanicShaderHandle shader, CharSequence source) {
        VulkanicAPI.uploadShaderSource(ctx, shader, source);
    }

    public static VulkanicUniformLocation resolveUniformLocation(CommandContext ctx, int program, CharSequence name) {
        return VulkanicAPI.resolveUniformLocation(ctx, program, name);
    }

    public static VulkanicUniformLocation resolveUniformLocationWithLegacySamplerFallback(CommandContext ctx, int program, CharSequence name) {
        return VulkanicAPI.resolveUniformLocationWithLegacySamplerFallback(ctx, program, name);
    }

    public static void setUniform1i(CommandContext ctx, VulkanicUniformLocation location, int value) {
        VulkanicAPI.setUniform1i(ctx, location, value);
    }

    public static void setUniform1f(CommandContext ctx, VulkanicUniformLocation location, float value) {
        VulkanicAPI.setUniform1f(ctx, location, value);
    }

    public static void setUniform2f(CommandContext ctx, VulkanicUniformLocation location, float v0, float v1) {
        VulkanicAPI.setUniform2f(ctx, location, v0, v1);
    }

    public static void setUniform3i(CommandContext ctx, VulkanicUniformLocation location, int v0, int v1, int v2) {
        VulkanicAPI.setUniform3i(ctx, location, v0, v1, v2);
    }

    public static void setUniform4f(CommandContext ctx, VulkanicUniformLocation location, float v0, float v1, float v2, float v3) {
        VulkanicAPI.setUniform4f(ctx, location, v0, v1, v2, v3);
    }

    public static void setUniform4i(CommandContext ctx, VulkanicUniformLocation location, int v0, int v1, int v2, int v3) {
        VulkanicAPI.setUniform4i(ctx, location, v0, v1, v2, v3);
    }

    public static void setUniformMatrix3fv(CommandContext ctx, VulkanicUniformLocation location, boolean transpose, java.nio.FloatBuffer matrix) {
        VulkanicAPI.setUniformMatrix3fv(ctx, location, transpose, matrix);
    }

    public static void setUniformMatrix4fv(CommandContext ctx, VulkanicUniformLocation location, boolean transpose, java.nio.FloatBuffer matrix) {
        VulkanicAPI.setUniformMatrix4fv(ctx, location, transpose, matrix);
    }

    public static void texParameteri(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName parameter,
        int value
    ) {
        Objects.requireNonNull(parameter, "parameter must not be null");
        VulkanicAPI.texParameteri(ctx, target, parameter, value);
    }

    public static int getInteger(CommandContext ctx, VulkanicIntegerQuery query) {
        return VulkanicAPI.getInteger(ctx, query);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        int maxNameLength
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength, stages);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program, stages);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program,
        int maxNameLength
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength, stages);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program);
    }

    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.deriveResourceLayoutFromProgramReflection(ctx, program, stages);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program, maxNameLength);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program, maxNameLength, stages);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program, stages);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program,
        int maxNameLength
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program, maxNameLength);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program, maxNameLength, stages);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program);
    }

    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return VulkanicAPI.withReflectedResourceLayout(ctx, descriptor, program, stages);
    }

    public static PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        return VulkanicAPI.createPipeline(descriptor);
    }

    public static PipelineHandle createPipeline(
        PipelineDescriptor.PortableState portableState,
        java.util.List<VulkanicSpirvModule> spirvModules
    ) {
        return VulkanicAPI.createPipeline(portableState, spirvModules);
    }

    public static PipelineDescriptor createPipelineDescriptor(
        PipelineDescriptor.PortableState portableState,
        java.util.List<VulkanicSpirvModule> spirvModules
    ) {
        return VulkanicAPI.createPipelineDescriptor(portableState, spirvModules);
    }
}
