package net.minecraft.world.item;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeEnchantmentRootsTest {
    private static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static Object backing(net.minecraft.world.item.enchantment.ItemEnchantments value) throws Exception {
        var field = value.getClass().getDeclaredField("enchantments"); field.setAccessible(true); return field.get(value);
    }
    private static final class Key {
        final int id;
        final int hashCode;
        Runnable hash = () -> {};
        Runnable equal = () -> {};
        Key(int id) { this(id, 5); }
        Key(int id, int hashCode) { this.id = id; this.hashCode = hashCode; }
        @Override public int hashCode() { hash.run(); return hashCode; }
        @Override public boolean equals(Object other) {
            equal.run();
            return other instanceof Key key && id == key.id;
        }
    }

    @Test void actualNativePayloadStoresLevelsAndResolvesEquivalentDistinctHolders() {
        var owner = new NativeItemRoots.Enchantments(16);
        assertEquals(0, owner.put(new Key(1), 255));
        assertEquals(255, owner.get(new Key(1), 0, false));
        assertEquals(255, owner.put(new Key(1), -2));
        assertEquals(-2, owner.get(new Key(1), 0, false));
        assertEquals(1, owner.size());
        assertEquals(0, owner.get(new Key(2), 0, false));
        assertEquals(91, owner.get(new Key(2), 91, true));
    }

    @Test void nullLevelsAndGrowthMatchTheActualOriginalMap() {
        var original = new Object2IntOpenHashMap<Object>();
        var owner = new NativeItemRoots.Enchantments(16);
        assertEquals(original.put(null, -9), owner.put(null, -9));
        for (int i = 0; i < 96; i++) {
            var key = new Key(i);
            assertEquals(original.put(key, i), owner.put(key, i));
        }
        assertEquals(original.size(), owner.size());
        assertEquals(original.getInt(null), owner.get(null, 0, false));
        for (int i = 0; i < 96; i++) assertEquals(original.getInt(new Key(i)), owner.get(new Key(i), 0, false));
    }

    @Test void equalityReentryUsesTheCurrentNativeValueWithoutHoldingANativeGuard() {
        var owner = new NativeItemRoots.Enchantments(16);
        owner.put(new Key(1), 1);
        var query = new Key(1);
        var once = new AtomicBoolean(true);
        query.equal = () -> { if (once.getAndSet(false)) owner.put(new Key(1), 7); };
        assertEquals(7, owner.get(query, 0, false));
        assertFalse(once.get());
    }

    @Test void hashAndEqualityErrorsEscapeAsTheExactOriginalThrowable() {
        var owner = new NativeItemRoots.Enchantments(16);
        owner.put(new Key(1), 4);
        var query = new Key(1);
        var expected = new AssertionError("original holder failure");
        query.hash = () -> { throw expected; };
        assertSame(expected, assertThrows(AssertionError.class, () -> owner.get(query, 0, false)));
        query.hash = () -> {};
        query.equal = () -> { throw expected; };
        assertSame(expected, assertThrows(AssertionError.class, () -> owner.get(query, 0, false)));
        assertEquals(4, owner.get(new Key(1), 0, false));
    }

    @Test void failedActualVmBindingCannotPublishANativeMember() throws Exception {
        var owner = new NativeItemRoots.Enchantments(16);
        var rootsField = owner.getClass().getDeclaredField("roots"); rootsField.setAccessible(true);
        Object roots = rootsField.get(owner);
        var anchorsField = roots.getClass().getDeclaredField("anchors"); anchorsField.setAccessible(true);
        var expected = new AssertionError("actual anchor allocation failure");
        var once = new AtomicBoolean(true);
        var anchors = new Long2ObjectOpenHashMap<Object>() {
            @Override public Object put(long token, Object value) {
                if (once.getAndSet(false)) throw expected;
                return super.put(token, value);
            }
        };
        anchorsField.set(roots, anchors);
        assertSame(expected, assertThrows(AssertionError.class, () -> owner.put(new Key(1), 3)));
        assertEquals(0, owner.size());
        assertEquals(0, owner.get(new Key(1), 0, false));
        owner.put(new Key(1), 3);
        assertEquals(3, owner.get(new Key(1), 0, false));
    }

    @Test void vmBindingThatThrowsAfterAcceptingTheAnchorRollsBackThatActualAnchor() throws Exception {
        var owner = new NativeItemRoots.Enchantments(16);
        var rootsField = owner.getClass().getDeclaredField("roots"); rootsField.setAccessible(true);
        Object roots = rootsField.get(owner);
        var anchorsField = roots.getClass().getDeclaredField("anchors"); anchorsField.setAccessible(true);
        var expected = new AssertionError("failure after actual anchor insertion");
        var anchors = new Long2ObjectOpenHashMap<Object>() {
            @Override public Object put(long token, Object value) { super.put(token, value); throw expected; }
        };
        anchorsField.set(roots, anchors);
        assertSame(expected, assertThrows(AssertionError.class, () -> owner.put(new Key(1), 3)));
        assertEquals(0, owner.size());
        assertEquals(0, anchors.size());
        assertEquals(0, owner.get(new Key(1), 0, false));
    }

    @Test void sameTableNestedWriteDuringActualVmBindingRejectsOnlyTheStaleOuterInsertion() throws Exception {
        var owner = new NativeItemRoots.Enchantments(16);
        var rootsField = owner.getClass().getDeclaredField("roots"); rootsField.setAccessible(true);
        Object roots = rootsField.get(owner);
        var anchorsField = roots.getClass().getDeclaredField("anchors"); anchorsField.setAccessible(true);
        var once = new AtomicBoolean(true);
        var anchors = new Long2ObjectOpenHashMap<Object>() {
            @Override public Object put(long token, Object value) {
                Object old = super.put(token, value);
                if (once.getAndSet(false)) owner.put(new Key(9), 12);
                return old;
            }
        };
        anchorsField.set(roots, anchors);
        assertThrows(IllegalStateException.class, () -> owner.put(new Key(1), 3));
        assertEquals(1, owner.size());
        assertEquals(1, anchors.size());
        assertEquals(0, owner.get(new Key(1), 0, false));
        assertEquals(12, owner.get(new Key(9), 0, false));
    }

    @Test void removalAndShrinkMatchOriginalCollisionChainsAndNullBehavior() {
        var original = new Object2IntOpenHashMap<Object>();
        var owner = new NativeItemRoots.Enchantments(16);
        original.put(null, 7); owner.put(null, 7);
        for (int i = 0; i < 96; i++) { original.put(new Key(i), i); owner.put(new Key(i), i); }
        for (int i = 0; i < 96; i++) {
            assertEquals(original.removeInt(new Key(i)), owner.removeInt(new Key(i)));
            assertEquals(original.size(), owner.size());
            for (int remaining = i + 1; remaining < 96; remaining++)
                assertEquals(original.getInt(new Key(remaining)), owner.get(new Key(remaining), 0, false));
        }
        assertEquals(original.removeInt(null), owner.removeInt(null));
        assertEquals(original.removeInt(null), owner.removeInt(null));
        assertEquals(0, owner.size());
    }

    @Test void displacedHolderHashErrorKeepsTheSamePartialMutationAsOriginal() {
        var original = new Object2IntOpenHashMap<Object>();
        var owner = new NativeItemRoots.Enchantments(16);
        var first = new Key(1); var second = new Key(2);
        original.put(first, 3); original.put(second, 4);
        owner.put(first, 3); owner.put(second, 4);
        var expected = new AssertionError("displaced holder hash");
        second.hash = () -> { throw expected; };
        assertSame(expected, assertThrows(AssertionError.class, () -> original.removeInt(first)));
        assertSame(expected, assertThrows(AssertionError.class, () -> owner.removeInt(first)));
        second.hash = () -> {};
        assertEquals(original.size(), owner.size());
        assertEquals(original.getInt(first), owner.get(first, 0, false));
        assertEquals(original.getInt(second), owner.get(second, 0, false));
    }

    private static Held removed() {
        var owner = new NativeItemRoots.Enchantments(16);
        var key = new Key(1);
        owner.put(key, 8);
        owner.removeInt(new Key(1));
        return new Held(owner, new WeakReference<>(key));
    }
    @Test void removedHolderLosesItsActualAnchorWhileTheNativeOwnerRemainsLive() throws Exception {
        Held held = removed();
        long until = System.nanoTime() + 5_000_000_000L;
        while (held.key.get() != null && System.nanoTime() < until) { System.gc(); Thread.sleep(10); }
        assertNull(held.key.get());
        assertEquals(0, held.owner.size());
        assertEquals(0, held.owner.get(new Key(1), 0, false));
        Reference.reachabilityFence(held);
    }

    @Test void actualNativeIteratorWrapRemovalMatchesOriginalOrderAndEffects() {
        var original = new Object2IntOpenHashMap<Object>();
        var owner = new NativeItemRoots.Enchantments(16);
        int hash = 0; while ((it.unimi.dsi.fastutil.HashCommon.mix(hash) & 31) != 31) hash++;
        var events = new java.util.ArrayList<Integer>();
        for (int i = 1; i <= 3; i++) {
            var key = new Key(i, hash); int id = i; key.hash = () -> events.add(id);
            original.put(key, i); owner.put(key, i);
        }
        events.clear();
        var expected = new java.util.ArrayList<Integer>();
        var old = original.keySet().iterator();
        while (old.hasNext()) { expected.add(((Key)old.next()).id); old.remove(); }
        var expectedEffects = java.util.List.copyOf(events); events.clear();
        var actual = new java.util.ArrayList<Integer>();
        var cursor = owner.keyIterator();
        while (cursor.hasNext()) { actual.add(((Key)cursor.next()).id); cursor.remove(); }
        assertEquals(java.util.List.of(1, 3, 2), expected);
        assertEquals(expected, actual);
        assertEquals(expectedEffects, events);
        assertEquals(0, owner.size());
        assertThrows(IllegalStateException.class, cursor::remove);
    }

    @Test void nativeIteratorConsumerFailurePreservesOriginalResumeStateAndError() {
        var original = new Object2IntOpenHashMap<Object>();
        var owner = new NativeItemRoots.Enchantments(16);
        for (int i = 1; i <= 2; i++) { original.put(new Key(i), i); owner.put(new Key(i), i); }
        var old = original.keySet().iterator(); var cursor = owner.keyIterator();
        var expected = new AssertionError("original iterator consumer");
        assertSame(expected, assertThrows(AssertionError.class, () -> old.forEachRemaining(key -> { throw expected; })));
        assertSame(expected, assertThrows(AssertionError.class, () -> cursor.forEachRemaining(key -> { throw expected; })));
        assertTrue(old.hasNext()); assertTrue(cursor.hasNext());
        assertEquals(((Key)old.next()).id, ((Key)cursor.next()).id);
        var oldFailure = assertThrows(NullPointerException.class, old::next);
        var newFailure = assertThrows(NullPointerException.class, cursor::next);
        assertEquals(oldFailure.getMessage(), newFailure.getMessage());
        assertFalse(old.hasNext()); assertFalse(cursor.hasNext());
    }

    @Test void nativeIteratorConsumerCanReenterItsActualCursorAndRestoreTheOuterEffectFrame() {
        var owner = new NativeItemRoots.Enchantments(16);
        owner.put(new Key(1), 1); owner.put(new Key(2), 2);
        var cursor = owner.keyIterator(); var seen = new java.util.ArrayList<Integer>();
        cursor.forEachRemaining(key -> {
            seen.add(((Key)key).id);
            if (cursor.hasNext()) seen.add(((Key)cursor.next()).id);
        });
        assertEquals(java.util.List.of(2, 1), seen);
        assertFalse(cursor.hasNext());
        assertEquals(2, owner.get(new Key(2), 0, false));
    }

    @Test void nativeIteratorNegativeSkipConsumesTheOriginalLiveRemainingCount() {
        var owner = new NativeItemRoots.Enchantments(16);
        owner.put(new Key(1), 1); owner.put(new Key(2), 2);
        var cursor = owner.keyIterator();
        assertEquals(2, cursor.skip(-1));
        assertFalse(cursor.hasNext());
        assertThrows(java.util.NoSuchElementException.class, cursor::next);
    }

    @Test void nativeIteratorOwnsItsActualSourceAllocationAfterOriginalHandleRelease() throws Exception {
        var owner = new NativeItemRoots.Enchantments(16); owner.put(new Key(1), 8);
        var cursor = owner.keyIterator();
        var ownerHandle = owner.getClass().getDeclaredMethod("handle"); ownerHandle.setAccessible(true);
        var cursorHandleMethod = cursor.getClass().getSuperclass().getDeclaredMethod("handle"); cursorHandleMethod.setAccessible(true);
        long originalHandle = (long)ownerHandle.invoke(owner);
        long cursorHandle = (long)cursorHandleMethod.invoke(cursor);
        var close = owner.getClass().getDeclaredMethod("close"); close.setAccessible(true); close.invoke(owner);
        assertFalse(NativeItemState.liveForTest(originalHandle));
        System.gc();
        assertTrue(cursor.hasNext());
        assertEquals(1, ((Key)cursor.next()).id);
        assertFalse(cursor.hasNext());
        assertTrue(NativeItemState.liveForTest(cursorHandle));
        var cursorClose = cursor.getClass().getSuperclass().getDeclaredMethod("close"); cursorClose.setAccessible(true); cursorClose.invoke(cursor);
        assertFalse(NativeItemState.liveForTest(cursorHandle));
        Reference.reachabilityFence(owner);
    }

    @Test void publicBuildersActuallyUseNativePayloadsShareImmutableViewsAndCopyOtherBuilders() throws Exception {
        bootstrap();
        var holder = net.minecraft.core.Holder.Reference.<net.minecraft.world.item.enchantment.Enchantment>createStandAlone(
            new net.minecraft.core.HolderOwner<>() {}, net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("native_root_alias_test")));
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        mutable.set(holder, 2); var first = mutable.toImmutable();
        assertInstanceOf(NativeItemRoots.Enchantments.class, backing(first));
        var field = mutable.getClass().getDeclaredField("enchantments"); field.setAccessible(true);
        assertSame(field.get(mutable), backing(first));
        var copied = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(first);
        var copy = copied.toImmutable(); assertNotSame(backing(first), backing(copy));
        mutable.set(holder, 7); assertEquals(7, first.getLevel(holder)); assertEquals(2, copy.getLevel(holder));
        var entry = first.entrySet().iterator().next(); assertEquals(7, entry.setValue(-9));
        assertEquals(-9, mutable.getLevel(holder));
        assertThrows(IllegalArgumentException.class, mutable::toImmutable);
        var invalidCopy = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(first);
        assertEquals(-9, invalidCopy.getLevel(holder));
    }

    @Test void realHolderReferencesUseActualIdentityRatherThanTheirSharedRegistryKey() {
        bootstrap();
        var holderOwner = new net.minecraft.core.HolderOwner<net.minecraft.world.item.enchantment.Enchantment>() {};
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("native_root_identity_test"));
        var first = net.minecraft.core.Holder.Reference.createStandAlone(holderOwner, key);
        var distinct = net.minecraft.core.Holder.Reference.createStandAlone(holderOwner, key);
        var original = new Object2IntOpenHashMap<Object>(); var map = new NativeItemRoots.Enchantments(16);
        original.put(first, 3); map.put(first, 3);
        assertEquals(original.getInt(first), map.getInt(first));
        assertEquals(original.getInt(distinct), map.getInt(distinct));
        assertEquals(0, map.getInt(distinct));
        map.put(distinct, 5); assertEquals(2, map.size()); assertEquals(3, map.getInt(first)); assertEquals(5, map.getInt(distinct));
    }

    @Test void nativePublicCodecAndStreamFactoriesMatchPhysicalJavaBackingAndOwnDecodedPayloads() throws Exception {
        bootstrap();
        var registry = new net.minecraft.core.MappedRegistry<net.minecraft.world.item.enchantment.Enchantment>(
            net.minecraft.core.registries.Registries.ENCHANTMENT, com.mojang.serialization.Lifecycle.stable());
        var definition = new net.minecraft.world.item.enchantment.Enchantment(net.minecraft.network.chat.Component.literal("test"),
            net.minecraft.world.item.enchantment.Enchantment.definition(net.minecraft.core.HolderSet.direct(), 1, 3,
                net.minecraft.world.item.enchantment.Enchantment.constantCost(1), net.minecraft.world.item.enchantment.Enchantment.constantCost(1), 1),
            net.minecraft.core.HolderSet.direct(), net.minecraft.core.component.DataComponentMap.EMPTY);
        var holder = registry.register(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("native_root_codec_test")), definition, net.minecraft.core.RegistrationInfo.BUILT_IN);
        registry.freeze();
        var access = new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(java.util.List.of(registry)).freeze();
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, access);
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        mutable.set(holder, 3); var nativeValue = mutable.toImmutable();
        var plain = new Object2IntOpenHashMap<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>>(); plain.put(holder, 3);
        var constructor = net.minecraft.world.item.enchantment.ItemEnchantments.class.getDeclaredConstructor(Object2IntOpenHashMap.class); constructor.setAccessible(true);
        var legacy = constructor.newInstance(plain); assertSame(plain, backing(legacy));
        var codec = net.minecraft.world.item.enchantment.ItemEnchantments.CODEC;
        var json = codec.encodeStart(ops, nativeValue).getOrThrow(); assertEquals(codec.encodeStart(ops, legacy).getOrThrow(), json);
        var decoded = codec.parse(ops, json).getOrThrow(); assertEquals(3, decoded.getLevel(holder)); assertInstanceOf(NativeItemRoots.Enchantments.class, backing(decoded));
        var left = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), access);
        var right = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), access);
        try {
            var stream = net.minecraft.world.item.enchantment.ItemEnchantments.STREAM_CODEC;
            stream.encode(left, nativeValue); stream.encode(right, legacy);
            assertArrayEquals(io.netty.buffer.ByteBufUtil.getBytes(right), io.netty.buffer.ByteBufUtil.getBytes(left));
            var streamed = stream.decode(left); assertEquals(3, streamed.getLevel(holder)); assertInstanceOf(NativeItemRoots.Enchantments.class, backing(streamed));
        } finally { left.release(); right.release(); }
    }

    private record Held(NativeItemRoots.Enchantments owner, WeakReference<Object> key) {}
    private static Held rooted() {
        var owner = new NativeItemRoots.Enchantments(16);
        var key = new Key(1);
        owner.put(key, 8);
        for (int i = 2; i <= 64; i++) owner.put(new Key(i), i);
        return new Held(owner, new WeakReference<>(key));
    }
    @Test void actualTableResourcesRetainHoldersAcrossRehashAndGcThenReleaseTheirLastVmRoot() throws Exception {
        Held held = rooted();
        System.gc(); Thread.sleep(25);
        assertNotNull(held.key.get());
        assertEquals(8, held.owner.get(new Key(1), 0, false));
        var owner = new WeakReference<Object>(held.owner);
        var key = held.key;
        Reference.reachabilityFence(held);
        held = null;
        long until = System.nanoTime() + 5_000_000_000L;
        while ((owner.get() != null || key.get() != null) && System.nanoTime() < until) { System.gc(); Thread.sleep(10); }
        assertNull(owner.get());
        assertNull(key.get());
    }
}
