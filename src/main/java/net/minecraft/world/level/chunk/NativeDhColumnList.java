package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.RandomAccess;

/** Direct CPU view of immutable producer or mutable Rust column storage. */
public final class NativeDhColumnList extends AbstractLongList implements RandomAccess, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private Object owner;
    private MemorySegment points;
    private NativeDhMutableColumn mutable;
    private boolean hashValid;
    private int hash;
    /** Arena owner for a mutable copy (null: the copy gets its own arena). */
    private transient NativeDhColumnGroup group;
    NativeDhColumnList(Object owner, MemorySegment points) { this.owner = owner; this.points = points; }
    NativeDhColumnList(Object owner, MemorySegment points, int hash) {
        this(owner, points); this.hash = hash; this.hashValid = true;
    }
    public static NativeDhColumnList empty() { return new NativeDhColumnList(null, MemorySegment.NULL); }
    /** An empty column whose mutable storage shares {@code group}'s arena. */
    public static NativeDhColumnList empty(NativeDhColumnGroup group) {
        var view = empty(); view.group = group; return view;
    }
    static NativeDhColumnList fromOwned(NativeDhMutableColumn column) {
        var view = empty(); view.mutable = column; return view;
    }
    public boolean isOriginalProducerView() { return owner instanceof NativeDhColumns && mutable == null; }
    public boolean isOriginalParentView() { return owner instanceof NativeDhParentColumns && mutable == null; }
    boolean isOriginal(NativeDhColumns source, long address, long length) {
        return mutable == null && owner == source && points.address() == address && points.byteSize() == length;
    }
    /** Java object serialization is an explicit compatibility array escape. */
    private Object writeReplace() { return new LongArrayList(this); }
    private MemorySegment data() { return mutable == null ? points : mutable.points(); }
    MemorySegment stagePoints() { return data(); }
    @Override public int size() { return (int)(data().byteSize() / 8); }
    @Override public long getLong(int index) {
        Objects.checkIndex(index, size());
        try { return data().getAtIndex(ValueLayout.JAVA_LONG, index); }
        finally { Reference.reachabilityFence(this); }
    }
    private NativeDhMutableColumn write() {
        hashValid = false;
        return ensureMutable();
    }
    private NativeDhMutableColumn ensureMutable() {
        if (mutable == null) {
            var previousOwner = owner;
            var previousPoints = points;
            try { detach(new NativeDhMutableColumn(previousPoints, size(), group)); }
            finally { Reference.reachabilityFence(previousOwner); Reference.reachabilityFence(previousPoints); Reference.reachabilityFence(this); }
        }
        return mutable;
    }
    MemorySegment stageOcclusionOwner(boolean writableCenter) {
        return mutable != null || writableCenter ? ensureMutable().stageOwner() : MemorySegment.NULL;
    }
    void acceptOcclusionView(MemorySegment address, long count, boolean changed, int newHash) {
        if (mutable != null) mutable.acceptView(address, count);
        if (changed) { hash = newHash; hashValid = true; }
    }
    private void detach(NativeDhMutableColumn values) {
        mutable = values;
        // The independent vector has copied the values. Keeping either the owner
        // or its arena-backed segment would unnecessarily retain the entire producer.
        owner = null;
        points = MemorySegment.NULL;
    }
    @Override public long set(int index, long value) { long old = getLong(index); write().set(index, value); return old; }
    @Override public void add(int index, long value) { ensureIndex(index); write().add(index, value); }
    @Override public long removeLong(int index) { long old = getLong(index); write().remove(index, index + 1); return old; }
    @Override public void clear() {
        hashValid = false;
        if (mutable == null) { if (points.byteSize() != 0) detach(new NativeDhMutableColumn(MemorySegment.NULL, 0, group)); }
        else mutable.remove(0, size());
    }
    @Override public void removeElements(int from, int to) { Objects.checkFromToIndex(from, to, size()); if (from != to) write().remove(from, to); }
    @Override public void addElements(int index, long[] array, int offset, int length) {
        ensureIndex(index); Objects.checkFromIndexSize(offset, length, array.length);
        if (length != 0) write().insert(index, MemorySegment.ofArray(array).asSlice(offset * 8L, length * 8L), length);
    }
    @Override public boolean addAll(int index, it.unimi.dsi.fastutil.longs.LongCollection values) {
        ensureIndex(index);
        if (values instanceof NativeDhColumnList nativeValues) {
            int count = nativeValues.size(); if (count == 0) return false;
            try { write().insert(index, nativeValues.data(), count); return true; }
            finally { Reference.reachabilityFence(nativeValues); Reference.reachabilityFence(this); }
        }
        if (values instanceof LongArrayList array) {
            int count = array.size(); addElements(index, array.elements(), 0, count); return count != 0;
        }
        long[] exported = values.toLongArray(); addElements(index, exported, 0, exported.length); return exported.length != 0;
    }
    @Override public boolean addAll(int index, it.unimi.dsi.fastutil.longs.LongList values) {
        return addAll(index, (it.unimi.dsi.fastutil.longs.LongCollection) values);
    }
    @Override public int hashCode() {
        try { if (!hashValid) { hash = size() == 0 ? 1 : NativeDhMutableColumn.hash(data(), size()); hashValid = true; } return hash; }
        finally { Reference.reachabilityFence(this); }
    }
    public void remapIds(int[] ids) { write().remap(ids); }
    /** Complete 2x2 producer over existing native CPU point views. */
    public static NativeDhColumnList tryReduce(NativeDhMaterials policy, it.unimi.dsi.fastutil.longs.LongList[] inputs) {
        if (policy == null || inputs.length != 4) return null;
        for (var input : inputs) if (input != null && !(input instanceof NativeDhColumnList)) return null;
        try {
            for (int i = 0; i < 4; i++) {
                var input = (NativeDhColumnList) inputs[i];
                policy.neighbors.setAtIndex(ValueLayout.ADDRESS, i * 2L, input == null ? MemorySegment.NULL : input.data());
                policy.neighbors.setAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1, input == null ? 0L : (long) input.size());
            }
            return NativeDhMutableColumn.reduce(policy);
        } finally { Reference.reachabilityFence(inputs); }
    }
    /** Borrow CPU views directly; shared center/neighbor list aliases keep Java semantics. */
    public boolean tryCull(NativeDhMaterials policy, it.unimi.dsi.fastutil.longs.LongList posX,
                           it.unimi.dsi.fastutil.longs.LongList negX, it.unimi.dsi.fastutil.longs.LongList posZ,
                           it.unimi.dsi.fastutil.longs.LongList negZ) {
        if (policy == null || !(posX instanceof NativeDhColumnList a) || !(negX instanceof NativeDhColumnList b)
            || !(posZ instanceof NativeDhColumnList c) || !(negZ instanceof NativeDhColumnList d)
            || this == a || this == b || this == c || this == d) return false;
        try {
            NativeDhColumnList[] columns = {a, b, c, d};
            for (int i = 0; i < 4; i++) {
                policy.neighbors.setAtIndex(ValueLayout.ADDRESS, i * 2L, columns[i].data());
                policy.neighbors.setAtIndex(ValueLayout.JAVA_LONG, i * 2L + 1, columns[i].size());
            }
            return write().cull(policy) != 0;
        } finally { Reference.reachabilityFence(a); Reference.reachabilityFence(b); Reference.reachabilityFence(c); Reference.reachabilityFence(d); Reference.reachabilityFence(this); }
    }
    @Override public void getElements(int from, long[] array, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, array.length); Objects.checkFromIndexSize(from, length, size());
        try { MemorySegment.copy(data(), ValueLayout.JAVA_LONG, from * 8L, array, offset, length); }
        finally { Reference.reachabilityFence(this); }
    }
}
