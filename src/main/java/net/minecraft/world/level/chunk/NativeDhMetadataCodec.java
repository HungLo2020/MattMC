package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import it.unimi.dsi.fastutil.bytes.ByteList;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;

/** Saved metadata policy runs against primary Rust planes; Java supplies byte IO. */
public final class NativeDhMetadataCodec {
    private static MethodHandle handle(String name,FunctionDescriptor type) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_dh_metadata_"+name,type);
    }
    private static final MethodHandle DECODER=handle("decoder",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle FEED=handle("feed",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    /** Same export, reading a heap slice in place (byte-array streams only). */
    private static final MethodHandle FEED_HEAP=net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_dh_metadata_feed",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT),Linker.Option.critical(true));
    private static final MethodHandle DECODER_RELEASE=handle("decoder_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ENCODER=handle("encoder",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle NEXT=handle("next",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle ENCODER_RELEASE=handle("encoder_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    public static final AtomicLong DECODED=new AtomicLong(),ENCODED=new AtomicLong();
    private NativeDhMetadataCodec() {}
    public static boolean decode(ByteList values,DhDataInputStream stream)throws IOException {
        if(!NativeDhCodec.enabled || !(values instanceof NativeDhMetadataList list))return false;
        return decode(list.root.owner,list.codecKind(),stream,list.root);
    }
    public static boolean decodeUniformity(FullDataSourceV2 source,DhDataInputStream stream)throws IOException {
        if(!NativeDhCodec.enabled || source.getClass()!=FullDataSourceV2.class)return false;
        return decode(source.nativeSource().metadataOwner(),2,stream,source);
    }
    private static boolean decode(MemorySegment metadata,int kind,DhDataInputStream stream,Object lease)throws IOException {
        MemorySegment owner=MemorySegment.NULL;
        try(var arena=Arena.ofConfined()) {
            owner=(MemorySegment)DECODER.invokeExact(metadata,kind);if(owner.address()==0)return false;
            if(stream.codecByteArrayInput()) {
                // The whole decompressed remainder in one call, read in place.
                int position=stream.codecPosition(),count=stream.codecLimit()-position;
                int status=(int)FEED_HEAP.invokeExact(owner,MemorySegment.ofArray(new byte[0]),0);
                if(status==0)status=(int)FEED_HEAP.invokeExact(owner,MemorySegment.ofArray(stream.codecArray()).asSlice(position,count),count);
                stream.codecSeek(position+count);
                if(status==0)throw new java.io.EOFException();
                if(status!=1)throw new IllegalStateException("Invalid DH metadata decode status: "+status);
                DECODED.incrementAndGet();return true;
            }
            var input=new NativeDhCodec.Input();var buffer=arena.allocate(input.bytes.length,1);
            int status=(int)FEED.invokeExact(owner,buffer,0);
            while(status==0) {
                int count=input.read(stream);
                MemorySegment.copy(input.bytes,0,buffer,ValueLayout.JAVA_BYTE,0,count);
                status=(int)FEED.invokeExact(owner,buffer,count);
                if(status==0 && input.pending!=null)throw input.pending;
            }
            if(status!=1)throw new IllegalStateException("Invalid DH metadata decode status: "+status);
            DECODED.incrementAndGet();return true;
        }catch(IOException|RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException("Cannot decode DH metadata",e);}
        finally {if(owner.address()!=0)release(DECODER_RELEASE,owner);Reference.reachabilityFence(lease);}
    }
    public static boolean encode(ByteList values,DhDataOutputStream stream)throws IOException {
        if(!NativeDhCodec.enabled || !(values instanceof NativeDhMetadataList list))return false;
        return encode(list.root.owner,list.codecKind(),false,stream,list.root);
    }
    public static boolean encodeCompression(FullDataSourceV2 source,DhDataOutputStream stream)throws IOException {
        if(!NativeDhCodec.enabled || source.getClass()!=FullDataSourceV2.class)return false;
        return encode(source.nativeSource().metadataOwner(),1,true,stream,source);
    }
    private static boolean encode(MemorySegment metadata,int kind,boolean uniform,DhDataOutputStream stream,Object lease)throws IOException {
        MemorySegment owner=MemorySegment.NULL;
        try(var arena=Arena.ofConfined()) {
            owner=(MemorySegment)ENCODER.invokeExact(metadata,kind,uniform?1:0);if(owner.address()==0)return false;
            var bytes=new byte[16384];var buffer=arena.allocate(bytes.length,1);var result=arena.allocate(16,8);long status;
            do {
                NEXT.invokeExact(owner,buffer,bytes.length,result);status=result.getAtIndex(ValueLayout.JAVA_LONG,0);
                if(status!=0 && status!=1)throw new IllegalStateException("Invalid DH metadata encode status: "+status);
                int count=(int)result.getAtIndex(ValueLayout.JAVA_LONG,1);
                MemorySegment.copy(buffer,ValueLayout.JAVA_BYTE,0,bytes,0,count);stream.write(bytes,0,count);
            }while(status==0);
            ENCODED.incrementAndGet();return true;
        }catch(IOException|RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException("Cannot encode DH metadata",e);}
        finally {if(owner.address()!=0)release(ENCODER_RELEASE,owner);Reference.reachabilityFence(lease);}
    }
    private static void release(MethodHandle handle,MemorySegment owner) {
        try{handle.invokeExact(owner);}catch(Throwable e){throw new IllegalStateException("Cannot release DH metadata codec",e);}
    }
}
