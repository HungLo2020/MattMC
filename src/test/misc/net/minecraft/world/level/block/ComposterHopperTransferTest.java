package net.minecraft.world.level.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NativeEntityGeometry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real Hopper ticks and Composter containers; only the world boundary is controlled. */
class ComposterHopperTransferTest {
    private static final BlockPos HOPPER = new BlockPos(0, 64, 0);
    private static final BlockPos COMPOSTER = HOPPER.above();
    private static final BlockPos OUTPUT = HOPPER.east();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void incompatiblePartialInventoryPreservesReadyBatchAcrossRepeatedTicks() {
        Fixture f = new Fixture();
        f.fillHopper(63);
        List<ItemStack> before = contents(f.hopper);

        for (int attempt = 0; attempt < 3; attempt++) {
            f.tick();
            assertEquals(8, f.compostLevel(), "Rejected transfer must preserve the world Composter");
            assertContents(before, f.hopper);
            Container fresh = HopperBlockEntity.getContainerAt(f.level, COMPOSTER);
            assertTrue(fresh.getItem(0).is(Items.BONE_MEAL));
            assertEquals(1, fresh.getItem(0).getCount());
        }
        assertTrue(f.composterWrites.isEmpty(), "Rollback must not transiently empty the world block");
        assertEquals(0, f.composterEvents);
    }

    @Test
    void rejectedBatchTransfersOnceAfterSpaceIsMade() {
        Fixture f = new Fixture();
        f.fillHopper(63);
        f.tick();
        assertEquals(8, f.compostLevel());

        f.hopper.setItem(4, ItemStack.EMPTY);
        f.tick();
        assertSingleCommit(f);
        assertTrue(f.hopper.getItem(4).is(Items.BONE_MEAL));
        assertEquals(1, f.hopper.getItem(4).getCount());
        List<ItemStack> after = contents(f.hopper);
        for (int tick = 0; tick < 24; tick++) {
            f.tick();
            WorldlyContainer fresh = (WorldlyContainer) HopperBlockEntity.getContainerAt(f.level, COMPOSTER);
            assertTrue(fresh.isEmpty());
            assertArrayEquals(new int[0], fresh.getSlotsForFace(Direction.DOWN));
        }
        assertContents(after, f.hopper);
        assertSingleCommit(f);
    }

    @Test
    void emptyHopperAcceptsExactlyOneBoneMeal() {
        Fixture f = new Fixture();
        f.tick();
        assertSingleCommit(f);
        assertTrue(f.hopper.getItem(0).is(Items.BONE_MEAL));
        assertEquals(1, f.hopper.getItem(0).getCount());
        List<ItemStack> after = contents(f.hopper);
        for (int tick = 0; tick < 24; tick++) f.tick();
        assertContents(after, f.hopper);
        assertSingleCommit(f);
    }

    @Test
    void matchingBoneMealStackAcceptsExactlyOne() {
        Fixture f = new Fixture();
        f.fillHopper(64);
        f.hopper.setItem(0, new ItemStack(Items.BONE_MEAL, 63));
        f.tick();
        assertSingleCommit(f);
        assertTrue(f.hopper.getItem(0).is(Items.BONE_MEAL));
        assertEquals(64, f.hopper.getItem(0).getCount());
        for (int slot = 1; slot < 5; slot++) assertEquals(64, f.hopper.getItem(slot).getCount());
    }

    @Test
    void boneMealWithDifferentComponentsDoesNotConsumeReadyBatch() {
        Fixture f = new Fixture();
        f.fillHopper(64);
        ItemStack named = new ItemStack(Items.BONE_MEAL, 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("incompatible"));
        f.hopper.setItem(0, named);
        List<ItemStack> before = contents(f.hopper);
        f.tick();
        assertEquals(8, f.compostLevel());
        assertContents(before, f.hopper);
        assertTrue(f.composterWrites.isEmpty());
    }

    @Test
    void fullyMaxedHopperDoesNotAttemptExtraction() {
        Fixture f = new Fixture();
        f.fillHopper(64);
        List<ItemStack> before = contents(f.hopper);
        for (int tick = 0; tick < 3; tick++) f.tick();
        assertEquals(0, f.sourceLookups);
        assertEquals(8, f.compostLevel());
        assertContents(before, f.hopper);
        assertTrue(f.composterWrites.isEmpty());
    }

    @Test
    void disabledHopperDoesNotAttemptExtraction() {
        Fixture f = new Fixture();
        f.fillHopper(63);
        BlockState disabled = f.blocks.get(HOPPER).setValue(HopperBlock.ENABLED, false);
        f.blocks.put(HOPPER, disabled);
        f.hopper.setBlockState(disabled);
        List<ItemStack> before = contents(f.hopper);
        for (int tick = 0; tick < 3; tick++) f.tick();
        assertEquals(0, f.sourceLookups);
        assertEquals(8, f.compostLevel());
        assertContents(before, f.hopper);
        assertTrue(f.composterWrites.isEmpty());
    }

    @Test
    void successfulTransferCooldownSkipsSevenTicksThenRetriesWithoutLosingBatch() {
        Fixture f = new Fixture();
        f.tick();
        assertSingleCommit(f);
        f.setCompostLevel(8);
        f.fillHopper(63);
        f.sourceLookups = 0;
        f.composterWrites.clear();
        f.composterEvents = 0;
        List<ItemStack> before = contents(f.hopper);

        for (int tick = 0; tick < 7; tick++) f.tick();
        assertEquals(0, f.sourceLookups);
        f.tick();
        assertEquals(1, f.sourceLookups, "Cooldown expiration must actually try extraction");
        assertEquals(8, f.compostLevel());
        assertContents(before, f.hopper);
        assertTrue(f.composterWrites.isEmpty());
        f.hopper.setItem(4, ItemStack.EMPTY);
        f.tick();
        assertSingleCommit(f);
    }

    @Test
    void precedingOutputThatFreesSlotAllowsSameTickExtraction() {
        Fixture f = new Fixture();
        f.fillHopper(1);
        BarrelBlockEntity output = f.attachBarrel();
        f.tick();
        assertTrue(output.getItem(0).is(Items.STONE));
        assertEquals(1, output.getItem(0).getCount());
        assertTrue(f.hopper.getItem(0).is(Items.BONE_MEAL));
        assertEquals(1, f.hopper.getItem(0).getCount());
        assertSingleCommit(f);
    }

    @Test
    void precedingOutputThatOnlyShrinksStackLeavesReadyBatchUntouched() {
        Fixture f = new Fixture();
        f.fillHopper(2);
        BarrelBlockEntity output = f.attachBarrel();
        f.tick();
        assertTrue(output.getItem(0).is(Items.STONE));
        assertEquals(1, output.getItem(0).getCount());
        assertTrue(f.hopper.getItem(0).is(Items.STONE));
        assertEquals(1, f.hopper.getItem(0).getCount());
        for (int slot = 1; slot < 5; slot++) assertEquals(64, f.hopper.getItem(slot).getCount());
        assertEquals(8, f.compostLevel());
        assertTrue(f.composterWrites.isEmpty());
    }

    @Test
    void incompatiblePartialOutputContainerRejectsBothMovesWithoutLosingItems() {
        Fixture f = new Fixture();
        f.fillHopper(63);
        BarrelBlockEntity output = f.attachBarrel();
        for (int slot = 0; slot < output.getContainerSize(); slot++) {
            output.setItem(slot, new ItemStack(Items.COBBLESTONE, slot == 0 ? 63 : 64));
        }
        List<ItemStack> hopperBefore = contents(f.hopper);
        List<ItemStack> outputBefore = contents(output);
        f.tick();
        assertContents(hopperBefore, f.hopper);
        assertContents(outputBefore, output);
        assertEquals(8, f.compostLevel());
        assertTrue(f.composterWrites.isEmpty());
    }

    @Test
    void temporaryOutputRemovalIsTentativeAndRollbackKeepsItExtractable() {
        Fixture f = new Fixture();
        WorldlyContainer source = (WorldlyContainer) HopperBlockEntity.getContainerAt(f.level, COMPOSTER);
        assertTrue(f.composterWrites.isEmpty(), "Constructing output must not consume it");
        source.setChanged();
        assertTrue(source.removeItem(0, 0).isEmpty());
        assertTrue(source.removeItem(1, 1).isEmpty());
        assertEquals(1, source.getItem(0).getCount());
        assertTrue(f.composterWrites.isEmpty());
        assertArrayEquals(new int[]{0}, source.getSlotsForFace(Direction.DOWN));
        for (Direction face : Direction.values()) {
            assertFalse(source.canPlaceItemThroughFace(0, new ItemStack(Items.BONE_MEAL), face));
            assertEquals(face == Direction.DOWN,
                source.canTakeItemThroughFace(0, source.getItem(0), face));
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            ItemStack removed = source.removeItem(0, 1);
            assertTrue(removed.is(Items.BONE_MEAL));
            assertEquals(8, f.compostLevel());
            assertTrue(f.composterWrites.isEmpty(), "Tentative removal cannot change the world");
            source.setItem(0, removed);
            assertTrue(source.canTakeItemThroughFace(0, source.getItem(0), Direction.DOWN));
        }
        source.removeItem(0, 1);
        source.setChanged();
        assertSingleCommit(f);
        source.setChanged();
        source.setChanged();
        assertTrue(source.removeItem(0, 1).isEmpty());
        assertFalse(source.canTakeItemThroughFace(0, new ItemStack(Items.BONE_MEAL), Direction.DOWN));
        assertSingleCommit(f);

        f.setCompostLevel(8);
        source.setChanged();
        assertEquals(8, f.compostLevel(), "An old committed callback must not empty a later batch");
        assertEquals(1, f.composterWrites.size());
        Container fresh = HopperBlockEntity.getContainerAt(f.level, COMPOSTER);
        assertEquals(1, fresh.removeItem(0, 1).getCount());
        fresh.setChanged();
        assertEquals(0, f.compostLevel());
        assertEquals(2, f.composterWrites.size());
        assertEquals(2, f.composterEvents);
    }

    @Test
    void hopperMinecartUsesTheSameRollbackAndCommitProtocol() {
        Fixture f = new Fixture();
        when(f.level.nativeEntityGeometry()).thenReturn(new NativeEntityGeometry());
        when(f.level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        MinecartHopper cart = new MinecartHopper(EntityType.HOPPER_MINECART, f.level);
        cart.setPos(0.5, 64.0, 0.5);
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            cart.setItem(slot, new ItemStack(Items.STONE, slot == 0 ? 63 : 64));
        }
        List<ItemStack> before = contents(cart);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertFalse(cart.suckInItems());
            assertEquals(8, f.compostLevel());
            assertContents(before, cart);
        }
        assertTrue(f.composterWrites.isEmpty());
        cart.setItem(4, ItemStack.EMPTY);
        assertTrue(cart.suckInItems());
        assertTrue(cart.getItem(4).is(Items.BONE_MEAL));
        assertEquals(1, cart.getItem(4).getCount());
        assertFalse(cart.suckInItems());
        assertSingleCommit(f);
    }

    @Test
    void manualHarvestAfterRejectedHopperTransferStillDropsOneBoneMeal() throws Exception {
        Fixture f = new Fixture();
        f.fillHopper(63);
        f.tick();
        assertEquals(8, f.compostLevel());
        RandomSource random = RandomSource.create(796);
        var randomField = Level.class.getDeclaredField("random");
        randomField.setAccessible(true);
        randomField.set(f.level, random);
        when(f.level.nativeEntityGeometry()).thenReturn(new NativeEntityGeometry());
        List<ItemEntity> drops = new ArrayList<>();
        when(f.level.addFreshEntity(any())).thenAnswer(invocation -> {
            drops.add(invocation.getArgument(0));
            return true;
        });
        var hit = new BlockHitResult(Vec3.atCenterOf(COMPOSTER), Direction.UP, COMPOSTER, false);
        ComposterBlock block = (ComposterBlock) Blocks.COMPOSTER;
        assertEquals(InteractionResult.SUCCESS,
            block.useWithoutItem(f.blocks.get(COMPOSTER), f.level, COMPOSTER, null, hit));
        assertSingleCommit(f);
        assertEquals(1, drops.size());
        assertTrue(drops.getFirst().getItem().is(Items.BONE_MEAL));
        assertEquals(1, drops.getFirst().getItem().getCount());
        assertEquals(InteractionResult.PASS,
            block.useWithoutItem(f.blocks.get(COMPOSTER), f.level, COMPOSTER, null, hit));
        assertEquals(1, drops.size());
    }

    @Test
    void hopperInputStillConsumesOneIngredientAndSchedulesLevelSevenMaturation() {
        Fixture f = new Fixture();
        f.setCompostLevel(6);
        HopperBlockEntity input = f.inputHopper(new ItemStack(Items.PUMPKIN_PIE, 2));
        assertEquals(2, input.getItem(0).getCount(), "Use a stackable 100% compost ingredient");
        f.tick(input);
        assertEquals(7, f.compostLevel());
        assertEquals(1, input.getItem(0).getCount());
        assertEquals(1, f.composterWrites.size());
        verify(f.level).scheduleTick(COMPOSTER, Blocks.COMPOSTER, 20);
        WorldlyContainer waiting = (WorldlyContainer) HopperBlockEntity.getContainerAt(f.level, COMPOSTER);
        assertEquals(0, waiting.getContainerSize());
        assertArrayEquals(new int[0], waiting.getSlotsForFace(Direction.UP));
        assertArrayEquals(new int[0], waiting.getSlotsForFace(Direction.DOWN));

        for (int tick = 0; tick < 8; tick++) f.tick(input);
        assertEquals(7, f.compostLevel());
        assertEquals(1, input.getItem(0).getCount());
        ((ComposterBlock) Blocks.COMPOSTER).tick(f.blocks.get(COMPOSTER), f.level, COMPOSTER, f.level.getRandom());
        assertEquals(8, f.compostLevel());
        assertEquals(2, f.composterWrites.size());
        f.tick();
        assertEquals(0, f.compostLevel());
        assertEquals(1, f.hopper.getItem(0).getCount());
        assertTrue(f.hopper.getItem(0).is(Items.BONE_MEAL));
    }

    @Test
    void firstHopperIngredientRemainsGuaranteedAndSideInputRemainsRejected() {
        Fixture f = new Fixture();
        f.setCompostLevel(0);
        WorldlyContainer source = (WorldlyContainer) HopperBlockEntity.getContainerAt(f.level, COMPOSTER);
        for (Direction face : Direction.values()) {
            assertEquals(face == Direction.UP,
                source.canPlaceItemThroughFace(0, new ItemStack(Items.WHEAT_SEEDS), face));
            assertFalse(source.canTakeItemThroughFace(0, new ItemStack(Items.WHEAT_SEEDS), face));
        }
        assertFalse(source.canPlaceItemThroughFace(0, new ItemStack(Items.STONE), Direction.UP));
        HopperBlockEntity input = f.inputHopper(new ItemStack(Items.WHEAT_SEEDS, 2));
        f.tick(input);
        assertEquals(1, f.compostLevel());
        assertEquals(1, input.getItem(0).getCount());
        assertEquals(1, f.composterWrites.size());
    }

    private static void assertSingleCommit(Fixture fixture) {
        assertEquals(0, fixture.compostLevel());
        assertEquals(1, fixture.composterWrites.size(), "Empty the world exactly once");
        assertEquals(1, fixture.composterEvents, "Emit the world change exactly once");
    }

    private static List<ItemStack> contents(Container container) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) result.add(container.getItem(slot).copy());
        return result;
    }

    private static void assertContents(List<ItemStack> expected, Container actual) {
        assertEquals(expected.size(), actual.getContainerSize());
        for (int slot = 0; slot < expected.size(); slot++) {
            assertTrue(ItemStack.matches(expected.get(slot), actual.getItem(slot)), "Changed slot " + slot);
        }
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
        final List<BlockState> composterWrites = new ArrayList<>();
        final HopperBlockEntity hopper;
        long gameTime;
        int sourceLookups;
        int composterEvents;

        Fixture() {
            BlockState hopperState = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST);
            blocks.put(HOPPER, hopperState);
            blocks.put(COMPOSTER, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
            blocks.put(OUTPUT, Blocks.STONE.defaultBlockState());
            when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
                BlockPos pos = invocation.getArgument(0);
                if (pos.equals(COMPOSTER)) sourceLookups++;
                return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            });
            when(level.getBlockEntity(any(BlockPos.class))).thenAnswer(invocation -> blockEntities.get(invocation.getArgument(0)));
            when(level.getGameTime()).thenAnswer(invocation -> gameTime);
            when(level.getRandom()).thenReturn(RandomSource.create(796));
            when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
                BlockPos pos = invocation.getArgument(0);
                BlockState state = invocation.getArgument(1);
                blocks.put(pos, state);
                if (pos.equals(COMPOSTER)) composterWrites.add(state);
                return true;
            });
            doAnswer(invocation -> {
                composterEvents++;
                return null;
            }).when(level).gameEvent(eq(GameEvent.BLOCK_CHANGE), eq(COMPOSTER), any(GameEvent.Context.class));
            hopper = new HopperBlockEntity(HOPPER, hopperState);
            hopper.setLevel(level);
            blockEntities.put(HOPPER, hopper);
        }

        void fillHopper(int firstCount) {
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                hopper.setItem(slot, new ItemStack(Items.STONE, slot == 0 ? firstCount : 64));
            }
        }

        void tick() {
            tick(hopper);
        }

        void tick(HopperBlockEntity target) {
            gameTime++;
            HopperBlockEntity.pushItemsTick(level, target.getBlockPos(), blocks.get(target.getBlockPos()), target);
        }

        void setCompostLevel(int value) {
            blocks.put(COMPOSTER, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, value));
        }

        BarrelBlockEntity attachBarrel() {
            BlockState state = Blocks.BARREL.defaultBlockState();
            blocks.put(OUTPUT, state);
            BarrelBlockEntity barrel = new BarrelBlockEntity(OUTPUT, state);
            barrel.setLevel(level);
            blockEntities.put(OUTPUT, barrel);
            return barrel;
        }

        HopperBlockEntity inputHopper(ItemStack ingredient) {
            BlockPos pos = COMPOSTER.above();
            BlockState state = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
            blocks.put(pos, state);
            blocks.put(pos.above(), Blocks.STONE.defaultBlockState());
            HopperBlockEntity input = new HopperBlockEntity(pos, state);
            input.setLevel(level);
            input.setItem(0, ingredient);
            blockEntities.put(pos, input);
            return input;
        }

        int compostLevel() {
            return blocks.get(COMPOSTER).getValue(ComposterBlock.LEVEL);
        }
    }
}
