package net.sodium.client.render.chunk;

import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.sodium.client.gl.attribute.GlVertexFormat;
import net.sodium.client.gl.device.CommandList;
import net.sodium.client.gl.device.RenderDevice;
import net.sodium.client.gl.shader.*;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.shader.*;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.util.FogParameters;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import java.util.Map;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat vertexFormat;

    protected final RenderDevice device;

    protected GlProgram<ChunkShaderInterface> activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();
    }

    protected GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
        GlProgram<ChunkShaderInterface> program = this.programs.get(options);

        if (program == null) {
            this.programs.put(options, program = this.createShader("blocks/block_layer_opaque", options));
        }

        return program;
    }

    private GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = createShaderConstants(options);

        GlShader vertShader = ShaderLoader.loadShader(ShaderType.VERTEX,
                ResourceLocation.fromNamespaceAndPath("sodium", path + ".vsh"), constants);

        GlShader fragShader = ShaderLoader.loadShader(ShaderType.FRAGMENT,
                ResourceLocation.fromNamespaceAndPath("sodium", path + ".fsh"), constants);

        try {
            return GlProgram.builder(ResourceLocation.fromNamespaceAndPath("sodium", "chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Position", ChunkShaderBindingPoints.ATTRIBUTE_POSITION)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE)
                    .bindAttribute("a_LightAndData", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .link((shader) -> new DefaultShaderInterface(shader, options));
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    private static ShaderConstants createShaderConstants(ChunkShaderOptions options) {
        ShaderConstants.Builder builder = ShaderConstants.builder();
        builder.addAll(options.fog().getDefines());

        if (options.pass().supportsFragmentDiscard()) {
            builder.add("USE_FRAGMENT_DISCARD");
        }

        builder.add("USE_VERTEX_COMPRESSION"); // TODO: allow compact vertex format to be disabled
        builder.add("MAX_TEXTURE_LOD_BIAS", String.valueOf(RenderDevice.INSTANCE.getMaxTextureLodBias()));

        return builder.build();
    }

    protected void begin(TerrainRenderPass pass, FogParameters parameters) {
        // Iris: From MixinShaderChunkRenderer - reset blend mode state
        net.irisshaders.iris.gl.blending.BlendModeOverride.restore();
        
        RenderTarget target = pass.getTarget();

        // Iris: From MixinShaderChunkRenderer - viewport redirect (skip if in shadow pass)
        if (!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            CommandContext ctx = VulkanicAPI.getCommandContext();
            VulkanicAPI.setDynamicViewport(ctx, 0, 0, target.getColorTexture().getWidth(0), target.getColorTexture().getHeight(0));
        }
        
        // Iris: From MixinShaderChunkRenderer - framebuffer binding is delayed/redirected
        // The framebuffer binding is handled below in the compileProgram redirect

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.applyPipelineState(pass.getPipeline());
        commandEncoder.invalidateCachedProgramBinding();

        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass, this.vertexType);

        // Iris: From MixinShaderChunkRenderer - redirect program compilation to use Iris programs if available
        net.irisshaders.iris.pipeline.WorldRenderingPipeline pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();

        GlProgram<ChunkShaderInterface> program = null;

        if (pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline irisRenderingPipeline) {
            irisRenderingPipeline.getSodiumPrograms().getFramebuffer(pass).bind();
            program = irisRenderingPipeline.getSodiumPrograms().getProgram(pass);
        }

        if (program == null) {
            target.iris$bindFramebuffer();
            program = this.compileProgram(options);
        }

        this.activeProgram = program;
        this.activeProgram.bind();
        this.activeProgram.getInterface()
                .setupState(pass, parameters);
    }

    protected void end(TerrainRenderPass pass) {
        this.activeProgram.getInterface()
                .resetState();
        this.activeProgram.unbind();
        this.activeProgram = null;
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values()
                .forEach(GlProgram::delete);
    }

}
