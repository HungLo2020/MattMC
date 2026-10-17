package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.bytes.AbstractByteList;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import java.lang.foreign.*;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.RandomAccess;

/** Fixed-size Java view of a Rust metadata plane: values are read and written in place. */
public final class NativeDhMetadataList extends AbstractByteList implements RandomAccess, java.io.Serializable {
    private static final long serialVersionUID=1L;
    final NativeDhMetadata root;
    private final int kind;
    int codecKind() { return kind; }
    private MemorySegment bytes;
    private int size;
    NativeDhMetadataList(NativeDhMetadata root, int kind) { this.root=root; this.kind=kind; refresh(); }
    private void refresh() { bytes=root.view(kind); size=(int)bytes.byteSize(); }
    /** Replace every value; {@code values} has one byte per column. */
    public void setAll(byte[] values) {
        if(values.length!=size)throw new IllegalArgumentException("DH metadata needs "+size+" values");
        setElements(0,values,0,size);
    }
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
    /** The plane has one byte per source cell; its size never changes. */
    @Override public void size(int count) { if(count!=size)throw fixed(); }
    @Override public void clear() { throw fixed(); }
    @Override public void add(int index, byte value) { throw fixed(); }
    @Override public byte removeByte(int index) { throw fixed(); }
    @Override public void removeElements(int from, int to) { if(from!=to)throw fixed(); }
    @Override public void addElements(int index, byte[] values, int offset, int count) { if(count!=0)throw fixed(); }
    private static UnsupportedOperationException fixed() {
        return new UnsupportedOperationException("DH metadata has exactly one value per column");
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
