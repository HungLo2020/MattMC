package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.*;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.jetbrains.annotations.Nullable;

/** Synchronous decode scope; Java receives independent primary CPU owners only. */
public final class NativeDecodedChunkSections implements AutoCloseable {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_sections_"+name, descriptor);
    }
    private static final MethodHandle TAKE = handle("take_owned", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("decoded_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ROOT = handle("decoded_root", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle HYDRATE = handle("hydrate", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE_ROWS = handle("hydrated_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle COUNT = handle("hydrated_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle ROW = handle("hydrated_row_primary", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE_ROW = handle("hydrated_row_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final Arena scope;
    private final MemorySegment owner;
    private final PalettedContainerFactory factory;
    private NativeDecodedChunkSections(Arena scope, MemorySegment owner, PalettedContainerFactory factory) {
        this.scope=scope;this.owner=owner;this.factory=factory;
    }

    static NativeDecodedChunkSections take(PalettedContainerFactory factory) {
        var scope=Arena.ofConfined();MemorySegment pointer=MemorySegment.NULL;boolean adopted=false;
        try {
            pointer=(MemorySegment)TAKE.invokeExact();
            if(pointer.address()==0) throw new IllegalStateException("Native chunk decode lost its result");
            var owned=pointer.asReadOnly().reinterpret(1,scope,p->release(RELEASE,p));adopted=true;
            return new NativeDecodedChunkSections(scope,owned,factory);
        } catch(Throwable failure) {
            if(!adopted&&pointer.address()!=0) release(RELEASE,pointer);
            scope.close();throw failure(failure);
        }
    }
    public byte[] root() {
        try {
            var size=scope.allocate(ValueLayout.JAVA_LONG);
            var pointer=(MemorySegment)ROOT.invokeExact(owner,size);
            long length=size.get(ValueLayout.JAVA_LONG,0);
            if(pointer.address()==0||length<0||length>Integer.MAX_VALUE) throw new IllegalStateException("Invalid native chunk root");
            return pointer.asReadOnly().reinterpret(length,scope,null).toArray(ValueLayout.JAVA_BYTE);
        } catch(Throwable failure) {throw failure(failure);}
    }
    /** Null restores the original full-tag reader, including partial/error diagnostics. */
    @Nullable public List<SerializableChunkData.SectionData> sections() {
        var binding=NativeLiveBiomeSection.binding(factory.biomeStrategy());
        if(binding==null) return null;
        int defaultBlock=Block.getId(factory.defaultBlockState()),defaultBiome=binding.id(factory.defaultBiome());
        if(defaultBlock<0||Block.BLOCK_STATE_REGISTRY.byId(defaultBlock)!=factory.defaultBlockState()||defaultBiome<0) return null;
        MemorySegment rows=MemorySegment.NULL;
        try {
            rows=(MemorySegment)HYDRATE.invokeExact(owner,Block.BLOCK_STATE_REGISTRY.size(),factory.blockStatesStrategy().globalPaletteBitsInMemory,defaultBlock,
                binding.values.size(),factory.biomeStrategy().globalPaletteBitsInMemory,defaultBiome);
            if(rows.address()==0) return null;
            int count=(int)COUNT.invokeExact(rows);
            if(count<0||count>4096) throw new IllegalStateException("Invalid native section count");
            var result=new ArrayList<SerializableChunkData.SectionData>(count);
            try(var temporary=Arena.ofConfined()) {
                var row=temporary.allocate(56,8);
                for(int index=0;index<count;index++) {
                    if((int)ROW.invokeExact(rows,index,row)!=0) throw new IllegalStateException("Invalid native section row");
                    try {
                        int y=row.getAtIndex(ValueLayout.JAVA_INT,0),flags=row.getAtIndex(ValueLayout.JAVA_INT,1);
                        LevelChunkSection section=null;
                        if((flags&16)!=0) {
                            var blocks=NativeLiveBlockSection.adoptOwned(take(row,1));
                            var biomes=NativeLiveBiomeSection.adoptOwned(take(row,2),binding);
                            section=new LevelChunkSection(new PalettedContainer<>(factory.blockStatesStrategy(),blocks),
                                new PalettedContainer<>(factory.biomeStrategy(),biomes));
                        }
                        DataLayer block=null,sky=null;
                        if((flags&4)!=0) {var light=take(row,3);var view=take(row,4);block=DataLayer.adoptDecoded(light,view);}
                        if((flags&8)!=0) {var light=take(row,5);var view=take(row,6);sky=DataLayer.adoptDecoded(light,view);}
                        result.add(new SerializableChunkData.SectionData(y,section,block,sky));
                    } finally {RELEASE_ROW.invokeExact(row);}
                }
            }
            return result;
        } catch(Throwable failure) {throw failure(failure);}
        finally {if(rows.address()!=0) release(RELEASE_ROWS,rows);}
    }
    private static MemorySegment take(MemorySegment row,int index) {
        var pointer=row.getAtIndex(ValueLayout.ADDRESS,index);
        row.setAtIndex(ValueLayout.ADDRESS,index,MemorySegment.NULL);
        return pointer;
    }
    private static void release(MethodHandle operation,MemorySegment pointer) {
        try {operation.invokeExact(pointer);} catch(Throwable failure) {throw failure(failure);}
    }
    private static RuntimeException failure(Throwable failure) {
        if(failure instanceof RuntimeException runtime) return runtime;
        if(failure instanceof Error error) throw error;
        return new IllegalStateException("Native decoded section handoff failed",failure);
    }
    @Override public void close() {scope.close();}
}
