package net.minecraft.world.item;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Retained structural state bridge. Roots resolve native tokens; they never decide membership. */
public final class NativeItemState {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final long PROTOTYPE_VALUE = Long.MAX_VALUE;
    private static final AtomicLong CLEANUP_FAILURES = new AtomicLong();
    private static final MethodHandle DEFAULT_NEW = call("defaults_create", 1), DEFAULT_ADDRESS = call("defaults_address", 1),
        DEFAULT_PUT = call("defaults_put_payload", 5), DEFAULT_REMOVE = call("defaults_remove", 3), DEFAULT_CLEAR = call("defaults_clear", 1),
        DEFAULT_CURSOR = call("defaults_cursor", 1), DEFAULT_CURSOR_OP = call("defaults_cursor_op", 2), OVERLAY_DEFAULTS = call("overlay_defaults", 2);
    private static final MethodHandle ATOMIC_NEW = call("atomic_create", 1);
    private static final MethodHandle ATOMIC_ADDRESS = call("atomic_address", 1);
    private static final MethodHandle ATOMIC_SWAP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_atomic_swap",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), java.lang.foreign.Linker.Option.critical(false));
    private static final MethodHandle OVERLAY_NEEDS_COPY=call("overlay_needs_copy",1),OVERLAY_STAGE=call("overlay_stage",1),OVERLAY_COMMIT=call("overlay_commit",3);
    private static final MethodHandle STACK_PROMOTE = call("stack_promote", 4);
    private static final MethodHandle STACK_COPY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_stack_copy_leased",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle STACK_RETAIN = call("stack_retain", 1);
    private static final MemorySegment SCALAR_RAW = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);
    private static final MethodHandle STACK_ADDRESS = call("stack_address", 1);
    private static final MethodHandle SLOTS_OWNER_ADDRESS = call("slots_owner_address", 1), DEFAULT_OWNER_ADDRESS = call("defaults_owner_address", 1);
    private static final MethodHandle TRANSFER_CONTINUE = call("transfer_continue", 4);
    private static final MethodHandle TRANSFER_BOUNDED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_transfer_bounded",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), java.lang.foreign.Linker.Option.critical(false));
    private static final MethodHandle MENU_STOCK=call("menu_stock_match",4),MENU_DOUBLE=call("menu_double_match",4),MENU_RETAIN=call("menu_retain",1),MENU_NEW=call("menu_create",2),MENU_ADDRESS=call("menu_address",1),MENU_GET=call("menu_get",2),MENU_CARRIED=call("menu_carried",2);
    private static final MethodHandle MENU_EDIT=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_menu_edit",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle EQUIPMENT_NEW=call("equipment_create",1);
    private static final MethodHandle CONTAINER_NEW=call("container_create",2),CONTAINER_ADDRESS=call("container_address",1),CONTAINER_SET=call("container_replace",3);
    private static final MethodHandle SLOT_CONTAINER_NEW=call("slot_container_create",2),SLOT_READ_REGULAR=call("slot_read_regular",3);
    private static final MethodHandle SLOT_NEW=call("slot_create",2),SLOT_ADDRESS=call("slot_address",1),SLOT_RETAIN=call("slot_retain",1);
    private static final MethodHandle SLOT_READ=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_slot_read",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),java.lang.foreign.Linker.Option.critical(false));
    private static final MethodHandle EQUIPMENT_OP=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_equipment_op",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle INVENTORY_EQUIPMENT=call("inventory_equipment",2),INVENTORY_NEW = call("inventory_create", 1), INVENTORY_ADDRESS = call("inventory_address", 1),
        INVENTORY_QUERY = call("inventory_query", 3), INVENTORY_SWAP = call("inventory_swap", 2), INVENTORY_RETAIN = call("inventory_retain", 1), INVENTORY_TRANSFER = call("inventory_transfer", 4);
    private static final MethodHandle INVENTORY_ADD = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_inventory_add_leased",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle INVENTORY_ADD_PENDING = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_inventory_add_pending_leased",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle INVENTORY_SCAN = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_inventory_scan_leased",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle INVENTORY_VISIT = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_inventory_visit_leased",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle MENU_ACTION=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_menu_action",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle MENU_ROWS_ADDRESS=call("menu_rows_address",1);
    private static final MethodHandle MENU_OWNER_ADDRESS=call("menu_owner_address",1),CONTAINER_OWNER_ADDRESS=call("container_owner_address",1);
    private static final MethodHandle MENU_FACADE_BOUNDED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_menu_facade_bounded",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),java.lang.foreign.Linker.Option.critical(false));
    private static final MethodHandle SLOT_READ_BOUNDED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_slot_read_bounded",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),java.lang.foreign.Linker.Option.critical(false));
    private static final MethodHandle SLOTS_FACADE_BOUNDED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_slots_facade_bounded",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),java.lang.foreign.Linker.Option.critical(false));
    private static final MethodHandle MENU_EMPTY_BOUNDED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_menu_empty_source_bounded",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),java.lang.foreign.Linker.Option.critical(false));
    public abstract static class MenuActionEffects {
        private Throwable failure;
        public abstract long effect(int operation,int first,int second,long third,long out);
        protected static boolean knownEndpoint(SlotLink link){return link!=null&&(link.chest==null||link.chest.nativeSlotsForTransfer()!=null);}
        public static boolean broadcastEndpoint(SlotLink link){return knownEndpoint(link);}
        protected static void endpoint(long out,SlotLink link){
            if(link!=null&&link.chest!=null&&link.chest.nativeSlotsForTransfer()==null)link=null;
            try{VIEW_LONG.set(SCALAR_RAW,out,link==null?0:link.address);}
            finally{java.lang.ref.Reference.reachabilityFence(link);}
        }
        protected static void stacks(long out,ItemStack source,ItemStack snapshot){
            try{
                VIEW_LONG.set(SCALAR_RAW,out,source==null?0:stackOwner(source.state).checkBound());
                VIEW_LONG.set(SCALAR_RAW,out+8,snapshot==null?0:stackOwner(snapshot.state).checkBound());
            }finally{java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(snapshot);}
        }
        protected static void transfer(long out,TransferEffects effects,ItemStack source){
            Slots destination=Objects.requireNonNull(effects.destination,"Missing admitted transfer owner");
            if(destination.inventoryAddress==0)DataComponentMap.nativeDefaults(source.state.prototype());
            try{
                boolean inventory=destination.inventoryAddress!=0;
                VIEW_LONG.set(SCALAR_RAW,out,inventory?destination.inventoryAddress:destination.ownerAddress);
                VIEW_LONG.set(SCALAR_RAW,out+8,effects.second!=null?2L:inventory?1L:0L);
            }finally{java.lang.ref.Reference.reachabilityFence(destination);java.lang.ref.Reference.reachabilityFence(effects);}
        }
        /** Op 19: the second half of a double-chest destination (kind 2). */
        protected static void secondDestination(long out,TransferEffects effects){
            Slots second=Objects.requireNonNull(effects.second,"Missing double-chest second half");
            try{VIEW_LONG.set(SCALAR_RAW,out,second.ownerAddress);}
            finally{java.lang.ref.Reference.reachabilityFence(second);java.lang.ref.Reference.reachabilityFence(effects);}
        }
        protected static void route(long out,int kind,int index){VIEW_LONG.set(SCALAR_RAW,out,kind);VIEW_LONG.set(SCALAR_RAW,out+8,index);}
        /** The prepared copy's scalar address Rust wrote to {@code out}. */
        protected static long preparedAddress(long out){return (long)VIEW_LONG.get(SCALAR_RAW,out);}
    }
    private static final ThreadLocal<MenuActionEffects> MENU_ACTION_EFFECTS=new ThreadLocal<>();
    private static long menuActionEffect(long action,long first,long second,long third,long pointer){
        var effects=MENU_ACTION_EFFECTS.get();if(effects==null)return Long.MIN_VALUE;
        try{return effects.effect(Math.toIntExact(action),Math.toIntExact(first),Math.toIntExact(second),third,pointer);}
        catch(Throwable failure){effects.failure=failure;return Long.MIN_VALUE;}
    }
    private static final class MenuActionCallback {
        static final MemorySegment STUB;
        static{try{
            var method=java.lang.invoke.MethodHandles.lookup().findStatic(NativeItemState.class,"menuActionEffect",java.lang.invoke.MethodType.methodType(long.class,long.class,long.class,long.class,long.class,long.class));
            STUB=java.lang.foreign.Linker.nativeLinker().upcallStub(method,FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),Arena.global());
        }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
    }

    public abstract static class InventoryVisitEffects {
        private Slots owner;
        private Throwable failure;
        public abstract long effect(long action,int index,ItemStack captured);
        /** A visitor that keeps no reference to the stacks it receives (saving) is given copies of pending contents. */
        public boolean retainsNothing() { return false; }
    }
    private static final ThreadLocal<InventoryVisitEffects> INVENTORY_VISIT_EFFECTS = new ThreadLocal<>();
    private static ItemStack visitStack(InventoryVisitEffects effects,int index,long facade){
        if(effects.retainsNothing()&&isPending(facade)){ItemStack snapshot=effects.owner.pendingSnapshot(index);if(snapshot!=null)return snapshot;}
        return effects.owner.resolveFacade(index,facade);
    }
    private static long inventoryVisitEffect(long action,long index,long facade) {
        var effects=INVENTORY_VISIT_EFFECTS.get();if(effects==null)return Long.MIN_VALUE;
        try {return effects.effect(action,Math.toIntExact(index),facade==0?null:visitStack(effects,Math.toIntExact(index),facade));}
        catch(Throwable failure){effects.failure=failure;return Long.MIN_VALUE;}
    }
    private static final class InventoryVisitCallback {
        static final MemorySegment STUB;
        static {
            try {
                var method=java.lang.invoke.MethodHandles.lookup().findStatic(NativeItemState.class,"inventoryVisitEffect",java.lang.invoke.MethodType.methodType(long.class,long.class,long.class,long.class));
                STUB=java.lang.foreign.Linker.nativeLinker().upcallStub(method,FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),Arena.global());
            }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    public abstract static class InventoryAddEffects {
        private Throwable failure;
        protected ItemStack candidate;
        protected final long candidateResult(boolean matched) {
            ItemStack captured = this.candidate;
            try { return stackOwner(captured.state).checkBound() | (matched ? 1L : 0L); }
            finally { java.lang.ref.Reference.reachabilityFence(captured); }
        }
        public abstract long effect(long action,int index);
        /**
         * Actions 33/34: the empty-slot resource or damaged copy the native add
         * prepared ({@code handle}, scalar {@code address}); adopt it with
         * {@link ItemStack#adoptPreparedCopy} or keep the original copy.
         */
        /** Whether the native add may publish empty-slot/damaged copies without a facade (actions 35/36). */
        public boolean pendingInsertions() { return false; }
        public long prepared(long action,int index,long handle,long address,long adopted) {
            throw new IllegalStateException("Unknown inventory add effect: "+action);
        }
    }
    private static final ThreadLocal<InventoryAddEffects> INVENTORY_ADD_EFFECTS = new ThreadLocal<>();
    private static long inventoryAddEffect(long action,long index,long handle,long address,long out) {
        InventoryAddEffects effects=INVENTORY_ADD_EFFECTS.get();if(effects==null)return Long.MIN_VALUE;
        try {return handle!=0&&(action==33||action==34||action==35||action==36)?effects.prepared(action,Math.toIntExact(index),handle,address,out+8):effects.effect(action,Math.toIntExact(index));}
        catch(Throwable failure){effects.failure=failure;return Long.MIN_VALUE;}
    }
    private static final class InventoryAddCallback {
        static final MemorySegment STUB;
        static {
            try {
                var method=java.lang.invoke.MethodHandles.lookup().findStatic(NativeItemState.class,"inventoryAddEffect",java.lang.invoke.MethodType.methodType(long.class,long.class,long.class,long.class,long.class,long.class));
                STUB=java.lang.foreign.Linker.nativeLinker().upcallStub(method,FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),Arena.global());
            } catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    private static final MethodHandle SLOTS_NEW = call("slots_create", 2);
    private static final MethodHandle SLOTS_GET = call("slots_get", 2);
    private static final MethodHandle SLOTS_MATERIALIZE = call("slots_materialize", 4);
    private static final MethodHandle SLOTS_BIND = call("slots_bind", 4);
    private static final MethodHandle SLOTS_PENDING_COPY = call("slots_pending_copy", 4);
    private static final MethodHandle MENU_DIGESTS = call("menu_digests", 4);
    private static final MethodHandle INVENTORY_TICK_NOOP = call("inventory_tick_noop", 1);
    private static final MethodHandle PURE_COPY = call("pure_copy", 1);
    private static final MethodHandle SHULKER_ITEM = call("shulker_item", 1);
    /** The item places a shulker box block; stock shulker boxes refuse it through their faces. */
    static void markShulkerItem(Item item) { c1(SHULKER_ITEM, Identities.of(item)); }
    private static final MethodHandle PENDING_KEY = call("pending_key", 0);
    private static final MethodHandle SLOTS_SIGNAL = call("slots_signal", 2);
    private static final MethodHandle SLOTS_SIGNAL_PAIR = call("slots_signal_pair", 3);
    private static final MethodHandle SLOTS_OCCUPANCY = call("slots_occupancy", 2);
    private static final MethodHandle SLOTS_OCCUPIED = call("slots_occupied", 2);
    private static final MethodHandle HOPPER_PLAN = call("hopper_plan", 6);
    private static final MethodHandle HOPPER_COMMIT = call("hopper_commit", 7);
    private static final MethodHandle HOPPER_TAKE = NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_hopper_take",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    /** The item's ItemStack copy is pure (inherited asItem): native transfers may copy it without Java. */
    static void markPureCopy(Item item) { c1(PURE_COPY, Identities.of(item)); }
    /** A fresh pending key (at or below PENDING_BASE, so never through checked()). */
    private static long pendingKey() {
        try { long key = (long)PENDING_KEY.invokeExact(); if (!isPending(key)) throw new IllegalStateException("Native pending key out of range: " + key); return key; }
        catch (Throwable failure) { throw failed(failure); }
    }
    /** The item's inventoryTick is Item's empty one: the native inventory tick only counts down pop time. */
    static void markInventoryTickNoop(Item item) { c1(INVENTORY_TICK_NOOP, Identities.of(item)); }
    /** Natively published contents not yet observed by Java carry a pending key (slots.rs PENDING_BASE). */
    static final long PENDING_BASE = -(1L << 40);
    static boolean isPending(long token) { return token <= PENDING_BASE; }
    private static final MethodHandle SLOTS_PUBLISH=call("slots_publish_leased",5);
    private static final MethodHandle SLOTS_CLEAR = call("slots_clear", 2);
    private static final MethodHandle TRANSFER_STEP=call("transfer_step",5),TRANSFER_PUBLISH=call("transfer_publish_leased",7);
    private static final class TransferIds { static final long AIR = Identities.of(Items.AIR); }
    private static final MethodHandle TRANSFER_RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_transfer_run",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final ThreadLocal<TransferEffects> TRANSFER_EFFECTS = new ThreadLocal<>();
    private static final class TransferCallback {
        static final MemorySegment STUB;
        static {
            try {
                var method = java.lang.invoke.MethodHandles.lookup().findStatic(NativeItemState.class, "transferEffect",
                    java.lang.invoke.MethodType.methodType(long.class, long.class, long.class, long.class, long.class, long.class));
                STUB = java.lang.foreign.Linker.nativeLinker().upcallStub(method,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Arena.global());
            } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
    }
    /** Call-local Java effects only. Native owns the running transfer and releases locks before each call. */
    public abstract static class TransferEffects {
        private Throwable failure;
        private Slots destination,second;
        protected final void destination(NativeItemSlots facade){this.destination=facade.actualOwner();this.second=null;}
        /** A double chest's two halves as one destination, in the compound's slot order. */
        protected final void destination(NativeItemSlots first,NativeItemSlots second){this.destination=first.actualOwner();this.second=second.actualOwner();}
        public abstract long effect(long action, long index, long phase, long amount, long changed);
        /**
         * An empty-destination insertion whose copy the native transfer already
         * prepared: adopt it ({@link ItemStack#adoptPreparedCopy}), then publish
         * and run the original insertion effects.
         */
        public long insertPrepared(long index, long amount, long handle, long address, long adopted) {
            throw new IllegalStateException("No prepared native insertion for this transfer");
        }
        /** Whether the native transfer may publish empty-slot copies without a Java facade (op 18). */
        public boolean pendingInsertions() { return false; }
        /**
         * The native transfer already published the copy under {@code key}: record
         * its materialization recipe (setting {@code adopted}), then run the
         * original insertion effects.
         */
        public long publishedPending(long index, long amount, long key, long retired, long adopted) {
            throw new IllegalStateException("No pending native insertion for this transfer");
        }
    }
    /** {@code source}'s copy prepared by a native operation, adopted as its owner. */
    public static Stack adoptPreparedCopy(Stack source,long handle,long address,long adopted){
        return net.minecraft.core.component.PatchedDataComponentMap.adoptPreparedStack(stackOwner(source),handle,address,adopted);
    }
    private static long transferEffect(long action, long index, long phase, long amount, long changed) {
        TransferEffects effects = TRANSFER_EFFECTS.get();
        if (effects == null) return -1;
        try { return effects.effect(action, index, phase, amount, changed); }
        catch (Throwable failure) { effects.failure = failure; return -1; }
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable> void throwOriginal(Throwable failure) throws E { throw (E)failure; }
    private static final java.lang.invoke.VarHandle COUNT = ValueLayout.JAVA_INT.varHandle();
    private static final MethodHandle PATCH_AT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_patch_at",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle PATCH_NEW = call("patch_create", 0);
    private static final MethodHandle PATCH_ADDRESS = call("patch_address", 1);
    private static final java.lang.invoke.VarHandle VIEW_LONG = ValueLayout.JAVA_LONG.varHandle();
    private static final MethodHandle PATCH_VERSION = call("patch_version", 1);
    private static final MethodHandle PATCH_SIZE = call("patch_size", 1);
    private static final MethodHandle PATCH_GET = call("patch_get", 2);
    private static final MethodHandle PATCH_PUT = call("patch_put_payload", 4);
    private static final MethodHandle PATCH_REMOVE = call("patch_remove", 2);
    private static final MethodHandle PATCH_SHARE = call("patch_share", 1);
    private static final MethodHandle PATCH_SNAPSHOT = call("patch_snapshot", 1);
    private static final MethodHandle PATCH_CLEAR = call("patch_clear", 1);
    private static final MethodHandle OVERLAY_NEW = call("overlay_create", 1);
    private static final MethodHandle OVERLAY_FROM_PATCH = call("overlay_from_patch", 2);
    private static final MethodHandle OVERLAY_VERSION = call("overlay_version", 1);
    private static final MethodHandle OVERLAY_SIZE = call("overlay_size", 1);
    private static final MethodHandle OVERLAY_GET = call("overlay_get", 2);
    private static final MethodHandle OVERLAY_PREPARE = call("overlay_prepare", 1);
    private static final MethodHandle OVERLAY_COPY = call("overlay_copy", 1);
    private static final MethodHandle OVERLAY_VIEW = call("overlay_view", 1);
    private static final MethodHandle OVERLAY_PATCH = call("overlay_as_patch", 1);
    private static final MethodHandle OVERLAY_CLEAR = call("overlay_clear", 1);
    private static final MethodHandle RELEASE = call("owner_release", 1);
    private static final MethodHandle LIVE = call("owner_live", 1);
    private static final MethodHandle SET = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_overlay_set_payload",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle REMOVE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_overlay_remove",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle APPLY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_overlay_apply_payload",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final ThreadLocal<MemorySegment> RESULT = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(32, 8));

    private NativeItemState() {}
    /** Internal enchantment backing; opaque Holder anchors stay with this actual map facade. */
    @SuppressWarnings("unchecked")
    public static <K> it.unimi.dsi.fastutil.objects.Object2IntMap<K> newEnchantmentMap(int expected) {
        return (it.unimi.dsi.fastutil.objects.Object2IntMap<K>)(it.unimi.dsi.fastutil.objects.Object2IntMap<?>)new NativeItemRoots.Enchantments(expected);
    }
    public static <K> it.unimi.dsi.fastutil.objects.Object2IntMap<K> copyEnchantmentMap(java.util.Map<K, Integer> source) {
        var result = NativeItemState.<K>newEnchantmentMap(source.size()); result.putAll(source); return result;
    }
    public static boolean upgradeEnchantment(it.unimi.dsi.fastutil.objects.Object2IntMap<?> map, Object holder, int level) {
        if (!(map instanceof NativeItemRoots.Enchantments owned)) return false;
        owned.upgrade(holder, level); return true;
    }
    public static long enchantmentPayloadAddress(it.unimi.dsi.fastutil.objects.Object2IntMap<?> map) {
        return map instanceof NativeItemRoots.Enchantments owned ? owned.address() : 0;
    }
    /** Typed scalar/native allocation input only; the original VM object stays rooted by its facade. */
    private static long payloadDescriptor(Object value) {
        if (value == null) return 3;
        if (value instanceof Integer integer) return ((long)integer << 2) | 1;
        if (value.getClass() == net.minecraft.world.item.enchantment.ItemEnchantments.class) {
            long address = ((net.minecraft.world.item.enchantment.ItemEnchantments)value).nativePayloadAddress();
            if (address != 0) return address | 2;
        }
        return 0;
    }
    // Semantic roles are bootstrap metadata. Building a foreign component map
    // before Bootstrap must not initialize DataComponents or its registries.
    private static volatile DataComponentType<?>[] defaultRoleKeys;
    private static final MethodHandle COMPONENT_ROLES = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_component_roles",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    static void bindDefaultRoles() {
        DataComponentType<?>[] keys = new DataComponentType<?>[] {
            net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,
            net.minecraft.core.component.DataComponents.MAX_DAMAGE,
            net.minecraft.core.component.DataComponents.DAMAGE,
            net.minecraft.core.component.DataComponents.UNBREAKABLE,
            net.minecraft.core.component.DataComponents.ENCHANTMENTS,
            net.minecraft.core.component.DataComponents.CUSTOM_NAME
        };
        var empty = net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rows = arena.allocate(96, 8);
            for (int index = 0; index < keys.length; index++) {
                rows.set(ValueLayout.JAVA_LONG, index * 16L, Identities.of(keys[index]));
                rows.set(ValueLayout.JAVA_LONG, index * 16L + 8, Integer.toUnsignedLong(System.identityHashCode(keys[index])));
            }
            if ((long)COMPONENT_ROLES.invokeExact(rows, empty.nativePayloadAddress(), Identities.of(DataComponentMap.EMPTY)) != 1)
                throw new IllegalStateException("Item component roles already bound to another allocation");
            defaultRoleKeys = keys;
        } catch (Throwable failure) { throw failed(failure); }
        finally { java.lang.ref.Reference.reachabilityFence(empty); }
    }
    private static int defaultRole(DataComponentType<?> key,Object value) {
        var keys=defaultRoleKeys;int kind=0;
        if(keys!=null)for(int i=0;i<keys.length;i++)if(key==keys[i]){kind=i+1;break;}
        if(kind>=1 && kind<=3 && !(value instanceof Integer))kind|=0x80000000;
        // PatchedDataComponentMap.has uses a non-null value, even when the
        // trusted prototype's key set contains this explicitly null entry.
        if(kind==4 && value==null)kind|=0x80000000;
        return kind;
    }
    public static it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> newPatchMap() {
        return Patch.create().mapView();
    }
    public static it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> copyPatchMap(
        it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> source) {
        if (source instanceof PatchMap map) return map.patch.snapshot().mapView();
        var result = newPatchMap(); result.putAll(source); return result;
    }

    /** Builds the original SimpleMap's native backing using its original insertion sequence. */
    public static it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, Object> defaultMap(
            Map<DataComponentType<?>, Object> source) {
        DefaultMap map = adopt(DEFAULT_NEW, DefaultMap::new, source.size());
        for (var entry : source.entrySet()) map.put(entry.getKey(), entry.getValue());
        return map;
    }
    private record DefaultBinding(DataComponentType<?> type, Object value) {}
    /** Native canonical layout; Java entries only pin the identities addressed by live native leaves. */
    public static final class DefaultMap extends it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap<DataComponentType<?>, Object> {
        private final long handle;
        private final long ownerAddress;
        private final MemorySegment header;
        private final Map<Long, DefaultBinding> bindings = new HashMap<>();
        private long next = 1;
        private boolean semanticRolesReady = defaultRoleKeys != null;
        private final Entries entries = new Entries();
        private final Keys keys = new Keys();
        private DefaultMap(long handle, Release ownership) {
            this.handle = handle; this.ownerAddress = c1(DEFAULT_OWNER_ADDRESS, handle); this.header = leaseView(c1(DEFAULT_ADDRESS, handle), 32, ownership).asReadOnly();
        }
        private long field(long offset) { return (long)VIEW_LONG.getAcquire(this.header, offset); }
        private int capacity() { return Math.toIntExact(this.field(16)); }
        private boolean hashed() { return this.field(24) != 0; }
        private long row(int index, int offset) {
            if (index < 0 || index >= this.capacity() + (this.hashed() ? 1 : 0)) throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + (this.capacity() + (this.hashed() ? 1 : 0)));
            MemorySegment rows = MemorySegment.ofAddress(this.field(0)).reinterpret((long)(this.capacity() + (this.hashed() ? 1 : 0))*32);
            return rows.get(ValueLayout.JAVA_LONG, (long)index*32+offset);
        }
        private DefaultBinding binding(long leaf) { return leaf == 0 ? null : Objects.requireNonNull(this.bindings.get(leaf), "Missing default leaf root"); }
        /** Annotate the existing canonical rows after bootstrap, without changing leaves or membership. */
        public synchronized boolean prepareNativeRoles() {
            if(semanticRolesReady)return true;
            if(defaultRoleKeys==null)return false;
            try {
                int bound=this.hashed()?this.capacity()+1:this.size();
                for(int i=0;i<bound;i++) {
                    long leaf=this.row(i,8);if(leaf==0)continue;
                    var binding=this.binding(leaf);var key=binding.type();var value=binding.value();
                    long hashKind=Integer.toUnsignedLong(System.identityHashCode(key))|((long)defaultRole(key,value)<<32);
                    long descriptor=payloadDescriptor(value);
                    checked((long)DEFAULT_PUT.invokeExact(this.handle,Identities.of(key),leaf,hashKind,descriptor));
                }
                semanticRolesReady=true;return true;
            } catch(Throwable failure){throw failed(failure);}
            finally {java.lang.ref.Reference.reachabilityFence(this);}
        }
        private int find(Object key) {
            if (key != null && !(key instanceof DataComponentType<?>)) return -1;
            long token = Identities.of(key);
            if (!this.hashed()) { for (int i=this.size();i--!=0;) if (this.row(i,0)==token) return i; return -1; }
            int n=this.capacity(); if (key==null) return this.row(n,8)!=0 ? n : -1;
            int hash=System.identityHashCode(key)*0x9e3779b9, mask=n-1, pos=(hash^(hash>>>16))&mask;
            for (int i=0;i<n;i++) { if(this.row(pos,8)==0)return -1; if(this.row(pos,0)==token)return pos; pos=(pos+1)&mask; } return -1;
        }
        @Override public synchronized int size() { try { return Math.toIntExact(this.field(8)); } finally { java.lang.ref.Reference.reachabilityFence(this.header); } }
        @Override public synchronized boolean containsKey(Object key) { try {return this.find(key)>=0;} finally {java.lang.ref.Reference.reachabilityFence(this.header);} }
        @Override public synchronized Object get(Object key) {
            try {int index=this.find(key);return index<0?this.defRetValue:this.binding(this.row(index,8)).value();}
            finally {java.lang.ref.Reference.reachabilityFence(this.header);}
        }
        @Override public synchronized Object put(DataComponentType<?> key,Object value) {
            long leaf=this.next++; this.bindings.put(leaf,new DefaultBinding(key,value));
            int kind=defaultRole(key,value);
            long hashKind=Integer.toUnsignedLong(System.identityHashCode(key)) | ((long)kind<<32);
            try {
                long descriptor=payloadDescriptor(value);
                long old=checked((long)DEFAULT_PUT.invokeExact(this.handle,Identities.of(key),leaf,hashKind,descriptor));
                DefaultBinding previous=this.bindings.remove(old);return previous==null?this.defRetValue:previous.value();
            } catch(Throwable failure) {this.bindings.remove(leaf);throw failed(failure);}
            finally {java.lang.ref.Reference.reachabilityFence(value);java.lang.ref.Reference.reachabilityFence(this);}
        }
        @Override public synchronized Object remove(Object key) {
            if(key!=null && !(key instanceof DataComponentType<?>))return this.defRetValue;
            try {long old=c3(DEFAULT_REMOVE,this.handle,Identities.of(key),System.identityHashCode(key));DefaultBinding previous=this.bindings.remove(old);return previous==null?this.defRetValue:previous.value();}
            finally {java.lang.ref.Reference.reachabilityFence(this.header);}
        }
        @Override public synchronized void clear() { try {c1(DEFAULT_CLEAR,this.handle);this.bindings.clear();} finally {java.lang.ref.Reference.reachabilityFence(this.header);} }
        private synchronized DefaultBinding at(int index) {try{return this.binding(this.row(index,8));}finally{java.lang.ref.Reference.reachabilityFence(this.header);}}
        @Override public int hashCode() {
            if (!this.hashed()) return super.hashCode();
            // Match the source's fixed initial count and live bucket reads around value.hashCode callbacks.
            int hash=0,pos=0,remaining=this.size();boolean nullKey=this.containsKey(null);if(nullKey)remaining--;
            while(remaining--!=0){DefaultBinding b;while((b=this.at(pos))==null)pos++;hash+=System.identityHashCode(b.type()) ^ Objects.hashCode(b.value());pos++;}
            if(nullKey){DefaultBinding b=this.at(this.capacity());hash+=b==null?0:Objects.hashCode(b.value());}return hash;
        }
        @Override public it.unimi.dsi.fastutil.objects.ObjectSet<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Object>> reference2ObjectEntrySet(){return this.entries;}
        private static class DefaultEntry extends it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap.BasicEntry<DataComponentType<?>,Object> {
            DefaultEntry(DefaultBinding binding){super(binding==null?null:binding.type(),binding==null?null:binding.value());}
            void assign(DefaultBinding binding){this.key=binding==null?null:binding.type();this.value=binding==null?null:binding.value();}
        }
        private final class HashEntry extends DefaultEntry {
            private int index;
            HashEntry(int index){super(null);this.index=index;}
            @Override public DataComponentType<?> getKey(){var binding=DefaultMap.this.at(index);return binding==null?null:binding.type();}
            @Override public Object getValue(){var binding=DefaultMap.this.at(index);return binding==null?null:binding.value();}
            @Override public Object setValue(Object value){synchronized(DefaultMap.this){return DefaultMap.this.put(getKey(),value);}}
            @Override public int hashCode(){return System.identityHashCode(getKey())^Objects.hashCode(getValue());}
            @Override public boolean equals(Object other){return other instanceof Map.Entry<?,?> entry && getKey()==entry.getKey() && Objects.equals(getValue(),entry.getValue());}
            @Override public String toString(){return String.valueOf(getKey())+"=>"+String.valueOf(getValue());}
        }
        private final class Cursor implements it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>,Object>> {
            private final long cursor;
            private final Cleaner.Cleanable cleanup;
            private final DefaultEntry reusable;
            private final boolean live=DefaultMap.this.hashed();
            private DefaultEntry lastReturned;
            Cursor(long cursor,Release ownership,boolean fast){this.cursor=cursor;this.cleanup=CLEANER.register(this,ownership);this.reusable=fast?(live?new HashEntry(-1):new DefaultEntry(null)):null;}
            private void nativeFailure() {
                int kind=(int)c2(DEFAULT_CURSOR_OP,this.cursor,3);long index=c2(DEFAULT_CURSOR_OP,this.cursor,4);
                if(kind==1)throw new NullPointerException("Cannot invoke \"it.unimi.dsi.fastutil.objects.ReferenceArrayList.get(int)\" because \"this.wrapped\" is null");
                if(kind==2)throw new IndexOutOfBoundsException("Index ("+index+") is greater than or equal to list size ("+c2(DEFAULT_CURSOR_OP,this.cursor,5)+")");
                if(kind==3)throw new ArrayIndexOutOfBoundsException("Index "+index+" out of bounds for length "+(DefaultMap.this.capacity()+1));
            }
            private DefaultEntry read(long operation) {
                long index=c2(DEFAULT_CURSOR_OP,this.cursor,operation);
                if(index<0){this.nativeFailure();throw new java.util.NoSuchElementException();}
                int row=Math.toIntExact(index);DefaultBinding b=DefaultMap.this.at(row);
                if(this.reusable==null)return this.lastReturned=live?new HashEntry(row):new DefaultEntry(b);
                if(live)((HashEntry)this.reusable).index=row;else this.reusable.assign(b);return this.lastReturned=this.reusable;
            }
            @Override public boolean hasNext(){synchronized(DefaultMap.this){return c2(DEFAULT_CURSOR_OP,this.cursor,0)!=0;}}
            @Override public DefaultEntry next(){synchronized(DefaultMap.this){return this.read(1);}}
            @Override public void remove(){synchronized(DefaultMap.this){long leaf=c2(DEFAULT_CURSOR_OP,this.cursor,2);if(leaf<0){this.nativeFailure();throw new IllegalStateException();}DefaultMap.this.bindings.remove(leaf);if(this.live&&this.reusable==null&&this.lastReturned!=null)((HashEntry)this.lastReturned).index=-1;}}
            @Override public void forEachRemaining(java.util.function.Consumer<? super it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>,Object>> action) {
                boolean hashed=DefaultMap.this.hashed();int end=DefaultMap.this.size();
                while(hashed?this.hasNext():c2(DEFAULT_CURSOR_OP,this.cursor,7)<end) {
                    DefaultEntry entry;synchronized(DefaultMap.this){entry=this.read(6);}
                    action.accept(entry);
                    c2(DEFAULT_CURSOR_OP,this.cursor,8);
                }
            }
        }
        @Override public it.unimi.dsi.fastutil.objects.ReferenceSet<DataComponentType<?>> keySet(){return this.keys;}
        private DataComponentType<?> keyAt(int index){DefaultBinding b=this.at(index);return b==null?null:b.type();}
        private final class Keys extends it.unimi.dsi.fastutil.objects.AbstractReferenceSet<DataComponentType<?>> {
            @Override public int size(){return DefaultMap.this.size();}
            @Override public boolean contains(Object key){return DefaultMap.this.containsKey(key);}
            @Override public boolean remove(Object key){boolean present=this.contains(key);if(present)DefaultMap.this.remove(key);return present;}
            @Override public void clear(){DefaultMap.this.clear();}
            @Override public void forEach(java.util.function.Consumer<? super DataComponentType<?>> action){
                if(!DefaultMap.this.hashed()){int end=this.size();for(int i=0;i<end;i++)action.accept(DefaultMap.this.keyAt(i));}
                else {if(DefaultMap.this.containsKey(null))action.accept(DefaultMap.this.keyAt(DefaultMap.this.capacity()));for(int i=DefaultMap.this.capacity();i--!=0;){DataComponentType<?> key=DefaultMap.this.keyAt(i);if(key!=null)action.accept(key);}}
            }
            @Override public it.unimi.dsi.fastutil.objects.ObjectIterator<DataComponentType<?>> iterator(){
                Cursor cursor=DefaultMap.this.entries.cursor(true);
                return new it.unimi.dsi.fastutil.objects.ObjectIterator<>() {
                    public boolean hasNext(){return cursor.hasNext();}
                    public DataComponentType<?> next(){return cursor.next().getKey();}
                    public void remove(){cursor.remove();}
                    @Override public int skip(int n) {
                        // Original hash MapIterator accepts negative n; array iterators reject it.
                        if (n < 0 && !DefaultMap.this.hashed()) throw new IllegalArgumentException("Argument must be nonnegative: " + n);
                        int remaining = n;
                        while (remaining-- != 0 && this.hasNext()) this.next();
                        return n - remaining - 1;
                    }
                    public void forEachRemaining(java.util.function.Consumer<? super DataComponentType<?>> action){cursor.forEachRemaining(e->action.accept(e.getKey()));}
                };
            }
            @Override public it.unimi.dsi.fastutil.objects.ObjectSpliterator<DataComponentType<?>> spliterator(){
                return DefaultMap.this.hashed()?new HashKeysSpliterator():new ArrayKeysSpliterator(0,this.size());
            }
        }
        private final class ArrayKeysSpliterator extends it.unimi.dsi.fastutil.objects.ObjectSpliterators.EarlyBindingSizeIndexBasedSpliterator<DataComponentType<?>> {
            ArrayKeysSpliterator(int pos,int max){super(pos,max);}
            @Override public int characteristics(){return 16465;}
            @Override protected DataComponentType<?> get(int index){return DefaultMap.this.keyAt(index);}
            @Override protected ArrayKeysSpliterator makeForSplit(int pos,int max){return new ArrayKeysSpliterator(pos,max);}
            @Override public void forEachRemaining(java.util.function.Consumer<? super DataComponentType<?>> action){int end=DefaultMap.this.size();while(this.pos<end)action.accept(DefaultMap.this.keyAt(this.pos++));}
        }
        private final class HashKeysSpliterator implements it.unimi.dsi.fastutil.objects.ObjectSpliterator<DataComponentType<?>> {
            int pos,max,count;boolean nullFirst,split;
            HashKeysSpliterator(){this(0,DefaultMap.this.capacity(),DefaultMap.this.containsKey(null),false);}
            HashKeysSpliterator(int pos,int max,boolean nullFirst,boolean split){this.pos=pos;this.max=max;this.nullFirst=nullFirst;this.split=split;}
            @Override public int characteristics(){return this.split?1:65;}
            @Override public long estimateSize(){long remaining=DefaultMap.this.size()-this.count;if(!this.split)return remaining;int real=DefaultMap.this.size()-(DefaultMap.this.containsKey(null)?1:0);return Math.min(remaining,(long)((double)real/DefaultMap.this.capacity()*(this.max-this.pos))+(this.nullFirst?1:0));}
            @Override public HashKeysSpliterator trySplit(){if(this.pos>=this.max-1)return null;int half=(this.max-this.pos)>>1;if(half<=1)return null;int mid=this.pos+half;var child=new HashKeysSpliterator(this.pos,mid,this.nullFirst,true);this.pos=mid;this.nullFirst=false;this.split=true;return child;}
            @Override public boolean tryAdvance(java.util.function.Consumer<? super DataComponentType<?>> action){
                if(this.nullFirst){this.nullFirst=false;this.count++;action.accept(DefaultMap.this.keyAt(DefaultMap.this.capacity()));return true;}
                while(this.pos<this.max){DataComponentType<?> key=DefaultMap.this.keyAt(this.pos);if(key!=null){this.count++;this.pos++;action.accept(key);return true;}this.pos++;}return false;
            }
            @Override public void forEachRemaining(java.util.function.Consumer<? super DataComponentType<?>> action){
                if(this.nullFirst){this.nullFirst=false;this.count++;action.accept(DefaultMap.this.keyAt(DefaultMap.this.capacity()));}
                while(this.pos<this.max){DataComponentType<?> key=DefaultMap.this.keyAt(this.pos);if(key!=null){action.accept(key);this.count++;}this.pos++;}
            }
            @Override public long skip(long n){if(n<0)throw new IllegalArgumentException("Argument must be nonnegative: "+n);if(n==0)return 0;long skipped=0;if(this.nullFirst){this.nullFirst=false;skipped++;n--;}while(this.pos<this.max&&n>0){if(DefaultMap.this.keyAt(this.pos++)!=null){skipped++;n--;}}return skipped;}
        }
        private final class Entries extends it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap.BasicEntrySet<DataComponentType<?>,Object>
                implements it.unimi.dsi.fastutil.objects.Reference2ObjectMap.FastEntrySet<DataComponentType<?>,Object> {
            Entries(){super(DefaultMap.this);}
            private Cursor cursor(boolean fast){synchronized(DefaultMap.this){return adopt(DEFAULT_CURSOR,(h,r)->new Cursor(h,r,fast),DefaultMap.this.handle);}}
            @Override public it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>,Object>> iterator(){return this.cursor(false);}
            @Override public it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>,Object>> fastIterator(){return this.cursor(true);}
            @Override public void fastForEach(java.util.function.Consumer<? super it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>,Object>> action){this.cursor(true).forEachRemaining(action);}
        }
    }

    static long backingForTest(Overlay value) { return value.roots.release.handle.get(); }
    static int bindingCapacityForTest(Overlay value) { return value.roots.values.length; }
    static long handleForTest(Slots value) { return value.handle; }
    static long stackAddressForTest(long handle) { return c1(STACK_ADDRESS, handle); }
    static long handleForTest(Stack value) { return stackOwner(value).handle; }
    static void closeForTest(Patch value) { value.cleanable.clean(); }
    static void closeForTest(Overlay value) { value.cleanable.clean(); }
    static long handleForTest(Overlay value) { return value.handle; }
    static boolean liveForTest(long handle) { return c1(LIVE, handle) != 0; }
    static long cleanupFailuresForTest() { return CLEANUP_FAILURES.get(); }
    private static MethodHandle call(String name, int arguments) {
        var layouts = new java.lang.foreign.MemoryLayout[arguments];
        java.util.Arrays.fill(layouts, ValueLayout.JAVA_LONG);
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_" + name, FunctionDescriptor.of(ValueLayout.JAVA_LONG, layouts));
    }
    private static IllegalStateException failed(Throwable cause) { return new IllegalStateException("Native item structural call failed", cause); }
    private static long c0(MethodHandle h) { try { return checked((long)h.invokeExact()); } catch (Throwable e) { throw failed(e); } }
    private static long c1(MethodHandle h, long a) { try { return checked((long)h.invokeExact(a)); } catch (Throwable e) { throw failed(e); } }
    private static long c2(MethodHandle h, long a, long b) { try { return checked((long)h.invokeExact(a, b)); } catch (Throwable e) { throw failed(e); } }
    private static long c3(MethodHandle h, long a, long b, long c) { try { return checked((long)h.invokeExact(a, b, c)); } catch (Throwable e) { throw failed(e); } }
    private static long raw2(MethodHandle h, long a, long b) { try { return (long)h.invokeExact(a, b); } catch (Throwable e) { throw failed(e); } }
    private static long raw4(MethodHandle h, long a, long b, long c, long d) { try { return (long)h.invokeExact(a, b, c, d); } catch (Throwable e) { throw failed(e); } }
    private static long checked(long result) {
        if (result < -1) throw new IllegalStateException("Native item owner rejected call: " + result);
        return result;
    }

    @FunctionalInterface
    private interface OwnerFactory<T> { T build(long handle, Release ownership); }
    /** Allocate the rollback action before acquiring native ownership, including retains. */
    private static <T> T adopt(MethodHandle create, OwnerFactory<T> factory, long... arguments) {
        Release ownership = new Release(0);
        try {
            long handle = switch (arguments.length) {
                case 0 -> c0(create);
                case 1 -> c1(create, arguments[0]);
                case 2 -> c2(create, arguments[0], arguments[1]);
                case 3 -> c3(create, arguments[0], arguments[1], arguments[2]);
                case 4 -> c4(create, arguments[0], arguments[1], arguments[2], arguments[3]);
                default -> throw new IllegalArgumentException("Unsupported item ownership call arity");
            };
            ownership.handle.set(handle);
            return factory.build(handle, ownership);
        } catch (Throwable failure) { ownership.run(); throw failure; }
    }
    /** Register on the actual shared scope, never on an arena wrapper or facade. */
    private static MemorySegment leaseView(long address, long bytes, Runnable cleanup) {
        Arena arena = Arena.ofAuto();
        CLEANER.register(arena.scope(), cleanup);
        return MemorySegment.ofAddress(address).reinterpret(bytes, arena, null);
    }

    /** No user equals/hashCode calls and no strong global keys, including foreign types/prototypes. */
    private static final class Identities {
        private interface Identity { Object value(); }
        private static boolean same(Identity a, Object b) {
            if (a == b) return true;
            Object value = a.value();
            return value != null && b instanceof Identity other && value == other.value();
        }
        private static final class WeakKey extends WeakReference<Object> implements Identity {
            final int hash;
            final long id;
            WeakKey(Object value, long id) { super(value, QUEUE); this.hash = System.identityHashCode(value); this.id = id; }
            public Object value() { return this.get(); }
            public int hashCode() { return this.hash; }
            public boolean equals(Object other) { return same(this, other); }
        }
        private static final class Lookup implements Identity {
            Object value;
            public Object value() { return this.value; }
            public int hashCode() { return System.identityHashCode(this.value); }
            public boolean equals(Object other) { return same(this, other); }
        }
        private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();
        private static final Map<Identity, WeakKey> IDS = new HashMap<>();
        private static final ThreadLocal<WeakKey[]> CACHE = ThreadLocal.withInitial(() -> new WeakKey[32]);
        private static final ThreadLocal<Lookup> LOOKUP = ThreadLocal.withInitial(Lookup::new);
        private static long next = 1;
        static long of(@Nullable Object value) {
            if (value == null) return 0;
            WeakKey[] cache = CACHE.get(); int hash = System.identityHashCode(value), slot = (hash ^ (hash >>> 16)) & 31;
            WeakKey found = cache[slot];
            if (found != null && found.get() == value) return found.id;
            return find(value, cache, slot);
        }
        private static synchronized long find(Object value, WeakKey[] cache, int slot) {
            for (var dead = QUEUE.poll(); dead != null; dead = QUEUE.poll()) IDS.remove(dead);
            Lookup lookup = LOOKUP.get(); lookup.value = value;
            try {
                WeakKey found = IDS.get(lookup);
                if (found == null) {
                    if (next <= 0) throw new IllegalStateException("Item identity token space exhausted");
                    found = new WeakKey(value, next++); IDS.put(found, found);
                }
                cache[slot] = found; return found.id;
            } finally { lookup.value = null; }
        }
    }

    /** Opaque GC identities only. Native membership decides retirement.
     * Growth is staged before commit; removal never allocates a smaller table. */
    private static final class OpaqueBindings<V> extends it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<V> {
        OpaqueBindings(int expected){super(expected);}
        @Override protected void rehash(int capacity){if(capacity>=this.n)super.rehash(capacity);}
    }
    /** One bag per native backing version. Only native mutation results change its roots. */
    private static final class Roots {
        final long version;
        /** Opaque type roots only; shrinking must never allocate after native commit. */
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<DataComponentType<?>> types=new OpaqueBindings<>(16);
        private final Mutation mutation=new Mutation();
        private java.util.Optional<?>[] values = new java.util.Optional<?>[16];
        private int[] free = new int[16];
        private int freeCount, next = 1, valueCount;
        // This retained handle pins the actual backing, independent of an overlay's COW root.
        private final MemorySegment view;
        private final Release release;
        Roots(long retainedPatch, Release release) {
            try {
                this.version = c1(PATCH_VERSION, retainedPatch);
                this.view = leaseView(c1(PATCH_ADDRESS, retainedPatch), 16, release).asReadOnly();
                this.release = release;
            } catch (Throwable failure) { release.run(); throw failure; }
        }
        static Roots retainPatch(long handle) { return adopt(PATCH_SHARE, Roots::new, handle); }
        static Roots fromOverlay(long handle) { return adopt(OVERLAY_VIEW, Roots::new, handle); }
        Roots copyPatch(long handle) { return adopt(PATCH_SHARE, this::copyOwned, handle); }
        Roots copyOverlay(long handle) { return adopt(OVERLAY_VIEW, this::copyOwned, handle); }
        Roots stageOverlay(long handle){return adopt(OVERLAY_STAGE,(staged,release)->staged==0?null:this.copyOwned(staged,release),handle);}
        private Roots copyOwned(long retainedPatch, Release release) {
            Roots copy = new Roots(retainedPatch, release); copy.types.putAll(this.types);
            copy.values = this.values.clone(); copy.free = java.util.Arrays.copyOf(this.free,Math.max(this.free.length,this.next)); copy.freeCount = this.freeCount;
            copy.next = this.next; copy.valueCount = this.valueCount; return copy;
        }
        /** Opaque binding allocation only. Native mutation results decide whether it is retained. */
        long reserve() {
            if(this.freeCount!=0)return this.free[--this.freeCount];
            if(this.next==Integer.MAX_VALUE)throw new IllegalStateException("Item leaf bindings exhausted");
            int slot=this.next;
            if(slot>=this.values.length){int capacity=Math.multiplyExact(this.values.length,2);java.util.Optional<?>[] values=java.util.Arrays.copyOf(this.values,capacity);int[] free=java.util.Arrays.copyOf(this.free,capacity);this.values=values;this.free=free;}
            this.next=slot+1;return slot;
        }
        private java.util.Optional<?> binding(long token) {
            if (token <= 0 || token >= this.values.length || this.values[(int)token] == null)
                throw new IllegalStateException("Native item leaf lost its root: " + token);
            return this.values[(int)token];
        }
        Object value(long token) { return this.binding(token).orElseThrow(); }
        java.util.Optional<?> optional(long token) { return this.binding(token); }
        void unused(long token) {
            if(token==0)return;
            if(this.values[Math.toIntExact(token)]!=null)throw new IllegalStateException("Retiring live opaque item binding");
            if(this.freeCount>=this.free.length)throw new IllegalStateException("Item retirement storage was not staged");
            this.free[this.freeCount++]=(int)token;
        }
        void prepareClear(){if(this.free.length<this.next)this.free=java.util.Arrays.copyOf(this.free,this.next);}
        void clear() {
            this.types.clear(); java.util.Arrays.fill(this.values, null); this.freeCount = 0; this.valueCount = 0;
            for (int i = 1; i < this.next; i++) this.unused(i);
        }
        /** One reusable publication record, used only under this bag's VM monitor.
         * Native leaf mutations have no callbacks, so no stage crosses reentry. */
        private final class Mutation {
            long key,leaf,old,payload;
            boolean active,typeAttempt,hadType;
            Mutation begin(long key,DataComponentType<?> type,java.util.Optional<?> value,boolean mayInsert){
                if(active)throw new IllegalStateException("Nested item root publication");
                active=true;this.key=key;
                try{
                    this.old=nativeGet(key);
                    if(this.old>0)binding(this.old);
                    prepareClear();
                    // Each positive native leaf belongs to one canonical patch
                    // member; COW clones the complete matching binding bag.
                    if(value!=null){this.leaf=reserve();values[(int)this.leaf]=value;valueCount++;this.payload=payloadDescriptor(value.orElseThrow());}
                    if(key!=0&&mayInsert){this.hadType=types.containsKey(key);if(!this.hadType){this.typeAttempt=true;types.put(key,type);}}
                    return this;
                }catch(Throwable failure){abort();throw failure;}
            }
            void finish(long old,long now){
                if(old!=this.old)throw new IllegalStateException("Item backing changed during publication");
                if(now>0&&now!=this.leaf&&now!=old)throw new IllegalStateException("Unexpected native item leaf");
                if(old>0&&old!=now){
                    values[(int)old]=null;valueCount--;unused(old);
                }
                if(this.leaf>0&&now!=this.leaf){values[(int)this.leaf]=null;valueCount--;unused(this.leaf);}
                if(now==-1&&this.key!=0)types.remove(this.key);
                reset();
            }
            void abort(){
                if(!active)return;
                if(this.leaf>0){values[(int)this.leaf]=null;valueCount--;unused(this.leaf);}
                if(this.typeAttempt&&!this.hadType)types.remove(this.key);
                reset();
            }
            private void reset(){this.key=0;this.leaf=0;this.old=0;this.payload=0;this.typeAttempt=false;this.hadType=false;this.active=false;}
        }
        Mutation stage(long key,DataComponentType<?> type,java.util.Optional<?> value,boolean mayInsert){return this.mutation.begin(key,type,value,mayInsert);}
        int nativeSize() {
            try { return Math.toIntExact((long)VIEW_LONG.getAcquire(this.view, 8L)); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        /** Caller holds this bag's guard, shared by every writer/alias. No callback occurs here. */
        long nativeGet(long key) {
            try {
                long size = (long)VIEW_LONG.getAcquire(this.view, 8L);
                if (size == 0) return -1;
                long pointer = (long)VIEW_LONG.getAcquire(this.view, 0L);
                MemorySegment rows = MemorySegment.ofAddress(pointer).reinterpret(Math.multiplyExact(size, 24L)).asReadOnly();
                for (long offset = 0; offset < size * 24L; offset += 24L) {
                    if (rows.get(ValueLayout.JAVA_LONG, offset) == key)
                        return rows.get(ValueLayout.JAVA_LONG, offset + 8L) == 0 ? 0 : rows.get(ValueLayout.JAVA_LONG, offset + 16L);
                }
                return -1;
            } finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
    }

    /** Cleaner state contains only a handle, never an owner, payload or root bag. */
    private static final class Release implements Runnable {
        private final AtomicLong handle;
        Release(long handle) { this.handle = new AtomicLong(handle); }
        public void run() {
            long handle = this.handle.getAndSet(0);
            if (handle != 0) {
                try { if ((long)RELEASE.invokeExact(handle) != 1) CLEANUP_FAILURES.incrementAndGet(); }
                catch (Throwable failure) { CLEANUP_FAILURES.incrementAndGet(); }
            }
        }
    }
    private abstract static class Owner {
        long handle;
        final Cleaner.Cleanable cleanable;
        final Release release;
        Roots roots;
        Owner(){this.release=new Release(0);this.cleanable=CLEANER.register(this,this.release);}
        Owner(long handle, Roots roots, Release release) {
            if (handle <= 0) throw new IllegalStateException("Invalid item owner: " + handle);
            this.handle = handle; this.roots = roots; this.release = release;
            try{this.cleanable = CLEANER.register(this, release);}catch(Throwable failure){release.run();throw failure;}
        }
        final void acquired(long handle){
            if(handle<=0||this.handle!=0)throw new IllegalStateException("Invalid new item owner: "+handle);
            this.release.handle.set(handle);this.handle=handle;
        }
        void closeForTest() { this.cleanable.clean(); }
        final void checkOpen() { if (this.release.handle.get() == 0) throw new IllegalStateException("Native item owner was released"); }
        final void checkVersion(long version) {
            if (version != this.roots.version) throw new IllegalStateException("Native item/root backing version mismatch");
        }
    }

    record ScalarLeaseForTest(long handle, MemorySegment view) {}
    /** A bound overlay owns stack scalars and components through the same actual native handle. */
    public sealed interface Stack permits Overlay {
        static Stack create(@Nullable Item item, int count, Overlay components) { return components.bindStack(item, count, false); }
        static Stack createEmpty(Overlay components) { return components.bindStack(null, 0, true); }
        @Nullable Item item();
        Overlay components();
        DataComponentMap prototype();
        Stack copyStack();
        int count();
        void count(int value);
        int popTime();
        void popTime(int value);
    }
    private static Overlay stackOwner(Stack stack) { return (Overlay)stack; }
    private static final MethodHandle STACK_SAME=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_stack_same_components",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    /**
     * The component comparison of {@link ItemStack#isSameItemSameComponents} for two
     * non-empty stacks, computed natively: 1 equal, 0 different, or -1 when a
     * component value needs the original Java {@code equals}.
     */
    private static final MethodHandle STACK_SAME_BOUNDED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_item_stack_same_bounded",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG),java.lang.foreign.Linker.Option.critical(false));
    static int sameComponents(Stack left,Stack right){
        try{
            long l=stackOwner(left).checkBound(),r=stackOwner(right).checkBound();
            // Bounded first (no locks held across): -1 retries through the regular entry,
            // -2 means a value needs the original Java equals.
            long value=(long)STACK_SAME_BOUNDED.invokeExact(l,r,TransferIds.AIR);
            if(value==-1)value=(long)STACK_SAME.invokeExact(l,r,TransferIds.AIR);
            return value<0?-1:(int)value;}
        catch(Throwable failure){throw failed(failure);}
        finally{java.lang.ref.Reference.reachabilityFence(left);java.lang.ref.Reference.reachabilityFence(right);}
    }
    static ScalarLeaseForTest scalarLeaseForTest(Stack stack) {
        Overlay allocation = stackOwner(stack);
        try { return adopt(STACK_RETAIN, (handle, ownership) -> new ScalarLeaseForTest(handle,
            leaseView(c1(STACK_ADDRESS, handle), 16, ownership)), allocation.handle); }
        finally { java.lang.ref.Reference.reachabilityFence(allocation); }
    }

    /** Isolated one-cell access lower bound; not the production component-overlay implementation. */
    static final class AtomicCell {
        private record Row(long token, Object value) {}
        private final long handle;
        private final MemorySegment cell;
        private volatile java.util.concurrent.atomic.AtomicReferenceArray<Row> rows = new java.util.concurrent.atomic.AtomicReferenceArray<>(2);
        private int next = 1, freeCount;
        private int[] free = new int[2];
        private long epoch = 1;
        AtomicCell() {
            Release release = new Release(0);
            try {
                this.handle = c1(ATOMIC_NEW, 1); release.handle.set(this.handle);
                this.cell = leaseView(c1(ATOMIC_ADDRESS, this.handle), 8, release);
            } catch (Throwable failure) { release.run(); throw failure; }
        }
        private Row resolve(long token) {
            var rows = this.rows; int index = (int)token;
            if (index < 0 || index >= rows.length()) return null;
            Row row = rows.getAcquire(index); return row != null && row.token() == token ? row : null;
        }
        Object get() {
            MemorySegment allocation = this.cell;
            try {
                for (int retry = 0; retry < 8; retry++) {
                    long token = (long)VIEW_LONG.getAcquire(allocation, 0L);
                    if (token <= 1) return null;
                    Row row = this.resolve(token); if (row != null) return row.value();
                }
                // Internal resolution only. No user callback has occurred and none is replayed.
                synchronized (this) {
                    long token = (long)VIEW_LONG.getAcquire(allocation, 0L); if (token <= 1) return null;
                    Row row = this.resolve(token);
                    if (row == null) throw new IllegalStateException("Unresolved native opaque binding incarnation");
                    return row.value();
                }
            } finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        private int reserve() {
            if (this.freeCount != 0) return this.free[--this.freeCount];
            int index = this.next++;
            if (index == this.rows.length()) {
                var old = this.rows; var grown = new java.util.concurrent.atomic.AtomicReferenceArray<Row>(Math.multiplyExact(old.length(), 2));
                for (int i = 0; i < old.length(); i++) grown.set(i, old.get(i));
                this.rows = grown;
            }
            return index;
        }
        private void retire(long token) {
            if (token <= 1) return;
            int index = (int)token; this.rows.setRelease(index, null);
            if (this.freeCount == this.free.length) this.free = java.util.Arrays.copyOf(this.free, this.free.length * 2);
            this.free[this.freeCount++] = index;
        }
        synchronized Object set(@Nullable Object value) { return this.replace(value, false); }
        synchronized Object tombstone() { return this.replace(null, true); }
        private Object replace(@Nullable Object value, boolean removed) {
            long token = removed ? 1 : 0;
            if (value != null) {
                if (this.epoch >= 0x80000000L) throw new IllegalStateException("Opaque binding incarnation exhausted");
                int index = this.reserve(); token = (this.epoch++ << 32) | Integer.toUnsignedLong(index);
                // Matching heap publication: resolve() uses getAcquire on this same array slot.
                // The immutable row safely publishes its Java payload independently of the native token.
                this.rows.setRelease(index, new Row(token, value));
            }
            // Strongly initialized Java row publication precedes native token publication.
            // Keep this conservative fence in the initial lower-bound safety probe.
            java.lang.invoke.VarHandle.fullFence();
            MemorySegment allocation = this.cell;
            try {
                long old = checked((long)ATOMIC_SWAP.invokeExact(allocation, token));
                Row previous = old <= 1 ? null : this.resolve(old);
                if (old > 1 && previous == null) throw new IllegalStateException("Native swap lost opaque binding");
                this.retire(old); return previous == null ? null : previous.value();
            } catch (Throwable failure) { throw failed(failure); }
            finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        long rawForTest() { try { return (long)VIEW_LONG.getAcquire(this.cell, 0L); } finally { java.lang.ref.Reference.reachabilityFence(this.cell); } }
        void corruptForTest(long token) {
            try { checked((long)ATOMIC_SWAP.invokeExact(this.cell, token)); }
            catch (Throwable failure) { throw failed(failure); }
            finally { java.lang.ref.Reference.reachabilityFence(this.cell); }
        }
        long handleForTest() { return this.handle; }
        int capacityForTest() { return this.rows.length(); }
    }

    /** Enum keys are immutable anchors; membership/null/value state lives only in Rust. */
    public static final class EquipmentMap extends java.util.AbstractMap<net.minecraft.world.entity.EquipmentSlot,ItemStack> {
        private static final net.minecraft.world.entity.EquipmentSlot[] KEYS=net.minecraft.world.entity.EquipmentSlot.values();
        private final long handle;
        private final Cleaner.Cleanable cleanup;
        private it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ItemStack> bindings=new OpaqueBindings<>(9);
        private final java.util.Set<Map.Entry<net.minecraft.world.entity.EquipmentSlot,ItemStack>> entries=new java.util.AbstractSet<>() {
            @Override public int size(){return EquipmentMap.this.size();}
            @Override public void clear(){EquipmentMap.this.clear();}
            @Override public java.util.Iterator<Map.Entry<net.minecraft.world.entity.EquipmentSlot,ItemStack>> iterator(){return new Cursor();}
        };
        private EquipmentMap(long handle,Release release){this.handle=handle;this.cleanup=CLEANER.register(this,release);}
        private long op(int operation,int index,long value) {
            try {return checked((long)EQUIPMENT_OP.invokeExact(handle,(long)operation,(long)index,value,RESULT.get()));}
            catch(Throwable failure){throw failed(failure);}
            finally {java.lang.ref.Reference.reachabilityFence(this);}
        }
        private ItemStack resolve(long token){return token<=0?null:Objects.requireNonNull(bindings.get(token),"Missing equipment facade binding");}
        @Override public synchronized int size(){return Math.toIntExact(op(4,0,0));}
        @Override public synchronized boolean containsKey(Object key){return key instanceof net.minecraft.world.entity.EquipmentSlot slot&&op(3,slot.ordinal(),0)!=0;}
        @Override public synchronized ItemStack get(Object key){return key instanceof net.minecraft.world.entity.EquipmentSlot slot?resolve(op(0,slot.ordinal(),0)):null;}
        @Override public synchronized ItemStack getOrDefault(Object key,ItemStack fallback){if(!(key instanceof net.minecraft.world.entity.EquipmentSlot slot))return fallback;long token=op(0,slot.ordinal(),0);return token==-1?fallback:resolve(token);}
        @Override public synchronized ItemStack put(net.minecraft.world.entity.EquipmentSlot key,ItemStack value) {
            if(key==null){new java.util.EnumMap<net.minecraft.world.entity.EquipmentSlot,ItemStack>(net.minecraft.world.entity.EquipmentSlot.class).put(key,value);throw new AssertionError();}
            long next=value==null?0:stackOwner(value.state).handle;ItemStack previousBinding=next==0?null:bindings.get(next);
            if(next!=0){if(previousBinding!=null&&previousBinding!=value)throw new IllegalStateException("Equipment facade identity collision");bindings.put(next,value);}
            long old;
            try {old=op(1,key.ordinal(),next);}
            catch(Throwable failure){if(next!=0){if(previousBinding==null)bindings.remove(next);else bindings.put(next,previousBinding);}throw failure;}
            ItemStack result=resolve(old);long retired=RESULT.get().get(ValueLayout.JAVA_LONG,0);if(retired>0)bindings.remove(retired);return result;
        }
        @Override public synchronized ItemStack remove(Object key) {
            if(!(key instanceof net.minecraft.world.entity.EquipmentSlot slot))return null;
            long old=op(2,slot.ordinal(),0);ItemStack result=resolve(old);long retired=RESULT.get().get(ValueLayout.JAVA_LONG,0);if(retired>0)bindings.remove(retired);return result;
        }
        @Override public synchronized void clear(){var staged=new OpaqueBindings<ItemStack>(9);op(5,0,0);bindings=staged;}
        public synchronized void clearValues(){
            var staged=new OpaqueBindings<ItemStack>(9);long empty=stackOwner(ItemStack.EMPTY.state).handle;
            if(size()!=0)staged.put(empty,ItemStack.EMPTY);op(6,0,empty);bindings=staged;
        }
        @Override public java.util.Set<Map.Entry<net.minecraft.world.entity.EquipmentSlot,ItemStack>> entrySet(){return entries;}
        private final class EquipmentEntry implements Map.Entry<net.minecraft.world.entity.EquipmentSlot,ItemStack> {
            private int index;EquipmentEntry(int index){this.index=index;}
            private void check(){if(index<0)throw new IllegalStateException("Entry was removed");}
            public net.minecraft.world.entity.EquipmentSlot getKey(){check();return KEYS[index];}
            public ItemStack getValue(){check();return EquipmentMap.this.get(KEYS[index]);}
            public ItemStack setValue(ItemStack value){check();return EquipmentMap.this.put(KEYS[index],value);}
            @Override public int hashCode(){return getKey().hashCode()^Objects.hashCode(getValue());}
            @Override public boolean equals(Object value){return value instanceof Map.Entry<?,?> entry&&Objects.equals(getKey(),entry.getKey())&&Objects.equals(getValue(),entry.getValue());}
            @Override public String toString(){return getKey()+"="+getValue();}
        }
        private final class Cursor implements java.util.Iterator<Map.Entry<net.minecraft.world.entity.EquipmentSlot,ItemStack>> {
            int next,last=-1;EquipmentEntry entry;
            public boolean hasNext(){synchronized(EquipmentMap.this){long index=op(7,next,0);next=index==-1?KEYS.length:(int)index;return index!=-1;}}
            public EquipmentEntry next(){synchronized(EquipmentMap.this){if(!hasNext())throw new java.util.NoSuchElementException();last=next++;return entry=new EquipmentEntry(last);}}
            public void remove(){synchronized(EquipmentMap.this){if(last<0)throw new IllegalStateException();EquipmentMap.this.remove(KEYS[last]);entry.index=-1;last=-1;}}
        }
    }
    public static Map<net.minecraft.world.entity.EquipmentSlot,ItemStack> newEquipmentMap(Map<net.minecraft.world.entity.EquipmentSlot,ItemStack> source) {
        var map=adopt(EQUIPMENT_NEW,EquipmentMap::new,net.minecraft.world.entity.EquipmentSlot.values().length);map.putAll(source);return map;
    }
    static long handleForTest(EquipmentMap map){return map.handle;}

    /** Actual menu roster/carried owner. Bindings resolve only opaque facade identities. */
    public static final class Menu extends java.util.AbstractList<net.minecraft.world.inventory.Slot> {
        private final long handle,address,rowsAddress,ownerAddress;
        private final Cleaner.Cleanable cleanup;
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.inventory.Slot> bindings=new OpaqueBindings<>(16);
        /**
         * The slot facades in list order, replaced after each successful edit (the
         * only way the native rows change), so get() is an array read rather than a
         * native lookup per slot; the per-tick broadcast reads every slot.
         */
        private volatile net.minecraft.world.inventory.Slot[] mirror=new net.minecraft.world.inventory.Slot[0];
        private ItemStack carriedRoot;
        private Menu(long handle,Release release,ItemStack carried){this.handle=handle;this.address=c1(MENU_ADDRESS,handle);this.rowsAddress=c1(MENU_ROWS_ADDRESS,handle);this.ownerAddress=c1(MENU_OWNER_ADDRESS,handle);this.carriedRoot=carried;this.cleanup=CLEANER.register(this,release);}
        public static Menu create(){return create(1);}
        public static Menu create(int kind){return adopt(MENU_NEW,(handle,release)->new Menu(handle,release,ItemStack.EMPTY),stackOwner(ItemStack.EMPTY.state).handle,kind);}
        public int chestRows(){try{return (int)COUNT.getOpaque(SCALAR_RAW,this.rowsAddress);}finally{java.lang.ref.Reference.reachabilityFence(this);}}
        private MemorySegment digests;
        /** One native call: the broadcast snapshot (SlotLink#broadcastDigest) of rows {@code from..} into {@code into}, -1 when unlinked. */
        public synchronized long[] broadcastDigests(long[] into,int from){
            int count=into.length;
            if(this.digests==null||this.digests.byteSize()<count*8L)this.digests=Arena.ofAuto().allocate(Math.max(64,count)*8L,8);
            try{c4(MENU_DIGESTS,this.handle,this.digests.address(),from,count);MemorySegment.copy(this.digests,ValueLayout.JAVA_LONG,from*8L,into,from,count-from);return into;}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        public void chestRows(int rows){try{COUNT.setOpaque(SCALAR_RAW,this.rowsAddress,rows);}finally{java.lang.ref.Reference.reachabilityFence(this);}}
        public boolean action(int index,boolean repeated,MenuActionEffects effects){
            var previous=MENU_ACTION_EFFECTS.get();long result;
            try{MENU_ACTION_EFFECTS.set(effects);result=(long)MENU_ACTION.invokeExact(this.handle,(long)index,TransferIds.AIR,repeated?1L:0L,MenuActionCallback.STUB);}
            catch(Throwable failure){throw failed(failure);}
            finally{if(previous==null)MENU_ACTION_EFFECTS.remove();else MENU_ACTION_EFFECTS.set(previous);java.lang.ref.Reference.reachabilityFence(effects);java.lang.ref.Reference.reachabilityFence(this);}
            if(effects.failure!=null)NativeItemState.<RuntimeException>throwOriginal(effects.failure);
            if(result<0)throw new IllegalStateException("Native menu action failed: "+result);return result!=0;
        }
        @Override public int size(){try{return (int)COUNT.getOpaque(SCALAR_RAW,this.address+8);}finally{java.lang.ref.Reference.reachabilityFence(this);}}
        public boolean emptySource(int index,ContainerCell clearChest){
            try{return (long)MENU_EMPTY_BOUNDED.invokeExact(this.ownerAddress,(long)index,TransferIds.AIR,clearChest==null?0L:clearChest.ownerAddress)!=0;}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(clearChest);java.lang.ref.Reference.reachabilityFence(this);}
        }
        public boolean matchesStock(ContainerCell chest,NativeItemSlots inventory,boolean reverse){
            try{return c4(MENU_STOCK,this.handle,chest.handle,inventory==null?0:inventory.actualOwner().handle,reverse?1:0)!=0;}
            finally{java.lang.ref.Reference.reachabilityFence(chest);java.lang.ref.Reference.reachabilityFence(inventory);java.lang.ref.Reference.reachabilityFence(this);}
        }
        /** Six-row layout over a double chest's two cells and the inventory (menu.rs matches_double). */
        public boolean matchesDouble(ContainerCell first,ContainerCell second,NativeItemSlots inventory){
            try{return c4(MENU_DOUBLE,this.handle,first.handle,second.handle,inventory.actualOwner().handle)!=0;}
            finally{java.lang.ref.Reference.reachabilityFence(first);java.lang.ref.Reference.reachabilityFence(second);java.lang.ref.Reference.reachabilityFence(inventory);java.lang.ref.Reference.reachabilityFence(this);}
        }
        public long version(){try{return (long)VIEW_LONG.getOpaque(SCALAR_RAW,this.address);}finally{java.lang.ref.Reference.reachabilityFence(this);}}
        public synchronized void changed(){
            try{long old;do{old=version();if(old==Long.MAX_VALUE)return;}while(!VIEW_LONG.compareAndSet(SCALAR_RAW,this.address,old,old+1));}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        private void checkIndex(int index,boolean adding){int size=size();if(index<0||(adding?index>size:index>=size))throw new IndexOutOfBoundsException(adding?"Index: "+index+", Size: "+size:"Index "+index+" out of bounds for length "+size);}
        private long facade(int index){
            try{long value=(long)MENU_FACADE_BOUNDED.invokeExact(this.ownerAddress,(long)index);return value>=0?value:c2(MENU_GET,this.handle,index);}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        @Override public net.minecraft.world.inventory.Slot get(int index){
            var slots=this.mirror;
            if(index<0||index>=slots.length)throw new IndexOutOfBoundsException("Index "+index+" out of bounds for length "+slots.length);
            return slots[index];
        }
        private net.minecraft.world.inventory.Slot edit(int operation,int index,net.minecraft.world.inventory.Slot value){
            checkIndex(index,operation==1);
            long previousToken=operation==1?0:this.facade(index);long previousKey=previousToken;
            var previous=operation==1?null:Objects.requireNonNull(bindings.get(previousKey),"Missing previous menu slot binding");
            long nextToken=operation==2?0:Identities.of(value);long nextKey=nextToken;
            var oldBinding=operation==2?null:bindings.get(nextKey);
            if(oldBinding!=null&&oldBinding!=value)throw new IllegalStateException("Menu facade identity collision");
            var link=operation==2?null:value.nativeLinkForMenu();boolean staged=false;
            // The projection is allocated before the irreversible native edit and published
            // immediately after it succeeds, so get() never disagrees with the native rows:
            // a failure before the edit leaves both unchanged, and nothing can fail between.
            var slots=this.mirror;var next=switch(operation){
                case 0->{var copy=slots.clone();copy[index]=value;yield copy;}
                case 1->{var copy=new net.minecraft.world.inventory.Slot[slots.length+1];System.arraycopy(slots,0,copy,0,index);copy[index]=value;System.arraycopy(slots,index,copy,index+1,slots.length-index);yield copy;}
                default->{var copy=new net.minecraft.world.inventory.Slot[slots.length-1];System.arraycopy(slots,0,copy,0,index);System.arraycopy(slots,index+1,copy,index,slots.length-index-1);yield copy;}
            };
            MemorySegment out=RESULT.get();long retired;
            try{
                if(operation!=2){staged=true;bindings.put(nextKey,value);}
                checked((long)MENU_EDIT.invokeExact(this.handle,(long)operation,(long)index,nextToken,link==null?0L:link.handle,out));
                this.mirror=next;
                retired=out.get(ValueLayout.JAVA_LONG,0);
            }catch(Throwable failure){
                if(staged){if(oldBinding==null)bindings.remove(nextKey);else bindings.put(nextKey,oldBinding);}
                if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw failed(failure);
            }finally{java.lang.ref.Reference.reachabilityFence(link);java.lang.ref.Reference.reachabilityFence(this);}
            if(retired!=0){if(retired!=previousToken)throw new IllegalStateException("Wrong retired menu slot binding");bindings.remove(previousKey);}
            return previous;
        }
        @Override public synchronized net.minecraft.world.inventory.Slot set(int index,net.minecraft.world.inventory.Slot value){Objects.requireNonNull(value);return edit(0,index,value);}
        @Override public synchronized void add(int index,net.minecraft.world.inventory.Slot value){Objects.requireNonNull(value);edit(1,index,value);}
        @Override public synchronized net.minecraft.world.inventory.Slot remove(int index){return edit(2,index,null);}
        ScalarLeaseForTest headerLeaseForTest(){
            try{return adopt(MENU_RETAIN,(handle,release)->new ScalarLeaseForTest(handle,leaseView(c1(MENU_ADDRESS,handle),24,release).asReadOnly()),this.handle);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        public synchronized ItemStack carried(){
            try{long token=(long)VIEW_LONG.getOpaque(SCALAR_RAW,this.address+16);if(token!=(carriedRoot==null?0:stackOwner(carriedRoot.state).handle))throw new IllegalStateException("Native carried facade binding mismatch");return carriedRoot;}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        public synchronized void carried(ItemStack value){
            try{c2(MENU_CARRIED,this.handle,value==null?0:stackOwner(value.state).handle);this.carriedRoot=value;}
            finally{java.lang.ref.Reference.reachabilityFence(value);java.lang.ref.Reference.reachabilityFence(this);}
        }
    }
    static long handleForTest(Menu menu){return menu.handle;}

    private record ContainerBinding(long token,net.minecraft.core.NonNullList<ItemStack> facade) {}
    /** One opaque identity anchor for the backing selected by the actual native cell. */
    public static final class ContainerCell {
        private final long handle,address,ownerAddress;
        private final Cleaner.Cleanable cleanup;
        private ContainerBinding binding;
        private ContainerCell(long handle,Release release,ContainerBinding binding){
            this.handle=handle;this.address=c1(CONTAINER_ADDRESS,handle);this.ownerAddress=c1(CONTAINER_OWNER_ADDRESS,handle);this.binding=binding;this.cleanup=CLEANER.register(this,release);
        }
        private static long rows(net.minecraft.core.NonNullList<ItemStack> facade){return facade instanceof NativeItemSlots nativeSlots?nativeSlots.actualOwner().handle:0;}
        public static ContainerCell create(net.minecraft.core.NonNullList<ItemStack> facade){
            var binding=new ContainerBinding(Identities.of(facade),facade);
            return adopt(CONTAINER_NEW,(handle,release)->new ContainerCell(handle,release,binding),binding.token(),rows(facade));
        }
        public synchronized net.minecraft.core.NonNullList<ItemStack> facade(){
            try{long token=(long)VIEW_LONG.getOpaque(SCALAR_RAW,this.address);
                if(token!=binding.token())throw new IllegalStateException("Native container facade binding mismatch");return binding.facade();
            }finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        public synchronized void replace(net.minecraft.core.NonNullList<ItemStack> facade){
            var staged=new ContainerBinding(Identities.of(facade),facade);long rows=rows(facade);
            try{c3(CONTAINER_SET,this.handle,staged.token(),rows);this.binding=staged;}
            finally{java.lang.ref.Reference.reachabilityFence(this);java.lang.ref.Reference.reachabilityFence(facade);}
        }
        synchronized ItemStack stack(SlotLink link){
            var facade=facade();if(!(facade instanceof NativeItemSlots nativeSlots))return link.chest.getItem(link.index());
            return nativeSlots.actualOwner().readCellStack(link);
        }
        synchronized int presence(SlotLink link){var facade=facade();return facade instanceof NativeItemSlots nativeSlots?nativeSlots.actualOwner().cellPresence(link):-1;}
        SlotLink slot(net.minecraft.world.level.block.entity.NativeStockContainer chest,int index,int offset){return SlotLink.create(this,chest,index,offset);}
    }

    public static final class SlotLink {
        private final long handle,address;
        private final Slots source;
        private final ContainerCell cell;
        private final net.minecraft.world.level.block.entity.NativeStockContainer chest;
        /** Position of the linked chest's slot 0 in the slot's own container (27 for a double chest's second half). */
        private final int offset;
        private final Cleaner.Cleanable cleanup;
        private SlotLink(long handle,Release ownership,Slots source){this(handle,ownership,source,null,null,0);}
        private SlotLink(long handle,Release ownership,Slots source,ContainerCell cell,net.minecraft.world.level.block.entity.NativeStockContainer chest,int offset){
            this.handle=handle;this.source=source;this.cell=cell;this.chest=chest;this.offset=offset;this.address=c1(SLOT_ADDRESS,handle);this.cleanup=CLEANER.register(this,ownership);
        }
        static SlotLink create(ContainerCell cell,net.minecraft.world.level.block.entity.NativeStockContainer chest,int index,int offset){return adopt(SLOT_CONTAINER_NEW,(handle,ownership)->new SlotLink(handle,ownership,null,cell,chest,offset),cell.handle,index);}
        static SlotLink create(Slots source,int index){return adopt(SLOT_NEW,(handle,ownership)->new SlotLink(handle,ownership,source),source.handle,index);}
        public int index(){try{return (int)COUNT.getOpaque(SCALAR_RAW,address);}finally{java.lang.ref.Reference.reachabilityFence(this);}}
        /** The index in the slot's own container: the linked row plus the half offset. */
        public int containerIndex(){return this.index()+this.offset;}
        public ItemStack stack(){return cell==null?source.readLinkedStack(this):chest.nativeSlotsForTransfer()==null?chest.getItem(index()):cell.stack(this);}
        public int presence(){return cell==null?source.linkedPresence(this):chest.nativeSlotsForTransfer()==null?-1:cell.presence(this);}
        /** Exact native snapshot of the linked contents for a broadcast comparison (slots.rs broadcast_digest), or -1. */
        public long broadcastDigest(){
            if(cell!=null&&chest.nativeSlotsForTransfer()==null)return -1;
            try{long value=(long)SLOT_READ_REGULAR.invokeExact(this.handle,TransferIds.AIR,2L);return value<0?-1:value;}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        /**
         * For pending linked contents, a copy built natively for a broadcast
         * comparison and packet, leaving the slot itself unobserved; otherwise
         * null, and the caller performs the original read.
         */
        public ItemStack pendingBroadcastCopy(){
            Slots owner=cell==null?source:chest.nativeSlotsForTransfer() instanceof NativeItemSlots rows?rows.actualOwner():null;
            return owner==null?null:owner.pendingCopy(this.index());
        }
        ScalarLeaseForTest leaseForTest(){try{return adopt(SLOT_RETAIN,(handle,ownership)->new ScalarLeaseForTest(handle,leaseView(c1(SLOT_ADDRESS,handle),4,ownership).asReadOnly()),this.handle);}finally{java.lang.ref.Reference.reachabilityFence(this);}}
    }
    public static SlotLink bindStockSlot(net.minecraft.world.Container container,int index){
        if(container!=null&&container.getClass()==net.minecraft.world.entity.player.Inventory.class&&index>=0&&index<36){
            var source=((net.minecraft.world.entity.player.Inventory)container).nativeMainSlotsForTransfer();
            if(source!=null)return source.bindMainSlot(index);
        }else if(net.minecraft.world.level.block.entity.NativeStockContainer.exact(container)&&index>=0&&index<27){
            var chest=(net.minecraft.world.level.block.entity.NativeStockContainer)container;return chest.nativeContainerForSlots().slot(chest,index,0);
        }else if(container!=null&&container.getClass()==net.minecraft.world.inventory.TransientCraftingContainer.class){
            // An exact crafting grid keeps native rows (TransientCraftingContainer); its slots link them.
            var grid=(net.minecraft.world.inventory.TransientCraftingContainer)container;
            return grid.nativeContainerForSlots()!=null&&index>=0&&index<grid.getContainerSize()?grid.nativeContainerForSlots().slot(grid,index,0):null;
        }else if(container!=null&&container.getClass()==net.minecraft.world.CompoundContainer.class){
            // A double chest of two exact stock halves: link the half's row. Reads and
            // broadcasts use it; writes and setChanged still go through the compound.
            var compound=(net.minecraft.world.CompoundContainer)container;
            if(!net.minecraft.world.level.block.entity.NativeStockContainer.exact(compound.firstContainer())
                ||!net.minecraft.world.level.block.entity.NativeStockContainer.exact(compound.secondContainer()))return null;
            int split=compound.firstContainer().getContainerSize();
            if(index<0||index>=compound.getContainerSize())return null;
            var half=(net.minecraft.world.level.block.entity.NativeStockContainer)(index<split?compound.firstContainer():compound.secondContainer());
            int local=index<split?index:index-split;
            return local<27?half.nativeContainerForSlots().slot(half,local,index-local):null;
        }
        return null;
    }
    static long handleForTest(ContainerCell cell){return cell.handle;}
    static long handleForTest(SlotLink link){return link.handle;}
    /** Fixed-size native membership; this Java bag resolves only native facade tokens. */
    static final class Slots extends java.util.AbstractList<ItemStack> {
        private final long handle;
        private final long ownerAddress;
        private final long inventoryAddress;
        private final int capacity;
        private final Cleaner.Cleanable cleanable;
        private it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ItemStack> roots;
        /** Recipes of natively published contents that no Java code has observed yet. */
        private it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Pending> pending=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private Slots(long handle, int size, ItemStack empty, Release ownership) { this(handle,size,empty,ownership,false); }
        private Slots(long handle, int size, ItemStack empty, Release ownership, boolean inventory) {
            this.handle = handle; this.ownerAddress = c1(SLOTS_OWNER_ADDRESS, handle); this.capacity = size;
            this.inventoryAddress = inventory ? c1(INVENTORY_ADDRESS,handle) : 0;
            this.roots=new OpaqueBindings<>(size+1);
            if(size!=0)this.roots.put(stackOwner(empty.state).handle,empty);
            this.cleanable = CLEANER.register(this, ownership);
        }
        static Slots create(int size, ItemStack empty) {
            return adopt(SLOTS_NEW, (handle, ownership) -> new Slots(handle, size, empty, ownership), size, stackOwner(empty.state).handle);
        }
        static Slots createInventory(ItemStack empty) {
            return adopt(INVENTORY_NEW,(handle,ownership)->new Slots(handle,36,empty,ownership,true),stackOwner(empty.state).handle);
        }
        void bindEquipment(EquipmentMap equipment){
            try{c2(INVENTORY_EQUIPMENT,this.handle,equipment.handle);}
            finally{java.lang.ref.Reference.reachabilityFence(equipment);java.lang.ref.Reference.reachabilityFence(this);}
        }
        private long inventoryAddress() {
            if (this.inventoryAddress == 0) throw new IllegalStateException("Not an inventory owner"); return this.inventoryAddress;
        }
        int selected() {
            try { return (int)COUNT.getOpaque(SCALAR_RAW,this.inventoryAddress()); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        void selected(int selected) {
            try { COUNT.setOpaque(SCALAR_RAW,this.inventoryAddress(),selected); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        void changed() {
            try { COUNT.getAndAdd(SCALAR_RAW,this.inventoryAddress()+4,1); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        int changes() {
            try { return (int)COUNT.getOpaque(SCALAR_RAW,this.inventoryAddress()+4); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        int freeSlot() {
            try { return Math.toIntExact(c3(INVENTORY_QUERY,this.handle,TransferIds.AIR,0)); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        boolean mainEmpty() {
            try { return c3(INVENTORY_QUERY,this.handle,TransferIds.AIR,1)!=0; }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        synchronized void swapSelected(int index) {
            this.checkIndex(index);
            try { c2(INVENTORY_SWAP,this.handle,index); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        ScalarLeaseForTest inventoryLeaseForTest() {
            try { this.inventoryAddress();return adopt(INVENTORY_RETAIN,(handle,ownership)->new ScalarLeaseForTest(handle,
                leaseView(c1(INVENTORY_ADDRESS,handle),8,ownership)),this.handle); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        boolean addInventory(ItemStack source,int index,InventoryAddEffects effects) {
            InventoryAddEffects previous=INVENTORY_ADD_EFFECTS.get();long result;
            try {
                INVENTORY_ADD_EFFECTS.set(effects);
                result=effects.pendingInsertions()
                    ?(long)INVENTORY_ADD_PENDING.invokeExact(this.inventoryAddress,stackOwner(source.state).checkBound(),(long)index,TransferIds.AIR,InventoryAddCallback.STUB)
                    :(long)INVENTORY_ADD.invokeExact(this.inventoryAddress,stackOwner(source.state).checkBound(),(long)index,TransferIds.AIR,InventoryAddCallback.STUB);
            } catch(Throwable failure){throw failed(failure);}
            finally {
                if(previous==null)INVENTORY_ADD_EFFECTS.remove();else INVENTORY_ADD_EFFECTS.set(previous);
                java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);
            }
            if(effects.failure!=null)NativeItemState.<RuntimeException>throwOriginal(effects.failure);
            if(result<0)throw new IllegalStateException("Native inventory add failed: "+result);
            return result!=0;
        }
        int scanInventory(ItemStack source,int mode,InventoryAddEffects effects) {
            InventoryAddEffects previous=INVENTORY_ADD_EFFECTS.get();long result;
            try {
                INVENTORY_ADD_EFFECTS.set(effects);
                long sourceHandle=source==null?0:mode==5?stackOwner(source.state).handle:stackOwner(source.state).checkBound();
                result=(long)INVENTORY_SCAN.invokeExact(this.inventoryAddress,sourceHandle,(long)mode,TransferIds.AIR,InventoryAddCallback.STUB);
            } catch(Throwable failure){throw failed(failure);}
            finally {
                if(previous==null)INVENTORY_ADD_EFFECTS.remove();else INVENTORY_ADD_EFFECTS.set(previous);
                effects.candidate=null;
                java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);
            }
            if(effects.failure!=null)NativeItemState.<RuntimeException>throwOriginal(effects.failure);
            if(result < -1)throw new IllegalStateException("Native inventory scan failed: "+result);
            return Math.toIntExact(result);
        }
        private synchronized ItemStack resolveFacade(long token) {return Objects.requireNonNull(this.roots.get(token),"Missing inventory facade binding");}
        private synchronized ItemStack resolveFacade(int index,long token) {return isPending(token)?this.materialize(index,token):this.resolveFacade(token);}
        /** Records the recipe for contents op 18 published under {@code key}; sets {@code adopted} once recorded. */
        void recordPending(long key,ItemStack source,long retired,long adopted){
            Pending recipe=new Pending(stackOwner(source.state));
            synchronized(this){this.pending.put(key,recipe);VIEW_LONG.set(SCALAR_RAW,adopted,1L);if(retired!=0)this.retire(retired);}
        }
        private void retire(long token){if(isPending(token))this.pending.remove(token);else this.roots.remove(token);}
        int pendingForTest(){synchronized(this){return this.pending.size();}}
        /** The materialization recipe for copies of slot {@code index}'s contents, pending or exposed. */
        private Pending recipeFor(int index){
            long token=this.facade(index);
            if(isPending(token))return Objects.requireNonNull(this.pending.get(token),"Native slot lost its pending contents");
            return new Pending(stackOwner(Objects.requireNonNull(this.roots.get(token),"Native slot lost its facade root").state));
        }
        /** Bitmask of non-empty rows, or -2 (more than 63 rows). */
        long occupiedMask(){
            try{long value=(long)SLOTS_OCCUPIED.invokeExact(this.handle,TransferIds.AIR);return value==-2?-2:checked(value);}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        /** Occupancy bits (1 all empty, 2 all full at max) computed natively, or -2 for the original scan. */
        long occupancy(){
            try{long value=(long)SLOTS_OCCUPANCY.invokeExact(this.handle,TransferIds.AIR);return value==-2?-2:checked(value);}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        /**
         * HopperBlockEntity.addItem(hopper, itemEntity) into these rows from a copy
         * of {@code source} (see mattmc_item_hopper_take); the placed remainder's
         * recipe is recorded first and dropped when nothing was placed.
         */
        synchronized long hopperTake(ItemStack source){
            Pending recipe=new Pending(stackOwner(source.state));long key=pendingKey();this.pending.put(key,recipe);
            MemorySegment out=RESULT.get();long result;
            try{result=(long)HOPPER_TAKE.invokeExact(this.handle,stackOwner(source.state).handle,key,TransferIds.AIR,out);}
            catch(Throwable failure){this.pending.remove(key);throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);}
            if(result<0||((result>>41)&1)==0)this.pending.remove(key);
            if(result==-2)return -2;
            checked(result);long retired=out.get(ValueLayout.JAVA_LONG,0);if(retired!=0)this.retire(retired);
            return result;
        }
        /** Comparator signal of these rows followed by {@code second}'s (a double chest), or -2. */
        int redstoneSignalWith(Slots second){
            try{long value=(long)SLOTS_SIGNAL_PAIR.invokeExact(this.handle,second.handle,TransferIds.AIR);if(value==-2)return -2;return Math.toIntExact(checked(value));}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(second);java.lang.ref.Reference.reachabilityFence(this);}
        }
        /** Comparator signal computed natively from the rows, or -2 for the original computation. */
        int redstoneSignal(){
            try{long value=(long)SLOTS_SIGNAL.invokeExact(this.handle,TransferIds.AIR);if(value==-2)return -2;return Math.toIntExact(checked(value));}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        /** Native stock hopper eject (or, {@code suck}, pull) plan into {@code target} (see mattmc_item_hopper_plan). */
        long hopperPlan(Slots target,boolean suck){return this.hopperPlan(null,target,null,suck?1:0);}
        /**
         * With a double chest's second half on either side ({@code null} when absent), planned as one
         * container. Flags: 1 suck, 2 the target refuses shulker box items through its faces.
         */
        long hopperPlan(@Nullable Slots second,Slots target,@Nullable Slots targetSecond,int flags){
            try{return (long)HOPPER_PLAN.invokeExact(this.handle,second==null?0L:second.handle,target.handle,targetSecond==null?0L:targetSecond.handle,TransferIds.AIR,(long)flags);}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(second);java.lang.ref.Reference.reachabilityFence(target);java.lang.ref.Reference.reachabilityFence(targetSecond);java.lang.ref.Reference.reachabilityFence(this);}
        }
        /**
         * Commits a plan: an empty placement publishes a one-item pending copy
         * whose recipe is recorded before the native commit (and removed if the
         * rows changed). False when the rows no longer match the plan.
         */
        boolean hopperCommit(Slots target,long plan){
            int index=(int)(plan&0xFF),slot=(int)((plan>>8)&0xFF);boolean empty=((plan>>16)&1)!=0;
            synchronized(this){synchronized(target){
                long expected=target.facade(slot),key=0;
                if(empty){Pending recipe=this.recipeFor(index);key=pendingKey();target.pending.put(key,recipe);}
                long result;
                try{result=checked((long)HOPPER_COMMIT.invokeExact(this.handle,target.handle,(long)index,(long)slot,key,expected,TransferIds.AIR));}
                catch(Throwable failure){if(empty)target.pending.remove(key);throw failed(failure);}
                finally{java.lang.ref.Reference.reachabilityFence(target);java.lang.ref.Reference.reachabilityFence(this);}
                if(result==0){if(empty)target.pending.remove(key);return false;}
                if((result&2)!=0)target.retire(expected);
                return true;
            }}
        }
        /**
         * For reading pending contents without exposing them: a native copy,
         * ItemStack.EMPTY when native reports them empty (-4), or null when the
         * row is not pending (or changed) and the caller reads it normally.
         */
        synchronized ItemStack pendingSnapshot(int index){
            if(index<0||index>=this.capacity)return null;
            long key=this.facade(index);if(!isPending(key))return null;
            Pending recipe=Objects.requireNonNull(this.pending.get(key),"Native slot lost its pending contents");
            long handle=raw4(SLOTS_PENDING_COPY,this.handle,index,key,recipe.roots.version);
            if(handle==-3)throw new IllegalStateException("Native pending contents no longer match their component roots");
            if(handle==-4)return ItemStack.EMPTY;
            if(handle<=0)return null;
            try{return ItemStack.materializeNative(net.minecraft.core.component.PatchedDataComponentMap.adoptPendingStack(recipe,handle,c1(STACK_ADDRESS,handle)));}
            catch(Throwable failure){c1(RELEASE,handle);throw failure;}
        }
        synchronized ItemStack pendingCopy(int index){
            if(index<0||index>=this.capacity)return null;
            long key=this.facade(index);if(!isPending(key))return null;
            Pending recipe=Objects.requireNonNull(this.pending.get(key),"Native slot lost its pending contents");
            long handle=raw4(SLOTS_PENDING_COPY,this.handle,index,key,recipe.roots.version);
            if(handle==-3)throw new IllegalStateException("Native pending contents no longer match their component roots");
            if(handle<=0)return null;
            try{return ItemStack.materializeNative(net.minecraft.core.component.PatchedDataComponentMap.adoptPendingStack(recipe,handle,c1(STACK_ADDRESS,handle)));}
            catch(Throwable failure){c1(RELEASE,handle);throw failure;}
        }
        /**
         * The first genuine observation of pending contents: build its one
         * canonical facade around the slot's own stack and bind it. A row that
         * changed meanwhile is re-read; the unbound facade releases its handle.
         */
        private synchronized ItemStack materialize(int index,long key){
            for(;;){
                Pending recipe=Objects.requireNonNull(this.pending.get(key),"Native slot lost its pending contents");
                long handle=raw4(SLOTS_MATERIALIZE,this.handle,index,key,recipe.roots.version);
                if(handle==-3)throw new IllegalStateException("Native pending contents no longer match their component roots");
                if(handle<=0){
                    long token=this.facade(index);
                    if(token==key)throw new IllegalStateException("Native pending contents could not be materialized: "+handle);
                    if(!isPending(token))return Objects.requireNonNull(this.roots.get(token),"Native slot lost its facade root");
                    key=token;continue;
                }
                ItemStack stack;
                try{stack=ItemStack.materializeNative(net.minecraft.core.component.PatchedDataComponentMap.adoptPendingStack(recipe,handle,c1(STACK_ADDRESS,handle)));}
                catch(Throwable failure){c1(RELEASE,handle);throw failure;}
                ItemStack added=this.roots.get(handle);if(added!=null)throw new IllegalStateException("Native stack facade identity collision");
                this.roots.put(handle,stack);long bound;
                try{bound=checked(raw4(SLOTS_BIND,this.handle,index,key,handle));}
                catch(Throwable failure){this.roots.remove(handle);throw failure;}
                finally{java.lang.ref.Reference.reachabilityFence(stack);java.lang.ref.Reference.reachabilityFence(this);}
                if(bound==0){this.roots.remove(handle);continue;}
                if((bound&2)!=0)this.pending.remove(key);
                return stack;
            }
        }
        private long readLink(SlotLink link,int mode){
            boolean held=false;
            try {
                held=VIEW_LONG.compareAndSet(SCALAR_RAW,this.ownerAddress,0L,1L);if(!held)return -1;
                return (long)SLOT_READ.invokeExact(link.address,this.ownerAddress,TransferIds.AIR,(long)mode);
            }catch(Throwable failure){throw failed(failure);}
            finally{if(held)VIEW_LONG.setRelease(SCALAR_RAW,this.ownerAddress,0L);java.lang.ref.Reference.reachabilityFence(link);java.lang.ref.Reference.reachabilityFence(this);}
        }
        private long readCell(SlotLink link,int mode){
            try{long value=(long)SLOT_READ_BOUNDED.invokeExact(link.address,TransferIds.AIR,(long)mode);if(value>=0||isPending(value))return value;
                value=(long)SLOT_READ_REGULAR.invokeExact(link.handle,TransferIds.AIR,(long)mode);if(value<-1&&!isPending(value))throw new IllegalStateException("Native item owner rejected call: "+value);return value;}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(link);java.lang.ref.Reference.reachabilityFence(this);}
        }
        synchronized ItemStack readCellStack(SlotLink link){
            try{long token=readCell(link,0);return token<0?get(link.index()):resolveFacade(token);}
            finally{java.lang.ref.Reference.reachabilityFence(link);java.lang.ref.Reference.reachabilityFence(this);}
        }
        synchronized int cellPresence(SlotLink link){
            try{return Math.toIntExact(readCell(link,1));}
            finally{java.lang.ref.Reference.reachabilityFence(link);java.lang.ref.Reference.reachabilityFence(this);}
        }
        synchronized ItemStack readLinkedStack(SlotLink link){long token=readLink(link,0);return token<0?get(link.index()):resolveFacade(token);}
        synchronized int linkedPresence(SlotLink link){return Math.toIntExact(readLink(link,1));}
        synchronized void clearAll() {
            long empty=stackOwner(ItemStack.EMPTY.state).handle;
            var staged=new OpaqueBindings<ItemStack>(this.capacity+1);
            if(this.capacity!=0)staged.put(empty,ItemStack.EMPTY);
            var stagedPending=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Pending>();
            try {c2(SLOTS_CLEAR,this.handle,empty);this.roots=staged;this.pending=stagedPending;}
            finally {java.lang.ref.Reference.reachabilityFence(this);}
        }
        boolean visitInventory(ItemStack source,int mode,int limit,InventoryVisitEffects effects) {
            InventoryVisitEffects previous=INVENTORY_VISIT_EFFECTS.get();long result;effects.owner=this;
            try {
                INVENTORY_VISIT_EFFECTS.set(effects);
                long sourceHandle=source==null?0:stackOwner(source.state).checkBound();
                result=(long)INVENTORY_VISIT.invokeExact(this.inventoryAddress,sourceHandle,(long)mode,(long)limit,TransferIds.AIR,InventoryVisitCallback.STUB);
            }catch(Throwable failure){throw failed(failure);}
            finally {
                if(previous==null)INVENTORY_VISIT_EFFECTS.remove();else INVENTORY_VISIT_EFFECTS.set(previous);
                java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);effects.owner=null;
            }
            if(effects.failure!=null)NativeItemState.<RuntimeException>throwOriginal(effects.failure);
            if(result<0)throw new IllegalStateException("Native inventory visitor failed: "+result);
            return result!=0;
        }
        private void checkIndex(int index) {
            if (index < 0 || index >= this.capacity) throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + this.capacity);
        }
        @Override public int size() { return this.capacity; }
        private long facade(int index){
            try{long value=(long)SLOTS_FACADE_BOUNDED.invokeExact(this.ownerAddress,(long)index);if(value>=0||isPending(value))return value;
                value=raw2(SLOTS_GET,this.handle,index);if(value<-1&&!isPending(value))throw new IllegalStateException("Native item owner rejected call: "+value);return value;}
            catch(Throwable failure){throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        @Override public synchronized ItemStack get(int index) {
            this.checkIndex(index); long token = this.facade(index);
            if(isPending(token))return this.materialize(index,token);
            ItemStack root=this.roots.get(token);
            if(root==null)throw new IllegalStateException("Native slot lost its facade root: "+token);
            return root;
        }
        /** One native operation owns continuation; Java only executes required effects. */
        boolean runTransfer(ItemStack source, TransferEffects effects) {
            if(this.inventoryAddress==0)DataComponentMap.nativeDefaults(source.state.prototype());
            TransferEffects previous=TRANSFER_EFFECTS.get();long result;
            try{
                TRANSFER_EFFECTS.set(effects);
                result=(long)TRANSFER_RUN.invokeExact(this.handle,stackOwner(source.state).handle,TransferIds.AIR,TransferCallback.STUB);
            }catch(Throwable failure){throw failed(failure);}
            finally{
                if(previous==null)TRANSFER_EFFECTS.remove();else TRANSFER_EFFECTS.set(previous);
                java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);
            }
            if(effects.failure!=null)NativeItemState.<RuntimeException>throwOriginal(effects.failure);
            if(result<0)throw new IllegalStateException("Native complete transfer failed: "+result);return result!=0;
        }
        private long boundedStep(Overlay allocation, DefaultMap defaults, long token) {
            boolean slotsHeld = false, defaultsHeld = false;
            try {
                long sourceAddress = allocation.checkBound();
                slotsHeld = VIEW_LONG.compareAndSet(SCALAR_RAW, this.ownerAddress, 0L, 1L);
                if (!slotsHeld) return 4;
                defaultsHeld = VIEW_LONG.compareAndSet(SCALAR_RAW, defaults.ownerAddress, 0L, 1L);
                if (!defaultsHeld) return 4;
                // Real retained owners and exact canonical gates are captured before any pointer read.
                // Native does no locking, allocation, owner lookup or upcall in this bounded body.
                return (long)TRANSFER_BOUNDED.invokeExact(this.ownerAddress, sourceAddress, defaults.ownerAddress, TransferIds.AIR, token);
            } catch (Throwable failure) { throw failed(failure); }
            finally {
                if (defaultsHeld) VIEW_LONG.setRelease(SCALAR_RAW, defaults.ownerAddress, 0L);
                if (slotsHeld) VIEW_LONG.setRelease(SCALAR_RAW, this.ownerAddress, 0L);
                java.lang.ref.Reference.reachabilityFence(allocation);
                java.lang.ref.Reference.reachabilityFence(defaults.header);
                java.lang.ref.Reference.reachabilityFence(this);
            }
        }
        synchronized long transferStep(ItemStack source, int phase, int index) {
            try {
                long result = (long)TRANSFER_STEP.invokeExact(this.handle, stackOwner(source.state).handle, TransferIds.AIR, (long)phase, (long)index);
                if ((result & 7) == 7) throw new IllegalStateException("Rejected native chest transfer");
                return result;
            } catch (Throwable failure) { throw failed(failure); }
            finally { java.lang.ref.Reference.reachabilityFence(source); java.lang.ref.Reference.reachabilityFence(this); }
        }
        synchronized void commitTransferInsert(ItemStack source,int index,ItemStack inserted,int amount){
            this.checkIndex(index);long next=stackOwner(inserted.state).handle,old=this.facade(index);
            if(!isPending(old))Objects.requireNonNull(this.roots.get(old),"Native slot lost its existing facade root");
            ItemStack added=this.roots.get(next);if(added!=null&&added!=inserted)throw new IllegalStateException("Native stack facade identity collision");
            this.roots.put(next,inserted);long retired;
            try{retired=checked((long)TRANSFER_PUBLISH.invokeExact(this.ownerAddress,stackOwner(source.state).checkBound(),(long)index,stackOwner(inserted.state).checkBound(),next,(long)amount,old));}
            catch(Throwable failure){if(added==null)this.roots.remove(next);throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(inserted);java.lang.ref.Reference.reachabilityFence(this);}
            if(retired!=0)this.retire(old);
        }
        @Override public synchronized ItemStack set(int index,ItemStack value){return this.set(index,value,true);}
        /** {@link #set} for a caller that discards the previous contents: pending contents are retired unobserved. */
        synchronized void replace(int index,ItemStack value){this.set(index,value,false);}
        private ItemStack set(int index,ItemStack value,boolean observed){
            this.checkIndex(index);long next=stackOwner(value.state).handle,old=this.facade(index);
            if(isPending(old)&&observed){this.materialize(index,old);old=this.facade(index);}
            ItemStack previous=isPending(old)?null:Objects.requireNonNull(this.roots.get(old),"Native slot lost its existing facade root");
            if(old==next)return previous;
            ItemStack added=this.roots.get(next);if(added!=null&&added!=value)throw new IllegalStateException("Native stack facade identity collision");
            this.roots.put(next,value);long retired;
            try{retired=checked((long)SLOTS_PUBLISH.invokeExact(this.ownerAddress,(long)index,stackOwner(value.state).checkBound(),next,old));}
            catch(Throwable failure){if(added==null)this.roots.remove(next);throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(value);java.lang.ref.Reference.reachabilityFence(this);}
            if(retired!=0)this.retire(old);return previous;
        }
    }

    public static final class Patch extends Owner {
        private Patch(long handle, Roots roots, Release ownership) { super(handle, roots, ownership); }
        public static Patch create() { return adopt(PATCH_NEW, (h, ownership) -> new Patch(h, Roots.retainPatch(h), ownership)); }
        public synchronized int size() { synchronized (this.roots) { this.checkOpen(); return this.roots.nativeSize(); } }
        @Nullable public synchronized <T> java.util.Optional<T> get(DataComponentType<? extends T> type) {
            synchronized (this.roots) {
                this.checkOpen(); long value = this.roots.nativeGet(Identities.of(type));
                return value == -1 ? null : value == 0 ? java.util.Optional.empty() : (java.util.Optional<T>)this.roots.optional(value);
            }
        }
        @Nullable public synchronized <T> java.util.Optional<T> put(DataComponentType<T> type,java.util.Optional<T> value){
            Objects.requireNonNull(value);
            synchronized(this.roots){this.checkOpen();long key=Identities.of(type);var stage=this.roots.stage(key,type,value.isPresent()?value:null,true);
                try{long old=c4(PATCH_PUT,this.handle,key,stage.leaf,stage.payload);java.util.Optional<T> previous=old==-1?null:old==0?java.util.Optional.empty():(java.util.Optional<T>)this.roots.optional(old);stage.finish(old,stage.leaf);return previous;}
                finally{stage.abort();}
            }
        }
        @Nullable public synchronized <T> java.util.Optional<T> remove(DataComponentType<T> type){
            synchronized(this.roots){long key=Identities.of(type);var stage=this.roots.stage(key,type,null,false);
                try{long old=c2(PATCH_REMOVE,this.handle,key);java.util.Optional<T> previous=old==-1?null:old==0?java.util.Optional.empty():(java.util.Optional<T>)this.roots.optional(old);stage.finish(old,-1);return previous;}
                finally{stage.abort();}
            }
        }
        public synchronized Patch share() { synchronized (this.roots) { return adopt(PATCH_SHARE, (h, ownership) -> new Patch(h, this.roots, ownership), this.handle); } }
        public synchronized Patch snapshot() {
            synchronized (this.roots) { return adopt(PATCH_SNAPSHOT, (h, ownership) -> new Patch(h, this.roots.copyPatch(h), ownership), this.handle); }
        }
        public synchronized void clear() {
            synchronized (this.roots) { this.roots.prepareClear();c1(PATCH_CLEAR, this.handle); this.roots.clear(); }
        }
        public it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> mapView() {
            return new PatchMap(this);
        }
        private synchronized PatchEntry entryAt(int index) {
            synchronized (this.roots) {
                MemorySegment out = RESULT.get();
                long found;
                try { found = checked((long)PATCH_AT.invokeExact(this.handle, (long)index, out)); }
                catch (Throwable failure) { throw failed(failure); }
                if (found == 0) return new PatchEntry(null, null);
                long key = out.get(ValueLayout.JAVA_LONG, 0), value = out.get(ValueLayout.JAVA_LONG, 8);
                DataComponentType<?> type = key == 0 ? null : this.roots.types.get(key);
                if (key != 0 && type == null) throw new IllegalStateException("Native item key lost its root: " + key);
                return new PatchEntry(type, value == 0 ? java.util.Optional.empty() : this.roots.optional(value));
            }
        }
        private synchronized void removeAt(int index) {
            synchronized (this.roots) {
                if (index < 0 || index >= this.size()) throw new IndexOutOfBoundsException("Invalid live item patch cursor: " + index);
                this.remove(this.entryAt(index).getKey());
            }
        }
        synchronized int rootedValuesForTest() { synchronized (this.roots) { return this.roots.valueCount; } }
    }

    private static final class PatchEntry extends it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap.BasicEntry<DataComponentType<?>, java.util.Optional<?>> {
        PatchEntry(DataComponentType<?> key, java.util.Optional<?> value) { super(key, value); }
        void assign(PatchEntry entry) { this.key = entry.key; this.value = entry.value; }
    }

    /** The adapter carries no membership or values. Cursors read the retained backing live. */
    private static final class PatchMap extends it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> {
        private final Patch patch;
        private final Entries entries = new Entries();
        PatchMap(Patch patch) { this.patch = patch; }
        @Override public int size() { return this.patch.size(); }
        @Override public void clear() { this.patch.clear(); }
        @Override public boolean containsKey(Object key) {
            return (key == null || key instanceof DataComponentType<?>) && this.patch.get((DataComponentType<?>)key) != null;
        }
        @Override public java.util.Optional<?> get(Object key) {
            java.util.Optional<?> value = key == null || key instanceof DataComponentType<?> ? this.patch.get((DataComponentType<?>)key) : null;
            return value == null ? this.defRetValue : value;
        }
        @Override public java.util.Optional<?> put(DataComponentType<?> key, java.util.Optional<?> value) {
            java.util.Optional<?> old = this.patch.put((DataComponentType)key, value);
            return old == null ? this.defRetValue : old;
        }
        @Override public java.util.Optional<?> remove(Object key) {
            java.util.Optional<?> old = key == null || key instanceof DataComponentType<?> ? this.patch.remove((DataComponentType<?>)key) : null;
            return old == null ? this.defRetValue : old;
        }
        @Override public boolean containsValue(Object value) {
            for (int i = this.size(); i-- != 0;) if (Objects.equals(this.patch.entryAt(i).getValue(), value)) return true;
            return false;
        }
        @Override public it.unimi.dsi.fastutil.objects.ObjectSet<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> reference2ObjectEntrySet() {
            return this.entries;
        }
        private final class Cursor implements it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> {
            int next;
            int current = -1;
            final PatchEntry reusable;
            Cursor(boolean fast) { this.reusable = fast ? new PatchEntry(null, null) : null; }
            @Override public boolean hasNext() { return this.next < PatchMap.this.size(); }
            private PatchEntry read() {
                this.current = this.next;
                PatchEntry entry = PatchMap.this.patch.entryAt(this.next++);
                if (this.reusable == null) return entry;
                this.reusable.assign(entry); return this.reusable;
            }
            @Override public PatchEntry next() {
                if (!this.hasNext()) throw new java.util.NoSuchElementException();
                return this.read();
            }
            @Override public void remove() {
                if (this.current == -1) throw new IllegalStateException();
                this.current = -1; PatchMap.this.patch.removeAt(--this.next);
            }
            @Override public void forEachRemaining(java.util.function.Consumer<? super it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> action) {
                int end = PatchMap.this.size();
                while (this.next < end) action.accept(this.read());
            }
        }
        private final class Entries extends it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap.BasicEntrySet<DataComponentType<?>, java.util.Optional<?>>
            implements it.unimi.dsi.fastutil.objects.Reference2ObjectMap.FastEntrySet<DataComponentType<?>, java.util.Optional<?>> {
            Entries() { super(PatchMap.this); }
            @Override public it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> iterator() { return new Cursor(false); }
            @Override public it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> fastIterator() { return new Cursor(true); }
            @Override public void forEach(java.util.function.Consumer<? super it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> action) { this.iterator().forEachRemaining(action); }
            @Override public void fastForEach(java.util.function.Consumer<? super it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, java.util.Optional<?>>> action) { this.fastIterator().forEachRemaining(action); }
        }
    }

    /**
     * What a natively published copy needs to become its canonical facade: the
     * source's component roots, prototype and item at publication, exactly what
     * adoption captured. Materialization checks {@code roots.version} against
     * the copy's actual backing before using them.
     */
    public static final class Pending {
        final Roots roots; final DataComponentMap prototype; final Item item;
        Pending(Overlay source) {
            synchronized(source){synchronized(source.roots){this.roots=source.roots;this.prototype=source.prototype;this.item=source.itemAnchor;}}
        }
    }

    public abstract static non-sealed class Overlay extends Owner implements Stack {
        protected final DataComponentMap prototype;
        /** Register handle-only rollback before native acquisition on the actual facade. */
        protected Overlay(DataComponentMap prototype) {
            super();this.prototype=prototype;
            try{this.acquired(c1(OVERLAY_NEW,Identities.of(prototype)));this.roots=Roots.fromOverlay(this.handle);this.bindDefaults();}
            catch(Throwable failure){this.release.run();if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        protected Overlay(DataComponentMap prototype,Patch patch) {
            super();this.prototype=prototype;
            try{synchronized(patch){synchronized(patch.roots){
                this.acquired(c2(OVERLAY_FROM_PATCH,Identities.of(prototype),patch.handle));this.roots=patch.roots;this.bindDefaults();
            }}}
            catch(Throwable failure){this.release.run();if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(patch);java.lang.ref.Reference.reachabilityFence(this);}
        }
        protected Overlay(Overlay source,boolean stackCopy) {
            super();this.prototype=source.prototype;
            try{synchronized(source){synchronized(source.roots){
                if(stackCopy){
                    long sourceAddress=source.checkBound();MemorySegment out=RESULT.get();
                    this.acquired(checked((long)STACK_COPY.invokeExact(sourceAddress,out)));this.roots=source.roots;
                    this.bindAddress(source.itemAnchor,out.get(ValueLayout.JAVA_LONG,0));
                }else{
                    this.acquired(c1(OVERLAY_COPY,source.handle));this.roots=source.roots;this.bindDefaults();
                }
            }}}
            catch(Throwable failure){this.release.run();if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);}
        }
        /**
         * Adopts a copy the native menu/inventory operation already prepared from
         * {@code source} (its registered owner {@code handle} and scalar
         * {@code address}). The release is registered before acquisition, as for
         * an ordinary copy; {@code adopted} records the acquisition so the
         * operation retires an unadopted copy itself.
         */
        protected Overlay(Overlay source,long handle,long address,long adopted) {
            super();this.prototype=source.prototype;
            try{synchronized(source){synchronized(source.roots){
                this.acquired(handle);VIEW_LONG.set(SCALAR_RAW,adopted,1L);this.roots=source.roots;
                this.bindAddress(source.itemAnchor,address);
            }}}
            catch(Throwable failure){this.release.run();if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(source);java.lang.ref.Reference.reachabilityFence(this);}
        }
        /** The canonical facade for natively published contents ({@link Pending}); {@code handle} registers the slot's own stack. */
        protected Overlay(Pending pending,long handle,long address) {
            super();this.prototype=pending.prototype;
            try{synchronized(pending.roots){this.acquired(handle);this.roots=pending.roots;this.bindAddress(pending.item,address);}}
            catch(Throwable failure){this.release.run();if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw failed(failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        private void bindDefaults(){
            DefaultMap defaults=DataComponentMap.nativeDefaults(this.prototype);
            try{if(defaults!=null)c2(OVERLAY_DEFAULTS,this.handle,defaults.handle);}
            finally{java.lang.ref.Reference.reachabilityFence(defaults);}
        }
        private volatile long scalarAddress;
        private Item itemAnchor;
        private void bindAddress(@Nullable Item item, long address) {
            try {
                if (address <= 0 || (address & 7) != 0 || (long)VIEW_LONG.get(SCALAR_RAW,address) != Identities.of(item))
                    throw new IllegalStateException("Native stack lost its immutable item identity");
                this.itemAnchor = item; this.scalarAddress = address;
            } finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        private synchronized Stack bindStack(@Nullable Item item,int count,boolean singleton) {
            try {
                if (this.scalarAddress != 0) throw new IllegalStateException("Component owner already bound to a stack");
                long address = c4(STACK_PROMOTE,this.handle,Identities.of(item),count,singleton ? 1 : 0);
                this.bindAddress(item,address); return this;
            } finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        @Override @Nullable public Item item() { return this.itemAnchor; }
        @Override public Overlay components() { return this; }
        @Override public DataComponentMap prototype() { return this.prototype; }
        @Override public Stack copyStack() {return net.minecraft.core.component.PatchedDataComponentMap.copyNativeStack(this);}
        private long checkBound() {
            long address = this.scalarAddress;
            if (address == 0) throw new IllegalStateException("Component owner has no stack scalars");
            this.checkOpen(); return address;
        }
        @Override public int count() {
            try { return (int)COUNT.getOpaque(SCALAR_RAW,this.checkBound()+8); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        @Override public void count(int value) {
            try { COUNT.setOpaque(SCALAR_RAW,this.checkBound()+8,value); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        @Override public int popTime() {
            try { return (int)COUNT.getOpaque(SCALAR_RAW,this.checkBound()+12); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        @Override public void popTime(int value) {
            try { COUNT.setOpaque(SCALAR_RAW,this.checkBound()+12,value); }
            finally { java.lang.ref.Reference.reachabilityFence(this); }
        }
        public static Overlay create(DataComponentMap prototype) {
            return new net.minecraft.core.component.PatchedDataComponentMap(prototype);
        }
        /** Caller must complete the original sanitization callbacks before selecting this path. */
        public static Overlay fromSanitized(DataComponentMap prototype, Patch patch) {
            return net.minecraft.core.component.PatchedDataComponentMap.fromNativePatch(prototype,patch);
        }
        public static Overlay fromPatchMap(DataComponentMap prototype,
            it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> source) {
            if (source instanceof PatchMap map) return fromSanitized(prototype, map.patch);
            Patch patch = Patch.create(); patch.mapView().putAll(source); return fromSanitized(prototype, patch);
        }
        public void beginPatchMutation() { try { this.prepare(); } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        public synchronized it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, java.util.Optional<?>> patchView() { try {
            synchronized (this.roots) { return adopt(OVERLAY_VIEW, (h, ownership) -> new Patch(h, this.roots, ownership).mapView(), this.handle); }
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        public synchronized boolean hasOverride(DataComponentType<?> type) { try {
            synchronized (this.roots) { this.checkOpen(); return this.roots.nativeGet(Identities.of(type)) != -1; }
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        /** applyPatch performs the prepare phase once, before entering its source iterator. */
        public void applyPrepared(DataComponentType<?> type, java.util.Optional<?> value) { try {
            Object prototypeValue = this.prototype.get(type);
            boolean equal = value.isPresent() && value.get().equals(prototypeValue);
            this.finishApply(type, value, prototypeValue != null, equal);
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        private synchronized void finishApply(DataComponentType<?> type,java.util.Optional<?> value,boolean prototypeExists,boolean equal){try{
            synchronized(this.roots){this.checkOpen();long key=Identities.of(type);boolean retained=value.isPresent()?!equal:prototypeExists;
                var stage=this.roots.stage(key,type,value.isPresent()&&!equal?value:null,retained);MemorySegment out=RESULT.get();
                // Equal present entries are discarded, but must still reach the
                // native present branch rather than the tombstone branch.
                long leaf=value.isPresent()&&equal?PROTOTYPE_VALUE:stage.leaf;
                try{try{checked((long)APPLY.invokeExact(this.handle,key,leaf,prototypeExists?1:0,equal?1:0,stage.payload,out));}catch(Throwable failure){throw failed(failure);}
                    this.checkVersion(out.get(ValueLayout.JAVA_LONG,24));stage.finish(out.get(ValueLayout.JAVA_LONG,8),out.get(ValueLayout.JAVA_LONG,16));
                }finally{stage.abort();}
            }
        }finally{java.lang.ref.Reference.reachabilityFence(this);}}
        private synchronized void prepare() { try {
            synchronized (this.roots) {
                if(c1(OVERLAY_NEEDS_COPY,this.handle)==0)return;
                Roots staged=this.roots.stageOverlay(this.handle);
                if(staged!=null){boolean committed=false;
                    try{if(c3(OVERLAY_COMMIT,this.handle,this.roots.version,staged.release.handle.get())!=1)throw new IllegalStateException("Item component backing changed during root staging");this.roots=staged;committed=true;}
                    finally{if(!committed)staged.release.run();}
                }
            }
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        @Nullable public <T> T get(DataComponentType<? extends T> type) { try {
            synchronized (this) { synchronized (this.roots) {
                this.checkOpen(); long value = this.roots.nativeGet(Identities.of(type));
                if (value != -1) return value == 0 ? null : (T)this.roots.value(value);
            } }
            return this.prototype.get(type);
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        @Nullable public <T> T set(DataComponentType<T> type, @Nullable T value) { try {
            this.prepare();
            T prototypeValue = this.prototype.get(type);
            boolean equal = Objects.equals(value, prototypeValue);
            // No owner/root/native lock is held over the two original callbacks above.
            return this.finishSet(type, value, prototypeValue, equal);
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        @Nullable private synchronized <T> T finishSet(DataComponentType<T> type,@Nullable T value,@Nullable T prototypeValue,boolean equal){try{
            synchronized(this.roots){this.checkOpen();long key=Identities.of(type),proto=prototypeValue==null?0:PROTOTYPE_VALUE;
                var stage=this.roots.stage(key,type,value!=null&&!equal?java.util.Optional.of(value):null,!equal);MemorySegment out=RESULT.get();
                try{try{checked((long)SET.invokeExact(this.handle,key,stage.leaf,proto,equal?1:0,stage.payload,out));}catch(Throwable failure){throw failed(failure);}
                    long returned=out.get(ValueLayout.JAVA_LONG,0);this.checkVersion(out.get(ValueLayout.JAVA_LONG,24));T previous=returned==0?null:returned==proto?prototypeValue:(T)this.roots.value(returned);
                    stage.finish(out.get(ValueLayout.JAVA_LONG,8),out.get(ValueLayout.JAVA_LONG,16));return previous;
                }finally{stage.abort();}
            }
        }finally{java.lang.ref.Reference.reachabilityFence(this);}}
        @Nullable public <T> T remove(DataComponentType<? extends T> type) { try {
            this.prepare(); T prototypeValue = this.prototype.get(type);
            return this.finishRemove(type, prototypeValue);
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        @Nullable private synchronized <T> T finishRemove(DataComponentType<? extends T> type,@Nullable T prototypeValue){try{
            synchronized(this.roots){long key=Identities.of(type),proto=prototypeValue==null?0:PROTOTYPE_VALUE;var stage=this.roots.stage(key,type,null,prototypeValue!=null);MemorySegment out=RESULT.get();
                try{try{checked((long)REMOVE.invokeExact(this.handle,key,proto,out));}catch(Throwable failure){throw failed(failure);}
                    long returned=out.get(ValueLayout.JAVA_LONG,0);this.checkVersion(out.get(ValueLayout.JAVA_LONG,24));T previous=returned==0?null:returned==proto?prototypeValue:(T)this.roots.value(returned);
                    stage.finish(out.get(ValueLayout.JAVA_LONG,8),out.get(ValueLayout.JAVA_LONG,16));return previous;
                }finally{stage.abort();}
            }
        }finally{java.lang.ref.Reference.reachabilityFence(this);}}
        public Overlay copy() {return net.minecraft.core.component.PatchedDataComponentMap.copyNativeComponents(this);}
        @Nullable public synchronized Patch nativePatchSnapshot() { try {
            synchronized (this.roots) { return adopt(OVERLAY_PATCH, (h, ownership) -> h == 0 ? null : new Patch(h, this.roots, ownership), this.handle); }
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        public synchronized void clear() { try {
            this.prepare();
            synchronized(this.roots){this.roots.prepareClear();c1(OVERLAY_CLEAR,this.handle);this.roots.clear();}
        } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        public synchronized int patchSize() { try { synchronized (this.roots) { this.checkOpen(); return this.roots.nativeSize(); } } finally { java.lang.ref.Reference.reachabilityFence(this); } }
        synchronized int rootedValuesForTest() { synchronized (this.roots) { return this.roots.valueCount; } }
    }
    private static final Object ACCESS_NULL_KEY = new Object();
    private static long accessIdentity(DataComponentType<?> type) { return Identities.of(type == null ? ACCESS_NULL_KEY : type); }
    private static final MethodHandle ACCESS_NEW = call("access_create", 1), ACCESS_VIEW = call("access_view", 1),
        ACCESS_ESCAPE = call("access_escape", 1), ACCESS_COPY = call("access_copy", 1), ACCESS_PREPARE = call("access_prepare", 1),
        ACCESS_VERSION = call("access_version", 1), ACCESS_ADDRESS = call("access_address", 1), ACCESS_FINISH = call("access_finish", 4),
        ACCESS_PATCH_PUT = call("access_patch_put", 3), ACCESS_STAGE = call("access_stage", 1),
        ACCESS_COMMIT = call("access_commit", 3), ACCESS_COW_ADDRESS = call("access_cow_address", 1);
    // Private absolute views require the actual root/cell scope to be pinned by every caller.
    private static final MemorySegment ACCESS_RAW = MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);
    private static long cellLong(long address, long offset) { return (long)VIEW_LONG.getAcquire(ACCESS_RAW, address + offset); }
    private static final MethodHandle ACCESS_CELL_RETAIN = call("access_cell_retain", 2), ACCESS_CELL_ADDRESS = call("access_cell_address", 1);
    private static final MethodHandle ACCESS_REPLACE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_access_replace",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), java.lang.foreign.Linker.Option.critical(false));
    private static long replaceExisting(long address, long version, long key, long incarnation, long expected, long next, MemorySegment out) {
        try { return checked((long)ACCESS_REPLACE.invokeExact(address, version, key, incarnation, expected, next, out)); }
        catch (Throwable failure) { throw failed(failure); }
    }
    private static final MethodHandle ACCESS_AT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_access_at",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static long c4(MethodHandle h, long a, long b, long c, long d) {
        try { return checked((long)h.invokeExact(a, b, c, d)); } catch (Throwable failure) { throw failed(failure); }
    }
    /** Experimental dynamic-key owner. No production caller selects this path. */
    static final class AccessRoots {
        private record Row(long token, DataComponentType<?> type, java.util.Optional<?> value) {}
        private static final Object ABSENT = new Object();
        final long version;
        final Release ownership;
        final MemorySegment header;
        final Map<Long, DataComponentType<?>> types;
        private volatile java.util.concurrent.atomic.AtomicReferenceArray<Row> rows;
        private int next = 1, freeCount;
        private int[] free = new int[16];
        private long epoch = 1;
        AccessRoots(long handle, Release ownership, @Nullable AccessRoots source) {
            this.version = c1(ACCESS_VERSION, handle); this.nativeHandle = handle; this.ownership = ownership;
            this.header = leaseView(c1(ACCESS_ADDRESS, handle), 64, ownership).asReadOnly();
            this.types = source == null ? new HashMap<>() : new HashMap<>(source.types);
            this.rows = new java.util.concurrent.atomic.AtomicReferenceArray<>(source == null ? 16 : source.rows.length());
            if (source != null) {
                for (int i = 0; i < this.rows.length(); i++) this.rows.setRelease(i, source.rows.getAcquire(i));
                this.next = source.next; this.freeCount = source.freeCount; this.free = source.free.clone(); this.epoch = source.epoch;
            }
        }
        static AccessRoots retain(long owner, @Nullable AccessRoots source) {
            return adopt(ACCESS_VIEW, (h, release) -> new AccessRoots(h, release, source), owner);
        }
        static AccessRoots stage(long owner, AccessRoots source) {
            return adopt(ACCESS_STAGE, (h, release) -> h == 0 ? null : new AccessRoots(h, release, source), owner);
        }
        /** Native inline cells select the opaque row/type; Java has no key/membership cache here. */
        Object read(DataComponentType<?> type) {
            MemorySegment allocation = this.header;
            try {
                for (int retry = 0; retry < 8; retry++) {
                    boolean stale = false;
                    for (int index = 0; index < 4; index++) {
                        long address = (long)VIEW_LONG.getAcquire(allocation, 32L + index * 8L);
                        if (address == 0) continue;
                        long token = cellLong(address, 0);
                        if (token <= 1) continue;
                        Row row = this.resolve(token);
                        if (row == null) { stale = true; break; }
                        if (row.type() == type) return row.value().orElseThrow();
                    }
                    if (!stale) break;
                }
                return this.read(accessIdentity(type));
            } finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        /** Caller holds this root's writer guard and pins allocation until all returned-address accesses finish. */
        private long cellFor(DataComponentType<?> type, MemorySegment allocation) {
            for (int index = 0; index < 4; index++) {
                long address = (long)VIEW_LONG.getAcquire(allocation, 32L + index * 8L);
                if (address == 0) continue;
                long token = cellLong(address, 0);
                if (token <= 1) continue;
                Row row = this.resolve(token);
                if (row != null && row.type() == type) return address;
            }
            return 0;
        }
        /** Writer guard makes the native index stable; actual allocation remains pinned by the caller. */
        private long indexedCellFor(long key, MemorySegment allocation) {
            long descriptor = (long)VIEW_LONG.getAcquire(allocation, 8L);
            long mask = cellLong(descriptor, 0), rows = cellLong(descriptor, 8);
            long mixed = key * 0x9e3779b97f4a7c15L, index = (mixed ^ (mixed >>> 32)) & mask;
            for (long probes = 0; probes <= mask; probes++) {
                long found = cellLong(rows, index * 16);
                if (found == key) return cellLong(rows, index * 16 + 8);
                if (found == 0) return 0;
                index = (index + 1) & mask;
            }
            return 0;
        }
        private Row resolve(long token) {
            var rows = this.rows; int index = (int)token;
            if (index <= 0 || index >= rows.length()) return null;
            Row row = rows.getAcquire(index); return row != null && row.token() == token ? row : null;
        }
        private long token(long key, MemorySegment allocation) {
            try { for (;;) {
                long before = (long)VIEW_LONG.getAcquire(allocation, 0L);
                if ((before & 1) != 0) { Thread.onSpinWait(); continue; }
                long descriptorAddress = (long)VIEW_LONG.getAcquire(allocation, 8L);
                // Pointer and size come from ONE immutable allocation descriptor.
                // The actual header scope pins this backing AND all retired geometric indexes.
                MemorySegment descriptor = MemorySegment.ofAddress(descriptorAddress).reinterpret(16);
                long mask = descriptor.get(ValueLayout.JAVA_LONG, 0), address = descriptor.get(ValueLayout.JAVA_LONG, 8);
                MemorySegment table = MemorySegment.ofAddress(address).reinterpret((mask + 1) * 16);
                long mixed = key * 0x9e3779b97f4a7c15L, index = (mixed ^ (mixed >>> 32)) & mask, value = 0;
                for (long probes = 0; probes <= mask; probes++) {
                    long found = (long)VIEW_LONG.getAcquire(table, index * 16);
                    if (found == key) {
                        long cell = (long)VIEW_LONG.getAcquire(table, index * 16 + 8);
                        value = cell == 0 ? 0 : cellLong(cell, 0); break;
                    }
                    if (found == 0) break;
                    index = (index + 1) & mask;
                }
                java.lang.invoke.VarHandle.fullFence();
                long after = (long)VIEW_LONG.getAcquire(allocation, 0L);
                if (before == after) return value;
            } } finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        Object read(long key) {
            MemorySegment allocation = this.header;
            try {
                for (int retry = 0; retry < 8; retry++) {
                    long token = this.token(key, allocation);
                    if (token == 0) return ABSENT;
                    if (token == 1) return null;
                    Row row = this.resolve(token); if (row != null) return row.value().orElseThrow();
                }
                synchronized (this) {
                    long token = this.token(key, allocation);
                    if (token <= 1) return token == 0 ? ABSENT : null;
                    Row row = this.resolve(token);
                    if (row == null) throw new IllegalStateException("Unresolved native access incarnation");
                    return row.value().orElseThrow();
                }
            } finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        private long publish(DataComponentType<?> type, Object value) {
            if (this.epoch >= 0x80000000L) throw new IllegalStateException("Access incarnation exhausted");
            int index;
            if (this.freeCount != 0) index = this.free[--this.freeCount];
            else {
                index = this.next++;
                if (index == this.rows.length()) {
                    var grown = new java.util.concurrent.atomic.AtomicReferenceArray<Row>(Math.multiplyExact(index, 2));
                    for (int i = 0; i < index; i++) grown.setRelease(i, this.rows.getAcquire(i));
                    this.rows = grown;
                }
            }
            long token = (this.epoch++ << 32) | Integer.toUnsignedLong(index);
            this.rows.setRelease(index, new Row(token, type, java.util.Optional.of(value)));
            java.lang.invoke.VarHandle.fullFence(); return token;
        }
        private void retire(long token) {
            if (token <= 1) return;
            int index = (int)token;
            if (this.freeCount == this.free.length) this.free = java.util.Arrays.copyOf(this.free, this.free.length * 2);
            this.rows.setRelease(index, null); this.free[this.freeCount++] = index;
        }
        private Object value(long token) {
            Row row = this.resolve(token);
            if (row == null) throw new IllegalStateException("Lost native access root");
            return row.value().orElseThrow();
        }
        private void changed(long key, DataComponentType<?> type, long old, long next) {
            if (next == 0) this.types.remove(key);
            else if (old == 0) this.types.put(key, type);
            if (old != next) this.retire(old);
        }
        synchronized java.util.List<DataComponentType<?>> order() {
            var result = new java.util.ArrayList<DataComponentType<?>>();
            // This view owns its retained handle through its cleanup action, not a global root bag.
            long handle = this.nativeHandleForTest();
            for (long i = 0;; i++) {
                MemorySegment out = RESULT.get(); long found;
                try { found = checked((long)ACCESS_AT.invokeExact(handle, i, out)); } catch (Throwable failure) { throw failed(failure); }
                if (found == 0) break;
                var type = this.types.get(out.get(ValueLayout.JAVA_LONG, 0));
                if (type == null && !this.types.containsKey(out.get(ValueLayout.JAVA_LONG, 0))) throw new IllegalStateException("Lost native access key identity"); result.add(type);
            }
            java.lang.ref.Reference.reachabilityFence(this.header); return result;
        }
        private final long nativeHandle;
        private long nativeHandleForTest() { return this.nativeHandle; }
    }
    static final class AccessView {
        final AccessRoots roots;
        final long handle;
        final Cleaner.Cleanable cleanable;
        AccessView(long handle, AccessRoots roots, Release ownership) {
            this.handle = handle; this.roots = roots; this.cleanable = CLEANER.register(this, ownership);
        }
        Object get(DataComponentType<?> type) { Object result = this.roots.read(type); return result == AccessRoots.ABSENT ? null : result; }
        java.util.Optional<?> optional(DataComponentType<?> type) {
            synchronized (this.roots) {
                long token = this.roots.token(accessIdentity(type), this.roots.header);
                if (token == 0) return null;
                if (token == 1) return java.util.Optional.empty();
                var row = this.roots.resolve(token);
                if (row == null) throw new IllegalStateException("Lost escaped access row");
                return row.value();
            }
        }
        void remove(DataComponentType<?> type) {
            synchronized (this.roots) {
                long key = accessIdentity(type), old = c3(ACCESS_PATCH_PUT, this.handle, key, 0);
                this.roots.changed(key, type, old, this.roots.token(key, this.roots.header));
            }
        }
    }
    static final class AccessOwner {
        final long handle;
        final MemorySegment cow;
        final DataComponentMap prototype;
        private volatile AccessRoots roots;
        private AccessOwner(long handle, AccessRoots roots, DataComponentMap prototype, Release ownership) {
            this.handle = handle; this.roots = roots; this.prototype = prototype;
            this.cow = leaseView(c1(ACCESS_COW_ADDRESS, handle), 8, ownership).asReadOnly();
        }
        static AccessOwner create(DataComponentMap prototype) {
            return adopt(ACCESS_NEW, (h, ownership) -> new AccessOwner(h, AccessRoots.retain(h, null), prototype, ownership), Identities.of(prototype));
        }
        synchronized void prepare() {
            MemorySegment allocation = this.cow;
            try {
                if ((long)VIEW_LONG.getAcquire(allocation, 0L) == 0) return;
                synchronized (this.roots) {
                    AccessRoots staged = AccessRoots.stage(this.handle, this.roots);
                    if (staged == null) return;
                    try {
                        c3(ACCESS_COMMIT, this.handle, this.roots.version, staged.nativeHandle);
                        // No fallible construction or bookkeeping after successful native commit.
                        this.roots = staged;
                    } catch (Throwable failure) { staged.ownership.run(); throw failure; }
                }
            } finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        <T> T get(DataComponentType<? extends T> type) {
            Object value = this.roots.read(type);
            return value == AccessRoots.ABSENT ? this.prototype.get(type) : (T)value;
        }
        <T> T set(DataComponentType<T> type, @Nullable T value) {
            this.prepare(); T prototypeValue = this.prototype.get(type);
            boolean equal = Objects.equals(value, prototypeValue);
            return this.finish(type, value, prototypeValue, equal, false);
        }
        <T> T remove(DataComponentType<T> type) {
            this.prepare(); T prototypeValue = this.prototype.get(type);
            return this.finish(type, null, prototypeValue, false, true);
        }
        private synchronized <T> T finish(DataComponentType<T> type, T value, T prototypeValue, boolean equal, boolean remove) {
            MemorySegment allocation = this.cow;
            AccessRoots roots = this.roots; MemorySegment backing = roots.header;
            try { synchronized (roots) {
                // All alias edits AND COW-sharing publication take this same guard.
                // User callbacks already ran once; rejection only changes the remaining native commit path.
                long address = value != null && !equal && !remove ? roots.cellFor(type, backing) : 0;
                long key = address == 0 ? accessIdentity(type) : cellLong(address, 8);
                if (address == 0 && value != null && !equal && !remove) address = roots.indexedCellFor(key, backing);
                long token = value == null || equal || remove ? 0 : roots.publish(type, value);
                long flags = (equal ? 1 : 0) | (prototypeValue != null ? 2 : 0) | (remove ? 4 : 0);
                long old, next;
                try {
                    MemorySegment out = RESULT.get();
                    long committed = address == 0 ? 0 : replaceExisting(address, roots.version, key,
                        cellLong(address, 40), cellLong(address, 0), token, out);
                    if (committed == 1) {
                        old = out.get(ValueLayout.JAVA_LONG, 0); next = out.get(ValueLayout.JAVA_LONG, 8);
                    } else {
                        // Commit-only fallback: preserve captured prototype/equality and current backing.
                        old = c4(ACCESS_FINISH, this.handle, key, token, flags);
                        next = roots.token(key, backing);
                    }
                } catch (Throwable failure) { roots.retire(token); throw failure; }
                T previous = old > 1 ? (T)roots.value(old) : remove && old == 1 ? null : prototypeValue;
                roots.changed(key, type, old, next);
                if (token > 1 && token != next) roots.retire(token);
                return previous;
            } } finally {
                java.lang.ref.Reference.reachabilityFence(backing);
                java.lang.ref.Reference.reachabilityFence(allocation);
            }
        }
        synchronized AccessOwner copy() {
            MemorySegment allocation = this.cow;
            try { synchronized (this.roots) { return adopt(ACCESS_COPY, (h, ownership) -> new AccessOwner(h, this.roots, this.prototype, ownership), this.handle); } }
            finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        synchronized AccessView asPatch() {
            MemorySegment allocation = this.cow;
            try { synchronized (this.roots) { return adopt(ACCESS_ESCAPE, (h, ownership) -> h == 0 ? null : new AccessView(h, this.roots, ownership), this.handle); } }
            finally { java.lang.ref.Reference.reachabilityFence(allocation); }
        }
        java.util.List<DataComponentType<?>> orderForTest() { return this.roots.order(); }
    }

    record CellLeaseForTest(MemorySegment view, long handle) {}
    static CellLeaseForTest retainedCellForTest(AccessOwner owner, DataComponentType<?> type) {
        AccessRoots roots = owner.roots; MemorySegment allocation = roots.header;
        try { synchronized (roots) {
            long address = roots.cellFor(type, allocation);
            if (address == 0) throw new IllegalStateException("No present inline cell for test");
            return adopt(ACCESS_CELL_RETAIN, (h, release) -> new CellLeaseForTest(leaseView(c1(ACCESS_CELL_ADDRESS, h), 48, release).asReadOnly(), h), roots.nativeHandle, address);
        } } finally { java.lang.ref.Reference.reachabilityFence(allocation); }
    }

}
