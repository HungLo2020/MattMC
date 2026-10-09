package net.minecraft.world.level.block.state.properties;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeBlockFamiliesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void canonicalFamilyViewsKeepCodecIdentityAndInteractionDifferences() {
        int id = 0;
        for (var set : BlockSetType.values().toList()) {
            assertSame(set, BlockSetType.nativeView(id++));
            var encoded = BlockSetType.CODEC.encodeStart(JsonOps.INSTANCE, set).getOrThrow();
            assertSame(set, BlockSetType.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
        assertEquals(17, id); id = 0;
        for (var wood : WoodType.values().toList()) {
            assertSame(wood, WoodType.nativeView(id++));
            var encoded = WoodType.CODEC.encodeStart(JsonOps.INSTANCE, wood).getOrThrow();
            assertSame(wood, WoodType.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
        assertEquals(12, id);
        assertFalse(BlockSetType.IRON.canOpenByHand());
        assertFalse(BlockSetType.IRON.canOpenByWindCharge());
        assertTrue(BlockSetType.COPPER.canOpenByHand());
        assertTrue(BlockSetType.COPPER.canOpenByWindCharge());
        assertFalse(BlockSetType.GOLD.canOpenByHand());
        assertTrue(BlockSetType.GOLD.canOpenByWindCharge());
        var custom = new BlockSetType("unregistered_test");
        assertTrue(custom.canButtonBeActivatedByArrows());
        assertEquals(SoundType.WOOD, custom.soundType());
        assertEquals(17, BlockSetType.values().count());
        var customWood = new WoodType("unregistered_test", custom);
        assertSame(custom, customWood.setType());
        assertEquals(12, WoodType.values().count());
    }

    @Test void registeredConstructorsUseNativeBindingsForEveryConfiguredBlock() throws Exception {
        int configured = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            NativeBlockFamilies.Kind kind;
            if (block instanceof DoorBlock) kind = NativeBlockFamilies.Kind.DOOR;
            else if (block instanceof TrapDoorBlock) kind = NativeBlockFamilies.Kind.TRAPDOOR;
            else if (block instanceof ButtonBlock) kind = NativeBlockFamilies.Kind.BUTTON;
            else if (block instanceof PressurePlateBlock) kind = NativeBlockFamilies.Kind.PRESSURE_PLATE;
            else if (block instanceof WeightedPressurePlateBlock) kind = NativeBlockFamilies.Kind.WEIGHTED_PLATE;
            else if (block instanceof FenceGateBlock) kind = NativeBlockFamilies.Kind.FENCE_GATE;
            else if (block instanceof StandingSignBlock) kind = NativeBlockFamilies.Kind.STANDING_SIGN;
            else if (block instanceof WallSignBlock) kind = NativeBlockFamilies.Kind.WALL_SIGN;
            else if (block instanceof CeilingHangingSignBlock) kind = NativeBlockFamilies.Kind.CEILING_HANGING_SIGN;
            else if (block instanceof WallHangingSignBlock) kind = NativeBlockFamilies.Kind.WALL_HANGING_SIGN;
            else continue;
            var properties = block.properties();
            Object expected = kind.ordinal() <= NativeBlockFamilies.Kind.WEIGHTED_PLATE.ordinal()
                ? NativeBlockFamilies.set(properties, kind) : NativeBlockFamilies.wood(properties, kind);
            assertSame(expected, field(block, "type"), block.toString());
            if (kind == NativeBlockFamilies.Kind.BUTTON)
                assertEquals(NativeBlockFamilies.parameter(properties, kind), field(block, "ticksToStayPressed"));
            if (kind == NativeBlockFamilies.Kind.WEIGHTED_PLATE)
                assertEquals(NativeBlockFamilies.parameter(properties, kind), field(block, "maxWeight"));
            configured++;
        }
        assertEquals(141, configured);
    }

    @Test void nativeOnlyConstructorsRejectMissingOrWrongFamilyBeforeAllocatingBlockHolders() {
        assertThrows(IllegalArgumentException.class, () -> new DoorBlock(BlockBehaviour.Properties.of()));
        var wrongFamily = BlockBehaviour.Properties.of().nativeDefinition(Blocks.STONE.nativeDefinition());
        assertThrows(IllegalStateException.class, () -> new ButtonBlock(wrongFamily));
        assertThrows(IllegalArgumentException.class,
            () -> NativeBlockFamilies.parameter(Blocks.OAK_DOOR.properties(), NativeBlockFamilies.Kind.DOOR));
    }

    private static Object field(Object value, String name) throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(value); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
