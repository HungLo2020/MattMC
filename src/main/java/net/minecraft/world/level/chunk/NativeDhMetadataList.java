package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.bytes.AbstractByteList;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import java.lang.foreign.*;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.RandomAccess;

/** Temporary Java identity view; authoritative metadata values and list edits live in Rust. */
public final class NativeDhMetadataList extends AbstractByteList implements RandomAccess, java.io.Serializable {
    private static final long serialVersionUID=1L;
    final NativeDhMetadata root;
    private final int kind;
    private MemorySegment bytes;
    private int size;
    NativeDhMetadataList(NativeDhMetadata root, int kind) { this.root=root; this.kind=kind; refresh(); }
    private void refresh() { bytes=root.view(kind); size=(int)bytes.byteSize(); }
    @Override public int size() { return size; }
    @Override public byte getByte(int index) {
        Objects.checkIndex(index,size);
        try { return bytes.get(ValueLayout.JAVA_BYTE,index); } finally { Reference.reachabilityFence(root); }
    }
    @Override public byte set(int index, byte value) {
        byte previous=getByte(index);
        try { bytes.set(ValueLayout.JAVA_BYTE,index,value); return previous; }
        finally { Reference.reachabilityFence(root); }
    }
    @Override public void size(int count) {
        if(count<0)throw new IllegalArgumentException("Negative DH metadata size");
        root.edit(kind,0,0,count,MemorySegment.NULL,(byte)0);refresh();
    }
    @Override public void clear() { size(0); }
    @Override public void add(int index, byte value) {
        ensureIndex(index);root.edit(kind,1,index,1,MemorySegment.NULL,value);refresh();
    }
    @Override public byte removeByte(int index) {
        byte previous=getByte(index);removeElements(index,index+1);return previous;
    }
    @Override public void removeElements(int from, int to) {
        Objects.checkFromToIndex(from,to,size);
        if(from!=to){root.edit(kind,3,from,to-from,MemorySegment.NULL,(byte)0);refresh();}
    }
    @Override public void addElements(int index, byte[] values, int offset, int count) {
        ensureIndex(index);Objects.checkFromIndexSize(offset,count,values.length);
        if(count==0)return;
        try(var scratch=Arena.ofConfined()) {
            var input=scratch.allocate(count);
            MemorySegment.copy(MemorySegment.ofArray(values),offset,input,0,count);
            root.edit(kind,2,index,count,input,(byte)0);refresh();
        }
    }
    @Override public boolean addAll(int index, it.unimi.dsi.fastutil.bytes.ByteCollection values) {
        ensureIndex(index);
        // Freeze list inputs before a resize can invalidate a self/sublist reader.
        if(values instanceof ByteList) {
            byte[] input=values.toByteArray();addElements(index,input);return input.length!=0;
        }
        return super.addAll(index,values);
    }
    @Override public boolean addAll(int index, java.util.Collection<? extends Byte> values) {
        if(values instanceof it.unimi.dsi.fastutil.bytes.ByteCollection bytes)return addAll(index,bytes);
        return super.addAll(index,values);
    }
    @Override public void getElements(int from, byte[] output, int offset, int count) {
        Objects.checkFromIndexSize(from,count,size);Objects.checkFromIndexSize(offset,count,output.length);
        try {MemorySegment.copy(bytes,from,MemorySegment.ofArray(output),offset,count);}
        finally {Reference.reachabilityFence(root);}
    }
    @Override public void setElements(int index, byte[] input, int offset, int count) {
        Objects.checkFromIndexSize(index,count,size);Objects.checkFromIndexSize(offset,count,input.length);
        try {MemorySegment.copy(MemorySegment.ofArray(input),offset,bytes,index,count);}
        finally {Reference.reachabilityFence(root);}
    }
    @Override public int hashCode() { return root.hash(kind); }
    private Object writeReplace() { return new ByteArrayList(this); }
    /** Private borrowed CPU plane; callers pin its list and exclude edits through the native call. */
    static MemorySegment borrow(ByteList list) {
        if(list instanceof NativeDhMetadataList nativeList)return nativeList.bytes;
        if(list.getClass()==ByteArrayList.class)return MemorySegment.ofArray(((ByteArrayList)list).elements());
        throw new IllegalArgumentException("Custom DH metadata");
    }
    static boolean supported(ByteList list) { return list instanceof NativeDhMetadataList || list.getClass()==ByteArrayList.class; }
    static boolean aliases(ByteList a, ByteList b) {
        if(a==b)return true;
        if(a instanceof NativeDhMetadataList x && b instanceof NativeDhMetadataList y)return x.root==y.root&&x.kind==y.kind;
        return a.getClass()==ByteArrayList.class&&b.getClass()==ByteArrayList.class&&((ByteArrayList)a).elements()==((ByteArrayList)b).elements();
    }
}
