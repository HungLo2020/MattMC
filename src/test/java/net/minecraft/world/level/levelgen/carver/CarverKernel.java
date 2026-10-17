package net.minecraft.world.level.levelgen.carver;

import java.util.function.Function;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Aquifer;
import org.apache.commons.lang3.mutable.MutableBoolean;

/** Executes the actual protected caller; only the terminal block operation is
 * replaced with ordered coordinate/column-flag consumption. */
final class CarverKernel {
    long checksum,calls;
    final WorldCarver<CarverConfiguration> nativeCarver=new WorldCarver<>(CarverConfiguration.CODEC.codec()) {
        @Override protected boolean carveBlock(CarvingContext c,CarverConfiguration cfg,ChunkAccess ch,Function<BlockPos,Holder<Biome>> f,CarvingMask m,BlockPos.MutableBlockPos p,BlockPos.MutableBlockPos q,Aquifer a,MutableBoolean b){return consume(p,b);}
        @Override public boolean carve(CarvingContext c,CarverConfiguration cfg,ChunkAccess ch,Function<BlockPos,Holder<Biome>> f,RandomSource r,Aquifer a,ChunkPos p,CarvingMask m){throw new UnsupportedOperationException();}
        @Override public boolean isStartChunk(CarverConfiguration c,RandomSource r){throw new UnsupportedOperationException();}
    };
    final JavaWorldCarver<CarverConfiguration> original=new JavaWorldCarver<>(CarverConfiguration.CODEC.codec()) {
        @Override protected boolean carveBlock(CarvingContext c,CarverConfiguration cfg,ChunkAccess ch,Function<BlockPos,Holder<Biome>> f,CarvingMask m,BlockPos.MutableBlockPos p,BlockPos.MutableBlockPos q,Aquifer a,MutableBoolean b){return consume(p,b);}
        @Override public boolean carve(CarvingContext c,CarverConfiguration cfg,ChunkAccess ch,Function<BlockPos,Holder<Biome>> f,RandomSource r,Aquifer a,ChunkPos p,CarvingMask m){throw new UnsupportedOperationException();}
        @Override public boolean isStartChunk(CarverConfiguration c,RandomSource r){throw new UnsupportedOperationException();}
    };
    boolean consume(BlockPos p,MutableBoolean flag){checksum=checksum*31+p.getX();checksum=checksum*31+p.getY();checksum=checksum*31+p.getZ();checksum=checksum*31+(flag.isTrue()?1:0);flag.setTrue();calls++;return (p.getY()&1)==0;}
    boolean evaluate(boolean nativeMode,CarverGeometryFixtures.Ellipsoid e,ChunkAccess chunk,CarvingMask mask,WorldCarver.CarveSkipChecker nc,JavaWorldCarver.CarveSkipChecker jc){
        return nativeMode?nativeCarver.carveEllipsoid(e.context(),e.config(),chunk,p->CarverFixtures.BIOME,null,e.x(),e.y(),e.z(),e.horizontal(),e.vertical(),mask,nc):
            original.carveEllipsoid(e.context(),e.config(),chunk,p->CarverFixtures.BIOME,null,e.x(),e.y(),e.z(),e.horizontal(),e.vertical(),mask,jc);
    }
}
