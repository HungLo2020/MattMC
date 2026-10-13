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
    /** True while some live source cell binds this list. */
    boolean isBound() {
        if (source != null && source.get() != null) return true;
        if (aliases != null) for (var binding : aliases) if (binding.source.get() != null) return true;
        return false;
    }
    /** An independent native copy of {@code values}, whose storage joins {@code group}. */
    static NativeDhColumnList copyOf(it.unimi.dsi.fastutil.longs.LongList values, NativeDhColumnGroup group) {
        var copy = empty(group);
        if (!values.isEmpty()) copy.addAll(0, values);
        return copy;
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
                : new NativeDhMutableColumn(points, size(), group);
            publishLease();
        }
        return sourceLease.stageOwner();
    }
    public boolean isOriginalProducerView() { return owner instanceof NativeDhColumns && mutable == null; }
    boolean isOriginal(NativeDhColumns source, long address, long length) {
        return mutable == null && owner == source && points.address() == address && points.byteSize() == length;
    }
    /** Java object serialization is an explicit compatibility array escape. */
    private Object writeReplace() { return new LongArrayList(this); }
    private MemorySegment data() { return mutable == null ? points : mutable.points(); }
    MemorySegment stagePoints() { return data(); }
    @Override public int size() { return mutable == null ? (int)(points.byteSize() / 8) : mutable.size(); }
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
    MemorySegment codecOwner() { return mutable != null ? mutable.stageOwner() : sourceLease != null ? sourceLease.stageOwner() : MemorySegment.NULL; }
    boolean codecAliases(NativeDhSource value) {
        int count = source == value.weak ? 1 : 0;
        if (aliases != null) for (var binding : aliases) if (binding.source == value.weak) count++;
        return count > 1;
    }
    void acceptCodecView(long address, long count, boolean owned, MemorySegment replacement) {
        if (replacement != null) detach(NativeDhMutableColumn.adoptOwner(replacement, group));
        else if (owned && mutable == null) detach(sourceLease);
        if (mutable != null) mutable.acceptCodecView(address, count);
    }
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
        if (count != size()) write().resize(count);
    }
    @Override public void clear() {
        if (mutable == null) { if (points.byteSize() != 0) detach(new NativeDhMutableColumn(MemorySegment.NULL, 0, group)); }
        else mutable.remove(0, Math.max(0, size()));
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
    @Override public void getElements(int from, long[] array, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, array.length); Objects.checkFromIndexSize(from, length, size());
        try { MemorySegment.copy(data(), ValueLayout.JAVA_LONG, from * 8L, array, offset, length); }
        finally { Reference.reachabilityFence(this); }
    }
}
