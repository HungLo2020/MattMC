package net.alexsmobs.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Real Crow/Animal interactions and item consumption; world I/O and players are mocked. */
@Isolated("Temporarily binds the bundled item tags")
class EntityCrowInteractionTest {
    private static Map<TagKey<Item>, List<Holder<Item>>> originalItemTags;
    private static String previousByteBuddy;

    @BeforeAll
    static void bootstrap() {
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalItemTags = snapshotTags();
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource()))) {
            TagLoader.loadTagsForExistingRegistries(resources,
                new RegistryAccess.ImmutableRegistryAccess(List.of(BuiltInRegistries.ITEM)))
                .forEach(Registry.PendingTags::apply);
        }
    }

    @AfterAll
    static void restore() {
        try {
            BuiltInRegistries.ITEM.prepareTagReload(
                new TagLoader.LoadResult<>(BuiltInRegistries.ITEM.key(), originalItemTags)).apply();
            assertEquals(originalItemTags, snapshotTags());
        } finally {
            if (previousByteBuddy == null) System.clearProperty("net.bytebuddy.experimental");
            else System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
        }
    }

    private static Map<TagKey<Item>, List<Holder<Item>>> snapshotTags() {
        return BuiltInRegistries.ITEM.listTags()
            .collect(Collectors.toMap(HolderSet.Named::key, tag -> tag.stream().toList()));
    }

    @ParameterizedTest
    @MethodSource("feedingCases")
    void feedingIsHandledOnceWithoutChangingCommandOrBeak(
        boolean baby, boolean clientSide, boolean creative, boolean occupied,
        InteractionHand hand, int count
    ) {
        Fixture f = new Fixture(clientSide, creative, true, true);
        f.crow.setAge(baby ? -24000 : 0);
        ItemStack beak = occupied ? new ItemStack(Items.DIAMOND, 2) : ItemStack.EMPTY;
        f.crow.setItemInHand(InteractionHand.MAIN_HAND, beak);
        ItemStack seeds = new ItemStack(Items.PUMPKIN_SEEDS, count);
        f.hold(hand, seeds);
        assertTrue(f.crow.isFood(seeds), "Uses the real bundled crow_breedables tag");
        clearInvocations(f.crow, f.player, f.level);

        InteractionResult result = f.crow.mobInteract(f.player, hand);

        assertSame(baby ? InteractionResult.SUCCESS : clientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS_SERVER, result);
        boolean consumesSeed = baby || !clientSide;
        assertEquals(count - (consumesSeed && !creative ? 1 : 0), seeds.getCount());
        assertEquals(1, f.crow.getCommand());
        assertSame(beak, f.crow.getMainHandItem());
        verify(f.crow, never()).spawnAtLocation(any(ServerLevel.class), any(ItemStack.class), anyFloat());
        verify(f.player, never()).displayClientMessage(any(), anyBoolean());
        if (baby) {
            verify(f.crow, times(1)).ageUp(clientSide ? 0 : 120, true);
            if (!clientSide) assertEquals(-21600, f.crow.getAge());
            assertFalse(f.crow.isInLove());
        } else {
            verify(f.crow, never()).ageUp(anyInt(), anyBoolean());
            assertEquals(clientSide ? 0 : 600, f.crow.getInLoveTime());
            if (!clientSide) {
                assertSame(f.player, f.crow.getLoveCause());
                verify(f.level, times(1)).broadcastEntityEvent(f.crow, (byte)18);
                assertEquals(InteractionResult.SwingSource.SERVER, ((InteractionResult.Success) result).swingSource());
            }
        }
    }

    static Stream<Arguments> feedingCases() {
        List<Arguments> cases = new ArrayList<>();
        for (boolean baby : new boolean[]{false, true})
            for (boolean client : new boolean[]{false, true})
                for (boolean creative : new boolean[]{false, true})
                    for (boolean occupied : new boolean[]{false, true})
                        for (InteractionHand hand : InteractionHand.values())
                            for (int count : new int[]{1, 2, 5})
                                cases.add(Arguments.of(baby, client, creative, occupied, hand, count));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("sidesAndModes")
    void nonFoodOwnerCommandsStillCycleAndSetSitting(boolean clientSide, boolean creative) {
        Fixture f = new Fixture(clientSide, creative, true, true);
        ItemStack stick = new ItemStack(Items.STICK, 2);
        f.hold(InteractionHand.MAIN_HAND, stick);

        assertSame(InteractionResult.SUCCESS, f.crow.mobInteract(f.player, InteractionHand.MAIN_HAND));
        assertEquals(2, f.crow.getCommand());
        assertTrue(f.crow.isSitting());
        assertEquals(2, stick.getCount());
        verify(f.player).displayClientMessage(any(), eq(true));

        f.crow.setCommand(3);
        assertSame(InteractionResult.SUCCESS, f.crow.mobInteract(f.player, InteractionHand.MAIN_HAND));
        assertEquals(0, f.crow.getCommand());
        assertFalse(f.crow.isSitting());
    }

    @ParameterizedTest
    @MethodSource("sidesAndModes")
    void nonBreedingEdibleItemStillFillsBeakAndCyclesOwnerCommand(boolean clientSide, boolean creative) {
        Fixture f = new Fixture(clientSide, creative, true, true);
        ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS, 2);
        f.hold(InteractionHand.OFF_HAND, seeds);
        assertFalse(f.crow.isFood(seeds));

        assertSame(InteractionResult.SUCCESS, f.crow.mobInteract(f.player, InteractionHand.OFF_HAND));
        assertTrue(f.crow.getMainHandItem().is(Items.WHEAT_SEEDS));
        assertEquals(1, f.crow.getMainHandItem().getCount());
        assertEquals(1, seeds.getCount()); // Existing command behavior also shrinks in Creative.
        assertEquals(2, f.crow.getCommand());
    }

    @ParameterizedTest
    @MethodSource("beakCases")
    void nonFoodBeakReturnStillWorksRegardlessOfTaming(boolean clientSide, boolean creative, boolean tame) {
        Fixture f = new Fixture(clientSide, creative, tame, tame);
        f.hold(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack carried = new ItemStack(Items.DIAMOND, 2);
        f.crow.setItemInHand(InteractionHand.MAIN_HAND, carried);
        clearInvocations(f.crow);

        assertSame(InteractionResult.SUCCESS, f.crow.mobInteract(f.player, InteractionHand.MAIN_HAND));
        assertTrue(f.crow.getMainHandItem().isEmpty());
        assertEquals(1, f.crow.getCommand());
        verify(f.crow, times(clientSide ? 0 : 1)).spawnAtLocation(any(ServerLevel.class),
            argThat(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 2), eq(0.0F));
    }

    @ParameterizedTest
    @MethodSource("sidesAndModes")
    void untamedCrowDoesNotTreatPumpkinSeedsAsBreedingFood(boolean clientSide, boolean creative) {
        Fixture f = new Fixture(clientSide, creative, false, false);
        f.crow.setAge(-24000);
        ItemStack seeds = new ItemStack(Items.PUMPKIN_SEEDS, 2);
        f.hold(InteractionHand.MAIN_HAND, seeds);
        clearInvocations(f.crow);

        assertSame(InteractionResult.PASS, f.crow.mobInteract(f.player, InteractionHand.MAIN_HAND));
        assertEquals(2, seeds.getCount());
        assertFalse(f.crow.isInLove());
        verify(f.crow, never()).ageUp(anyInt(), anyBoolean());
        assertEquals(1, f.crow.getCommand());
    }

    @Test
    void alreadyInLoveAdultDoesNotConsumeAnotherSeed() {
        Fixture f = new Fixture(false, false, true, true);
        f.crow.setInLoveTime(400);
        ItemStack seeds = new ItemStack(Items.PUMPKIN_SEEDS, 2);
        f.hold(InteractionHand.MAIN_HAND, seeds);

        assertSame(InteractionResult.PASS, f.crow.mobInteract(f.player, InteractionHand.MAIN_HAND));
        assertEquals(2, seeds.getCount());
        assertEquals(400, f.crow.getInLoveTime());
        assertEquals(1, f.crow.getCommand());
    }

    @Test
    void nonOwnerCannotIssueEmptyHandCommands() {
        Fixture f = new Fixture(false, false, true, false);
        f.hold(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        assertSame(InteractionResult.PASS, f.crow.mobInteract(f.player, InteractionHand.MAIN_HAND));
        assertEquals(1, f.crow.getCommand());
    }

    static Stream<Arguments> sidesAndModes() {
        return Stream.of(Arguments.of(false, false), Arguments.of(false, true),
            Arguments.of(true, false), Arguments.of(true, true));
    }

    static Stream<Arguments> beakCases() {
        return sidesAndModes().flatMap(a -> Stream.of(
            Arguments.of(a.get()[0], a.get()[1], false), Arguments.of(a.get()[0], a.get()[1], true)));
    }

    private static final class Fixture {
        final Level level;
        final Player player;
        final EntityCrow crow;

        Fixture(boolean clientSide, boolean creative, boolean tame, boolean owner) {
            level = clientSide ? mock(Level.class, RETURNS_DEEP_STUBS) : mock(ServerLevel.class, RETURNS_DEEP_STUBS);
            when(level.isClientSide()).thenReturn(clientSide);
            when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
            when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
            player = clientSide ? mock(Player.class) : mock(ServerPlayer.class);
            when(player.getUUID()).thenReturn(UUID.randomUUID());
            when(player.hasInfiniteMaterials()).thenReturn(creative);
            when(player.isCreative()).thenReturn(creative);
            crow = spy(EntityType.CROW.create(level, EntitySpawnReason.COMMAND));
            assertNotNull(crow);
            crow.setTame(tame, false);
            if (owner) crow.setOwner(player);
            crow.setCommand(1);
            doReturn(null).when(crow).spawnAtLocation(any(ServerLevel.class), any(ItemStack.class), anyFloat());
        }

        void hold(InteractionHand hand, ItemStack item) {
            when(player.getItemInHand(hand)).thenReturn(item);
        }
    }
}
