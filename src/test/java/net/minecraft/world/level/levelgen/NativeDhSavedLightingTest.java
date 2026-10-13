package net.minecraft.world.level.levelgen;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling.ChunkCompoundTagParser;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
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
    /** One recorded light plane: the allocation summary, then every value, which the field must match. */
    private static void compare(DataInputStream in,NativeDhLighting.Field field,boolean sky,int min,int count) throws IOException {
        boolean empty=in.readBoolean();
        if(field!=null)assertEquals(!empty,field.hasAllocatedSections(sky));
        skipSections(in,count);
        for(int y=min-1;y<=min+count*16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int expected=in.readUnsignedByte();
            if(field!=null)assertEquals(expected,field.light(x,y,z,sky),"y "+y+" x "+x+" z "+z);
        }
    }
    /** Java light-storage section details, which no longer exist in production. */
    private static void skipSections(DataInputStream in,int count) throws IOException {
        for(int s=0;s<count;s++) if(in.readBoolean()) { in.readByte();in.readBoolean();for(int n=0;n<16;n++)in.readShort(); }
    }
    @Test void actualFrozenSavedImportsMatchNativeFieldsAndMalformedDataIsNotImported() throws Exception {
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
                var result=ChunkCompoundTagParser.readLightingForGeneration(chunk,tag);
                // Canonical saved light imports natively and matches Frozen; anything
                // else is not imported (DH lights that chunk), where Frozen imported
                // a partial Java copy or threw.
                if(canonical){assertEquals("",error);assertTrue(returned);assertNotNull(result,"Actual native import "+c);nativeCases++;}
                else assertNull(result,"Malformed saved light is not imported "+c);
                if(!returned)continue;
                var field=result==null?null:result.nativeField();
                if(field!=null)assertEquals(-1,field.sourceCount(),"Import must not freeze an empty emitter cache");
                compare(in,field,false,min,count);compare(in,field,true,min,count);
                // Frozen's mutable clear/edit storage policy has no production counterpart.
                compare(in,null,false,min,count);compare(in,null,true,min,count);
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
        // Wrappers that skip the Java height scan still get Rust height fields, so they adopt saved light.
        var saved=new ChunkWrapper(source,null,false);assertNotNull(saved.nativeDhLightingInput());imported.applyTo(saved);
        assertTrue(saved.isDhBlockLightingCorrect());assertSame(imported.nativeField(),saved.nativeDhLightingInput().previous());
    }
}
