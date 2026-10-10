import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import net.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;

/** Candidate-only reference recorder: actual unchanged Frozen methods. */
public final class GenerateFrozenFacePolicyOracle {
    static String hash(Class<?> type) throws Exception {
        try(var input=type.getResourceAsStream(type.getSimpleName()+".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
    static final class World implements BlockGetter {
        BlockState neighbor;
        public BlockState getBlockState(BlockPos p) { return neighbor; }
        public FluidState getFluidState(BlockPos p) { return neighbor.getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos p) { return null; }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("output and expected Frozen cache class hash");
        String actual=hash(BlockOcclusionCache.class);
        if(!actual.equals(args[1])) throw new IllegalStateException("Not Frozen cache: "+actual);
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var states=new ArrayList<BlockState>();
        var shapes=new ArrayList<VoxelShape>(); var ids=new IdentityHashMap<VoxelShape,Integer>();
        ids.put(Shapes.empty(),0);shapes.add(Shapes.empty());ids.put(Shapes.block(),1);shapes.add(Shapes.block());
        for(BlockState state:Block.BLOCK_STATE_REGISTRY) {
            states.add(state);
            for(Direction direction:Direction.values()) {
                var shape=state.getFaceOcclusionShape(direction);
                if(!ids.containsKey(shape)){ids.put(shape,shapes.size());shapes.add(shape);}
            }
        }
        Block[] special={Blocks.IRON_BARS,Blocks.GLASS,Blocks.GLASS_PANE,Blocks.OAK_LEAVES,
            Blocks.MANGROVE_ROOTS,Blocks.POWDER_SNOW,Blocks.WATER,Blocks.OAK_SLAB,
            Blocks.OAK_STAIRS,Blocks.AIR,Blocks.STONE,Blocks.WHITE_STAINED_GLASS};
        var random=new Random(0x43554c4c46524f5aL);var world=new World();var cache=new BlockOcclusionCache();
        int count=states.size()*24+4096*6;
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x54434f32);out.writeUTF(actual);out.writeUTF(hash(BlockState.class));out.writeUTF(hash(Shapes.class));
            out.writeUTF("Bootstrap-only Frozen reference; no loaded-world resource tags or hook lifecycle acceptance");
            out.writeInt(shapes.size());
            for(var shape:shapes) {
                out.writeBoolean(shape==Shapes.block());out.writeBoolean(shape.isEmpty());
                var boxes=shape.toAabbs();out.writeInt(boxes.size());
                for(var b:boxes){out.writeDouble(b.minX);out.writeDouble(b.minY);out.writeDouble(b.minZ);out.writeDouble(b.maxX);out.writeDouble(b.maxY);out.writeDouble(b.maxZ);}
            }
            out.writeInt(states.size());
            for(var s:states) {
                out.writeInt(Block.getId(s));out.writeInt(BuiltInRegistries.BLOCK.getId(s.getBlock()));
                out.writeUTF(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());out.writeUTF(s.getBlock().getClass().getName());
                var block=s.getBlock();
                out.writeByte(block instanceof IronBarsBlock?3:block instanceof MangroveRootsBlock?4:
                    block instanceof LiquidBlock?5:block instanceof LeavesBlock?6:
                    block instanceof PowderSnowBlock?2:block instanceof HalfTransparentBlock?1:0);
                var fluid=s.getFluidState().getType();
                out.writeByte(fluid.isSame(net.minecraft.world.level.material.Fluids.WATER)?1:
                    fluid.isSame(net.minecraft.world.level.material.Fluids.LAVA)?2:0);
                out.writeBoolean(s.canOcclude());out.writeBoolean(s.is(BlockTags.BARS));
                int connections=0;
                for(Direction d:Direction.values()) {
                    var p=IronBarsBlock.PROPERTY_BY_DIRECTION.get(d);
                    if(p!=null&&s.hasProperty(p)&&s.getValue(p))connections|=1<<d.get3DDataValue();
                }
                out.writeInt(connections);out.writeInt(BuiltInRegistries.FLUID.getId(s.getFluidState().getType()));
                for(Direction d:Direction.values())out.writeInt(ids.get(s.getFaceOcclusionShape(d)));
            }
            out.writeInt(count);
            for(var self:states)for(int mode=0;mode<4;mode++) {
                var other=mode==0?self:mode==1?states.get(random.nextInt(states.size())):
                    special[(Block.getId(self)+mode)%special.length].defaultBlockState();
                write(out,cache,world,self,other);
            }
            for(int i=0;i<4096;i++)write(out,cache,world,
                special[random.nextInt(special.length)].defaultBlockState(),
                special[random.nextInt(special.length)].defaultBlockState());
            var geometry=new LinkedHashMap<List<Long>,Integer>();
            for(int i=0;i<shapes.size();i++) {
                var shape=shapes.get(i);var key=new ArrayList<Long>();
                key.add(shape==Shapes.block()?1L:0L);key.add(shape.isEmpty()?1L:0L);
                for(var box:shape.toAabbs())for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})key.add(Double.doubleToRawLongBits(value));
                geometry.putIfAbsent(key,i);
            }
            out.writeInt(geometry.size()*geometry.size());
            for(int a:geometry.values())for(int b:geometry.values()) {
                out.writeInt(a);out.writeInt(b);
                out.writeBoolean(Shapes.joinIsNotEmpty(shapes.get(a),shapes.get(b),BooleanOp.ONLY_FIRST));
            }
            System.out.println("Recorded exact Frozen shape pairs="+(geometry.size()*geometry.size()));
        }
        System.out.println("Recorded actual Frozen culling states="+states.size()+" shapes="+shapes.size()+" queries="+count);
    }
    static void write(DataOutputStream out,BlockOcclusionCache cache,World world,BlockState self,BlockState other) throws Exception {
        world.neighbor=other;
        for(Direction d:Direction.values()) {
            out.writeInt(Block.getId(self));out.writeInt(Block.getId(other));out.writeInt(d.get3DDataValue());
            out.writeBoolean(self.skipRendering(other,d));
            out.writeBoolean(cache.shouldDrawSide(self,world,BlockPos.ZERO,d));
        }
    }
}
