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
    private NativeDhMutableColumn sourceLease;
    private record Binding(java.lang.ref.WeakReference<NativeDhSource> source, int index) {}
    private java.lang.ref.WeakReference<NativeDhSource> source;
    private int sourceIndex;
    private java.util.ArrayList<Binding> aliases;
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
        var view = empty(column.group()); view.mutable = column; return view;
    }
    void attach(NativeDhSource value, int index) {
        if (source != null && source.get() == null) source = null;
        if (aliases != null) { aliases.removeIf(b -> b.source.get() == null); if (aliases.isEmpty()) aliases = null; }
        if (source == null) { source = value.weak; sourceIndex = index; }
        else if (source != value.weak || sourceIndex != index) {
            if (aliases == null) aliases = new java.util.ArrayList<>();
            if (aliases.stream().noneMatch(b -> b.source == value.weak && b.index == index)) aliases.add(new Binding(value.weak, index));
        }
    }
    void detach(NativeDhSource value, int index) {
        if (source == value.weak && sourceIndex == index) source = null;
        if (aliases != null) { aliases.removeIf(b -> b.source == value.weak && b.index == index || b.source.get() == null); if (aliases.isEmpty()) aliases = null; }
    }
    private void publishLease() {
        var lease = (mutable == null ? sourceLease : mutable).stageOwner();
        if (source != null) { var value = source.get(); if (value != null) value.materialized(sourceIndex, this, lease); }
        if (aliases != null) for (var binding : aliases) {
            var value = binding.source.get(); if (value != null) value.materialized(binding.index, this, lease);
        }
    }
    MemorySegment stageSourceOwner() {
        if (mutable != null) return mutable.stageOwner();
        if (sourceLease == null) {
            if (points.byteSize() == 0 && owner == null && source == null && aliases == null) return MemorySegment.NULL;
            sourceLease = owner instanceof NativeDhColumns producer ? producer.sharedColumn(points)
                : owner instanceof NativeDhParentColumns parent ? parent.sharedColumn(points)
                : new NativeDhMutableColumn(points, size(), group);
            publishLease();
        }
        return sourceLease.stageOwner();
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
        return ensureMutable();
    }
    private NativeDhMutableColumn ensureMutable() {
        if (mutable == null) {
            var previousOwner = owner;
            var previousPoints = points;
            try {
                if (sourceLease != null) { sourceLease.makeOwned(); detach(sourceLease); }
                else detach(new NativeDhMutableColumn(previousPoints, size(), group));
            }
            finally { Reference.reachabilityFence(previousOwner); Reference.reachabilityFence(previousPoints); Reference.reachabilityFence(this); }
        }
        return mutable;
    }
    MemorySegment stageOcclusionOwner(boolean writableCenter) {
        return mutable != null || writableCenter ? ensureMutable().stageOwner() : MemorySegment.NULL;
    }
    void acceptOcclusionView(MemorySegment address, long count, boolean changed, int newHash) {
        if (mutable != null) mutable.acceptView(address, count);
    }
    /** Project a Rust table commit without restaging each native owner. */
    void acceptMergeView(long address, long count, boolean owned, MemorySegment replacement) {
        if (replacement != null) detach(NativeDhMutableColumn.adoptOwner(replacement, group));
        else if (owned && mutable == null) {
            if (sourceLease == null) throw new IllegalStateException("Missing retained DH column lease");
            detach(sourceLease);
        }
        if (mutable != null) mutable.acceptView(address, count);
    }
    private void detach(NativeDhMutableColumn values) {
        mutable = values; sourceLease = null;
        publishLease();
        // The independent vector has copied the values. Keeping either the owner
        // or its arena-backed segment would unnecessarily retain the entire producer.
        owner = null;
        points = MemorySegment.NULL;
    }
    @Override public long set(int index, long value) { long old = getLong(index); write().set(index, value); return old; }
    @Override public void add(int index, long value) { ensureIndex(index); write().add(index, value); }
    @Override public long removeLong(int index) { long old = getLong(index); write().remove(index, index + 1); return old; }
    @Override public void size(int count) {
        // Preserve the inherited invalid-size exception and its removal prefix.
        if (count < 0) { super.size(count); return; }
        if (count != size()) write().resize(count);
    }
    @Override public void clear() {
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
        try {
            if (mutable != null) return mutable.hash();
            if (sourceLease != null) return sourceLease.hash();
            if (!hashValid) { hash = size() == 0 ? 1 : NativeDhMutableColumn.hash(data(), size()); hashValid = true; }
            return hash;
        }
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
