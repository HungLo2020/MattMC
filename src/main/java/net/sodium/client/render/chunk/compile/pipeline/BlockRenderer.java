package net.sodium.client.render.chunk.compile.pipeline;

import net.sodium.api.util.ColorARGB;
import net.sodium.api.util.ColorMixer;
import net.sodium.client.compatibility.workarounds.Workarounds;
import net.sodium.client.model.color.ColorProvider;
import net.sodium.client.model.color.ColorProviderRegistry;
import net.sodium.client.model.light.LightMode;
import net.sodium.client.model.light.LightPipelineProvider;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.model.quad.properties.ModelQuadOrientation;
import net.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import net.sodium.client.render.chunk.terrain.material.parameters.MaterialParameters;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.sodium.client.render.texture.SpriteFinderCache;
import net.sodium.client.world.LevelSlice;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class BlockRenderer extends AbstractBlockRenderContext implements net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface {
    private final ColorProviderRegistry colorProviderRegistry;
    private final int[] vertexColors = new int[4];

    public ChunkBuildBuffers buffers; // Made public for Iris Sodium integration (though currently unused)

    private final Vector3f posOffset = new Vector3f();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    @Nullable
    private ColorProvider<BlockState> colorProvider;
    private TranslucentGeometryCollector collector;
    private int emittedQuadCount;
    
    // Iris: From MixinBlockRenderer - vertex encoder fields
    private boolean iris$hasOverride;
    private int iris$blockId;
    private byte iris$isFluid;
    private byte iris$lightEmission;
    private int iris$localX, iris$localY, iris$localZ;

    public BlockRenderer(ColorProviderRegistry colorRegistry, LightPipelineProvider lighters) {
        this.colorProviderRegistry = colorRegistry;
        this.lighters = lighters;

        this.random = new SingleThreadedRandomSource(42L);
    }

    public void prepare(ChunkBuildBuffers buffers, LevelSlice level, TranslucentGeometryCollector collector) {
        this.buffers = buffers;
        this.level = level;
        this.collector = collector;
        this.slice = level;
        this.emittedQuadCount = 0;
    }

    public void release() {
        this.buffers = null;
        this.level = null;
        this.collector = null;
        this.slice = null;
    }

    public int getEmittedQuadCount() {
        return this.emittedQuadCount;
    }

    public void renderModel(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin) {
        // The whole-frame Vulkan source owns the compact semantic layout and
        // must not read Iris material-map state.  Iris overrides remain a
        // private compatibility concern for the legacy renderer.
        if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
                && net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE
                .getBlockTypeIds().containsKey(state.getBlock())) {
            iris$hasOverride = true;
        }
        
        this.state = state;
        this.pos = pos;

        this.prepareAoInfo(true);


        this.posOffset.set(origin.getX(), origin.getY(), origin.getZ());
        if (state.hasOffsetFunction()) {
            Vec3 modelOffset = state.getOffset(pos);
            this.posOffset.add((float) modelOffset.x, (float) modelOffset.y, (float) modelOffset.z);
        }

        this.colorProvider = this.colorProviderRegistry.getColorProvider(state.getBlock());

        this.prepareCulling(true);

        this.defaultRenderType = ItemBlockRenderTypes.getChunkRenderType(state);
        this.allowDowngrade = true;


        random.setSeed(state.getSeed(pos));
        ((FabricBlockStateModel) model).emitQuads(getEmitter(), this.level, pos, state, this.random, this::isFaceCulled);

        this.defaultRenderType = null;
        
        // Iris: From MixinBlockRenderer - clear override flag at TAIL
        iris$hasOverride = false;
    }

    /**
     * Process quad, after quad transforms and the culling check have been applied.
     */
    @Override
    protected void processQuad(MutableQuadViewImpl quad) {
        this.emittedQuadCount++;
        final TriState aoMode = quad.ambientOcclusion();
        final ShadeMode shadeMode = quad.shadeMode();
        final LightMode lightMode;
        if (aoMode == TriState.DEFAULT) {
            lightMode = this.defaultLightMode;
        } else {
            lightMode = this.useAmbientOcclusion && aoMode.get() ? LightMode.SMOOTH : LightMode.FLAT;
        }
        final boolean emissive = quad.emissive();

        final ChunkSectionLayer blendMode = quad.renderLayer();
        final Material material = DefaultMaterials.forChunkLayer(blendMode == null ? defaultRenderType : blendMode);

        this.tintQuad(quad);
        this.shadeQuad(quad, lightMode, emissive, shadeMode);
        this.bufferQuad(quad, this.quadLightData.br, material);
    }

    private void tintQuad(MutableQuadViewImpl quad) {
        int tintIndex = quad.tintIndex();

        if (tintIndex != -1) {
            ColorProvider<BlockState> colorProvider = this.colorProvider;

            if (colorProvider != null) {
                int[] vertexColors = this.vertexColors;
                colorProvider.getColors(this.slice, this.pos, this.scratchPos, this.state, quad, vertexColors, slice.hasBiomeBlend());

                for (int i = 0; i < 4; i++) {
                    quad.color(i, ColorMixer.mulComponentWise(vertexColors[i], quad.color(i)));
                }
            }
        }
    }

    private void bufferQuad(MutableQuadViewImpl quad, float[] brightnesses, Material material) {
        // TODO: Find a way to reimplement quad reorientation
        ModelQuadOrientation orientation = ModelQuadOrientation.NORMAL;
        Vector3f offset = this.posOffset;

        var atlasSprite = quad.sprite(SpriteFinderCache.forBlockAtlas());
        var materialBits = material.bits();
        ModelQuadFacing normalFace = quad.normalFace();

        // attempt render pass downgrade if possible
        var pass = material.pass;

        // Iris: From MixinBlockRenderer - skip pass downgrade when hasOverride
        var downgradedPass = iris$hasOverride ? null : attemptPassDowngrade(atlasSprite, pass, quad);
        if (downgradedPass != null) {
            pass = downgradedPass;
        }

        // if there was a downgrade from translucent to cutout, the material bits' alpha cutoff needs to be updated
        if (downgradedPass != null && material == DefaultMaterials.TRANSLUCENT && pass == DefaultTerrainRenderPasses.CUTOUT) {
            // ONE_TENTH and HALF are functionally the same so it doesn't matter which one we take here
            materialBits = MaterialParameters.pack(AlphaCutoffParameter.ONE_TENTH, material.mipped);
        }

        ChunkModelBuilder builder = this.buffers.get(pass);
        NativeSectionMeshBuilder.FacingBuffer vertexBuffer = builder.getVertexBuffer(normalFace);
        if (pass.isTranslucent() && this.collector != null) {
            if (this.appendQuad(vertexBuffer, materialBits, quad, brightnesses, orientation, offset,
                    this.collector, normalFace, quad.getFaceNormal())) {
                return;
            }
        } else {
            this.appendQuad(vertexBuffer, materialBits, quad, brightnesses, orientation, offset, null, null, 0);
        }

        if (atlasSprite != null) {
            builder.addSprite(atlasSprite);
        }
    }

    private boolean appendQuad(NativeSectionMeshBuilder.FacingBuffer vertexBuffer, int materialBits,
            MutableQuadViewImpl quad, float[] brightnesses, ModelQuadOrientation orientation, Vector3f offset,
            TranslucentGeometryCollector collector, ModelQuadFacing collectorFacing, int packedNormal) {
        int src0 = orientation.getVertexIndex(0);
        int src1 = orientation.getVertexIndex(1);
        int src2 = orientation.getVertexIndex(2);
        int src3 = orientation.getVertexIndex(3);

        if (collector != null) {
            return vertexBuffer.appendFlatTranslucentQuad(materialBits, collector, collectorFacing, packedNormal,
                    iris$lightEmission, iris$isFluid, false, iris$blockId, iris$localX, iris$localY, iris$localZ,
                    quad.x(src0) + offset.x, quad.y(src0) + offset.y, quad.z(src0) + offset.z,
                    ColorARGB.toABGR(quad.color(src0)), brightnesses[src0], quad.u(src0), quad.v(src0),
                    quad.lightmap(src0),
                    quad.x(src1) + offset.x, quad.y(src1) + offset.y, quad.z(src1) + offset.z,
                    ColorARGB.toABGR(quad.color(src1)), brightnesses[src1], quad.u(src1), quad.v(src1),
                    quad.lightmap(src1),
                    quad.x(src2) + offset.x, quad.y(src2) + offset.y, quad.z(src2) + offset.z,
                    ColorARGB.toABGR(quad.color(src2)), brightnesses[src2], quad.u(src2), quad.v(src2),
                    quad.lightmap(src2),
                    quad.x(src3) + offset.x, quad.y(src3) + offset.y, quad.z(src3) + offset.z,
                    ColorARGB.toABGR(quad.color(src3)), brightnesses[src3], quad.u(src3), quad.v(src3),
                    quad.lightmap(src3));
        }

        vertexBuffer.appendFlatQuad(materialBits, iris$lightEmission, iris$isFluid, false, iris$blockId,
                iris$localX, iris$localY, iris$localZ,
                quad.x(src0) + offset.x, quad.y(src0) + offset.y, quad.z(src0) + offset.z,
                ColorARGB.toABGR(quad.color(src0)), brightnesses[src0], quad.u(src0), quad.v(src0),
                quad.lightmap(src0),
                quad.x(src1) + offset.x, quad.y(src1) + offset.y, quad.z(src1) + offset.z,
                ColorARGB.toABGR(quad.color(src1)), brightnesses[src1], quad.u(src1), quad.v(src1),
                quad.lightmap(src1),
                quad.x(src2) + offset.x, quad.y(src2) + offset.y, quad.z(src2) + offset.z,
                ColorARGB.toABGR(quad.color(src2)), brightnesses[src2], quad.u(src2), quad.v(src2),
                quad.lightmap(src2),
                quad.x(src3) + offset.x, quad.y(src3) + offset.y, quad.z(src3) + offset.z,
                ColorARGB.toABGR(quad.color(src3)), brightnesses[src3], quad.u(src3), quad.v(src3),
                quad.lightmap(src3));
        return false;
    }

    private static boolean validateQuadUVs(TextureAtlasSprite atlasSprite, MutableQuadViewImpl quad) {
        // sanity check that the quad's UVs are within the sprite's bounds
        var spriteUMin = atlasSprite.getU0();
        var spriteUMax = atlasSprite.getU1();
        var spriteVMin = atlasSprite.getV0();
        var spriteVMax = atlasSprite.getV1();

        for (int i = 0; i < 4; i++) {
            var u = quad.u(i);
            var v = quad.v(i);
            if (u < spriteUMin || u > spriteUMax || v < spriteVMin || v > spriteVMax) {
                return false;
            }
        }

        return true;
    }

    private @Nullable TerrainRenderPass attemptPassDowngrade(TextureAtlasSprite sprite, TerrainRenderPass pass,
            MutableQuadViewImpl quad) {
        if (!allowDowngrade || Workarounds.isWorkaroundEnabled(Workarounds.Reference.INTEL_DEPTH_BUFFER_COMPARISON_UNRELIABLE)) {
            return null;
        }

        boolean attemptDowngrade = true;
        boolean hasNonOpaqueVertex = false;

        for (int i = 0; i < 4; i++) {
            hasNonOpaqueVertex |= ColorARGB.unpackAlpha(quad.color(i)) != 0xFF;
        }

        // don't do downgrade if some vertex is not fully opaque
        if (pass.isTranslucent() && hasNonOpaqueVertex) {
            attemptDowngrade = false;
        }

        if (attemptDowngrade) {
            attemptDowngrade = validateQuadUVs(sprite, quad);
        }

        if (attemptDowngrade) {
            return getDowngradedPass(sprite, pass);
        }

        return null;
    }

    private static TerrainRenderPass getDowngradedPass(TextureAtlasSprite sprite, TerrainRenderPass pass) {
        if (sprite instanceof TextureAtlasSpriteExtension spriteExt) {
            // Some mods may use a custom ticker which we cannot look into. To avoid problems with these mods,
            // do not attempt to downgrade the render pass.
            if (spriteExt.sodium$hasUnknownImageContents()) {
                return pass;
            }

            if (sprite.contents() instanceof SpriteContentsExtension contentsExt) {
                if (pass == DefaultTerrainRenderPasses.TRANSLUCENT && !contentsExt.sodium$hasTranslucentPixels()) {
                    pass = DefaultTerrainRenderPasses.CUTOUT;
                }
                if (pass == DefaultTerrainRenderPasses.CUTOUT && !contentsExt.sodium$hasTransparentPixels()) {
                    pass = DefaultTerrainRenderPasses.SOLID;
                }
            }
        }

        return pass;
    }
    
    // Iris: From MixinBlockRenderer - VertexEncoderInterface implementation
    @Override
    public void beginBlock(int blockId, byte isFluid, byte lightEmission, int x, int y, int z) {
        this.iris$blockId = blockId;
        this.iris$isFluid = isFluid;
        this.iris$lightEmission = lightEmission;
        this.iris$localX = x;
        this.iris$localY = y;
        this.iris$localZ = z;
    }
}
