package net.minecraft.world.item;

/** Current-only setup adapter; the shared observer compiles unchanged on Frozen. */
public final class AtomicAccessTimingCurrent {
    private static final class Store implements AtomicAccessTimingReference.Store {
        final NativeItemState.AtomicCell cell = new NativeItemState.AtomicCell();
        public Object get() { return cell.get(); }
        public Object set(Object value) { return cell.set(value); }
    }
    public static AtomicAccessTimingReference.Store create() { return new Store(); }
}
