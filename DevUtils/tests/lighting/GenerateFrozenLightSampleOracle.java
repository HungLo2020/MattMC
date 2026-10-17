package net.minecraft.world.level.lighting;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.*;

/** Record actual Frozen world light consumers; never reproduce their expected algorithm. */
public final class GenerateFrozenLightSampleOracle {
    static void verify(Class<?> type, String expected) throws Exception {
        try (var input = type.getResourceAsStream(type.getSimpleName()+".class")) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            if (!hash.equals(expected)) throw new IllegalStateException("Not Frozen "+type+": "+hash);
        }
    }
    public static void main(String[] args) throws Exception {
        verify(BlockLightSectionStorage.class,"d8c3b6ffcf90ce7042318b6c3fbd8979fc691bffbca4c717703d186eb6eee0f5");
        verify(SkyLightSectionStorage.class,"f76ee1e1c0bbabe4eecb1c4deef21ba6110ae2969e358a323f6f92d0850d8828");
        verify(LayerLightSectionStorage.class,"31076b253b190711c0516653cb0a85718a7b96ea1af0883ed3e837720c051539");
        verify(DataLayer.class,"fa57ffc1904d3844c390aeccff1cdfe8f88f7f3b1fa62b15a1d1e59b31b3f795");
        var getter = new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x,int z){ return null; }
            public net.minecraft.world.level.BlockGetter getLevel(){ return EmptyBlockGetter.INSTANCE; }
        };
        var random = new Random(0x4c53414d504c4553L);
        int[] defaults={0,3,15,31,-1,-9,Integer.MIN_VALUE,Integer.MAX_VALUE};
        try (var out = new DataOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            out.writeInt(0x4c535031); out.writeInt(1024);
            for (int row=0;row<1024;row++) {
                int kind=row&1, mode=(row>>1)%6, initial=defaults[(row>>4)%defaults.length];
                boolean allocated=(row&8)!=0, updating=(row&4)!=0, enabled=(row&16)!=0;
                long block=row%31==0?BlockPos.asLong(33554431,-1,-1):BlockPos.asLong(random.nextInt(),random.nextInt(),random.nextInt());
                long key=SectionPos.blockToSection(block),column=SectionPos.getZeroNode(key);
                var layer=new DataLayer(initial);
                if (allocated) { byte[] bytes=new byte[2048];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*31+row);layer=new DataLayer(bytes); }
                int result;
                if (kind==0) {
                    var storage=new BlockLightSectionStorage(getter);
                    if (mode!=0) storage.updatingSectionData.setLayer(key,layer);
                    storage.changedSections.add(key);storage.swapSectionMap();
                    result=storage.getLightValue(block);
                } else {
                    var storage=new SkyLightSectionStorage(getter);
                    var map=storage.updatingSectionData;
                    int y=SectionPos.y(key);map.currentLowestY=y-4;map.topSections.defaultReturnValue(y-4);
                    int top=mode==0?y-4:mode==1?y:y+(mode==2?1:5);
                    map.topSections.put(column,top);
                    if (mode==2) map.setLayer(key,layer);
                    else if (mode==3||mode==5) {
                        map.setLayer(SectionPos.offset(key,0,mode==3?2:3,0),layer);
                        if (mode==5) map.setLayer(key,null);
                    }
                    storage.setLightEnabled(column,enabled);
                    storage.changedSections.add(key);storage.swapSectionMap();
                    result=storage.getLightValue(block,updating);
                }
                out.writeInt(kind);out.writeInt(mode);out.writeLong(block);out.writeInt(initial);
                out.writeInt(allocated?1:0);out.writeInt(updating?1:0);out.writeInt(enabled?1:0);out.writeInt(result);
            }
        }
        System.out.println("Recorded1024 actual Frozen block/sky scalar consumer cases");
    }
}
