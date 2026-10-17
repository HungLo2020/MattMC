package net.minecraft.world.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Executes the registered wand, ItemStack, BlockItem, BlockPlaceContext and slab
 * placement code. Only world storage/permissions/collisions and the player are
 * mocked; these are behavioral interaction tests, not live gameplay tests.
 */
@Isolated("Temporarily enables Byte Buddy support for the required JDK")
class BuildingWandPlacementTest {
    private static final BlockPos SOURCE = new BlockPos(-7, -12, -9);
    private static String previousByteBuddy;

    @BeforeAll
    static void bootstrap() {
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
    void translatedPlacementContextPreservesLocalCoordinatesInsideFlagHandAndStackIdentity() throws Exception {
        Fixture fixture = new Fixture(false);
        ItemStack wand = new ItemStack(Items.BUILDING_WAND);
        ItemStack placementStack = new ItemStack(Items.OAK_SLAB, 3);
        BlockPos translatedSource = new BlockPos(8, 4, 12);
        Method factory = BuildingWandItem.class.getDeclaredMethod("placementContext", Level.class, Player.class,
            UseOnContext.class, ItemStack.class, BlockPos.class, Direction.class);
        factory.setAccessible(true);

        for (boolean inside : new boolean[]{false, true}) {
            UseOnContext original = new UseOnContext(fixture.level, fixture.player, InteractionHand.OFF_HAND, wand,
                new BlockHitResult(new Vec3(SOURCE.getX() + 0.125, SOURCE.getY() + 0.5, SOURCE.getZ() + 0.875),
                    Direction.DOWN, SOURCE, inside));

            UseOnContext translated = (UseOnContext)factory.invoke(null, fixture.level, fixture.player, original,
                placementStack, translatedSource, Direction.DOWN);

            assertEquals(new Vec3(8.125, 4.5, 12.875), translated.getClickLocation());
            assertEquals(translatedSource, translated.getClickedPos());
            assertEquals(Direction.DOWN, translated.getClickedFace());
            assertEquals(inside, translated.isInside());
            assertEquals(InteractionHand.OFF_HAND, translated.getHand());
            assertSame(placementStack, translated.getItemInHand());
            assertSame(fixture.player, translated.getPlayer());
            assertSame(fixture.level, translated.getLevel());
            assertSame(wand, original.getItemInHand());
            assertEquals(SOURCE, original.getClickedPos());
        }
    }

    @ParameterizedTest
    @MethodSource("sideClicks")
    void sideClickExtendsTheSlabHalfWithoutFillingTheSource(Direction face, SlabType type, double localY) {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, type);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 4);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, face, localY));

        fixture.assertSlab(SOURCE, type);
        fixture.assertSlab(SOURCE.relative(face), type);
        assertEquals(List.of(SOURCE.relative(face)), fixture.writes);
        assertEquals(3, supply.getCount());
        fixture.assertPlaceSoundAt(SOURCE.relative(face));
    }

    static Stream<Arguments> sideClicks() {
        return Stream.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
            .flatMap(face -> Stream.of(
                Arguments.of(face, SlabType.TOP, 0.75),
                Arguments.of(face, SlabType.BOTTOM, 0.25)));
    }

    @ParameterizedTest
    @MethodSource("slabHeightBoundary")
    void sideClickOnDoubleSlabPreservesVanillaHalfHeightBoundary(double localY, SlabType expected) {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.DOUBLE);
        fixture.supply(Items.OAK_SLAB, 1);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.EAST, localY));

        fixture.assertSlab(SOURCE, SlabType.DOUBLE);
        fixture.assertSlab(SOURCE.east(), expected);
        assertEquals(List.of(SOURCE.east()), fixture.writes);
    }

    static Stream<Arguments> slabHeightBoundary() {
        return Stream.of(
            Arguments.of(0.4999, SlabType.BOTTOM),
            Arguments.of(0.5, SlabType.BOTTOM),
            Arguments.of(0.5001, SlabType.TOP));
    }

    @ParameterizedTest
    @EnumSource(value = SlabType.class, names = {"TOP", "BOTTOM"})
    void everySourceUsesTheOriginalLocalHitOffsetAcrossHeightsAndDiagonalConnections(SlabType type) {
        Fixture fixture = new Fixture(false);
        List<BlockPos> sources = List.of(SOURCE, SOURCE.above(), SOURCE.above().west(), SOURCE.below());
        sources.forEach(pos -> fixture.putSlab(pos, type));
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 8);

        assertEquals(InteractionResult.SUCCESS_SERVER,
            fixture.use(SOURCE, Direction.NORTH, type == SlabType.TOP ? 0.75 : 0.25));

        for (BlockPos source : sources) {
            fixture.assertSlab(source, type);
            fixture.assertSlab(source.north(), type);
        }
        assertEquals(Set.copyOf(sources.stream().map(BlockPos::north).toList()), Set.copyOf(fixture.writes));
        assertEquals(sources.size(), fixture.writes.size());
        assertEquals(4, supply.getCount());
    }

    @ParameterizedTest
    @EnumSource(value = SlabType.class, names = {"TOP", "BOTTOM"})
    void mixedSlabPlaneRetainsVanillaTranslatedClickMergingRatherThanFilteringSourceStates(SlabType clickedType) {
        Fixture fixture = new Fixture(false);
        BlockPos otherSource = SOURCE.above();
        fixture.putSlab(SOURCE, clickedType);
        fixture.putSlab(otherSource, clickedType == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 4);

        assertEquals(InteractionResult.SUCCESS_SERVER,
            fixture.use(SOURCE, Direction.NORTH, clickedType == SlabType.TOP ? 0.75 : 0.25));

        fixture.assertSlab(SOURCE, clickedType);
        fixture.assertSlab(SOURCE.north(), clickedType);
        fixture.assertSlab(otherSource, SlabType.DOUBLE);
        assertTrue(fixture.state(otherSource.north()).isAir());
        assertEquals(Set.of(SOURCE.north(), otherSource), Set.copyOf(fixture.writes));
        assertEquals(2, supply.getCount());
        fixture.assertPlaceSoundAt(otherSource);
    }

    @ParameterizedTest
    @MethodSource("verticalClicks")
    void verticalClicksRetainVanillaMergingAndOutwardPlacement(
        SlabType sourceType, Direction face, double localY, boolean merge, SlabType placedType
    ) {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, sourceType);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 3);
        BlockPos target = merge ? SOURCE : SOURCE.relative(face);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, face, localY));

        fixture.assertSlab(target, placedType);
        if (!merge) {
            fixture.assertSlab(SOURCE, sourceType);
        }
        assertEquals(List.of(target), fixture.writes);
        assertEquals(2, supply.getCount());
        fixture.assertPlaceSoundAt(target);
    }

    static Stream<Arguments> verticalClicks() {
        return Stream.of(
            Arguments.of(SlabType.BOTTOM, Direction.UP, 0.5, true, SlabType.DOUBLE),
            Arguments.of(SlabType.TOP, Direction.DOWN, 0.5, true, SlabType.DOUBLE),
            Arguments.of(SlabType.TOP, Direction.UP, 1.0, false, SlabType.BOTTOM),
            Arguments.of(SlabType.BOTTOM, Direction.DOWN, 0.0, false, SlabType.TOP),
            Arguments.of(SlabType.DOUBLE, Direction.UP, 1.0, false, SlabType.BOTTOM),
            Arguments.of(SlabType.DOUBLE, Direction.DOWN, 0.0, false, SlabType.TOP));
    }

    @Test
    void deniedResolvedMergeTargetCannotBeModifiedEvenWhenAdjacentSpaceIsAllowed() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.BOTTOM);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 2);
        fixture.denied.add(SOURCE);

        assertEquals(InteractionResult.PASS, fixture.use(SOURCE, Direction.UP, 0.5));

        fixture.assertSlab(SOURCE, SlabType.BOTTOM);
        assertTrue(fixture.state(SOURCE.above()).isAir());
        assertTrue(fixture.writes.isEmpty());
        assertEquals(2, supply.getCount());
    }

    @Test
    void deniedAdjacentSpaceDoesNotBlockAnAllowedInPlaceMerge() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.BOTTOM);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 2);
        fixture.denied.add(SOURCE.above());

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.UP, 0.5));

        fixture.assertSlab(SOURCE, SlabType.DOUBLE);
        assertEquals(List.of(SOURCE), fixture.writes);
        assertEquals(1, supply.getCount());
    }

    @Test
    void mixedPlaneCannotMergeADeniedNeighborThroughAnAllowedAdjacentPosition() {
        Fixture fixture = new Fixture(false);
        BlockPos neighbor = SOURCE.above();
        fixture.putSlab(SOURCE, SlabType.TOP);
        fixture.putSlab(neighbor, SlabType.BOTTOM);
        fixture.denied.add(neighbor);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 3);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.NORTH, 0.75));

        fixture.assertSlab(SOURCE.north(), SlabType.TOP);
        fixture.assertSlab(neighbor, SlabType.BOTTOM);
        assertEquals(List.of(SOURCE.north()), fixture.writes);
        assertEquals(2, supply.getCount());
    }

    @Test
    void actualPlacementRechecksResolvedTargetPermissionAfterPreflight() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.BOTTOM);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 2);
        when(fixture.level.mayInteract(fixture.player, SOURCE)).thenReturn(true, false);

        assertEquals(InteractionResult.PASS, fixture.use(SOURCE, Direction.UP, 0.5));

        fixture.assertSlab(SOURCE, SlabType.BOTTOM);
        assertTrue(fixture.writes.isEmpty());
        assertEquals(2, supply.getCount());
        verify(fixture.level, times(2)).mayInteract(fixture.player, SOURCE);
    }

    @Test
    void deniedSideTargetDoesNotConsumeResourcesOrModifyEitherPosition() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.TOP);
        fixture.denied.add(SOURCE.east());
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 3);

        assertEquals(InteractionResult.PASS, fixture.use(SOURCE, Direction.EAST, 0.75));

        fixture.assertSlab(SOURCE, SlabType.TOP);
        assertTrue(fixture.state(SOURCE.east()).isAir());
        assertTrue(fixture.writes.isEmpty());
        assertEquals(3, supply.getCount());
    }

    @Test
    void obstructedSideTargetDoesNotFallBackToFillingTheTopSlab() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.TOP);
        fixture.blocks.put(SOURCE.east(), Blocks.STONE.defaultBlockState());
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 3);

        assertEquals(InteractionResult.PASS, fixture.use(SOURCE, Direction.EAST, 0.75));

        fixture.assertSlab(SOURCE, SlabType.TOP);
        assertTrue(fixture.state(SOURCE.east()).is(Blocks.STONE));
        assertTrue(fixture.writes.isEmpty());
        assertEquals(3, supply.getCount());
    }

    @Test
    void finiteInventoryStopsFurtherPlacementWithoutChangingUnfundedSources() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.TOP);
        fixture.putSlab(SOURCE.above(), SlabType.TOP);
        ItemStack supply = fixture.supply(Items.OAK_SLAB, 1);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.NORTH, 0.75));

        fixture.assertSlab(SOURCE.north(), SlabType.TOP);
        fixture.assertSlab(SOURCE.above(), SlabType.TOP);
        assertTrue(fixture.state(SOURCE.above().north()).isAir());
        assertEquals(List.of(SOURCE.north()), fixture.writes);
        assertTrue(supply.isEmpty());
    }

    @Test
    void survivalCanUseOffhandSupplyWithoutConsumingUnrelatedMainInventory() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.TOP);
        ItemStack unrelated = fixture.supply(Items.STONE, 5);
        ItemStack offhand = new ItemStack(Items.OAK_SLAB, 3);
        fixture.inventoryItems.put(Inventory.SLOT_OFFHAND, offhand);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.WEST, 0.75));

        fixture.assertSlab(SOURCE.west(), SlabType.TOP);
        assertEquals(2, offhand.getCount());
        assertEquals(5, unrelated.getCount());
    }

    @Test
    void survivalWithoutMatchingResourcesDoesNothing() {
        Fixture fixture = new Fixture(false);
        fixture.putSlab(SOURCE, SlabType.TOP);
        ItemStack unrelated = fixture.supply(Items.STONE, 5);

        assertEquals(InteractionResult.PASS, fixture.use(SOURCE, Direction.NORTH, 0.75));

        fixture.assertSlab(SOURCE, SlabType.TOP);
        assertTrue(fixture.writes.isEmpty());
        assertEquals(5, unrelated.getCount());
    }

    @Test
    void creativePlacesWithoutTakingAnyInventoryResources() {
        Fixture fixture = new Fixture(true);
        fixture.putSlab(SOURCE, SlabType.TOP);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.NORTH, 0.75));

        fixture.assertSlab(SOURCE.north(), SlabType.TOP);
        verify(fixture.inventory, never()).getItem(anyInt());
    }

    @Test
    void ordinaryFullBlocksStillExtendIntoTheClickedPlane() {
        Fixture fixture = new Fixture(false);
        fixture.blocks.put(SOURCE, Blocks.STONE.defaultBlockState());
        fixture.blocks.put(SOURCE.above(), Blocks.STONE.defaultBlockState());
        ItemStack supply = fixture.supply(Items.STONE, 3);

        assertEquals(InteractionResult.SUCCESS_SERVER, fixture.use(SOURCE, Direction.SOUTH, 0.8));

        assertTrue(fixture.state(SOURCE.south()).is(Blocks.STONE));
        assertTrue(fixture.state(SOURCE.above().south()).is(Blocks.STONE));
        assertEquals(Set.of(SOURCE.south(), SOURCE.above().south()), Set.copyOf(fixture.writes));
        assertEquals(1, supply.getCount());
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final Inventory inventory = mock(Inventory.class);
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<Integer, ItemStack> inventoryItems = new HashMap<>();
        final Set<BlockPos> denied = new HashSet<>();
        final List<BlockPos> writes = new ArrayList<>();

        Fixture(boolean creative) {
            when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
            when(level.getBlockState(any(BlockPos.class))).thenAnswer(call -> state(call.getArgument(0)));
            when(level.getFluidState(any(BlockPos.class))).thenAnswer(call -> state(call.getArgument(0)).getFluidState());
            when(level.mayInteract(eq(player), any(BlockPos.class))).thenAnswer(call -> !denied.contains(call.getArgument(1)));
            when(level.isUnobstructed(any(BlockState.class), any(BlockPos.class), any(CollisionContext.class))).thenReturn(true);
            when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(call -> {
                BlockPos pos = ((BlockPos)call.getArgument(0)).immutable();
                blocks.put(pos, call.getArgument(1));
                writes.add(pos);
                return true;
            });
            when(player.level()).thenReturn(level);
            when(player.getAbilities()).thenReturn(new Abilities());
            when(player.hasInfiniteMaterials()).thenReturn(creative);
            when(player.getMainHandItem()).thenReturn(new ItemStack(Items.BUILDING_WAND));
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItem(anyInt())).thenAnswer(call -> inventoryItems.getOrDefault(call.getArgument(0), ItemStack.EMPTY));
        }

        ItemStack supply(Item item, int count) {
            ItemStack stack = new ItemStack(item, count);
            inventoryItems.put(0, stack);
            return stack;
        }

        void putSlab(BlockPos pos, SlabType type) {
            blocks.put(pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type));
        }

        BlockState state(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        InteractionResult use(BlockPos source, Direction face, double localY) {
            Vec3 hit = new Vec3(source.getX() + 0.5 + face.getStepX() * 0.5,
                source.getY() + localY, source.getZ() + 0.5 + face.getStepZ() * 0.5);
            return Items.BUILDING_WAND.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND,
                new ItemStack(Items.BUILDING_WAND), new BlockHitResult(hit, face, source, false)));
        }

        void assertSlab(BlockPos pos, SlabType type) {
            assertTrue(state(pos).is(Blocks.OAK_SLAB), "Expected oak slab at " + pos);
            assertEquals(type, state(pos).getValue(SlabBlock.TYPE), "Slab half at " + pos);
        }

        void assertPlaceSoundAt(BlockPos pos) {
            verify(level).playSound(eq(player), eq(pos), eq(state(pos).getSoundType().getPlaceSound()),
                eq(SoundSource.BLOCKS), anyFloat(), anyFloat());
        }
    }
}
