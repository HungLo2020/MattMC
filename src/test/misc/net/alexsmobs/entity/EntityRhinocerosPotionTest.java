package net.alexsmobs.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class EntityRhinocerosPotionTest {
    private static String previousByteBuddy;

    @BeforeAll
    static void bootstrap() {
        // Match NativeSurfaceTest: the bundled Mockito predates the required JDK.
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterAll
    static void restoreProperties() {
        if (previousByteBuddy == null) {
            System.clearProperty("net.bytebuddy.experimental");
        } else {
            System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
        }
    }

    @Test
    void ordinaryWaterResetsEveryFieldAfterAnEffectBearingCoating() {
        Fixture fixture = new Fixture(false, false);
        fixture.coat();

        assertTrue(fixture.rhino.applyPotion(Potions.WATER.value()));

        fixture.assertCleared();
        verify(fixture.rhino).resetPotion();
    }

    @Test
    void nullRetainsTheExistingExplicitResetBehavior() {
        Fixture fixture = new Fixture(false, false);
        fixture.coat();

        assertTrue(fixture.rhino.applyPotion(null));

        fixture.assertCleared();
    }

    @ParameterizedTest
    @MethodSource("waterInteractions")
    void waterInteractionMutatesOnlyOnServerAndReturnsOneBottle(
        Item item, InteractionHand hand, boolean creative, boolean clientSide
    ) {
        Fixture fixture = new Fixture(clientSide, creative);
        fixture.coat();
        // PotionItem's real default-instance route installs the registered water holder.
        ItemStack water = item.getDefaultInstance();
        assertTrue(water.get(DataComponents.POTION_CONTENTS).is(Potions.WATER));
        fixture.hands.put(hand, water);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherItem = new ItemStack(Items.STICK);
        fixture.hands.put(otherHand, otherItem);

        assertEquals(InteractionResult.SUCCESS, fixture.rhino.mobInteract(fixture.player, hand));
        assertSame(otherItem, fixture.hands.get(otherHand));
        if (clientSide) {
            fixture.assertCoated();
            fixture.assertNoTransaction(water, hand);
        } else {
            fixture.assertCleared();
            verify(fixture.rhino).resetPotion();
            verify(fixture.rhino).gameEvent(GameEvent.ENTITY_INTERACT);
            verify(fixture.rhino).playSound(SoundEvents.DYE_USE);
            verify(fixture.player).setItemInHand(eq(hand), any(ItemStack.class));
            assertEquals(creative ? 1 : 0, water.getCount());
            if (creative) {
                assertSame(water, fixture.hands.get(hand));
                assertEquals(1, fixture.bottles.size());
            } else {
                assertTrue(fixture.hands.get(hand).is(Items.GLASS_BOTTLE));
                assertEquals(1, fixture.hands.get(hand).getCount());
                assertTrue(fixture.bottles.isEmpty(), "The use remainder must not create a second bottle");
            }
            verify(fixture.player, never()).addItem(any(ItemStack.class));
            verify(fixture.player, never()).drop(any(ItemStack.class), anyBoolean());
        }
    }

    static Stream<Arguments> waterInteractions() {
        List<Arguments> cases = new ArrayList<>();
        for (Item item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            for (InteractionHand hand : InteractionHand.values()) {
                for (boolean creative : new boolean[]{false, true}) {
                    for (boolean clientSide : new boolean[]{false, true}) {
                        cases.add(Arguments.of(item, hand, creative, clientSide));
                    }
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("sides")
    void effectBearingInteractionStillUsesOnlyTheFirstBaseEffect(boolean clientSide) {
        Fixture fixture = new Fixture(clientSide, false);
        fixture.coat();
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.TURTLE_MASTER);
        potion.set(DataComponents.POTION_CONTENTS,
            new PotionContents(Potions.TURTLE_MASTER).withEffectAdded(new MobEffectInstance(MobEffects.POISON, 20, 4)));
        fixture.hands.put(InteractionHand.OFF_HAND, potion);

        assertEquals(InteractionResult.SUCCESS, fixture.rhino.mobInteract(fixture.player, InteractionHand.OFF_HAND));

        if (clientSide) {
            fixture.assertCoated();
            fixture.assertNoTransaction(potion, InteractionHand.OFF_HAND);
        } else {
            MobEffectInstance first = Potions.TURTLE_MASTER.value().getEffects().getFirst();
            assertEquals(BuiltInRegistries.MOB_EFFECT.getKey(first.getEffect().value()).toString(), fixture.potionId);
            assertEquals(first.getAmplifier(), fixture.potionLevel);
            assertEquals(first.getDuration(), fixture.potionDuration);
            assertEquals(0, fixture.inflictedCount);
            assertTrue(fixture.hands.get(InteractionHand.OFF_HAND).is(Items.GLASS_BOTTLE));
        }
    }

    @ParameterizedTest
    @MethodSource("sides")
    void absentComponentEmptyContentsAndCustomOnlyContentsKeepLegacyResetBehavior(boolean clientSide) {
        for (int variant = 0; variant < 3; variant++) {
            Fixture fixture = new Fixture(clientSide, false);
            fixture.coat();
            ItemStack potion = new ItemStack(Items.POTION);
            if (variant == 0) {
                potion.remove(DataComponents.POTION_CONTENTS);
            } else if (variant == 2) {
                potion.set(DataComponents.POTION_CONTENTS,
                    PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.POISON, 20)));
            }
            fixture.hands.put(InteractionHand.MAIN_HAND, potion);

            assertEquals(InteractionResult.SUCCESS, fixture.rhino.mobInteract(fixture.player, InteractionHand.MAIN_HAND));

            if (clientSide) {
                fixture.assertCoated();
                fixture.assertNoTransaction(potion, InteractionHand.MAIN_HAND);
            } else {
                fixture.assertCleared();
                assertTrue(fixture.hands.get(InteractionHand.MAIN_HAND).is(Items.GLASS_BOTTLE));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sides")
    void otherNoEffectPotionsAreRejectedWithoutClearingOrConsuming(boolean clientSide) {
        for (Holder<Potion> potion : List.of(Potions.AWKWARD, Potions.MUNDANE, Potions.THICK, Holder.direct(new Potion("water")))) {
            Fixture fixture = new Fixture(clientSide, false);
            fixture.coat();
            ItemStack stack = PotionContents.createItemStack(Items.POTION, potion);
            fixture.hands.put(InteractionHand.MAIN_HAND, stack);

            assertFalse(fixture.rhino.applyPotion(potion.value()));
            assertEquals(InteractionResult.PASS, fixture.rhino.mobInteract(fixture.player, InteractionHand.MAIN_HAND));

            fixture.assertCoated();
            fixture.assertNoTransaction(stack, InteractionHand.MAIN_HAND);
        }
    }

    @ParameterizedTest
    @EnumSource(InteractionHand.class)
    void stackedSurvivalBottlesConsumeOnceAndDropOneRemainderWhenInventoryIsFull(InteractionHand hand) {
        Fixture fixture = new Fixture(false, false);
        fixture.coat();
        ItemStack water = Items.POTION.getDefaultInstance();
        water.setCount(2);
        fixture.hands.put(hand, water);
        doReturn(false).when(fixture.inventory).add(any(ItemStack.class));

        assertEquals(InteractionResult.SUCCESS, fixture.rhino.mobInteract(fixture.player, hand));

        fixture.assertCleared();
        assertSame(water, fixture.hands.get(hand));
        assertEquals(1, water.getCount());
        verify(fixture.inventory).add(argThat(stack -> stack.is(Items.GLASS_BOTTLE) && stack.getCount() == 1));
        verify(fixture.player).drop(argThat(stack -> stack.is(Items.GLASS_BOTTLE) && stack.getCount() == 1), eq(false));
    }

    @Test
    void repeatedCreativeUseDoesNotConsumePotionOrDuplicateGlassBottles() {
        Fixture fixture = new Fixture(false, true);
        fixture.coat();
        ItemStack water = Items.POTION.getDefaultInstance();
        fixture.hands.put(InteractionHand.MAIN_HAND, water);

        assertEquals(InteractionResult.SUCCESS, fixture.rhino.mobInteract(fixture.player, InteractionHand.MAIN_HAND));
        assertEquals(InteractionResult.SUCCESS, fixture.rhino.mobInteract(fixture.player, InteractionHand.MAIN_HAND));

        fixture.assertCleared();
        assertSame(water, fixture.hands.get(InteractionHand.MAIN_HAND));
        assertEquals(1, water.getCount());
        assertEquals(1, fixture.bottles.size());
        verify(fixture.inventory).add(any(ItemStack.class));
    }

    @ParameterizedTest
    @MethodSource("sides")
    void babiesStillRejectPotionCoatings(boolean clientSide) {
        Fixture fixture = new Fixture(clientSide, false);
        fixture.coat();
        when(fixture.rhino.isBaby()).thenReturn(true);
        ItemStack water = Items.POTION.getDefaultInstance();
        fixture.hands.put(InteractionHand.MAIN_HAND, water);

        assertEquals(InteractionResult.PASS, fixture.rhino.mobInteract(fixture.player, InteractionHand.MAIN_HAND));

        fixture.assertCoated();
        fixture.assertNoTransaction(water, InteractionHand.MAIN_HAND);
    }

    static Stream<Boolean> sides() {
        return Stream.of(false, true);
    }

    /**
     * Isolate world/synced storage and player inventory, but execute the real
     * mobInteract, applyPotion, resetPotion, ItemStack and ItemUtils code.
     * This is a unit interaction test, not a live client/server gameplay test.
     */
    private static final class Fixture {
        final EntityRhinoceros rhino = mock(EntityRhinoceros.class);
        final Player player = mock(Player.class);
        final Inventory inventory = mock(Inventory.class);
        final EnumMap<InteractionHand, ItemStack> hands = new EnumMap<>(InteractionHand.class);
        final List<ItemStack> bottles = new ArrayList<>();
        String potionId = "";
        int potionLevel;
        int potionDuration;
        int inflictedCount;

        Fixture(boolean clientSide, boolean creative) {
            Level level = mock(Level.class);
            when(level.isClientSide()).thenReturn(clientSide);
            when(rhino.level()).thenReturn(level);
            doCallRealMethod().when(rhino).mobInteract(any(Player.class), any(InteractionHand.class));
            doCallRealMethod().when(rhino).applyPotion(nullable(Potion.class));
            doCallRealMethod().when(rhino).resetPotion();
            doAnswer(call -> { potionId = call.getArgument(0); return null; }).when(rhino).setAppliedPotionId(anyString());
            doAnswer(call -> { potionLevel = call.getArgument(0); return null; }).when(rhino).setPotionLevel(anyInt());
            doAnswer(call -> { potionDuration = call.getArgument(0); return null; }).when(rhino).setPotionDuration(anyInt());
            doAnswer(call -> { inflictedCount = call.getArgument(0); return null; }).when(rhino).setInflictedCount(anyInt());
            when(player.hasInfiniteMaterials()).thenReturn(creative);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getItemInHand(any(InteractionHand.class))).thenAnswer(call -> hands.getOrDefault(call.getArgument(0), ItemStack.EMPTY));
            doAnswer(call -> { hands.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(player).setItemInHand(any(InteractionHand.class), any(ItemStack.class));
            when(inventory.contains(any(ItemStack.class))).thenAnswer(call ->
                bottles.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, call.getArgument(0))));
            when(inventory.add(any(ItemStack.class))).thenAnswer(call -> {
                bottles.add(((ItemStack)call.getArgument(0)).copy());
                return true;
            });
        }

        void coat() {
            assertTrue(rhino.applyPotion(Potions.STRONG_POISON.value()));
            rhino.setInflictedCount(7);
            assertCoated();
            clearInvocations(rhino, player, inventory);
        }

        void assertCoated() {
            assertEquals("minecraft:poison", potionId);
            assertEquals(1, potionLevel);
            assertEquals(432, potionDuration);
            assertEquals(7, inflictedCount);
        }

        void assertCleared() {
            assertEquals("", potionId);
            assertEquals(0, potionLevel);
            assertEquals(0, potionDuration);
            assertEquals(0, inflictedCount);
        }

        void assertNoTransaction(ItemStack stack, InteractionHand hand) {
            assertSame(stack, hands.get(hand));
            assertEquals(1, stack.getCount());
            assertTrue(bottles.isEmpty());
            verify(rhino, never()).resetPotion();
            verify(rhino, never()).setAppliedPotionId(anyString());
            verify(rhino, never()).setPotionLevel(anyInt());
            verify(rhino, never()).setPotionDuration(anyInt());
            verify(rhino, never()).setInflictedCount(anyInt());
            verify(rhino, never()).gameEvent(GameEvent.ENTITY_INTERACT);
            verify(rhino, never()).playSound(SoundEvents.DYE_USE);
            verify(player, never()).setItemInHand(any(InteractionHand.class), any(ItemStack.class));
            verify(player, never()).addItem(any(ItemStack.class));
            verify(player, never()).drop(any(ItemStack.class), anyBoolean());
            verifyNoInteractions(inventory);
        }
    }
}
