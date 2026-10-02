package net.minecraft.world.entity.monster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractThrownPotion;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real health/mount transitions and randomTeleport, with mocked worlds and controlled avoidance attempts. */
@Isolated("Temporarily binds the bundled fluid tags")
class EnderManBehaviorTest {
    private static Map<TagKey<Fluid>, List<Holder<Fluid>>> originalFluidTags;
    private static String previousByteBuddy;
    private static RegistryAccess registries;
    private static DamageSources damageSources;
    private static Method teleportToDestination;

    @BeforeAll
    static void bootstrap() throws Exception {
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalFluidTags = snapshotFluidTags();

        // This registry belongs to the test, so its damage tags never mutate global state.
        MappedRegistry<DamageType> damageTypes = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        DamageTypes.bootstrap(new BootstrapContext<>() {
            @Override
            public Holder.Reference<DamageType> register(ResourceKey<DamageType> key, DamageType value, Lifecycle lifecycle) {
                return damageTypes.register(key, value, RegistrationInfo.BUILT_IN);
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                throw new UnsupportedOperationException("Damage types have no registry dependencies");
            }
        });
        damageTypes.freeze();
        registries = new RegistryAccess.ImmutableRegistryAccess(Stream.concat(
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries(),
            Stream.of(new RegistryAccess.RegistryEntry<>(Registries.DAMAGE_TYPE, damageTypes))));
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource()))) {
            TagLoader.loadTagsForExistingRegistries(resources,
                new RegistryAccess.ImmutableRegistryAccess(List.of(damageTypes, BuiltInRegistries.FLUID)))
                .forEach(Registry.PendingTags::apply);
        }
        damageSources = new DamageSources(registries);
        teleportToDestination = EnderMan.class.getDeclaredMethod("teleport", double.class, double.class, double.class);
        teleportToDestination.setAccessible(true);
    }

    @AfterAll
    static void restore() {
        try {
            if (originalFluidTags != null) {
                BuiltInRegistries.FLUID.prepareTagReload(
                    new TagLoader.LoadResult<>(BuiltInRegistries.FLUID.key(), originalFluidTags)).apply();
                assertEquals(originalFluidTags, snapshotFluidTags());
            }
        } finally {
            if (previousByteBuddy == null) System.clearProperty("net.bytebuddy.experimental");
            else System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
        }
    }

    private static Map<TagKey<Fluid>, List<Holder<Fluid>>> snapshotFluidTags() {
        return BuiltInRegistries.FLUID.listTags()
            .collect(Collectors.toMap(HolderSet.Named::key, tag -> tag.stream().toList()));
    }

    @ParameterizedTest
    @MethodSource("projectileCases")
    void mountedProjectilesDamageWithoutTeleporting(EntityType<?> vehicleType, ResourceKey<DamageType> type, String ownerKind) {
        Fixture f = new Fixture();
        Entity vehicle = f.mount(vehicleType);
        DamageSource source = f.projectile(type, ownerKind);
        assertTrue(source.is(DamageTypeTags.IS_PROJECTILE), "Use the bundled damage-type tag");
        doReturn(false).when(f.enderman).teleport();
        clearInvocations(f.enderman, f.level);

        assertTrue(f.enderman.hurtServer(f.level, source, 6.0F));

        assertEquals(34.0F, f.enderman.getHealth());
        assertSame(vehicle, f.enderman.getVehicle());
        assertTrue(vehicle.hasPassenger(f.enderman));
        assertSame(source, f.enderman.getLastDamageSource());
        verify(f.enderman, never()).teleport();
        verify(f.level).broadcastDamageEvent(f.enderman, source);
    }

    static Stream<Arguments> projectileCases() {
        return Stream.of(EntityType.OAK_BOAT, EntityType.MINECART).flatMap(vehicle ->
            Stream.of(DamageTypes.ARROW, DamageTypes.TRIDENT, DamageTypes.BULLET).flatMap(type ->
                Stream.of("player", "mob", "self", "none").map(owner -> Arguments.of(vehicle, type, owner))));
    }

    @ParameterizedTest
    @MethodSource("unmountedProjectiles")
    void unmountedProjectilesKeepAvoidanceWhetherTeleportSucceeds(ResourceKey<DamageType> type, boolean teleportSucceeds) {
        Fixture f = new Fixture();
        DamageSource source = f.projectile(type, "mob");
        doReturn(teleportSucceeds).when(f.enderman).teleport();
        clearInvocations(f.enderman, f.level);

        assertEquals(teleportSucceeds, f.enderman.hurtServer(f.level, source, 6.0F));

        assertEquals(40.0F, f.enderman.getHealth());
        verify(f.enderman, times(teleportSucceeds ? 1 : 64)).teleport();
        verify(f.level, never()).broadcastDamageEvent(any(), any());
    }

    static Stream<Arguments> unmountedProjectiles() {
        return Stream.of(DamageTypes.ARROW, DamageTypes.TRIDENT, DamageTypes.BULLET).flatMap(type ->
            Stream.of(false, true).map(success -> Arguments.of(type, success)));
    }

    @Test
    void projectileTagDoesNotRequireADirectEntity() {
        Fixture f = new Fixture();
        f.mount(EntityType.MINECART);
        DamageSource source = damageSources.source(DamageTypes.ARROW);
        assertNull(source.getDirectEntity());
        doReturn(false).when(f.enderman).teleport();
        assertTrue(f.enderman.hurtServer(f.level, source, 6.0F));
        assertEquals(34.0F, f.enderman.getHealth());
        verify(f.enderman, never()).teleport();
    }

    @Test
    void mountDismountAndRemountUseCurrentPassengerState() {
        Fixture f = new Fixture();
        DamageSource source = f.projectile(DamageTypes.ARROW, "none");
        doReturn(false).when(f.enderman).teleport();
        Entity boat = f.mount(EntityType.OAK_BOAT);
        assertTrue(f.enderman.hurtServer(f.level, source, 4.0F));
        assertEquals(36.0F, f.enderman.getHealth());
        f.enderman.stopRiding();
        assertFalse(f.enderman.isPassenger());
        assertFalse(boat.hasPassenger(f.enderman));
        f.enderman.invulnerableTime = 0;
        assertFalse(f.enderman.hurtServer(f.level, source, 4.0F));
        assertEquals(36.0F, f.enderman.getHealth());
        Entity minecart = f.mount(EntityType.MINECART);
        f.enderman.invulnerableTime = 0;
        assertTrue(f.enderman.hurtServer(f.level, source, 4.0F));
        assertEquals(32.0F, f.enderman.getHealth());
        assertSame(minecart, f.enderman.getVehicle());
        verify(f.enderman, times(64)).teleport();
    }

    @Test
    void mountedProjectileStillRespectsInvulnerabilityAndDamageCooldown() {
        Fixture f = new Fixture();
        f.mount(EntityType.OAK_BOAT);
        DamageSource source = f.projectile(DamageTypes.ARROW, "self");
        doReturn(false).when(f.enderman).teleport();
        f.enderman.setInvulnerable(true);
        assertFalse(f.enderman.hurtServer(f.level, source, 4.0F));
        assertEquals(40.0F, f.enderman.getHealth());
        f.enderman.setInvulnerable(false);
        assertTrue(f.enderman.hurtServer(f.level, source, 4.0F));
        assertFalse(f.enderman.hurtServer(f.level, source, 4.0F));
        assertEquals(36.0F, f.enderman.getHealth());
        verify(f.enderman, never()).teleport();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void meleeAndCleanWaterKeepTheirExistingDamageRules(boolean mounted) {
        Fixture f = new Fixture();
        if (mounted) f.mount(EntityType.OAK_BOAT);
        doReturn(false).when(f.enderman).teleport();
        LivingEntity attacker = EntityType.ZOMBIE.create(f.level, EntitySpawnReason.COMMAND);
        assertNotNull(attacker);
        assertTrue(f.enderman.hurtServer(f.level, damageSources.mobAttack(attacker), 3.0F));
        assertEquals(37.0F, f.enderman.getHealth());
        verify(f.enderman, never()).teleport();
        f.enderman.invulnerableTime = 0;
        AbstractThrownPotion water = f.potion(true);
        DamageSource source = damageSources.indirectMagic(water, attacker);
        assertFalse(source.is(DamageTypeTags.IS_PROJECTILE));
        assertTrue(f.enderman.hurtServer(f.level, source, 1.0F));
        assertEquals(36.0F, f.enderman.getHealth());
        verify(f.enderman, times(64)).teleport();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nonWaterThrownPotionKeepsExistingDamageAvoidance(boolean mounted) {
        Fixture f = new Fixture();
        if (mounted) f.mount(EntityType.MINECART);
        doReturn(false).when(f.enderman).teleport();
        DamageSource source = damageSources.indirectMagic(f.potion(false), null);
        assertFalse(source.is(DamageTypeTags.IS_PROJECTILE));
        assertFalse(f.enderman.hurtServer(f.level, source, 6.0F));
        assertEquals(40.0F, f.enderman.getHealth());
        verify(f.enderman, times(64)).teleport();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void projectileTaggedPotionKeepsInstanceBasedWaterException(boolean water) {
        Fixture f = new Fixture();
        f.mount(EntityType.OAK_BOAT);
        doReturn(false).when(f.enderman).teleport();
        // A custom source can classify a potion as projectile damage. Preserve
        // the potion branch even when the source matches the projectile tag.
        DamageSource source = damageSources.source(DamageTypes.ARROW, f.potion(water), null);
        assertTrue(source.is(DamageTypeTags.IS_PROJECTILE));

        assertEquals(water, f.enderman.hurtServer(f.level, source, 1.0F));
        assertEquals(water ? 39.0F : 40.0F, f.enderman.getHealth());
        verify(f.enderman, times(64)).teleport();
    }

    @Test
    void mountedDamagePublishesHealthDataThatClientEntityCanApply() {
        Fixture f = new Fixture();
        f.mount(EntityType.OAK_BOAT);
        Level clientLevel = mock(Level.class, RETURNS_DEEP_STUBS);
        configureLevel(clientLevel);
        when(clientLevel.isClientSide()).thenReturn(true);
        EnderMan clientEntity = EntityType.ENDERMAN.create(clientLevel, EntitySpawnReason.COMMAND);
        assertNotNull(clientEntity);
        f.enderman.getEntityData().packDirty();
        DamageSource source = f.projectile(DamageTypes.ARROW, "self");

        assertTrue(f.enderman.hurtServer(f.level, source, 6.0F));
        var changes = f.enderman.getEntityData().packDirty();
        assertNotNull(changes);
        clientEntity.getEntityData().assignValues(changes);

        assertEquals(34.0F, clientEntity.getHealth());
        verify(f.level).broadcastDamageEvent(f.enderman, source);
        // This covers the synchronized-data path, not a running network/client session.
    }

    @ParameterizedTest
    @ValueSource(ints = {65, 75})
    void bedrockSupportIsRejectedBeforeRandomTeleport(int targetY) throws Exception {
        Fixture f = new Fixture();
        f.floor(Blocks.BEDROCK.defaultBlockState(), 64);
        Vec3 before = f.enderman.position();
        clearInvocations(f.enderman, f.level);

        assertFalse(f.teleport(8.5, targetY, 8.5));

        assertEquals(before, f.enderman.position());
        verify(f.enderman, never()).randomTeleport(anyDouble(), anyDouble(), anyDouble(), anyBoolean());
        f.assertNoTeleportEvent();
    }

    @ParameterizedTest
    @MethodSource("ordinaryFloors")
    void ordinarySupportUsesRealRandomTeleportAndPublishesSuccess(Block support) throws Exception {
        Fixture f = new Fixture();
        f.floor(support.defaultBlockState(), 64);
        Vec3 before = f.enderman.position();
        clearInvocations(f.enderman, f.level);

        assertTrue(f.teleport(8.5, 75.0, 8.5));

        assertEquals(new Vec3(8.5, 65.0, 8.5), f.enderman.position());
        verify(f.enderman).randomTeleport(8.5, 75.0, 8.5, true);
        verify(f.level).noCollision(f.enderman);
        verify(f.level).containsAnyLiquid(f.enderman.getBoundingBox());
        verify(f.level).broadcastEntityEvent(f.enderman, (byte)46);
        verify(f.level).gameEvent(eq(GameEvent.TELEPORT), eq(before), any(GameEvent.Context.class));
    }

    static Stream<Block> ordinaryFloors() {
        return Stream.of(Blocks.STONE, Blocks.END_STONE);
    }

    @Test
    void waterloggedSupportingBlockRemainsRejectedBeforeRandomTeleport() throws Exception {
        Fixture f = new Fixture();
        BlockState waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        assertTrue(waterlogged.blocksMotion());
        assertTrue(waterlogged.getFluidState().is(FluidTags.WATER));
        f.floor(waterlogged, 64);
        assertFalse(f.teleport(8.5, 75.0, 8.5));
        verify(f.enderman, never()).randomTeleport(anyDouble(), anyDouble(), anyDouble(), anyBoolean());
        f.assertNoTeleportEvent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void collisionAndBodyLiquidFailuresRestoreOriginalPosition(boolean liquid) throws Exception {
        Fixture f = new Fixture();
        f.floor(Blocks.STONE.defaultBlockState(), 64);
        // Mock world queries using the actual destination bounding box. The
        // collision engine itself is not under test in this entity regression.
        AABB obstacle = new AABB(8.0, 66.0, 8.0, 9.0, 67.0, 9.0);
        if (liquid) {
            when(f.level.containsAnyLiquid(any(AABB.class))).thenAnswer(call ->
                ((AABB)call.getArgument(0)).intersects(obstacle));
        } else {
            when(f.level.noCollision(any(Entity.class))).thenAnswer(call ->
                !((Entity)call.getArgument(0)).getBoundingBox().intersects(obstacle));
        }
        Vec3 before = f.enderman.position();
        clearInvocations(f.enderman, f.level);

        assertFalse(f.teleport(8.5, 75.0, 8.5));

        assertEquals(before, f.enderman.position());
        verify(f.enderman).randomTeleport(8.5, 75.0, 8.5, true);
        verify(f.level).noCollision(f.enderman);
        f.assertNoTeleportEvent();
    }

    @Test
    void unloadedDestinationRemainsRejected() throws Exception {
        Fixture f = new Fixture();
        f.floor(Blocks.STONE.defaultBlockState(), 64);
        when(f.level.hasChunkAt(any(BlockPos.class))).thenReturn(false);
        Vec3 before = f.enderman.position();
        clearInvocations(f.enderman, f.level);
        assertFalse(f.teleport(8.5, 75.0, 8.5));
        assertEquals(before, f.enderman.position());
        verify(f.level, never()).noCollision(any(Entity.class));
        f.assertNoTeleportEvent();
    }

    @Test
    void minimumHeightRetainsOrdinarySupportAndRejectsBedrockOrAbsentGround() throws Exception {
        Fixture f = new Fixture();
        f.floor(Blocks.STONE.defaultBlockState(), -64);
        assertTrue(f.teleport(8.5, -60.0, 8.5));
        assertEquals(new Vec3(8.5, -63.0, 8.5), f.enderman.position());
        assertFalse(f.teleport(10.5, -64.0, 10.5));
        assertEquals(new Vec3(8.5, -63.0, 8.5), f.enderman.position());
        f.floor(Blocks.BEDROCK.defaultBlockState(), -64);
        assertFalse(f.teleport(10.5, -60.0, 10.5));
        assertEquals(new Vec3(8.5, -63.0, 8.5), f.enderman.position());
        f.floor(Blocks.AIR.defaultBlockState(), -64);
        assertFalse(f.teleport(10.5, -60.0, 10.5));
        assertEquals(new Vec3(8.5, -63.0, 8.5), f.enderman.position());
    }

    private static void configureLevel(Level level) {
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        when(level.registryAccess()).thenReturn(registries);
        when(level.damageSources()).thenReturn(damageSources);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getMinY()).thenReturn(-64);
        when(level.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(level.noCollision(any(Entity.class))).thenReturn(true);
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        final EnderMan enderman;

        Fixture() {
            configureLevel(level);
            enderman = spy(EntityType.ENDERMAN.create(level, EntitySpawnReason.COMMAND));
            assertNotNull(enderman);
            enderman.setPos(1.5, 70.0, 1.5);
        }

        Entity mount(EntityType<?> vehicleType) {
            Entity vehicle = vehicleType.create(level, EntitySpawnReason.COMMAND);
            assertNotNull(vehicle);
            assertTrue(enderman.startRiding(vehicle, true, true));
            assertSame(vehicle, enderman.getVehicle());
            return vehicle;
        }

        DamageSource projectile(ResourceKey<DamageType> type, String ownerKind) {
            Entity projectile = (type == DamageTypes.TRIDENT ? EntityType.TRIDENT : EntityType.ARROW)
                .create(level, EntitySpawnReason.COMMAND);
            assertNotNull(projectile);
            Entity owner = switch (ownerKind) {
                case "player" -> {
                    Player player = mock(Player.class);
                    when(player.getUUID()).thenReturn(UUID.randomUUID());
                    yield player;
                }
                case "mob" -> EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                case "self" -> projectile;
                case "none" -> null;
                default -> throw new AssertionError(ownerKind);
            };
            return new DamageSource(registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), projectile, owner);
        }

        AbstractThrownPotion potion(boolean water) {
            AbstractThrownPotion potion = EntityType.SPLASH_POTION.create(level, EntitySpawnReason.COMMAND);
            assertNotNull(potion);
            potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, water ? Potions.WATER : Potions.HARMING));
            return potion;
        }

        void floor(BlockState state, int y) {
            when(level.getBlockState(any(BlockPos.class))).thenAnswer(call ->
                ((BlockPos)call.getArgument(0)).getY() == y ? state : Blocks.AIR.defaultBlockState());
        }

        boolean teleport(double x, double y, double z) throws Exception {
            return (boolean)teleportToDestination.invoke(enderman, x, y, z);
        }

        void assertNoTeleportEvent() {
            verify(level, never()).broadcastEntityEvent(enderman, (byte)46);
            verify(level, never()).gameEvent(eq(GameEvent.TELEPORT), any(Vec3.class), any(GameEvent.Context.class));
            verify(enderman, never()).playSound(eq(SoundEvents.ENDERMAN_TELEPORT), anyFloat(), anyFloat());
        }
    }
}
