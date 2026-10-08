package net.minecraft.world.level.block.state;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeBlockPhysicsTest {
    // Exercise the actual superclass constructor without allocating a new
    // intrusive registry holder after Bootstrap has frozen block registration.
    private static final class BehaviorView extends BlockBehaviour {
        BehaviorView(Properties properties) { super(properties); }
        @Override protected MapCodec<? extends Block> codec() { return Block.CODEC; }
        @Override public Item asItem() { return Items.STONE; }
        @Override protected Block asBlock() { return Blocks.STONE; }
    }
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nativeDefinitionControlsPhysicalConstructorCaches() {
        var key = ResourceKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("stone"));
        var properties = BlockBehaviour.Properties.of().setId(key)
            .nativeDefinition(NativeBlockDefinitions.require("minecraft:stone"))
            .strength(99.0F, 101.0F).friction(0.1F).speedFactor(3.0F).jumpFactor(2.0F)
            .noCollision().air().randomTicks().replaceable();
        var block = new BehaviorView(properties);
        assertEquals(1.5F, properties.destroyTime);
        assertEquals(6.0F, block.explosionResistance);
        assertEquals(0.6F, block.friction);
        assertEquals(1.0F, block.speedFactor);
        assertEquals(1.0F, block.jumpFactor);
        assertTrue(properties.hasCollision);
        assertTrue(properties.canOcclude);
        assertTrue(block.hasCollision);
        assertFalse(properties.isAir);
        assertFalse(properties.replaceable);
        assertFalse(block.isRandomlyTicking);
    }

    @Test
    void unregisteredCompatibilityObjectsRetainTheirExplicitSettings() {
        var key = ResourceKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("test_physics"));
        var properties = BlockBehaviour.Properties.of().setId(key)
            .strength(7.0F, 11.0F).friction(0.3F).noCollision().randomTicks();
        var block = new BehaviorView(properties);
        assertEquals(7.0F, properties.destroyTime);
        assertEquals(11.0F, block.explosionResistance);
        assertEquals(0.3F, block.friction);
        assertFalse(properties.hasCollision);
        assertFalse(properties.canOcclude);
        assertFalse(block.hasCollision);
        assertTrue(block.isRandomlyTicking);
    }
}
