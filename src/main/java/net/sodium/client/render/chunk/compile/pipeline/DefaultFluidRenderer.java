package net.sodium.client.render.chunk.compile.pipeline;


import net.sodium.api.util.ColorARGB;
import net.sodium.api.util.NormI8;
import net.sodium.client.model.color.ColorProvider;
import net.sodium.client.model.light.LightMode;
import net.sodium.client.model.light.LightPipeline;
import net.sodium.client.model.light.LightPipelineProvider;
import net.sodium.client.model.light.data.QuadLightData;
import net.sodium.client.model.quad.ModelQuad;
import net.sodium.client.model.quad.ModelQuadView;
import net.sodium.client.model.quad.ModelQuadViewMutable;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.model.quad.properties.ModelQuadFlags;
import net.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.sodium.client.services.PlatformBlockAccess;
import net.sodium.api.util.DirectionUtil;
import net.sodium.client.world.LevelSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;

public class DefaultFluidRenderer implements net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface {
    // TODO: allow this to be changed by vertex format, WARNING: make sure TQuad knows about EPSILON
    // TODO: move fluid rendering to a separate render pass and control glPolygonOffset and glDepthFunc to fix this properly
    public static final float EPSILON = 0.001f;
    private static final float ALIGNED_EQUALS_EPSILON = 0.011f;
    private static final int FLUID_FACE_TOP_NE_SW = 0;
    private static final int FLUID_FACE_TOP_NW_SE = 1;
    private static final boolean JAVA_FLUID_DIAG = System.getenv("MATTMC_JAVA_FLUID_DIAG") != null;
    private static final int JAVA_FLUID_DIAG_LIMIT = 10_000;
    private static int javaFluidDiagCount;

    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final MutableFloat scratchHeight = new MutableFloat(0);
    private final MutableInt scratchSamples = new MutableInt();

    private final BlockOcclusionCache occlusionCache = new BlockOcclusionCache();

    private final ModelQuadViewMutable quad = new ModelQuad();

    private final LightPipelineProvider lighters;

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];
    private final float[] brightness = new float[4];

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    
    // Iris: From MixinDefaultFluidRenderer - vertex encoder fields
    private int iris$blockId;
    private byte iris$isFluid;
    private byte iris$lightEmission;
    private int iris$localX, iris$localY, iris$localZ;

    public DefaultFluidRenderer(LightPipelineProvider lighters) {
        this.quad.setLightFace(Direction.UP);

        this.lighters = lighters;
    }

    private boolean isFullBlockFluidOccluded(BlockAndTintGetter world, BlockPos pos, Direction dir, BlockState blockState, FluidState fluid) {
        // check if this face of the fluid, assuming a full-block cull shape, is occluded by the block it's in or a neighboring block.
        // it doesn't do a voxel shape comparison with the neighboring blocks since that is already done by isSideExposed
        return !this.occlusionCache.shouldDrawFullBlockFluidSide(blockState, world, pos, dir, fluid, Shapes.block());
    }

    private boolean isSideExposed(BlockAndTintGetter world, int x, int y, int z, Direction dir, float height) {
        BlockPos pos = this.scratchPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
        BlockState blockState = world.getBlockState(pos);

        if (blockState.canOcclude()) {
            VoxelShape shape = blockState.getOcclusionShape();

            // Hoist these checks to avoid allocating the shape below
            if (shape.isEmpty()) {
                return true;
            }

            VoxelShape threshold = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);

            return !Shapes.blockOccludes(threshold, shape, dir);
        }

        return true;
    }

    public void render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, TextureAtlasSprite[] sprites) {
        int posX = blockPos.getX();
        int posY = blockPos.getY();
        int posZ = blockPos.getZ();

        Fluid fluid = fluidState.getType();

        boolean cullUp = this.isFullBlockFluidOccluded(level, blockPos, Direction.UP, blockState, fluidState);
        boolean cullDown = this.isFullBlockFluidOccluded(level, blockPos, Direction.DOWN, blockState, fluidState) ||
                !this.isSideExposed(level, posX, posY, posZ, Direction.DOWN, 0.8888889F);
        boolean cullNorth = this.isFullBlockFluidOccluded(level, blockPos, Direction.NORTH, blockState, fluidState);
        boolean cullSouth = this.isFullBlockFluidOccluded(level, blockPos, Direction.SOUTH, blockState, fluidState);
        boolean cullWest = this.isFullBlockFluidOccluded(level, blockPos, Direction.WEST, blockState, fluidState);
        boolean cullEast = this.isFullBlockFluidOccluded(level, blockPos, Direction.EAST, blockState, fluidState);

        // stop rendering if all faces of the fluid are occluded
        if (cullUp && cullDown && cullEast && cullWest && cullNorth && cullSouth) {
            return;
        }

        boolean isWater = fluidState.is(FluidTags.WATER);

        float fluidHeight = this.fluidHeight(level, fluid, blockPos, Direction.UP);
        float northWestHeight, southWestHeight, southEastHeight, northEastHeight;
        if (fluidHeight >= 1.0f) {
            northWestHeight = 1.0f;
            southWestHeight = 1.0f;
            southEastHeight = 1.0f;
            northEastHeight = 1.0f;
        } else {
            var scratchPos = new BlockPos.MutableBlockPos();
            float heightNorth = this.fluidHeight(level, fluid, scratchPos.setWithOffset(blockPos, Direction.NORTH), Direction.NORTH);
            float heightSouth = this.fluidHeight(level, fluid, scratchPos.setWithOffset(blockPos, Direction.SOUTH), Direction.SOUTH);
            float heightEast = this.fluidHeight(level, fluid, scratchPos.setWithOffset(blockPos, Direction.EAST), Direction.EAST);
            float heightWest = this.fluidHeight(level, fluid, scratchPos.setWithOffset(blockPos, Direction.WEST), Direction.WEST);
            northWestHeight = this.fluidCornerHeight(level, fluid, fluidHeight, heightNorth, heightWest, scratchPos.set(blockPos)
                    .move(Direction.NORTH)
                    .move(Direction.WEST));
            southWestHeight = this.fluidCornerHeight(level, fluid, fluidHeight, heightSouth, heightWest, scratchPos.set(blockPos)
                    .move(Direction.SOUTH)
                    .move(Direction.WEST));
            southEastHeight = this.fluidCornerHeight(level, fluid, fluidHeight, heightSouth, heightEast, scratchPos.set(blockPos)
                    .move(Direction.SOUTH)
                    .move(Direction.EAST));
            northEastHeight = this.fluidCornerHeight(level, fluid, fluidHeight, heightNorth, heightEast, scratchPos.set(blockPos)
                    .move(Direction.NORTH)
                    .move(Direction.EAST));
        }
        float yOffset = cullDown ? 0.0F : EPSILON;

        final ModelQuadViewMutable quad = this.quad;

        LightMode lightMode = isWater && Minecraft.useAmbientOcclusion() ? LightMode.SMOOTH : LightMode.FLAT;
        LightPipeline lighter = this.lighters.getLighter(lightMode);

        quad.setFlags(0);

        if (!cullUp && this.isSideExposed(level, posX, posY, posZ, Direction.UP, Math.min(Math.min(northWestHeight, southWestHeight), Math.min(southEastHeight, northEastHeight)))) {
            northWestHeight -= EPSILON;
            southWestHeight -= EPSILON;
            southEastHeight -= EPSILON;
            northEastHeight -= EPSILON;

            Vec3 velocity = fluidState.getFlow(level, blockPos);

            TextureAtlasSprite sprite;
            float u1, u2, u3, u4;
            float v1, v2, v3, v4;

            if (velocity.x == 0.0D && velocity.z == 0.0D) {
                sprite = sprites[0];
                u1 = sprite.getU(0.0f);
                v1 = sprite.getV(0.0f);
                u2 = u1;
                v2 = sprite.getV(1.0f);
                u3 = sprite.getU(1.0f);
                v3 = v2;
                u4 = u3;
                v4 = v1;
            } else {
                sprite = sprites[1];
                float dir = (float) Mth.atan2(velocity.z, velocity.x) - (1.5707964f);
                float sin = Mth.sin(dir) * 0.25F;
                float cos = Mth.cos(dir) * 0.25F;
                u1 = sprite.getU(0.5F + (-cos - sin));
                v1 = sprite.getV(0.5F + -cos + sin);
                u2 = sprite.getU(0.5F + -cos + sin);
                v2 = sprite.getV(0.5F + cos + sin);
                u3 = sprite.getU(0.5F + cos + sin);
                v3 = sprite.getV(0.5F + (cos - sin));
                u4 = sprite.getU(0.5F + (cos - sin));
                v4 = sprite.getV(0.5F + (-cos - sin));
            }

            float uAvg = (u1 + u2 + u3 + u4) / 4.0F;
            float vAvg = (v1 + v2 + v3 + v4) / 4.0F;
            float s3 = sprites[0].uvShrinkRatio();

            u1 = Mth.lerp(s3, u1, uAvg);
            u2 = Mth.lerp(s3, u2, uAvg);
            u3 = Mth.lerp(s3, u3, uAvg);
            u4 = Mth.lerp(s3, u4, uAvg);
            v1 = Mth.lerp(s3, v1, vAvg);
            v2 = Mth.lerp(s3, v2, vAvg);
            v3 = Mth.lerp(s3, v3, vAvg);
            v4 = Mth.lerp(s3, v4, vAvg);

            quad.setSprite(sprite);

            // top surface alignedness is calculated with a more relaxed epsilon
            boolean aligned = isAlignedEquals(northEastHeight, northWestHeight)
                    && isAlignedEquals(northWestHeight, southEastHeight)
                    && isAlignedEquals(southEastHeight, southWestHeight)
                    && isAlignedEquals(southWestHeight, northEastHeight);

            boolean creaseNorthEastSouthWest = aligned
                    || northEastHeight > northWestHeight && northEastHeight > southEastHeight
                    || northEastHeight < northWestHeight && northEastHeight < southEastHeight
                    || southWestHeight > northWestHeight && southWestHeight > southEastHeight
                    || southWestHeight < northWestHeight && southWestHeight < southEastHeight;

            if (creaseNorthEastSouthWest) {
                setVertex(quad, 1, 0.0f, northWestHeight, 0.0f, u1, v1);
                setVertex(quad, 2, 0.0f, southWestHeight, 1.0F, u2, v2);
                setVertex(quad, 3, 1.0F, southEastHeight, 1.0F, u3, v3);
                setVertex(quad, 0, 1.0F, northEastHeight, 0.0f, u4, v4);
            } else {
                setVertex(quad, 0, 0.0f, northWestHeight, 0.0f, u1, v1);
                setVertex(quad, 1, 0.0f, southWestHeight, 1.0F, u2, v2);
                setVertex(quad, 2, 1.0F, southEastHeight, 1.0F, u3, v3);
                setVertex(quad, 3, 1.0F, northEastHeight, 0.0f, u4, v4);
            }

            this.updateQuad(quad, level, blockPos, lighter, Direction.UP, ModelQuadFacing.POS_Y, 1.0F, colorProvider, fluidState);
            this.logJavaFluidDiag(offset, material, aligned ? ModelQuadFacing.POS_Y : ModelQuadFacing.UNASSIGNED,
                    false, creaseNorthEastSouthWest ? FLUID_FACE_TOP_NE_SW : FLUID_FACE_TOP_NW_SE, 0.0F,
                    northWestHeight, southWestHeight, southEastHeight, northEastHeight);
            this.writeQuad(meshBuilder, collector, material, offset, quad, aligned ? ModelQuadFacing.POS_Y : ModelQuadFacing.UNASSIGNED, false);

            if (fluidState.shouldRenderBackwardUpFace(level, this.scratchPos.set(posX, posY + 1, posZ))) {
                this.logJavaFluidDiag(offset, material, aligned ? ModelQuadFacing.NEG_Y : ModelQuadFacing.UNASSIGNED,
                        true, creaseNorthEastSouthWest ? FLUID_FACE_TOP_NE_SW : FLUID_FACE_TOP_NW_SE, 0.0F,
                        northWestHeight, southWestHeight, southEastHeight, northEastHeight);
                this.writeQuad(meshBuilder, collector, material, offset, quad,
                        aligned ? ModelQuadFacing.NEG_Y : ModelQuadFacing.UNASSIGNED, true);
            }
        }

        if (!cullDown) {
            TextureAtlasSprite sprite = sprites[0];

            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            quad.setSprite(sprite);

            setVertex(quad, 0, 0.0f, yOffset, 1.0F, minU, maxV);
            setVertex(quad, 1, 0.0f, yOffset, 0.0f, minU, minV);
            setVertex(quad, 2, 1.0F, yOffset, 0.0f, maxU, minV);
            setVertex(quad, 3, 1.0F, yOffset, 1.0F, maxU, maxV);

            this.updateQuad(quad, level, blockPos, lighter, Direction.DOWN, ModelQuadFacing.NEG_Y, 1.0F, colorProvider, fluidState);
            this.writeQuad(meshBuilder, collector, material, offset, quad, ModelQuadFacing.NEG_Y, false);
        }

        quad.setFlags(ModelQuadFlags.IS_PARALLEL | ModelQuadFlags.IS_ALIGNED);

        for (Direction dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            float c1;
            float c2;
            float x1;
            float z1;
            float x2;
            float z2;

            switch (dir) {
                case NORTH -> {
                    if (cullNorth) {
                        continue;
                    }
                    c1 = northWestHeight;
                    c2 = northEastHeight;
                    x1 = 0.0f;
                    x2 = 1.0F;
                    z1 = EPSILON;
                    z2 = z1;
                }
                case SOUTH -> {
                    if (cullSouth) {
                        continue;
                    }
                    c1 = southEastHeight;
                    c2 = southWestHeight;
                    x1 = 1.0F;
                    x2 = 0.0f;
                    z1 = 1.0f - EPSILON;
                    z2 = z1;
                }
                case WEST -> {
                    if (cullWest) {
                        continue;
                    }
                    c1 = southWestHeight;
                    c2 = northWestHeight;
                    x1 = EPSILON;
                    x2 = x1;
                    z1 = 1.0F;
                    z2 = 0.0f;
                }
                case EAST -> {
                    if (cullEast) {
                        continue;
                    }
                    c1 = northEastHeight;
                    c2 = southEastHeight;
                    x1 = 1.0f - EPSILON;
                    x2 = x1;
                    z1 = 0.0f;
                    z2 = 1.0F;
                }
                default -> {
                    continue;
                }
            }

            if (this.isSideExposed(level, posX, posY, posZ, dir, Math.max(c1, c2))) {
                int adjX = posX + dir.getStepX();
                int adjY = posY + dir.getStepY();
                int adjZ = posZ + dir.getStepZ();

                TextureAtlasSprite sprite = sprites[1];

                boolean isOverlay = false;

                if (sprites.length > 2 && sprites[2] != null) {
                    BlockPos adjPos = this.scratchPos.set(adjX, adjY, adjZ);
                    BlockState adjBlock = level.getBlockState(adjPos);

                    if (PlatformBlockAccess.getInstance().shouldShowFluidOverlay(adjBlock, level, adjPos, fluidState)) {
                        sprite = sprites[2];
                        isOverlay = true;
                    }
                }

                float u1 = sprite.getU(0.0F);
                float u2 = sprite.getU(0.5F);
                float v1 = sprite.getV((1.0F - c1) * 0.5F);
                float v2 = sprite.getV((1.0F - c2) * 0.5F);
                float v3 = sprite.getV(0.5F);

                quad.setSprite(sprite);

                setVertex(quad, 0, x2, c2, z2, u2, v2);
                setVertex(quad, 1, x2, yOffset, z2, u2, v3);
                setVertex(quad, 2, x1, yOffset, z1, u1, v3);
                setVertex(quad, 3, x1, c1, z1, u1, v1);

                // Iris: From MixinDefaultFluidRenderer - modify brightness for directional shading
                float br = dir.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;
                if (net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldDisableDirectionalShading()) {
                    br = 1.0f;
                }

                ModelQuadFacing facing = ModelQuadFacing.fromDirection(dir);

                this.updateQuad(quad, level, blockPos, lighter, dir, facing, br, colorProvider, fluidState);
                this.writeQuad(meshBuilder, collector, material, offset, quad, facing, false);

                if (!isOverlay) {
                    this.writeQuad(meshBuilder, collector, material, offset, quad, facing.getOpposite(), true);
                }

            }
        }
    }

    private static boolean isAlignedEquals(float a, float b) {
        return Math.abs(a - b) <= ALIGNED_EQUALS_EPSILON;
    }

    private void updateQuad(ModelQuadViewMutable quad, LevelSlice level, BlockPos pos, LightPipeline lighter, Direction dir, ModelQuadFacing facing, float brightness,
                            ColorProvider<FluidState> colorProvider, FluidState fluidState) {

        int normal;
        if (facing.isAligned()) {
            normal = facing.getPackedAlignedNormal();
        } else {
            normal = quad.calculateNormal();
        }

        quad.setFaceNormal(normal);

        QuadLightData light = this.quadLightData;

        lighter.calculate(quad, pos, light, null, dir, false, false);

        colorProvider.getColors(level, pos, scratchPos, fluidState, quad, this.quadColors, level.hasBiomeBlend());

        // multiply the per-vertex color against the combined brightness
        // the combined brightness is the per-vertex brightness multiplied by the block's brightness
        for (int i = 0; i < 4; i++) {
            this.quadColors[i] = ColorARGB.toABGR(this.quadColors[i]);
            this.brightness[i] = light.br[i] * brightness;
        }
    }

    private void writeQuad(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material, BlockPos offset, ModelQuadView quad,
                           ModelQuadFacing facing, boolean flip) {
        var vertices = this.vertices;

        for (int i = 0; i < 4; i++) {
            var out = vertices[flip ? (3 - i + 1) & 0b11 : i];
            out.x = offset.getX() + quad.getX(i);
            // Iris: From MixinDefaultFluidRenderer - set vertex data after x is set
            ((net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension) out).iris$setData(iris$lightEmission, iris$isFluid, iris$blockId, iris$localX, iris$localY, iris$localZ);
            out.y = offset.getY() + quad.getY(i);
            out.z = offset.getZ() + quad.getZ(i);

            out.color = this.quadColors[i];
            out.ao = this.brightness[i];
            out.u = quad.getTexU(i);
            out.v = quad.getTexV(i);
            out.light = this.quadLightData.lm[i];
        }

        TextureAtlasSprite sprite = quad.getSprite();

        if (sprite != null) {
            builder.addSprite(sprite);
        }

        if (material.isTranslucent() && collector != null) {
            int normal;

            if (facing.isAligned()) {
                normal = facing.getPackedAlignedNormal();
            } else {
                // This was updated earlier in updateQuad. There is no situation where the normal vector should have changed.
                normal = quad.getFaceNormal();
            }

            if (flip) {
                normal = NormI8.flipPacked(normal);
            }

            // discard the quad if it's invalid (i.e. not visible)
            if (collector.appendQuad(vertices, facing, normal)) {
                return;
            }
        }

        var vertexBuffer = builder.getVertexBuffer(facing);
        vertexBuffer.push(vertices, material);
    }

    private void logJavaFluidDiag(BlockPos offset, Material material, ModelQuadFacing facing, boolean flip,
                                  int faceKind, float yOffset, float height0, float height1, float height2,
                                  float height3) {
        if (!JAVA_FLUID_DIAG || javaFluidDiagCount >= JAVA_FLUID_DIAG_LIMIT || !shouldLogFluidDiag(this.iris$localX,
                this.iris$localY, this.iris$localZ)) {
            return;
        }
        if (!fluidDiagTargetMatches(offset)) {
            return;
        }

        int index = javaFluidDiagCount++;
        int[] encodedTextures = encodedFluidDiagTextures(flip);
        System.out.printf("MATTMC_JAVA_FLUID_DIAG #%d pos=%d,%d,%d blockId=%d renderType=%d material=%d facing=%s flip=%s face=%d origin=%d,%d,%d yOffset=%.4f heights=%.4f,%.4f,%.4f,%.4f uv0=%.5f,%.5f uv1=%.5f,%.5f uv2=%.5f,%.5f uv3=%.5f,%.5f tex=0x%08x,0x%08x,0x%08x,0x%08x color0=0x%08x ao0=%.4f light=0x%08x,0x%08x,0x%08x,0x%08x%n",
                index, this.iris$localX, this.iris$localY, this.iris$localZ, this.iris$blockId,
                this.iris$isFluid, material.bits(), facing, flip, faceKind, offset.getX(), offset.getY(),
                offset.getZ(), yOffset, height0, height1, height2, height3,
                this.quad.getTexU(0), this.quad.getTexV(0), this.quad.getTexU(1), this.quad.getTexV(1),
                this.quad.getTexU(2), this.quad.getTexV(2), this.quad.getTexU(3), this.quad.getTexV(3),
                encodedTextures[0], encodedTextures[1], encodedTextures[2], encodedTextures[3],
                this.quadColors[0], this.brightness[0], this.quadLightData.lm[0], this.quadLightData.lm[1],
                this.quadLightData.lm[2], this.quadLightData.lm[3]);
    }

    private int[] encodedFluidDiagTextures(boolean flip) {
        float uCenter = (this.quad.getTexU(0) + this.quad.getTexU(1) + this.quad.getTexU(2) + this.quad.getTexU(3)) * 0.25f;
        float vCenter = (this.quad.getTexV(0) + this.quad.getTexV(1) + this.quad.getTexV(2) + this.quad.getTexV(3)) * 0.25f;
        int[] output = new int[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            output[vertex] = packDiagTexture(encodeDiagTexture(uCenter, this.quad.getTexU(vertex)),
                    encodeDiagTexture(vCenter, this.quad.getTexV(vertex)));
        }
        if (flip) {
            return new int[] { output[0], output[3], output[2], output[1] };
        }
        return output;
    }

    private static int encodeDiagTexture(float center, float value) {
        int bias = value < center ? 1 : -1;
        int quantized = Math.round(value * (1 << 15)) + bias;
        return (quantized & 0x7fff) | ((bias >>> 31) << 15);
    }

    private static int packDiagTexture(int u, int v) {
        return (u & 0xffff) | ((v & 0xffff) << 16);
    }

    private static boolean shouldLogFluidDiag(int x, int y, int z) {
        if (System.getenv("MATTMC_FLUID_DIAG_REPLAY") != null) {
            return x >= 0 && x <= 15 && z >= 0 && z <= 15 && (y >= 0 && y <= 15 || y >= 64 && y <= 79);
        }

        return x >= 0 && x <= 160 && y >= 60 && y <= 72 && z >= 360 && z <= 660;
    }

    private static boolean fluidDiagTargetMatches(BlockPos offset) {
        String target = System.getenv("MATTMC_FLUID_DIAG_TARGET");
        if (target == null || target.isBlank()) {
            return true;
        }

        String[] parts = target.split(",");
        if (parts.length != 3) {
            return true;
        }

        try {
            return offset.getX() == Integer.parseInt(parts[0].trim()) &&
                    offset.getY() == Integer.parseInt(parts[1].trim()) &&
                    offset.getZ() == Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private static void setVertex(ModelQuadViewMutable quad, int i, float x, float y, float z, float u, float v) {
        quad.setX(i, x);
        quad.setY(i, y);
        quad.setZ(i, z);
        quad.setTexU(i, u);
        quad.setTexV(i, v);
    }

    private float fluidCornerHeight(BlockAndTintGetter world, Fluid fluid, float fluidHeight, float fluidHeightX, float fluidHeightY, BlockPos blockPos) {
        if (fluidHeightY >= 1.0f || fluidHeightX >= 1.0f) {
            return 1.0f;
        }

        if (fluidHeightY > 0.0f || fluidHeightX > 0.0f) {
            float height = this.fluidHeight(world, fluid, blockPos, Direction.UP);

            if (height >= 1.0f) {
                return 1.0f;
            }

            this.modifyHeight(this.scratchHeight, this.scratchSamples, height);
        }

        this.modifyHeight(this.scratchHeight, this.scratchSamples, fluidHeight);
        this.modifyHeight(this.scratchHeight, this.scratchSamples, fluidHeightY);
        this.modifyHeight(this.scratchHeight, this.scratchSamples, fluidHeightX);

        float result = this.scratchHeight.floatValue() / this.scratchSamples.intValue();
        this.scratchHeight.setValue(0);
        this.scratchSamples.setValue(0);

        return result;
    }

    private void modifyHeight(MutableFloat totalHeight, MutableInt samples, float target) {
        if (target >= 0.8f) {
            totalHeight.add(target * 10.0f);
            samples.add(10);
        } else if (target >= 0.0f) {
            totalHeight.add(target);
            samples.increment();
        }
    }

    private float fluidHeight(BlockAndTintGetter world, Fluid fluid, BlockPos blockPos, Direction direction) {
        BlockState blockState = world.getBlockState(blockPos);
        FluidState fluidState = blockState.getFluidState();

        if (fluid.isSame(fluidState.getType())) {
            FluidState fluidStateUp = world.getFluidState(blockPos.above());

            if (fluid.isSame(fluidStateUp.getType())) {
                return 1.0f;
            } else {
                return fluidState.getOwnHeight();
            }
        }
        if (!blockState.isSolid()) {
            return 0.0f;
        }
        return -1.0f;
    }
    
    // Iris: From MixinDefaultFluidRenderer - VertexEncoderInterface implementation
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
