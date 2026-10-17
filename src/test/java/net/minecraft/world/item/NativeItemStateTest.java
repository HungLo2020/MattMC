package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeItemStateTest {
    @Test void equalPresentPatchEntryRemovesOverrideWithoutBecomingAnUnrootedTombstone() {
        var first=new Probe("prototype");var prototype=DataComponentMap.builder().set(TYPE,first).build();
        var map=new net.minecraft.core.component.PatchedDataComponentMap(prototype);
        map.set(TYPE,new Probe("override"));
        map.applyPatch(net.minecraft.core.component.DataComponentPatch.builder().set(TYPE,new Probe("prototype")).build());
        assertSame(first,map.get(TYPE));assertFalse(map.hasNonDefault(TYPE));assertEquals(0,((NativeItemState.Overlay)map).rootedValuesForTest());
        assertSame(net.minecraft.core.component.DataComponentPatch.EMPTY,map.asPatch());assertEquals(java.util.Set.of(TYPE),map.keySet());
        map.applyPatch(net.minecraft.core.component.DataComponentPatch.builder().remove(TYPE).build());
        assertNull(map.get(TYPE));assertTrue(map.hasNonDefault(TYPE));assertEquals(Optional.empty(),map.asPatch().get(TYPE));assertTrue(map.keySet().isEmpty());
    }
    private static Object injectRootFailure(Object owner,java.util.concurrent.atomic.AtomicBoolean cloneFailure,java.util.concurrent.atomic.AtomicBoolean putFailure,AssertionError expected) throws Exception {
        var field=NativeItemState.Overlay.class.getSuperclass().getDeclaredField("roots");field.setAccessible(true);Object roots=field.get(owner);
        var types=roots.getClass().getDeclaredField("types");types.setAccessible(true);
        @SuppressWarnings("unchecked")var original=(java.util.Map<Long,DataComponentType<?>>)types.get(roots);
        var injected=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<DataComponentType<?>>(){
            @Override public int size(){if(cloneFailure.getAndSet(false))throw expected;return super.size();}
            @Override public DataComponentType<?> put(long key,DataComponentType<?> type){if(putFailure.getAndSet(false))throw expected;return super.put(key,type);}
        };
        injected.putAll(original);types.set(roots,injected);return roots;
    }
    @Test void failedActualCowRootCloneKeepsSharedNativeBackingAndMatchingVmRoots() throws Exception {
        for(boolean clear:new boolean[]{false,true}){
            var map=NativeItemState.Overlay.create(DataComponentMap.EMPTY);var first=new Probe("first");map.set(TYPE,first);var escaped=map.nativePatchSnapshot();
            var cloneFailure=new java.util.concurrent.atomic.AtomicBoolean(false);var expected=new AssertionError("actual root clone failure");
            Object before=injectRootFailure(map,cloneFailure,new java.util.concurrent.atomic.AtomicBoolean(false),expected);cloneFailure.set(true);
            assertSame(expected,assertThrows(AssertionError.class,()->{if(clear)map.clear();else map.set(TYPE,new Probe("next"));}));
            var field=NativeItemState.Overlay.class.getSuperclass().getDeclaredField("roots");field.setAccessible(true);assertSame(before,field.get(map));assertSame(first,map.get(TYPE));
            escaped.remove(TYPE);Object other=new Object();escaped.put(OBJECT,java.util.Optional.of(other));assertNull(map.get(TYPE));assertSame(other,map.get(OBJECT));
            var projected=map.nativePatchSnapshot().mapView();assertEquals(1,projected.size());var entry=projected.reference2ObjectEntrySet().iterator().next();assertSame(OBJECT,entry.getKey());assertSame(other,entry.getValue().orElseThrow());
            map.set(TYPE,new Probe("retry"));assertNull(escaped.get(TYPE));assertSame(other,map.get(OBJECT));
        }
    }
    @Test void failedActualTypePublicationCannotPublishPatchOrOverlayMemberAndRetryKeepsExactRoots() throws Exception {
        var expected=new AssertionError("actual type root publication failure");var map=NativeItemState.Overlay.create(DataComponentMap.EMPTY);var first=new Probe("first");map.set(TYPE,first);
        var putFailure=new java.util.concurrent.atomic.AtomicBoolean(false);injectRootFailure(map,new java.util.concurrent.atomic.AtomicBoolean(false),putFailure,expected);putFailure.set(true);Object next=new Object();
        assertSame(expected,assertThrows(AssertionError.class,()->map.set(OBJECT,next)));assertNull(map.get(OBJECT));assertSame(first,map.get(TYPE));assertEquals(1,map.rootedValuesForTest());
        assertSame(TYPE,map.nativePatchSnapshot().mapView().reference2ObjectEntrySet().iterator().next().getKey());map.set(OBJECT,next);assertSame(next,map.get(OBJECT));assertEquals(2,map.rootedValuesForTest());
        var patch=NativeItemState.Patch.create();patch.put(TYPE,java.util.Optional.of(first));putFailure.set(false);injectRootFailure(patch,new java.util.concurrent.atomic.AtomicBoolean(false),putFailure,expected);putFailure.set(true);
        assertSame(expected,assertThrows(AssertionError.class,()->patch.put(OBJECT,java.util.Optional.of(next))));assertNull(patch.get(OBJECT));assertEquals(1,patch.size());assertEquals(1,patch.rootedValuesForTest());
        assertSame(TYPE,patch.mapView().reference2ObjectEntrySet().iterator().next().getKey());var optional=java.util.Optional.of(next);patch.put(OBJECT,optional);assertSame(optional,patch.get(OBJECT));assertEquals(2,patch.size());
    }
    private record HeldDefaultEntry(WeakReference<Object> map,WeakReference<Object> retired,java.util.Map.Entry<?,?> entry,Object expected) {}
    private static HeldDefaultEntry defaultEntryAfterReplacement(boolean hashed) {
        var builder=DataComponentMap.builder();var values=new Object[hashed?12:2];
        for(int i=0;i<values.length;i++){values[i]=new Object();builder.set(DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(s->new Object(),v->"opaque")).build(),values[i]);}
        var map=DataComponentMap.nativeDefaults(builder.build());var entry=map.reference2ObjectEntrySet().iterator().next();
        Object expected,retired;
        if(hashed){retired=entry.getValue();expected=new Object();entry.setValue(expected);}
        else {expected=entry.getValue();retired=values[1];}
        return new HeldDefaultEntry(new WeakReference<>(map),new WeakReference<>(retired),entry,expected);
    }
    @Test void defaultEntriesPreserveSnapshotAndLiveMapReachabilityWithoutRetiredValues() throws Exception {
        bootstrap();
        for(boolean hashed:new boolean[]{false,true}) {
            var held=defaultEntryAfterReplacement(hashed);long until=System.nanoTime()+5_000_000_000L;
            while((held.retired().get()!=null || !hashed&&held.map().get()!=null)&&System.nanoTime()<until){System.gc();Thread.sleep(10);}
            assertNull(held.retired().get());if(hashed)assertNotNull(held.map().get());else assertNull(held.map().get());
            assertSame(held.expected(),held.entry().getValue());java.lang.ref.Reference.reachabilityFence(held);
        }
    }
    private static final DataComponentType<Probe> TYPE = DataComponentType.<Probe>builder().persistent(Codec.STRING.xmap(Probe::new, p -> p.name)).build();
    private static final DataComponentType<Object> OBJECT = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(s -> s, Object::toString)).build();
    private static final class Probe {
        final String name;
        Runnable beforeEquals = () -> {};
        Probe(String name) { this.name = name; }
        @Override public boolean equals(Object object) { beforeEquals.run(); return object instanceof Probe other && name.equals(other.name); }
        @Override public int hashCode() { return name.hashCode(); }
    }
    @Test void callbackExposesBackingBeforeOuterWriteThenLaterMutationDetaches() {
        Probe base = new Probe("base"), value = new Probe("new");
        var map = NativeItemState.Overlay.create(DataComponentMap.builder().set(TYPE, base).build());
        map.set(OBJECT, new Object());
        var escaped = new AtomicReference<NativeItemState.Patch>();
        var copied = new AtomicReference<NativeItemState.Overlay>();
        value.beforeEquals = () -> { escaped.set(map.nativePatchSnapshot()); copied.set(map.copy()); };
        assertSame(base, map.set(TYPE, value)); value.beforeEquals = () -> {};
        assertSame(value, escaped.get().get(TYPE).orElseThrow()); assertSame(value, copied.get().get(TYPE));
        assertSame(value, map.set(TYPE, base)); assertSame(value, escaped.get().get(TYPE).orElseThrow());
        assertSame(value, copied.get().get(TYPE)); assertSame(base, map.get(TYPE));
        NativeItemState.closeForTest(map); NativeItemState.closeForTest(escaped.get()); NativeItemState.closeForTest(copied.get());
    }
    @Test void nestedMutationDuringEqualsDetachesAtItsOwnCowPoint() {
        Probe base = new Probe("base"), value = new Probe("new");
        var map = NativeItemState.Overlay.create(DataComponentMap.builder().set(TYPE, base).build()); map.set(OBJECT, new Object());
        var escaped = new AtomicReference<NativeItemState.Patch>();
        value.beforeEquals = () -> { escaped.set(map.nativePatchSnapshot()); map.set(OBJECT, new Object()); };
        map.set(TYPE, value); value.beforeEquals = () -> {};
        assertNull(escaped.get().get(TYPE)); assertSame(value, map.get(TYPE));
        NativeItemState.closeForTest(map); NativeItemState.closeForTest(escaped.get());
    }
    @Test void tombstoneReturnsAndOriginalThrowableRemainExact() {
        Probe base = new Probe("base"), value = new Probe("new");
        var map = NativeItemState.Overlay.create(DataComponentMap.builder().set(TYPE, base).build());
        assertSame(base, map.remove(TYPE)); assertNull(map.remove(TYPE)); assertSame(base, map.set(TYPE, value));
        var thrown = new AssertionError("original equals failure"); value.beforeEquals = () -> { throw thrown; };
        assertSame(thrown, assertThrows(AssertionError.class, () -> map.set(TYPE, value)));
        value.beforeEquals = () -> {}; assertSame(value, map.get(TYPE)); NativeItemState.closeForTest(map);
    }
    @Test void rootsDoNotAccumulateHistoricalValuesAndEscapedRootsRemainAlive() {
        var map = NativeItemState.Overlay.create(DataComponentMap.EMPTY);
        Object old = new Object(); map.set(OBJECT, old); var escaped = map.nativePatchSnapshot();
        for (int i = 0; i < 1000; i++) { map.set(OBJECT, new Object()); assertEquals(1, map.rootedValuesForTest()); }
        assertSame(old, escaped.get(OBJECT).orElseThrow()); assertEquals(1, escaped.rootedValuesForTest());
        map.remove(OBJECT); assertEquals(0, map.rootedValuesForTest()); assertSame(old, escaped.get(OBJECT).orElseThrow());
        NativeItemState.closeForTest(map); assertSame(old, escaped.get(OBJECT).orElseThrow()); NativeItemState.closeForTest(escaped);
        assertThrows(IllegalStateException.class, () -> map.get(OBJECT));
    }
    @Test void builderSharesButSnapshotCopiesAndSourceReleaseCannotFreeTheView() {
        var builder = NativeItemState.Patch.create(); Object value = new Object(); builder.put(OBJECT, Optional.of(value));
        var built = builder.share(); var snapshot = builder.snapshot(); builder.remove(OBJECT);
        assertNull(built.get(OBJECT)); assertSame(value, snapshot.get(OBJECT).orElseThrow());
        NativeItemState.closeForTest(builder); built.put(TYPE, Optional.of(new Probe("foreign"))); assertEquals(1, built.size());
        NativeItemState.closeForTest(built); NativeItemState.closeForTest(snapshot);
    }
    @Test void patchPreservesExactOptionalIdentityAcrossReadsSharesAndSnapshots() {
        var patch = NativeItemState.Patch.create();
        Optional<Object> original = Optional.of(new Object());
        assertNull(patch.put(OBJECT, original));
        assertSame(original, patch.get(OBJECT));
        var shared = patch.share(); var snapshot = patch.snapshot();
        assertSame(original, shared.get(OBJECT)); assertSame(original, snapshot.get(OBJECT));
        Optional<Object> replacement = Optional.of(new Object());
        assertSame(original, patch.put(OBJECT, replacement));
        assertSame(replacement, shared.get(OBJECT)); assertSame(original, snapshot.get(OBJECT));
        assertSame(replacement, patch.remove(OBJECT));
        NativeItemState.closeForTest(patch); NativeItemState.closeForTest(shared); NativeItemState.closeForTest(snapshot);
    }
    private static java.util.List<String> mapScenario(it.unimi.dsi.fastutil.objects.Reference2ObjectMap<DataComponentType<?>, Optional<?>> map, boolean fast) {
        java.util.List<String> events = new java.util.ArrayList<>();
        Optional<?> first = Optional.of("first"), second = Optional.of("second");
        map.put(TYPE, first); map.put(OBJECT, second); map.put(null, Optional.empty());
        events.add("identity=" + (map.get(TYPE) == first) + "/" + (map.get(OBJECT) == second));
        var entriesIterator = fast ? it.unimi.dsi.fastutil.objects.Reference2ObjectMaps.fastIterator(map) : map.reference2ObjectEntrySet().iterator();
        var entry1 = entriesIterator.next(); var entry2 = entriesIterator.next();
        events.add("sameEntry=" + (entry1 == entry2) + " firstNow=" + entry1.getValue());
        try { entry2.setValue(Optional.empty()); } catch (Throwable e) { events.add("entrySet=" + e.getClass().getName() + ":" + e.getMessage()); }
        // External removal changes the row at the saved index; remove must use that index.
        map.remove(TYPE); entriesIterator.remove();
        events.add("afterCursorRemove=" + map.size() + "/object=" + map.containsKey(OBJECT) + "/null=" + map.containsKey(null));
        try { entriesIterator.remove(); } catch (Throwable e) { events.add("doubleRemove=" + e.getClass().getName() + ":" + e.getMessage()); }
        map.clear(); map.put(TYPE, first); map.put(OBJECT, second);
        var entrySet = map.reference2ObjectEntrySet();
        var retained = new java.util.ArrayList<it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>>>();
        if (fast) ((it.unimi.dsi.fastutil.objects.Reference2ObjectMap.FastEntrySet<DataComponentType<?>, Optional<?>>)entrySet).fastForEach(retained::add);
        else entrySet.forEach(retained::add);
        events.add("forEachReuse=" + (retained.get(0) == retained.get(1)) + "/firstNow=" + retained.get(0).getValue());
        // forEachRemaining captures the endpoint, but next()/hasNext() sees later appends.
        var cursor = map.reference2ObjectEntrySet().iterator(); int[] visits = {0};
        cursor.forEachRemaining(entry -> { visits[0]++; map.put(null, Optional.empty()); });
        events.add("remaining=" + visits[0] + "/hasNext=" + cursor.hasNext() + "/nextNull=" + (cursor.next().getKey() == null));
        map.clear(); map.put(TYPE, first); map.put(OBJECT, second);
        var shrinking = map.reference2ObjectEntrySet().iterator();
        shrinking.forEachRemaining(entry -> { events.add("shrink=" + entry.getValue()); map.clear(); });
        return events;
    }
    @Test void mapAdapterMatchesOriginalArrayMapEntryIdentityLiveCursorAndCallbackEndpoints() {
        for (boolean fast : new boolean[]{false, true}) {
            var nativePatch = NativeItemState.Patch.create();
            var expected = mapScenario(new it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap<>(), fast);
            var actual = mapScenario(nativePatch.mapView(), fast);
            assertEquals(expected, actual, "fast=" + fast);
            assertEquals(0, nativePatch.rootedValuesForTest()); NativeItemState.closeForTest(nativePatch);
        }
    }
    @Test void readOnlyPatchIterationDoesNotChangeSharingAndPreparedApplyRetainsWrapper() {
        var map = NativeItemState.Overlay.create(DataComponentMap.EMPTY); map.set(OBJECT, new Object());
        var view = map.patchView(); Optional<Probe> value = Optional.of(new Probe("prepared"));
        map.beginPatchMutation(); map.applyPrepared(TYPE, value);
        assertSame(value, view.get(TYPE)); assertSame(value, map.nativePatchSnapshot().get(TYPE));
        map.set(TYPE, new Probe("later")); assertSame(value, view.get(TYPE));
        NativeItemState.closeForTest(map);
    }
    @Test void productionOverlayUsesNativeAuthorityAndEscapedBuilderBacking() throws Exception {
        var builder = net.minecraft.core.component.DataComponentPatch.builder().set(TYPE, new Probe("first"));
        var patch = builder.build();
        var map = net.minecraft.core.component.PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, patch);
        var state=map.nativeState();assertSame(map,state);assertSame(map,state.components());assertEquals(1,state.patchSize());
        builder.set(OBJECT, new Object()); assertNotNull(map.get(OBJECT));
        var iterator = patch.entrySet().iterator(); iterator.next(); iterator.remove(); assertNull(map.get(TYPE));
        map.restorePatch(patch); assertSame(patch.get(OBJECT), map.asPatch().get(OBJECT));
        map.clearPatch(); assertNull(map.get(OBJECT)); assertNotNull(patch.get(OBJECT));
    }
    @Test void nativeScalarViewPreservesAllCountBits() {
        var overlay = NativeItemState.Overlay.create(DataComponentMap.EMPTY);
        var stack = NativeItemState.Stack.create(null, Integer.MIN_VALUE, overlay);
        assertNull(stack.item()); assertEquals(Integer.MIN_VALUE, stack.count());
        for (int count : new int[]{Integer.MAX_VALUE, -1, 0, 1, 64, Integer.MIN_VALUE}) { stack.count(count); assertEquals(count, stack.count()); }
        assertTrue(NativeItemState.liveForTest(NativeItemState.handleForTest(stack)));
    }
    private record BorrowedScalar(WeakReference<Object> view, long handle) {}
    private static BorrowedScalar checkBorrowedScalarAfterFacadeCollection() throws Exception {
        var overlay = NativeItemState.Overlay.create(DataComponentMap.EMPTY);
        var stack = NativeItemState.Stack.create(null, 27, overlay);
        var weak = new WeakReference<>(stack); long original = NativeItemState.handleForTest(stack);
        long address = NativeItemState.stackAddressForTest(original);
        var retained = NativeItemState.scalarLeaseForTest(stack); long handle = retained.handle();
        assertNotEquals(original, handle); assertEquals(address, NativeItemState.stackAddressForTest(handle));
        var view = retained.view().asSlice(8, 4); retained = null;
        stack = null; overlay = null; long deadline = System.nanoTime() + 5_000_000_000L;
        while ((weak.get() != null || NativeItemState.liveForTest(original)) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(weak.get(), "facade must be collectible while a borrowed scalar view remains");
        assertFalse(NativeItemState.liveForTest(original), "the original facade lease must be released independently");
        assertTrue(NativeItemState.liveForTest(handle), "derived view must retain the actual same allocation after original release");
        assertEquals(27, view.get(java.lang.foreign.ValueLayout.JAVA_INT, 0));
        var result = new BorrowedScalar(new WeakReference<>(view), handle);
        java.lang.ref.Reference.reachabilityFence(view); return result;
    }
    @Test void derivedScalarViewPinsActualAllocationUntilLastViewIsCollected() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); BorrowedScalar borrowed = checkBorrowedScalarAfterFacadeCollection();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((borrowed.view().get() != null || NativeItemState.liveForTest(borrowed.handle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(borrowed.view().get()); assertFalse(NativeItemState.liveForTest(borrowed.handle()));
        assertThrows(IllegalStateException.class, () -> NativeItemState.stackAddressForTest(borrowed.handle()));
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }
    private record BorrowedBacking(WeakReference<Object> view, long handle) {}
    private static BorrowedBacking checkDerivedBackingAcrossCowAndReuse() throws Exception {
        var map = NativeItemState.Overlay.create(DataComponentMap.EMPTY); map.set(OBJECT, new Object());
        var escaped = map.nativePatchSnapshot(); long backing = NativeItemState.backingForTest(map);
        var rootsField = NativeItemState.Overlay.class.getSuperclass().getDeclaredField("roots"); rootsField.setAccessible(true);
        Object oldRoots = rootsField.get(map); var weakRoots = new WeakReference<>(oldRoots);
        var viewField = oldRoots.getClass().getDeclaredField("view"); viewField.setAccessible(true);
        var parent = (java.lang.foreign.MemorySegment)viewField.get(oldRoots);
        var view = parent.asSlice(8, 8).asReadOnly();
        assertSame(parent.scope(), view.scope()); assertTrue(view.isReadOnly());
        var atomicLong = java.lang.foreign.ValueLayout.JAVA_LONG.varHandle();
        assertEquals(1L, (long)atomicLong.getAcquire(view, 0L));
        map.set(OBJECT, new Object()); assertNotEquals(backing, NativeItemState.backingForTest(map));
        for (int i = 0; i < 2000; i++) { map.remove(OBJECT); map.set(OBJECT, new Object()); }
        // This is Rust-mutated shared metadata, not immutable snapshot bytes.
        escaped.put(TYPE, Optional.of(new Probe("old backing"))); assertEquals(2L, (long)atomicLong.getAcquire(view, 0L));
        escaped.clear(); escaped.put(OBJECT, Optional.of(new Object())); assertEquals(1L, (long)atomicLong.getAcquire(view, 0L));
        NativeItemState.closeForTest(escaped); escaped = null; oldRoots = null; parent = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (weakRoots.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(weakRoots.get(), "old Java COW bag must collect while its derived metadata view remains");
        assertTrue(NativeItemState.liveForTest(backing), "view must pin the actual old backing allocation");
        assertEquals(1L, (long)atomicLong.getAcquire(view, 0L)); assertNotNull(map.get(OBJECT));
        NativeItemState.closeForTest(map);
        var result = new BorrowedBacking(new WeakReference<>(view), backing);
        java.lang.ref.Reference.reachabilityFence(view); return result;
    }
    @Test void derivedBackingViewPinsOldCowAllocationAcrossReplacementAndSlotReuse() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); var borrowed = checkDerivedBackingAcrossCowAndReuse();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((borrowed.view().get() != null || NativeItemState.liveForTest(borrowed.handle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(borrowed.view().get()); assertFalse(NativeItemState.liveForTest(borrowed.handle()));
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }
    @Test void atomicAccessLowerBoundPreservesOpaqueIdentityTombstonesAndBoundedReuse() {
        var cell = new NativeItemState.AtomicCell(); Object first = new Object(); assertNull(cell.set(first)); assertSame(first, cell.get());
        for (int i = 0; i < 10000; i++) { Object value = new Object(); assertNotNull(cell.set(value)); assertSame(value, cell.get()); }
        assertTrue(cell.capacityForTest() <= 4); Object last = cell.get(); assertSame(last, cell.tombstone());
        assertEquals(1, cell.rawForTest()); assertNull(cell.get()); assertNull(cell.set(null)); assertEquals(0, cell.rawForTest());
    }
    @Test void atomicAccessRejectsForcedStaleIncarnationThenRestoresPositiveRead() {
        var cell = new NativeItemState.AtomicCell(); cell.set(new Object()); long stale = cell.rawForTest();
        cell.set(new Object()); Object latest = new Object(); cell.set(latest); long current = cell.rawForTest();
        assertEquals((int)stale, (int)current, "same opaque slot must actually be reused"); assertNotEquals(stale, current);
        cell.corruptForTest(stale); assertThrows(IllegalStateException.class, cell::get);
        cell.corruptForTest(current); assertSame(latest, cell.get());
    }
    @Test void atomicAccessControlledRetirementRejectsReusedSlotAndKeepsCapturedRowStrong() throws Exception {
        var cell = new NativeItemState.AtomicCell(); Object original = new Object(); cell.set(original);
        var resolve = NativeItemState.AtomicCell.class.getDeclaredMethod("resolve", long.class); resolve.setAccessible(true);
        long token = cell.rawForTest(); Object captured = resolve.invoke(cell, token); assertNotNull(captured);
        var valueMethod = captured.getClass().getDeclaredMethod("value"); valueMethod.setAccessible(true);
        var capturedToken = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Object latest = new Object();
        Thread reader = Thread.ofPlatform().start(() -> {
            try {
                // The exact production token-load/resolve steps are separated by a controlled pause.
                long delayed = cell.rawForTest(); capturedToken.countDown(); assertTrue(resume.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertNull(resolve.invoke(cell, delayed), "reused incarnation must not return the replacement payload");
                assertSame(latest, cell.get());
            } catch (Throwable thrown) { failure.set(thrown); }
        });
        try {
            assertTrue(capturedToken.await(5, java.util.concurrent.TimeUnit.SECONDS));
            cell.set(new Object()); cell.set(latest); assertEquals((int)token, (int)cell.rawForTest());
            assertNotEquals(token, cell.rawForTest()); System.gc();
        } finally { resume.countDown(); reader.join(5000); }
        assertFalse(reader.isAlive()); assertNull(failure.get());
        assertSame(original, valueMethod.invoke(captured), "already captured strong row survives native retirement and slot reuse");
        java.lang.ref.Reference.reachabilityFence(captured);
    }
    private record PublishedPayload(int sequence, long check) {}
    @Test void atomicAccessPublishesStrongInitializedRowsToConcurrentReaders() throws Exception {
        var cell = new NativeItemState.AtomicCell(); var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var running = new java.util.concurrent.atomic.AtomicBoolean(true); java.util.List<Thread> readers = new java.util.ArrayList<>();
        for (int n = 0; n < 3; n++) {
            Thread reader = Thread.ofPlatform().start(() -> {
                try {
                    while (running.get()) {
                        Object value = cell.get();
                        if (value != null) { var payload = (PublishedPayload)value; if (payload.check() != (payload.sequence() ^ 0x12345678L)) throw new AssertionError("partially published opaque payload"); }
                    }
                } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
            }); readers.add(reader);
        }
        try {
            for (int i = 0; i < 30000; i++) { cell.set(new PublishedPayload(i, i ^ 0x12345678L)); if ((i & 255) == 0) cell.set(null); }
        } finally { running.set(false); for (Thread reader : readers) reader.join(5000); }
        for (Thread reader : readers) assertFalse(reader.isAlive()); assertNull(failure.get());
    }
    private static Cycle atomicCycle() {
        var cell = new NativeItemState.AtomicCell(); Object[] payload = {cell}; cell.set(payload);
        return new Cycle(new WeakReference<>(cell), new WeakReference<>(payload), cell.handleForTest(), cell.handleForTest());
    }
    @Test void atomicAccessViewAndPayloadCycleReleaseActualAllocation() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); var cycle = atomicCycle(); long deadline = System.nanoTime() + 5_000_000_000L;
        while ((cycle.owner().get() != null || cycle.payload().get() != null || NativeItemState.liveForTest(cycle.handle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(cycle.owner().get()); assertNull(cycle.payload().get()); assertFalse(NativeItemState.liveForTest(cycle.handle()));
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }
    @Test void adoptionOrderingControlsShareExactlyOnceReleaseBeforeAndAfterScopeRegistration() throws Exception {
        // Counter-only ordering controls, not injected actual heap exhaustion.
        long failures = NativeItemState.cleanupFailuresForTest();
        Class<?> factoryType = Class.forName("net.minecraft.world.item.NativeItemState$OwnerFactory");
        var adopt = NativeItemState.class.getDeclaredMethod("adopt", java.lang.invoke.MethodHandle.class, factoryType, long[].class); adopt.setAccessible(true);
        var createField = NativeItemState.class.getDeclaredField("PATCH_NEW"); createField.setAccessible(true);
        var create = (java.lang.invoke.MethodHandle)createField.get(null);
        var lease = NativeItemState.class.getDeclaredMethod("leaseView", long.class, long.class, Runnable.class); lease.setAccessible(true);
        for (boolean registered : new boolean[]{false, true}) {
            var owned = new java.util.concurrent.atomic.AtomicLong(); var cleanups = new java.util.concurrent.atomic.AtomicInteger();
            var escaped = new java.util.concurrent.atomic.AtomicReference<java.lang.foreign.MemorySegment>();
            AssertionError expected = new AssertionError("modelled adoption failure");
            Object factory = java.lang.reflect.Proxy.newProxyInstance(NativeItemState.class.getClassLoader(), new Class<?>[]{factoryType}, (proxy, method, arguments) -> {
                if (!method.getName().equals("build")) throw new AssertionError("Unexpected model call: " + method);
                long handle = (long)arguments[0]; Runnable action = (Runnable)arguments[1]; owned.set(handle);
                assertTrue(NativeItemState.liveForTest(handle));
                if (registered) {
                    Runnable counted = () -> { cleanups.incrementAndGet(); action.run(); };
                    // Zero bytes at a sentinel address: no native memory is read after rollback.
                    var view = (java.lang.foreign.MemorySegment)lease.invoke(null, 1L, 0L, counted);
                    escaped.set(view.asReadOnly());
                }
                throw expected;
            });
            var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> adopt.invoke(null, create, factory, new long[0]));
            assertSame(expected, thrown.getCause()); assertFalse(NativeItemState.liveForTest(owned.get()), "rollback must release actual native handle");
            if (registered) {
                var weak = new WeakReference<>(escaped.get()); escaped.set(null);
                long deadline = System.nanoTime() + 5_000_000_000L;
                while ((weak.get() != null || cleanups.get() == 0) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
                assertNull(weak.get()); assertEquals(1, cleanups.get(), "registered scope action must run once after rollback");
            } else assertEquals(0, cleanups.get());
            assertEquals(failures, NativeItemState.cleanupFailuresForTest(), "rollback and later cleanup must not release native ownership twice");
        }
    }
    private static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void stockChestActuallyOwnsNativeSlotsAndCountAliases() throws Exception {
        bootstrap();
        var chest = new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var getter = net.minecraft.world.level.block.entity.ChestBlockEntity.class.getDeclaredMethod("getItems"); getter.setAccessible(true);
        var slots = assertInstanceOf(NativeItemSlots.class, getter.invoke(chest));
        var shared = new ItemStack(Items.STICK, 12); chest.setItem(0, shared); chest.setItem(1, shared);
        assertSame(shared, chest.getItem(0)); assertSame(shared, chest.getItem(1)); shared.setCount(5);
        assertEquals(5, chest.getItem(0).getCount()); assertEquals(5, chest.getItem(1).getCount());
        slots.set(0, ItemStack.EMPTY); assertSame(shared, slots.get(1)); slots.clear(); assertEquals(5, shared.getCount());
        var subclass = new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()) {};
        assertFalse(getter.invoke(subclass) instanceof NativeItemSlots);
    }
    private record SlotCycle(WeakReference<Object> slots, WeakReference<Object> stack, long slotsHandle, long stackHandle) {}
    private static SlotCycle slotCycle() {
        var slots = (NativeItemSlots)NativeItemSlots.withSize(27); var stack = new ItemStack(Items.STICK, 3);
        slots.set(0, stack); slots.set(1, stack); stack.set(OBJECT, slots);
        return new SlotCycle(new WeakReference<>(slots), new WeakReference<>(stack), slots.handleForTest(), NativeItemState.handleForTest(stack.state));
    }
    @Test void slotStackPayloadCyclesReleaseBothActualNativeOwners() throws Exception {
        bootstrap(); long failures = NativeItemState.cleanupFailuresForTest(); SlotCycle cycle = slotCycle();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((cycle.slots().get() != null || cycle.stack().get() != null || NativeItemState.liveForTest(cycle.slotsHandle()) || NativeItemState.liveForTest(cycle.stackHandle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(cycle.slots().get()); assertNull(cycle.stack().get());
        assertFalse(NativeItemState.liveForTest(cycle.slotsHandle())); assertFalse(NativeItemState.liveForTest(cycle.stackHandle()));
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }
    @Test void readViewTracksCowAndReusedOpaqueSlotsWithoutHistoricalGrowth() {
        var map = NativeItemState.Overlay.create(DataComponentMap.EMPTY);
        Object first = new Object(); map.set(OBJECT, first); var escaped = map.nativePatchSnapshot(); long backing = NativeItemState.backingForTest(map);
        for (int i = 0; i < 2000; i++) {
            Object value = new Object(); assertNull(map.get(TYPE)); map.set(OBJECT, value); assertSame(value, map.get(OBJECT));
            map.remove(OBJECT); assertNull(map.get(OBJECT)); map.set(OBJECT, value); assertSame(value, map.get(OBJECT));
            assertEquals(1, map.rootedValuesForTest()); assertTrue(NativeItemState.bindingCapacityForTest(map) <= 16);
        }
        assertNotEquals(backing, NativeItemState.backingForTest(map)); assertSame(first, escaped.get(OBJECT).orElseThrow());
        var copy = map.copy(); Object latest = map.get(OBJECT); map.clear(); assertSame(latest, copy.get(OBJECT));
        assertEquals(0, map.rootedValuesForTest()); NativeItemState.closeForTest(map); NativeItemState.closeForTest(copy); NativeItemState.closeForTest(escaped);
    }
    private record Cycle(WeakReference<Object> owner, WeakReference<Object> payload, long handle, long backing) {}
    private static Cycle cycle(boolean prototypeCycle) {
        Object[] anchor = {null};
        DataComponentMap prototype = prototypeCycle ? new DataComponentMap() {
            public <T> T get(DataComponentType<? extends T> type) { return null; }
            public java.util.Set<DataComponentType<?>> keySet() { return anchor[0] == null ? java.util.Set.of(TYPE) : java.util.Set.of(); }
        } : DataComponentMap.EMPTY;
        var map = NativeItemState.Overlay.create(prototype); anchor[0] = map;
        Object[] payload = {map}; map.set(OBJECT, payload);
        return new Cycle(new WeakReference<>(map), new WeakReference<>(payload), NativeItemState.handleForTest(map), NativeItemState.backingForTest(map));
    }
    @Test void cleanerAndWeakIdentityRegistryDoNotRootFacadePayloadCycles() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest();
        for (boolean prototypeCycle : new boolean[]{false, true}) {
            Cycle cycle = cycle(prototypeCycle); long deadline = System.nanoTime() + 5_000_000_000L;
            while ((cycle.owner().get() != null || cycle.payload().get() != null || NativeItemState.liveForTest(cycle.handle()) || NativeItemState.liveForTest(cycle.backing())) && System.nanoTime() < deadline) {
                System.gc(); Thread.sleep(10);
            }
            assertNull(cycle.owner().get(), "owner/payload/prototype Java cycles must be collectible"); assertNull(cycle.payload().get());
            assertFalse(NativeItemState.liveForTest(cycle.handle()), "native owner must release after collection");
            assertFalse(NativeItemState.liveForTest(cycle.backing()), "actual native backing pin must release after collection");
        }
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }
    @Test void dynamicAccessOwnerRetainsForeignKeysOrderAndBoundedRoots() {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY);
        var keys = new java.util.ArrayList<DataComponentType<Object>>();
        var values = new java.util.ArrayList<Object>();
        for (int i = 0; i < 257; i++) {
            var type = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(v -> v, Object::toString)).build();
            Object value = new Object(); keys.add(type); values.add(value); assertNull(owner.set(type, value));
        }
        assertEquals(keys, owner.orderForTest());
        for (int i = 0; i < keys.size(); i++) assertSame(values.get(i), owner.get(keys.get(i)));
        for (int i = 0; i < 2000; i++) owner.set(keys.get(0), new Object());
        assertSame(values.get(1), owner.remove(keys.get(1))); assertNull(owner.get(keys.get(1)));
        assertNull(owner.set(keys.get(1), values.get(1)));
        var order = owner.orderForTest(); assertEquals(keys.get(0), order.get(0)); assertEquals(keys.get(1), order.getLast());

    }
    @Test void dynamicAccessOwnerPreservesCallbackCowTombstonesAndOriginalThrows() {
        Probe base = new Probe("base"), value = new Probe("new");
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.builder().set(TYPE, base).build());
        assertSame(base, owner.remove(TYPE)); assertNull(owner.remove(TYPE));
        owner.set(OBJECT, new Object());
        var escaped = new AtomicReference<NativeItemState.AccessView>();
        var copied = new AtomicReference<NativeItemState.AccessOwner>();
        value.beforeEquals = () -> { escaped.set(owner.asPatch()); copied.set(owner.copy()); };
        assertSame(base, owner.set(TYPE, value)); value.beforeEquals = () -> {};
        assertSame(value, escaped.get().get(TYPE)); assertSame(value, copied.get().get(TYPE));
        owner.set(TYPE, base); assertSame(value, escaped.get().get(TYPE)); assertSame(value, copied.get().get(TYPE));
        var thrown = new AssertionError("dynamic original equals failure"); value.beforeEquals = () -> { throw thrown; };
        assertSame(thrown, assertThrows(AssertionError.class, () -> owner.set(TYPE, value))); assertSame(base, owner.get(TYPE));
        value.beforeEquals = () -> {};
         escaped.get().cleanable.clean();
    }
    @Test void dynamicAccessOwnerNestedMutationAndEscapedRemovalShareOriginalBacking() {
        Probe base = new Probe("base"), value = new Probe("new");
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.builder().set(TYPE, base).build());
        owner.set(OBJECT, new Object()); var escaped = new AtomicReference<NativeItemState.AccessView>();
        value.beforeEquals = () -> { escaped.set(owner.asPatch()); owner.set(OBJECT, new Object()); };
        owner.set(TYPE, value); value.beforeEquals = () -> {};
        assertNull(escaped.get().get(TYPE)); assertSame(value, owner.get(TYPE));
        var shared = owner.asPatch(); var copy = owner.copy(); shared.remove(TYPE);
        assertSame(base, owner.get(TYPE)); assertSame(base, copy.get(TYPE));
        shared.cleanable.clean(); escaped.get().cleanable.clean();
    }
    @Test void dynamicAccessOwnerConcurrentGrowthNeverCombinesDifferentIndexAllocations() throws Exception {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); owner.set(OBJECT, new Object());
        var failure = new AtomicReference<Throwable>(); var done = new java.util.concurrent.atomic.AtomicBoolean();
        Thread reader = new Thread(() -> {
            try { while (!done.get()) assertNotNull(owner.get(OBJECT)); } catch (Throwable thrown) { failure.set(thrown); }
        });
        reader.start();
        try {
            for (int i = 0; i < 2000; i++) {
                var key = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(v -> v, Object::toString)).build();
                owner.set(key, new Object()); if ((i & 1) == 0) owner.remove(key);
                owner.set(OBJECT, new Object());
            }
        } finally { done.set(true); reader.join(10000); }
        assertFalse(reader.isAlive()); assertNull(failure.get()); assertNotNull(owner.get(OBJECT));
    }

    private static BorrowedBacking checkDynamicIndexLeaseAcrossGrowthAndCow() throws Exception {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); owner.set(OBJECT, new Object());
        var field = NativeItemState.AccessOwner.class.getDeclaredField("roots"); field.setAccessible(true);
        Object roots = field.get(owner); var weakRoots = new WeakReference<>(roots);
        var root = (NativeItemState.AccessRoots)roots;
        var handleField = root.getClass().getDeclaredField("nativeHandle"); handleField.setAccessible(true);
        long backing = handleField.getLong(root);
        var parent = root.header;
        long originalDescriptor = parent.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8);
        for (int i = 0; i < 1000; i++) {
            var key = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(v -> v, Object::toString)).build();
            owner.set(key, new Object());
        }
        assertNotEquals(originalDescriptor, parent.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8));
        var view = parent.asSlice(0, 16).asReadOnly(); assertSame(parent.scope(), view.scope());
        var escaped = owner.asPatch(); owner.set(OBJECT, new Object()); escaped.cleanable.clean();
        escaped = null; roots = null; root = null; parent = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (weakRoots.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(weakRoots.get()); assertTrue(NativeItemState.liveForTest(backing));
        // The old descriptor itself is retained by this actual backing, not by an epoch check.
        var descriptor = java.lang.foreign.MemorySegment.ofAddress(originalDescriptor).reinterpret(16);
        assertEquals(7L, descriptor.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0));
        assertNotEquals(0L, descriptor.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8));
        assertNotNull(owner.get(OBJECT));
        var result = new BorrowedBacking(new WeakReference<>(view), backing);
        java.lang.ref.Reference.reachabilityFence(view); return result;
    }
    @Test void dynamicIndexDerivedViewPinsAllRetiredAllocationsUntilLastScopeDies() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); var borrowed = checkDynamicIndexLeaseAcrossGrowthAndCow();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((borrowed.view().get() != null || NativeItemState.liveForTest(borrowed.handle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(borrowed.view().get()); assertFalse(NativeItemState.liveForTest(borrowed.handle()));
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }

    @Test void dynamicAccessEscapedOptionalIdentitySurvivesCopyAndDetachment() {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); Object original = new Object();
        owner.set(OBJECT, original); var view = owner.asPatch(); var copy = owner.copy(); var copiedView = copy.asPatch();
        var exact = view.optional(OBJECT); assertSame(original, exact.orElseThrow()); assertSame(exact, copiedView.optional(OBJECT));
        owner.set(OBJECT, new Object()); assertSame(exact, view.optional(OBJECT)); assertSame(exact, copiedView.optional(OBJECT));
        view.remove(OBJECT); assertNull(copiedView.optional(OBJECT)); assertNull(copy.get(OBJECT)); assertNotNull(owner.get(OBJECT));
        view.cleanable.clean(); copiedView.cleanable.clean();
    }

    @Test void dynamicStagedAdoptionFailureKeepsOriginalCowRootAndEscapedMutationPrefix() throws Exception {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); owner.set(OBJECT, new Object());
        var escaped = owner.asPatch();
        var field = NativeItemState.AccessOwner.class.getDeclaredField("roots"); field.setAccessible(true); Object original = field.get(owner);
        var stageField = NativeItemState.class.getDeclaredField("ACCESS_STAGE"); stageField.setAccessible(true);
        var factoryClass = Class.forName("net.minecraft.world.item.NativeItemState$OwnerFactory");
        var adopt = NativeItemState.class.getDeclaredMethod("adopt", java.lang.invoke.MethodHandle.class, factoryClass, long[].class); adopt.setAccessible(true);
        var constructor = NativeItemState.AccessRoots.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var allocated = new java.util.concurrent.atomic.AtomicLong(); var injected = new AssertionError("modeled staged adoption failure");
        Object factory = java.lang.reflect.Proxy.newProxyInstance(factoryClass.getClassLoader(), new Class<?>[]{factoryClass}, (proxy, method, args) -> {
            allocated.set((long)args[0]); constructor.newInstance(args[0], args[1], original); throw injected;
        });
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> adopt.invoke(null, stageField.get(null), factory, new long[]{owner.handle}));
        assertSame(injected, failure.getCause()); assertTrue(allocated.get() > 0); assertFalse(NativeItemState.liveForTest(allocated.get()));
        assertSame(original, field.get(owner)); assertEquals(1L, (long)java.lang.foreign.ValueLayout.JAVA_LONG.varHandle().getAcquire(owner.cow, 0L));
        escaped.remove(OBJECT); assertNull(owner.get(OBJECT)); Object next = new Object(); owner.set(OBJECT, next);
        assertSame(next, owner.get(OBJECT)); assertNull(escaped.get(OBJECT)); escaped.cleanable.clean();
    }
    private static BorrowedBacking checkCowControlScopeAfterFacadeGc() throws Exception {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); owner.set(OBJECT, new Object());
        long handle = owner.handle; var weak = new WeakReference<>(owner); var view = owner.cow.asSlice(0, 8).asReadOnly();
        owner = null; long deadline = System.nanoTime() + 5_000_000_000L;
        while (weak.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(weak.get()); assertTrue(NativeItemState.liveForTest(handle));
        assertEquals(0L, (long)java.lang.foreign.ValueLayout.JAVA_LONG.varHandle().getAcquire(view, 0L));
        var result = new BorrowedBacking(new WeakReference<>(view), handle);
        java.lang.ref.Reference.reachabilityFence(view); return result;
    }
    @Test void dynamicCowControlDerivedViewPinsActualOwnerUntilItsLastScopeDies() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); var borrowed = checkCowControlScopeAfterFacadeGc();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((borrowed.view().get() != null || NativeItemState.liveForTest(borrowed.handle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(borrowed.view().get()); assertFalse(NativeItemState.liveForTest(borrowed.handle())); assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }

    @Test void dynamicNullTypeIdentityAndLivePayloadAliasesMatchReferenceMap() {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY);
        var reference = new net.minecraft.core.component.PatchedDataComponentMap(DataComponentMap.EMPTY);
        DataComponentType<Object> key = null; long[] value = {7};
        assertSame(reference.set(key, value), owner.set(key, value)); assertSame(value, owner.get(key));
        value[0] = 11; assertEquals(((long[])reference.get(key))[0], ((long[])owner.get(key))[0]);
        assertEquals(java.util.Collections.singletonList(null), owner.orderForTest());
        var escaped = owner.asPatch(); var copy = owner.copy(); assertSame(value, escaped.optional(key).orElseThrow());
        owner.remove(key); assertNull(owner.get(key)); assertSame(value, copy.get(key));
        escaped.remove(key); assertNull(copy.get(key)); escaped.cleanable.clean();
    }

    private static Cycle makeDynamicOwnerPayloadCycle() throws Exception {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); Object[] payload = {owner}; owner.set(OBJECT, payload);
        var field = NativeItemState.AccessOwner.class.getDeclaredField("roots"); field.setAccessible(true); Object roots = field.get(owner);
        var handle = NativeItemState.AccessRoots.class.getDeclaredField("nativeHandle"); handle.setAccessible(true);
        return new Cycle(new WeakReference<>(owner), new WeakReference<>(payload), owner.handle, handle.getLong(roots));
    }
    @Test void dynamicOwnerScopeAndInlineTypePayloadCycleReleaseBothActualNativeOwners() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); var cycle = makeDynamicOwnerPayloadCycle();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((cycle.owner().get() != null || cycle.payload().get() != null || NativeItemState.liveForTest(cycle.handle()) || NativeItemState.liveForTest(cycle.backing())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(cycle.owner().get()); assertNull(cycle.payload().get()); assertFalse(NativeItemState.liveForTest(cycle.handle())); assertFalse(NativeItemState.liveForTest(cycle.backing()));
        assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }

    private static NativeItemState.AccessRoots dynamicRoots(NativeItemState.AccessOwner owner) throws Exception {
        var field = NativeItemState.AccessOwner.class.getDeclaredField("roots"); field.setAccessible(true);
        return (NativeItemState.AccessRoots)field.get(owner);
    }
    private static long criticalCellReplace(NativeItemState.CellLeaseForTest lease, long version, long key, long incarnation, long expected, long next, java.lang.foreign.MemorySegment out) throws Throwable {
        var field = NativeItemState.class.getDeclaredField("ACCESS_REPLACE"); field.setAccessible(true);
        var call = (java.lang.invoke.MethodHandle)field.get(null); var view = lease.view();
        try { return (long)call.invokeExact(view.address(), version, key, incarnation, expected, next, out); }
        finally { java.lang.ref.Reference.reachabilityFence(view); }
    }
    @Test void dynamicSharingDuringEqualityUsesCommitOnlyFallbackWithoutReplayingCallbacks() throws Exception {
        Probe base = new Probe("base"), old = new Probe("old"), next = new Probe("next");
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.builder().set(TYPE, base).build()); owner.set(TYPE, old);
        long version = dynamicRoots(owner).version; var cell = NativeItemState.retainedCellForTest(owner, TYPE);
        var escaped = new AtomicReference<NativeItemState.AccessView>(); var copy = new AtomicReference<NativeItemState.AccessOwner>();
        var calls = new java.util.concurrent.atomic.AtomicInteger(); var failure = new AtomicReference<Throwable>();
        next.beforeEquals = () -> {
            calls.incrementAndGet(); escaped.set(owner.asPatch()); copy.set(owner.copy());
            Thread other = new Thread(() -> { try { copy.get().set(OBJECT, new Object()); } catch (Throwable thrown) { failure.set(thrown); } });
            other.start();
            try { other.join(5000); } catch (InterruptedException thrown) { throw new AssertionError(thrown); }
            assertFalse(other.isAlive(), "no owner/root/native lock may cross equality callback"); assertNull(failure.get());
            assertEquals(1L, cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 24), "new COW sharing must invalidate fast admission");
        };
        assertSame(old, owner.set(TYPE, next)); next.beforeEquals = () -> {};
        assertEquals(1, calls.get()); assertEquals(version, dynamicRoots(owner).version, "fallback must not detach the logical backing after callbacks");
        assertSame(next, owner.get(TYPE)); assertSame(next, escaped.get().get(TYPE)); assertSame(old, copy.get().get(TYPE));
        assertSame(old, cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 0) > 1 ? copy.get().get(TYPE) : null);
        escaped.get().cleanable.clean(); java.lang.ref.Reference.reachabilityFence(cell.view());
    }
    @Test void dynamicCriticalAdmissionRejectsWrongRootKeyIncarnationAndTokenBeforeEffects() throws Throwable {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); Object value = new Object(); owner.set(OBJECT, value);
        var roots = dynamicRoots(owner); var cell = NativeItemState.retainedCellForTest(owner, OBJECT); var view = cell.view();
        long key = view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8), incarnation = view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 40);
        long token = view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0);
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var out = arena.allocate(16, 8); out.fill((byte)0x5a); long canary = out.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0);
            synchronized (roots) {
                assertEquals(0, criticalCellReplace(cell, roots.version + 1, key, incarnation, token, Long.MAX_VALUE, out));
                assertEquals(0, criticalCellReplace(cell, roots.version, key + 1, incarnation, token, Long.MAX_VALUE, out));
                assertEquals(0, criticalCellReplace(cell, roots.version, key, incarnation + 1, token, Long.MAX_VALUE, out));
                assertEquals(0, criticalCellReplace(cell, roots.version, key, incarnation, token + 1, Long.MAX_VALUE, out));
            }
            assertEquals(canary, out.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0)); assertEquals(token, view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0));
        }
        assertSame(value, owner.get(OBJECT)); Object next = new Object(); assertSame(value, owner.set(OBJECT, next)); assertSame(next, owner.get(OBJECT));
    }

    @Test void dynamicRootGuardMakesAdmissionReplacementAtomicWithNewCowSharing() throws Throwable {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); owner.set(OBJECT, new Object());
        var alias = owner.copy(); var roots = dynamicRoots(owner); var cell = NativeItemState.retainedCellForTest(owner, OBJECT);
        var attempted = new java.util.concurrent.CountDownLatch(1); var failure = new AtomicReference<Throwable>();
        Thread sharing = new Thread(() -> {
            attempted.countDown(); try { alias.set(TYPE, new Probe("other")); } catch (Throwable thrown) { failure.set(thrown); }
        });
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var out = arena.allocate(16, 8);
            try { synchronized (roots) {
                sharing.start(); assertTrue(attempted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                long deadline = System.nanoTime() + 1_000_000_000L;
                while (sharing.getState() != Thread.State.BLOCKED && failure.get() == null && System.nanoTime() < deadline) Thread.sleep(1);
                assertEquals(Thread.State.BLOCKED, sharing.getState(), "COW sharing must use the exact same root guard");
                long token = cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 0);
                assertEquals(0L, cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 24));
                assertEquals(1, criticalCellReplace(cell, roots.version,
                    cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 8), cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 40), token, token, out));
                assertEquals(token, out.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0));
                assertEquals(0L, cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 24));
            } } finally { sharing.join(5000); }
        }
        assertFalse(sharing.isAlive()); assertNull(failure.get()); assertEquals(1L, cell.view().get(java.lang.foreign.ValueLayout.JAVA_LONG, 24));
        assertSame(owner.get(OBJECT), alias.get(OBJECT)); java.lang.ref.Reference.reachabilityFence(cell.view());
    }
    private static BorrowedBacking checkCanonicalCellViewAfterEntireRootGc() throws Exception {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); Object[] payload = {owner}; owner.set(OBJECT, payload);
        var roots = dynamicRoots(owner); var handleField = NativeItemState.AccessRoots.class.getDeclaredField("nativeHandle"); handleField.setAccessible(true);
        long rootHandle = handleField.getLong(roots), ownerHandle = owner.handle;
        var ownerWeak = new WeakReference<>(owner); var payloadWeak = new WeakReference<>(payload); var rootWeak = new WeakReference<>(roots);
        var lease = NativeItemState.retainedCellForTest(owner, OBJECT); long cellHandle = lease.handle();
        var view = lease.view().asSlice(0, 48).asReadOnly(); assertSame(lease.view().scope(), view.scope());
        assertEquals(1L, view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 32));
        owner = null; roots = null; payload = null; lease = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((ownerWeak.get() != null || rootWeak.get() != null || NativeItemState.liveForTest(rootHandle) || NativeItemState.liveForTest(ownerHandle)) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(ownerWeak.get()); assertNull(rootWeak.get()); assertNull(payloadWeak.get());
        assertFalse(NativeItemState.liveForTest(rootHandle)); assertFalse(NativeItemState.liveForTest(ownerHandle));
        assertTrue(NativeItemState.liveForTest(cellHandle)); assertTrue(view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0) > 1);
        // Backing::drop invalidates creator admission; this witnesses actual native
        // backing destruction while the independently retained cell stays readable.
        assertEquals(0L, view.get(java.lang.foreign.ValueLayout.JAVA_LONG, 32));
        var result = new BorrowedBacking(new WeakReference<>(view), cellHandle);
        java.lang.ref.Reference.reachabilityFence(view); return result;
    }
    @Test void dynamicCanonicalCellDerivedViewOutlivesRootAndReleasesAfterLastScope() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest(); var borrowed = checkCanonicalCellViewAfterEntireRootGc();
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((borrowed.view().get() != null || NativeItemState.liveForTest(borrowed.handle())) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(borrowed.view().get()); assertFalse(NativeItemState.liveForTest(borrowed.handle())); assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }

    @Test void dynamicNonInlineCellReplacementKeepsExactForeignKeyAndCopyAliases() {
        var owner = NativeItemState.AccessOwner.create(DataComponentMap.EMPTY); var keys = new java.util.ArrayList<DataComponentType<Object>>();
        var values = new java.util.ArrayList<Object>();
        for (int i = 0; i < 17; i++) {
            var key = DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(v -> v, Object::toString)).build();
            Object value = new Object(); keys.add(key); values.add(value); owner.set(key, value);
        }
        Object next = new Object(); assertSame(values.get(16), owner.set(keys.get(16), next)); assertSame(next, owner.get(keys.get(16)));
        var escaped = owner.asPatch(); var copied = owner.copy(); Object changed = new Object();
        assertSame(next, owner.set(keys.get(16), changed)); assertSame(changed, owner.get(keys.get(16)));
        assertSame(next, copied.get(keys.get(16))); assertSame(next, escaped.optional(keys.get(16)).orElseThrow());
        for (int i = 0; i < 16; i++) assertSame(values.get(i), owner.get(keys.get(i)));
        escaped.cleanable.clean();
    }


    private record BundledComponents(net.minecraft.core.component.PatchedDataComponentMap map, WeakReference<Object> stack, long handle) {}
    private static BundledComponents bundledComponents() throws Exception {
        bootstrap();
        ItemStack source = new ItemStack(Items.STICK, 11); Object leaf = new Object(); source.set(OBJECT, leaf);
        ItemStack copy = source.copyForNativeTransfer();
        long handle = NativeItemState.handleForTest(copy.state);
        var components = (net.minecraft.core.component.PatchedDataComponentMap)copy.getComponents();
        assertEquals(handle, NativeItemState.handleForTest(components.nativeState()), "one owner must supply both facades");
        var weak = new WeakReference<Object>(copy); copy = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (weak.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(weak.get()); assertTrue(NativeItemState.liveForTest(handle)); assertSame(leaf, components.get(OBJECT));
        Object replacement = new Object(); assertSame(leaf, components.set(OBJECT, replacement));
        assertSame(leaf, source.get(OBJECT)); assertSame(replacement, components.get(OBJECT));
        java.lang.ref.Reference.reachabilityFence(source);
        return new BundledComponents(components, weak, handle);
    }
    @Test void bundledTransferComponentsKeepActualStackAllocationAliveAfterScalarFacadeGc() throws Exception {
        long failures = NativeItemState.cleanupFailuresForTest();
        BundledComponents held = bundledComponents(); long handle = held.handle();
        WeakReference<Object> map = new WeakReference<>(held.map()); held = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((map.get() != null || NativeItemState.liveForTest(handle)) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(map.get()); assertFalse(NativeItemState.liveForTest(handle)); assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }
    @Test void bundledTransferDerivedScalarScopePinsSameOwnerAcrossComponentCow() throws Exception {
        bootstrap(); long failures = NativeItemState.cleanupFailuresForTest();
        ItemStack source = new ItemStack(Items.STICK, 11); source.set(OBJECT, new Object());
        ItemStack copy = source.copyForNativeTransfer(); long original = NativeItemState.handleForTest(copy.state);
        var retained = NativeItemState.scalarLeaseForTest(copy.state); long handle = retained.handle();
        assertEquals(NativeItemState.stackAddressForTest(original), NativeItemState.stackAddressForTest(handle));
        var view = retained.view().asSlice(8,4).asReadOnly(); retained = null;
        copy.set(OBJECT, new Object()); copy = null; source = null;
        System.gc(); assertTrue(NativeItemState.liveForTest(handle)); assertEquals(11, view.get(java.lang.foreign.ValueLayout.JAVA_INT,0));
        WeakReference<Object> weak = new WeakReference<>(view); view = null;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((weak.get() != null || NativeItemState.liveForTest(handle)) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(10); }
        assertNull(weak.get()); assertFalse(NativeItemState.liveForTest(handle)); assertEquals(failures, NativeItemState.cleanupFailuresForTest());
    }

    @Test void slotRootPublicationFailureLeavesActualNativeMembershipUnchanged() throws Exception {
        bootstrap(); var slots = (NativeItemSlots)NativeItemSlots.withSize(27);
        var ownerField = NativeItemSlots.class.getDeclaredField("owner"); ownerField.setAccessible(true);
        Object owner = ownerField.get(slots); var rootsField = owner.getClass().getDeclaredField("roots"); rootsField.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<Long,Object> original = (java.util.Map<Long,Object>)rootsField.get(owner);
        AssertionError failure = new AssertionError("modelled root allocation failure");
        var throwing = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Object>(original) {
            @Override public Object put(long key, Object value) { if (!original.containsKey(key)) throw failure; return super.put(key,value); }
        };
        rootsField.set(owner, throwing);
        ItemStack value = new ItemStack(Items.STICK, 11);
        assertSame(failure, assertThrows(AssertionError.class, () -> slots.set(0,value)));
        assertSame(ItemStack.EMPTY, slots.get(0), "native membership must remain old when root staging fails");
        assertEquals(11, value.getCount()); assertEquals(original.keySet(),throwing.keySet());
        rootsField.set(owner, original);
        assertSame(ItemStack.EMPTY,slots.set(0,value)); assertSame(value,slots.get(0));
        slots.set(1,value);throwing.putAll(original);rootsField.set(owner,throwing);
        var inserted=new ItemStack(Items.STICK,5);
        assertSame(failure,assertThrows(AssertionError.class,()->actualSlots(slots).commitTransferInsert(value,0,inserted,5)));
        assertEquals(11,value.getCount());assertSame(value,slots.get(0));assertSame(value,slots.get(1));
        rootsField.set(owner,original);actualSlots(slots).commitTransferInsert(value,0,inserted,5);
        assertEquals(6,value.getCount());assertSame(inserted,slots.get(0));assertSame(value,slots.get(1));
        slots.set(1,ItemStack.EMPTY);assertFalse(original.containsKey(NativeItemState.handleForTest(value.state)));
    }
    private static long ownerAddress(Object owner) throws Exception {
        var field = owner.getClass().getDeclaredField("ownerAddress"); field.setAccessible(true); return field.getLong(owner);
    }
    private static NativeItemState.Slots actualSlots(NativeItemSlots slots) throws Exception {
        var field = NativeItemSlots.class.getDeclaredField("owner"); field.setAccessible(true); return (NativeItemState.Slots)field.get(slots);
    }
    @Test void boundedTransferReleasesActualGatesBeforeEffectsAndPreservesThrownObject() throws Exception {
        bootstrap(); var facade = (NativeItemSlots)NativeItemSlots.withSize(27); var slots = actualSlots(facade);
        ItemStack source = new ItemStack(Items.STICK,11), target = new ItemStack(Items.STICK,60); facade.set(0,target);
        var defaults = DataComponentMap.nativeDefaults(source.getPrototype());
        long slotAddress = ownerAddress(slots), defaultAddress = ownerAddress(defaults);
        var raw = java.lang.foreign.MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);
        var atomic = java.lang.foreign.ValueLayout.JAVA_LONG.varHandle();
        AssertionError expected = new AssertionError("actual Java effect failure");
        var effects = new NativeItemState.TransferEffects() {
            @Override public long effect(long action,long index,long phase,long amount,long changed) {
                assertEquals(0L,(long)atomic.getAcquire(raw,slotAddress));
                assertEquals(0L,(long)atomic.getAcquire(raw,defaultAddress));
                assertEquals(1,action); assertEquals(0,index); assertEquals(7,source.getCount()); assertEquals(64,target.getCount());
                assertSame(target,facade.get(0));
                Object max = defaults.get(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
                assertSame(max,defaults.put(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,max));
                throw expected;
            }
        };
        assertSame(expected,assertThrows(AssertionError.class,()->slots.runTransfer(source,effects)));
        assertEquals(0L,(long)atomic.getAcquire(raw,slotAddress)); assertEquals(0L,(long)atomic.getAcquire(raw,defaultAddress));
        assertEquals(7,source.getCount()); assertEquals(64,target.getCount());
        java.lang.ref.Reference.reachabilityFence(source); java.lang.ref.Reference.reachabilityFence(defaults);
        java.lang.ref.Reference.reachabilityFence(facade);
    }
    @Test void boundedTransferGateContentionDeclinesBeforeEffectsAndReleasesPartialAdmission() throws Exception {
        bootstrap(); var facade = (NativeItemSlots)NativeItemSlots.withSize(27); var slots = actualSlots(facade);
        ItemStack source = new ItemStack(Items.STICK,11); var defaults = DataComponentMap.nativeDefaults(source.getPrototype());
        long slotAddress = ownerAddress(slots), defaultAddress = ownerAddress(defaults);
        var raw = java.lang.foreign.MemorySegment.ofAddress(0).reinterpret(Long.MAX_VALUE);
        var atomic = java.lang.foreign.ValueLayout.JAVA_LONG.varHandle();
        Object lease = source.state;
        var step = slots.getClass().getDeclaredMethod("boundedStep",NativeItemState.Overlay.class,defaults.getClass(),long.class); step.setAccessible(true);
        for (long address : new long[]{slotAddress,defaultAddress}) {
            assertTrue(atomic.compareAndSet(raw,address,0L,1L));
            try { assertEquals(4L,step.invoke(slots,lease,defaults,0L)); }
            finally { atomic.setRelease(raw,address,0L); }
            assertEquals(0L,(long)atomic.getAcquire(raw,slotAddress)); assertEquals(0L,(long)atomic.getAcquire(raw,defaultAddress));
            assertEquals(11,source.getCount()); assertSame(ItemStack.EMPTY,facade.get(0));
        }
        long accepted = (long)step.invoke(slots,lease,defaults,0L);
        assertEquals(2L,accepted & 7,"positive control must execute bounded native consumer, with no regular fallback");
        assertEquals(11L,accepted >> 32);
        assertEquals(0L,(long)atomic.getAcquire(raw,slotAddress)); assertEquals(0L,(long)atomic.getAcquire(raw,defaultAddress));
        java.lang.ref.Reference.reachabilityFence(lease); java.lang.ref.Reference.reachabilityFence(defaults);
        java.lang.ref.Reference.reachabilityFence(facade);
    }

    @Test void boundedTransferSignedAmountSurvivesBothCriticalAndRegularContinuation() throws Exception {
        bootstrap(); var defaults = DataComponentMap.nativeDefaults(new ItemStack(Items.STICK).getPrototype());
        var maxType = net.minecraft.core.component.DataComponents.MAX_STACK_SIZE; Object original = defaults.put(maxType,-7);
        try {
            for (boolean invalidate : new boolean[]{false,true}) {
                var facade = (NativeItemSlots)NativeItemSlots.withSize(27); var slots = actualSlots(facade);
                ItemStack source = new ItemStack(Items.STICK,11);
                if (invalidate) ((net.minecraft.core.component.PatchedDataComponentMap)source.getComponents()).clearPatch();
                int[] calls = {0};
                assertTrue(slots.runTransfer(source,new NativeItemState.TransferEffects() {
                    @Override public long effect(long action,long index,long phase,long amount,long changed) {
                        calls[0]++; assertEquals(2,action); assertEquals(0,index); assertEquals(-7,amount);
                        ItemStack inserted = source.copyForNativeTransferWithCount((int)amount);
                        slots.commitTransferInsert(source,(int)index,inserted,(int)amount); return 1;
                    }
                }));
                assertEquals(1,calls[0]); assertEquals(18,source.getCount()); assertEquals(-7,facade.get(0).state.count());
            }
        } finally { defaults.put(maxType,original); }
    }

    @Test void combinedOwnerPromotionReusesActualComponentHandleAndRejectsSecondBinding() {
        var components = NativeItemState.Overlay.create(DataComponentMap.EMPTY); long handle = NativeItemState.handleForTest(components);
        NativeItemState.Stack stack = NativeItemState.Stack.create(null,Integer.MIN_VALUE,components);
        assertSame(components,stack); assertSame(components,stack.components()); assertEquals(handle,NativeItemState.handleForTest(stack));
        assertThrows(IllegalStateException.class,()->NativeItemState.Stack.create(null,11,components));
        assertEquals(Integer.MIN_VALUE,stack.count()); assertTrue(NativeItemState.liveForTest(handle));
        stack.count(Integer.MAX_VALUE); assertEquals(Integer.MAX_VALUE,stack.count());
    }

    @Test void actualComponentFacadeOwnsOriginalCopiedAndComponentOnlyAllocationsWithoutAComposedLease() {
        bootstrap();var source=new ItemStack(Items.STICK,11);Object leaf=new Object();source.set(OBJECT,leaf);source.setPopTime(7);
        assertSame(source.components,source.state);assertSame(source.components,source.components.nativeState());assertSame(source.components,source.state.components());
        var copy=source.copy();assertSame(copy.components,copy.state);assertSame(copy.components,copy.state.components());
        assertNotEquals(NativeItemState.handleForTest(source.state),NativeItemState.handleForTest(copy.state));assertSame(leaf,copy.get(OBJECT));assertEquals(7,copy.getPopTime());
        var components=source.components.copy();assertSame(components,components.nativeState());assertSame(leaf,components.get(OBJECT));
        assertThrows(IllegalStateException.class,components::count);
        var escaped=source.components.asPatch();source.set(OBJECT,new Object());assertSame(leaf,copy.get(OBJECT));assertSame(leaf,components.get(OBJECT));assertSame(leaf,escaped.get(OBJECT).orElseThrow());
        source.setCount(-7);assertEquals(11,copy.getCount());assertEquals(-7,source.state.count());
    }

}
