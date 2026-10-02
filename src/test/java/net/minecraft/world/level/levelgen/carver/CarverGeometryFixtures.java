package net.minecraft.world.level.levelgen.carver;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;

final class CarverGeometryFixtures {
    record Ellipsoid(CarvingContext context,CarverConfiguration config,ChunkPos chunk,
        double x,double y,double z,double horizontal,double vertical,int kind,double floor,float[] widths) {
        WorldCarver.CarveSkipChecker nativeChecker(){
            return kind==1?(c,x,y,z,blockY)->JavaCarverGeometry.cave(x,y,z,floor):
                NativeCanyonGeometry.canyon(widths,(c,x,y,z,blockY)->JavaCarverGeometry.canyon(c,widths,x,y,z,blockY));
        }
        JavaWorldCarver.CarveSkipChecker originalChecker(){
            return kind==1?(c,x,y,z,blockY)->JavaCarverGeometry.cave(x,y,z,floor):
                (c,x,y,z,blockY)->JavaCarverGeometry.canyon(c,widths,x,y,z,blockY);
        }
        int[] frame() {
            int l=chunk.getMinBlockX(),m=chunk.getMinBlockZ();
            return new int[]{l,m,Math.max(Mth.floor(x-horizontal)-l-1,0),Math.min(Mth.floor(x+horizontal)-l,15),
                Math.max(Mth.floor(y-vertical)-1,context.getMinGenY()+1),
                Math.min(Mth.floor(y+vertical)+1,context.getMinGenY()+context.getGenDepth()-8),
                Math.max(Mth.floor(z-horizontal)-m-1,0),Math.min(Mth.floor(z+horizontal)-m,15),kind,context.getMinGenY()};
        }
        long volume(){var f=frame();return Math.max(0,f[3]-f[2]+1L)*Math.max(0,f[7]-f[6]+1L)*Math.max(0,f[5]-f[4]);}
    }
    static void capture(List<Ellipsoid> target,CarvingContext c,CarverConfiguration config,ChunkAccess chunk,
        double x,double y,double z,double h,double v,JavaWorldCarver.CarveSkipChecker checker,int kind) {
        // Retain the original producer's private numeric samples. These captures
        // are test-only and happen outside benchmarks; no RNG is substituted.
        double floor=0;float[] widths=null;
        try {
            for(var f:checker.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if(f.getType()==double.class)floor=f.getDouble(checker);
                if(f.getType()==float[].class)widths=((float[])f.get(checker)).clone();
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(kind==2&&widths==null)throw new AssertionError("Missing canyon factors");
        var e=new Ellipsoid(c,config,chunk.getPos(),x,y,z,h,v,kind,floor,widths);
        if(e.volume()>0 && Math.abs(x-chunk.getPos().getMiddleBlockX())<=16+h*2 && Math.abs(z-chunk.getPos().getMiddleBlockZ())<=16+h*2)target.add(e);
    }
    static List<Ellipsoid> captured(CarverFixtures.Config config,int limit) {
        var context=CarverFixtures.context(config.type().contains("nether")?0:-64,config.type().contains("nether")?128:384);
        var chunk=new CarverTestChunk(new ChunkPos(0,0),context.getMinGenY(),context.getGenDepth(),false);chunk.recording=false;
        List<Ellipsoid> all=new ArrayList<>();
        JavaWorldCarver carver=config.type().equals("minecraft:canyon")?new JavaCanyonWorldCarver(CanyonCarverConfiguration.CODEC) {
            @Override protected boolean carveEllipsoid(CarvingContext c,CanyonCarverConfiguration cfg,ChunkAccess ch,java.util.function.Function<BlockPos,Holder<Biome>> fn,Aquifer aq,double x,double y,double z,double h,double v,CarvingMask mask,JavaWorldCarver.CarveSkipChecker check) {
                capture(all,c,cfg,ch,x,y,z,h,v,check,2);return false;
            }
        }:config.type().contains("nether")?new JavaNetherWorldCarver(CaveCarverConfiguration.CODEC) {
            @Override protected boolean carveEllipsoid(CarvingContext c,CaveCarverConfiguration cfg,ChunkAccess ch,java.util.function.Function<BlockPos,Holder<Biome>> fn,Aquifer aq,double x,double y,double z,double h,double v,CarvingMask mask,JavaWorldCarver.CarveSkipChecker check) {
                capture(all,c,cfg,ch,x,y,z,h,v,check,1);return false;
            }
        }:new JavaCaveWorldCarver(CaveCarverConfiguration.CODEC) {
            @Override protected boolean carveEllipsoid(CarvingContext c,CaveCarverConfiguration cfg,ChunkAccess ch,java.util.function.Function<BlockPos,Holder<Biome>> fn,Aquifer aq,double x,double y,double z,double h,double v,CarvingMask mask,JavaWorldCarver.CarveSkipChecker check) {
                capture(all,c,cfg,ch,x,y,z,h,v,check,1);return false;
            }
        };
        for(int seed=0;seed<4096&&all.size()<limit*4;seed++) {
            var random=new WorldgenRandom(new XoroshiroRandomSource(seed*0x9e3779b97f4a7c15L));
            carver.carve(context,config.value(),chunk,p->CarverFixtures.BIOME,random,chunk.aquifer(),new ChunkPos(seed%3-1,(seed/3)%3-1),new CarvingMask(context.getGenDepth(),context.getMinGenY()));
        }
        if(all.size()<limit)throw new AssertionError("Insufficient original ellipsoid captures: "+config.name());
        var selected=new ArrayList<Ellipsoid>();for(int i=0;i<limit;i++)selected.add(all.get((int)((long)i*all.size()/limit)));return selected;
    }
}
