import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.sodium.client.model.light.data.LightDataAccess;
import net.sodium.client.services.PlatformBlockAccess;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

/** Independent baseline recording; compiled outside production and Frozen. */
public final class GenerateFrozenTerrainLightOracle {
    private static final class World implements BlockAndTintGetter {
        BlockState state;
        int block, sky;
        public int getBrightness(LightLayer type, BlockPos pos) { return type == LightLayer.BLOCK ? block : sky; }
        public BlockState getBlockState(BlockPos pos) { return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState(); }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
        public float getShade(Direction direction, boolean shade) { return 1; }
        public LevelLightEngine getLightEngine() { throw new AssertionError("Unexpected engine query"); }
        public int getBlockTint(BlockPos pos, ColorResolver resolver) { throw new AssertionError("Unexpected tint query"); }
    }
    private static final class Access extends LightDataAccess {
        Access(World world) { this.level = world; }
        public int get(int x, int y, int z) { return compute(x, y, z); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected oracle output");
        try (var source = LightDataAccess.class.getResourceAsStream("LightDataAccess.class")) {
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()));
            if (!hash.equals("1e2887937e92d9fb3da3531ed9a4d32a05a36e860e5d0e12d500bc6bf072167a"))
                throw new IllegalStateException("Not recorded Frozen LightDataAccess: " + hash);
        }
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        World world = new World(); Access access = new Access(world);
        int[][] values = {{0,0},{15,15},{16,31},{255,-1},{-1,255},{Integer.MIN_VALUE,Integer.MAX_VALUE},{0xffff,0x10000},{7,11}};
        int rows = 0;
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x4c574f52); out.writeInt(Block.BLOCK_STATE_REGISTRY.size()*values.length);
            for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
                world.state = state;
                for (int variant = 0; variant < values.length; variant++) {
                    WorldRenderingSettings.INSTANCE.setAmbientOcclusionLevel(variant % 3 * .5f);
                    world.block = values[variant][0]; world.sky = values[variant][1];
                    // The expectation comes from the actual Frozen production consumer.
                    int expected = access.get(0,0,0);
                    boolean em = state.emissiveRendering(world, BlockPos.ZERO);
                    boolean op = state.isViewBlocking(world, BlockPos.ZERO) && state.getLightBlock()!=0;
                    boolean fo = state.isSolidRender(), fc = state.isCollisionShapeFullBlock(world, BlockPos.ZERO);
                    int lu = PlatformBlockAccess.getInstance().getLightEmission(state, world, BlockPos.ZERO);
                    boolean second = !fo || lu != 0 ? !em && state.emissiveRendering(world, BlockPos.ZERO) : false;
                    float ao = lu == 0 ? state.getShadeBrightness(world, BlockPos.ZERO) : 1;
                    out.writeInt(Block.getId(state)); out.writeInt(variant);
                    out.writeInt((em?1:0)|(op?2:0)|(fo?4:0)|(fc?8:0)|(second?16:0));
                    out.writeInt(lu); out.writeFloat(ao); out.writeInt(state.getLightEmission());
                    out.writeInt(world.block); out.writeInt(world.sky); out.writeInt(expected); rows++;
                }
            }
        }
        System.out.println("Recorded Frozen production terrain light rows=" + rows);
    }
}
