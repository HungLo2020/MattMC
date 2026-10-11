package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling.ChunkCompoundTagParser;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.chunk.NativeDhLighting;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("parity")
class NativeDhSavedLightingTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static byte[] array(DataInputStream in) throws IOException { int n=in.readInt();return n<0?null:in.readNBytes(n); }
    private static void compare(DataInputStream in,ChunkLightStorage storage,NativeDhLighting.Field field,boolean sky,int min,int count) throws IOException {
        boolean empty=in.readBoolean();assertEquals(empty,storage.isEmpty());
        if(field!=null)assertEquals(!empty,field.hasAllocatedSections(sky));
        for(int s=0;s<count;s++) {
            boolean allocated=in.readBoolean();var v=storage.lightSections==null?null:storage.lightSections[s];assertEquals(allocated,v!=null);
            if(allocated) { assertEquals(in.readByte(),v.constantValue);assertEquals(in.readBoolean(),v.data!=null);for(int n=0;n<16;n++)assertEquals(in.readShort(),v.counts[n]); }
        }
        for(int y=min-1;y<=min+count*16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int expected=in.readUnsignedByte();assertEquals(expected,storage.get(x,y,z));
            if(field!=null)assertEquals(expected,field.light(x,y,z,sky));
        }
    }
    @Test void actualFrozenSavedImportsAndMutableClearEditPolicies() throws Exception {
        int nativeCases=0;
        try(var in=new DataInputStream(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/world/lighting/frozen-dh-saved-light.bin.gz"))))) {
            assertEquals(0x44484931,in.readInt());assertEquals(7,in.readInt());
            for(int i=0;i<7;i++){in.readUTF();assertEquals(64,in.readUTF().length());}
            assertEquals(64,in.readInt());
            for(int c=0;c<64;c++) {
                assertEquals(c,in.readInt());int min=in.readInt(),count=in.readInt(),kind=in.readInt(),rows=in.readInt();
                var tag=new CompoundTag();var list=new ListTag();boolean canonical=kind==0;
                for(int i=0;i<rows;i++) {
                    byte[] block=array(in),sky=array(in);var row=new CompoundTag();row.putByte("Y",(byte)(31-i*7));
                    if(block!=null){row.putByteArray("BlockLight",block);if(block.length>0&&block.length<2048)canonical=false;}
                    if(sky!=null){row.putByteArray("SkyLight",sky);if(sky.length>0&&sky.length<2048)canonical=false;}
                    list.add(row);
                }
                if(kind==0)tag.put("sections",list);
                else if(kind==2)tag.putInt("sections",7);
                else if(kind==3){var wrong=new ListTag();wrong.add(IntTag.valueOf(8));tag.put("sections",wrong);}
                else if(kind==4)tag=null;
                String error=in.readUTF();boolean returned=in.readBoolean();var chunk=HeightmapFixtures.chunk("empty",min,count*16,0);
                ChunkCompoundTagParser.LoadedChunkLighting result=null;String actual="";
                try { result=ChunkCompoundTagParser.readLightingForGeneration(chunk,tag); }catch(RuntimeException e){actual=e.getClass().getName();}
                assertEquals(error,actual,"Frozen exception case "+c);assertEquals(returned,result!=null);
                if(result==null)continue;
                var field=result.nativeField();if(canonical){assertNotNull(field,"Actual native import "+c);nativeCases++;assertEquals(-1,field.sourceCount(),"Import must not freeze an empty emitter cache");}
                var mutable=field==null?ChunkCompoundTagParser.readLight(chunk,tag):null;
                var block=field==null?mutable.blockLightStorage:field.copyToMutableStorage(false);
                var sky=field==null?mutable.skyLightStorage:field.copyToMutableStorage(true);
                compare(in,block,field,false,min,count);compare(in,sky,field,true,min,count);
                int retained=field==null?0:field.light(7,min+1,9,true);
                block.clear();sky.clear();block.set(7,min+1,9,3);sky.set(7,min+1,9,11);
                compare(in,block,null,false,min,count);compare(in,sky,null,true,min,count);
                if(field!=null)assertEquals(retained,field.light(7,min+1,9,true),"Mutable reader must not modify native field");
            }
            assertEquals(-1,in.read());
        }
        assertEquals(52,nativeCases);
    }
    @Test void loadedFieldDefersEmitterEnumerationAndFeedsTheNextNativePass() {
        var source=HeightmapFixtures.chunk("empty",-64,32,0);
        source.getSection(0).setBlockState(1,1,1,Blocks.GLOWSTONE.defaultBlockState());
        var tag=new CompoundTag();var rows=new ListTag();
        for(int i=0;i<2;i++){var row=new CompoundTag();row.putByteArray("BlockLight",new byte[0]);row.putByteArray("SkyLight",new byte[0]);rows.add(row);}
        tag.put("sections",rows);var imported=ChunkCompoundTagParser.readLightingForGeneration(source,tag);
        assertNotNull(imported.nativeField());assertEquals(-1,imported.nativeField().sourceCount());
        var wrapper=new ChunkWrapper(source,null);imported.applyTo(wrapper);
        assertSame(imported.nativeField(),wrapper.nativeDhLightingInput().previous());
        source.getSection(0).setBlockState(2,2,2,Blocks.GLOWSTONE.defaultBlockState());
        assertEquals(2,wrapper.getWorldBlockLightPosList().size(),"Enumeration occurs after the edit, not at saved import");
        var nearby=new java.util.ArrayList<com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper>();nearby.add(wrapper);
        assertTrue(NativeDhLighting.tryLight(wrapper,nearby,15,true,true)>=0);
        assertEquals(2,wrapper.nativeDhLightingInput().previous().sourceCount());
        assertEquals(0,imported.nativeField().light(1,-63,1,false),"Rebake does not mutate saved field");
        var direct=new ChunkWrapper(source,null);imported.applyTo(direct);nearby.clear();nearby.add(direct);
        assertTrue(NativeDhLighting.tryLight(direct,nearby,15,true,true)>=0);
        assertEquals(2,direct.nativeDhLightingInput().previous().sourceCount(),"Native pass also performs deferred enumeration");
        var noHeights=new ChunkWrapper(source,null,false);imported.applyTo(noHeights);
        assertNull(noHeights.nativeDhLightingInput());assertTrue(noHeights.isDhBlockLightingCorrect());assertEquals(0,noHeights.getDhBlockLight(1,-63,1));
    }
}
