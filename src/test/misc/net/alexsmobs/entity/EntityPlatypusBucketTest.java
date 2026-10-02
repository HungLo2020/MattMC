package net.alexsmobs.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real entities, item codecs and bucket dispatch; world placement/player I/O are mocked. */
class EntityPlatypusBucketTest {
    private static final String[] FLAGS = {"Fedora", "Sensing", "FromBucket", "HasEgg", "SuperCharged"};
    private static String previousByteBuddy;
    private ServerLevel level;

    @BeforeAll
    static void bootstrap() {
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterAll
    static void restoreProperties() {
        if (previousByteBuddy == null) System.clearProperty("net.bytebuddy.experimental");
        else System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
    }

    @BeforeEach
    void createLevel() {
        level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
    }

    private EntityPlatypus platypus() {
        EntityPlatypus entity = EntityType.PLATYPUS.create(level, EntitySpawnReason.COMMAND);
        assertNotNull(entity);
        return entity;
    }

    private EntityPlatypus prepared() {
        EntityPlatypus entity = platypus();
        CompoundTag flags = new CompoundTag();
        for (String key : FLAGS) flags.putBoolean(key, true);
        entity.loadFromBucketTag(flags);
        entity.setFromBucket(false);
        entity.setHealth(7.0F);
        entity.setNoAi(true);
        entity.setSilent(true);
        entity.setNoGravity(true);
        entity.setGlowingTag(true);
        entity.setInvulnerable(true);
        entity.setCustomName(Component.literal("Perry test"));
        return entity;
    }

    @Test
    void captureWritesCustomFlagsAlongsideDefaultBucketDataWithoutTouchingCustomData() {
        EntityPlatypus source = prepared();
        ItemStack bucket = source.getBucketItemStack();
        CompoundTag unrelated = new CompoundTag();
        unrelated.putString("plugin_note", "keep me");
        unrelated.putBoolean("Sensing", false);
        bucket.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));

        source.saveToBucketTag(bucket);

        CompoundTag saved = bucket.get(DataComponents.BUCKET_ENTITY_DATA).copyTag();
        for (String key : FLAGS) assertEquals(!key.equals("FromBucket"), saved.getBooleanOr(key, false), key);
        assertEquals(7.0F, saved.getFloatOr("Health", -1.0F));
        for (String key : new String[]{"NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable"})
            assertTrue(saved.getBooleanOr(key, false), key);
        assertEquals(source.getCustomName(), bucket.get(DataComponents.CUSTOM_NAME));
        assertEquals(unrelated, bucket.get(DataComponents.CUSTOM_DATA).copyTag());
    }

    @Test
    void repeatedItemSaveReloadAndReleasePreserveStateAndSetFromBucket() throws Exception {
        EntityPlatypus current = prepared();
        for (int cycle = 0; cycle < 3; cycle++) {
            ItemStack bucket = current.getBucketItemStack();
            current.saveToBucketTag(bucket);
            ItemStack reloaded = saveReload(bucket);
            assertTrue(reloaded.is(Items.PLATYPUS_BUCKET));
            assertEquals(1, reloaded.getCount());
            current = releaseThroughMobBucket(reloaded);
            assertPreserved(current);
        }
    }

    @Test
    void legacyBucketImportsOnlyMissingKnownFlagsAndNeverChangesTheItem() throws Exception {
        ItemStack bucket = new ItemStack(Items.PLATYPUS_BUCKET);
        CompoundTag current = new CompoundTag();
        current.putFloat("Health", 6.0F);
        current.putBoolean("Sensing", false); // Explicit new false wins over legacy true.
        current.putBoolean("Fedora", true);
        current.putString("SuperCharged", "invalid-new-value");
        current.putString("new_extra", "keep");
        CompoundTag legacy = new CompoundTag();
        for (String key : FLAGS) legacy.putBoolean(key, true);
        legacy.putBoolean("Fedora", false);
        legacy.putFloat("Health", 99.0F);
        legacy.putBoolean("Invulnerable", true);
        legacy.putString("plugin_note", "untouched");
        bucket.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(current));
        bucket.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        bucket.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy Perry"));

        ItemStack reloaded = saveReload(bucket);
        EntityPlatypus released = releaseThroughMobBucket(reloaded);

        assertTrue(released.hasFedora());
        assertFalse(released.isSensing());
        assertTrue(released.hasEgg());
        assertFalse(released.superCharged);
        assertTrue(released.fromBucket());
        assertEquals(6.0F, released.getHealth());
        assertFalse(released.isInvulnerable());
        assertEquals(Component.literal("Legacy Perry"), released.getCustomName());
        assertEquals(current, bucket.get(DataComponents.BUCKET_ENTITY_DATA).copyTag());
        assertEquals(legacy, bucket.get(DataComponents.CUSTOM_DATA).copyTag());
        assertEquals(current, reloaded.get(DataComponents.BUCKET_ENTITY_DATA).copyTag());
        assertEquals(legacy, reloaded.get(DataComponents.CUSTOM_DATA).copyTag());
    }

    @Test
    void legacyOnlyAndEmptyBucketsUseDocumentedDefaults() throws Exception {
        ItemStack legacyBucket = new ItemStack(Items.PLATYPUS_BUCKET);
        CompoundTag legacy = new CompoundTag();
        for (String key : FLAGS) legacy.putBoolean(key, true);
        legacyBucket.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
        EntityPlatypus legacyEntity = releaseThroughMobBucket(saveReload(legacyBucket));
        assertTrue(legacyEntity.hasFedora());
        assertTrue(legacyEntity.isSensing());
        assertTrue(legacyEntity.hasEgg());
        assertTrue(legacyEntity.superCharged);
        assertTrue(legacyEntity.fromBucket());

        EntityPlatypus empty = releaseThroughMobBucket(new ItemStack(Items.PLATYPUS_BUCKET));
        assertFalse(empty.hasFedora());
        assertFalse(empty.isSensing());
        assertFalse(empty.hasEgg());
        assertFalse(empty.superCharged);
        assertTrue(empty.fromBucket());
        assertEquals(empty.getMaxHealth(), empty.getHealth());
    }

    @Test
    void transientAnimationAndExistingAgeLimitationAreUnchanged() throws Exception {
        EntityPlatypus source = prepared();
        source.setAge(-24000);
        source.setSensingVisual(true);
        source.setDigging(true);
        ItemStack bucket = source.getBucketItemStack();
        source.saveToBucketTag(bucket);
        CompoundTag saved = bucket.get(DataComponents.BUCKET_ENTITY_DATA).copyTag();
        assertFalse(saved.contains("Age"));
        assertFalse(saved.contains("SensingVisual"));
        assertFalse(saved.contains("Digging"));

        EntityPlatypus released = releaseThroughMobBucket(saveReload(bucket));
        assertTrue(released.isSensing());
        assertTrue(released.hasEgg());
        assertFalse(released.isSensingVisual());
        assertFalse(released.isDigging());
        assertFalse(released.isBaby());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realPickupKeepsSurvivalAndCreativeItemTransactions(boolean creative) throws Exception {
        for (InteractionHand hand : InteractionHand.values()) {
            EntityPlatypus source = prepared();
            ServerPlayer player = mock(ServerPlayer.class);
            Inventory inventory = mock(Inventory.class);
            when(player.getInventory()).thenReturn(inventory);
            when(player.hasInfiniteMaterials()).thenReturn(creative);
            ItemStack water = new ItemStack(Items.WATER_BUCKET);
            AtomicReference<ItemStack> held = new AtomicReference<>(water);
            AtomicReference<ItemStack> added = new AtomicReference<>();
            when(player.getItemInHand(hand)).thenAnswer(call -> held.get());
            doAnswer(call -> { held.set(call.getArgument(1)); return null; }).when(player).setItemInHand(eq(hand), any());
            when(inventory.add(any(ItemStack.class))).thenAnswer(call -> {
                added.set(call.getArgument(0)); return true;
            });

            assertSame(InteractionResult.SUCCESS, Bucketable.bucketMobPickup(player, hand, source).orElseThrow());
            assertTrue(source.isRemoved());
            ItemStack captured = creative ? added.get() : held.get();
            assertNotNull(captured);
            assertTrue(captured.is(Items.PLATYPUS_BUCKET));
            if (creative) {
                assertSame(water, held.get());
                assertEquals(1, water.getCount());
                verify(inventory, times(1)).add(any(ItemStack.class));
            } else {
                assertEquals(0, water.getCount());
                verifyNoInteractions(inventory);
            }
            verify(player, times(1)).setItemInHand(eq(hand), any(ItemStack.class));
            assertPreserved(releaseThroughMobBucket(saveReload(captured)));
        }
    }

    @Test
    void vanillaFishDefaultLoaderStillReadsOnlyBucketEntityData() {
        ItemStack bucket = new ItemStack(Items.COD_BUCKET);
        CompoundTag data = new CompoundTag();
        data.putFloat("Health", 2.0F);
        data.putBoolean("NoAI", true);
        CompoundTag custom = new CompoundTag();
        custom.putFloat("Health", 99.0F);
        custom.putBoolean("Invulnerable", true);
        custom.putBoolean("Sensing", true);
        bucket.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(data));
        bucket.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        Cod cod = EntityType.COD.create(level, EntitySpawnReason.BUCKET);
        assertNotNull(cod);

        cod.loadFromBucketItem(bucket);

        assertEquals(2.0F, cod.getHealth());
        assertTrue(cod.isNoAi());
        assertFalse(cod.isInvulnerable());
        assertEquals(data, bucket.get(DataComponents.BUCKET_ENTITY_DATA).copyTag());
        assertEquals(custom, bucket.get(DataComponents.CUSTOM_DATA).copyTag());
    }

    private ItemStack saveReload(ItemStack bucket) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        return ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, bucket).getOrThrow()).getOrThrow();
    }

    @SuppressWarnings("unchecked")
    private EntityPlatypus releaseThroughMobBucket(ItemStack bucket) throws Exception {
        // Mock only the spawn factory/world placement. Execute the real private
        // MobBucketItem spawn dispatch, item component config and bucket loader.
        EntityPlatypus released = spy(platypus());
        EntityType<EntityPlatypus> factory = mock(EntityType.class);
        BlockPos pos = new BlockPos(1, 64, 1);
        when(factory.create(eq(level), any(), eq(pos), eq(EntitySpawnReason.BUCKET), eq(true), eq(false)))
            .thenAnswer(call -> {
                Consumer<EntityPlatypus> configure = call.getArgument(1);
                configure.accept(released);
                return released;
            });
        MobBucketItem item = mock(MobBucketItem.class);
        Field type = MobBucketItem.class.getDeclaredField("type");
        type.setAccessible(true);
        type.set(item, factory); // Only this mock, never the registered item.
        doCallRealMethod().when(item).checkExtraContent(nullable(LivingEntity.class), any(Level.class), any(ItemStack.class), any(BlockPos.class));

        item.checkExtraContent(null, level, bucket, pos);

        verify(released, times(1)).loadFromBucketItem(bucket);
        assertTrue(released.fromBucket());
        verify(level).addFreshEntityWithPassengers(released);
        return released;
    }

    private static void assertPreserved(EntityPlatypus entity) {
        assertTrue(entity.hasFedora());
        assertTrue(entity.isSensing());
        assertTrue(entity.hasEgg());
        assertTrue(entity.superCharged);
        assertTrue(entity.fromBucket());
        assertEquals(7.0F, entity.getHealth());
        assertTrue(entity.isNoAi());
        assertTrue(entity.isSilent());
        assertTrue(entity.isNoGravity());
        assertTrue(entity.hasGlowingTag());
        assertTrue(entity.isInvulnerable());
        assertEquals(Component.literal("Perry test"), entity.getCustomName());
    }
}
