package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;

/** Setup adapter for the experimental full dynamic-key native owner. */
public final class OwnerAccessTimingCurrent {
    private static final class Store implements OwnerAccessTimingReference.Store {
        final NativeItemState.AccessOwner owner;
        Store(NativeItemState.AccessOwner owner) { this.owner = owner; }
        public Object get(DataComponentType<Object> key) { return this.owner.get(key); }
        public Object set(DataComponentType<Object> key, Object value) { return this.owner.set(key, value); }
        public Object remove(DataComponentType<Object> key) { return this.owner.remove(key); }
        public OwnerAccessTimingReference.Store copy() { return new Store(this.owner.copy()); }
        public OwnerAccessTimingReference.Patch patch() {
            var view = this.owner.asPatch();
            if (view == null) return null;
            return new OwnerAccessTimingReference.Patch() {
                public java.util.Optional<?> optional(DataComponentType<Object> key) { return view.optional(key); }
                public void remove(DataComponentType<Object> key) { view.remove(key); }
            };
        }
        public List<DataComponentType<?>> order() { return this.owner.orderForTest(); }
    }
    public static OwnerAccessTimingReference.Store create(DataComponentMap prototype) {
        return new Store(NativeItemState.AccessOwner.create(prototype));
    }
}
