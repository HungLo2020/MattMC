package net.alexsmobs.entity;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntityGrizzlyBearBreedingTest {
    private ServerLevel level;
    private static String previousByteBuddy;

    @BeforeAll
    static void bootstrapMinecraft() {
        // The bundled Mockito predates the required JDK; match existing tests.
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

    @BeforeEach
    void createLevel() {
        // Real entity factories, attributes and persistence; world I/O is mocked.
        level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)).thenReturn(true);
    }

    private EntityGrizzlyBear parent() {
        EntityGrizzlyBear bear = EntityType.GRIZZLY_BEAR.create(level, EntitySpawnReason.COMMAND);
        assertNotNull(bear);
        bear.setTame(true, false);
        bear.setOwnerReference(EntityReference.of(UUID.randomUUID()));
        return bear;
    }

    @Test
    void offspringFactoryCreatesAnUntamedUnownedGrizzly() {
        EntityGrizzlyBear parent = parent();
        AgeableMob offspring = parent.getBreedOffspring(level, parent());

        EntityGrizzlyBear cub = assertInstanceOf(EntityGrizzlyBear.class, offspring);
        assertSame(EntityType.GRIZZLY_BEAR, cub.getType());
        assertFalse(cub.isTame());
        assertNull(cub.getOwnerReference());
        // Animal.spawnChildFromBreeding owns the age change, not this factory.
        assertEquals(0, cub.getAge());
    }

    @Test
    void breedingSpawnsOneBabyAndPreservesCooldownLoveResetAndExperience() {
        EntityGrizzlyBear first = parent();
        EntityGrizzlyBear second = parent();
        first.setInLoveTime(600);
        second.setInLoveTime(600);
        assertTrue(first.canMate(second));

        first.spawnChildFromBreeding(level, second);

        ArgumentCaptor<Entity> spawned = ArgumentCaptor.forClass(Entity.class);
        verify(level, times(1)).addFreshEntityWithPassengers(spawned.capture());
        EntityGrizzlyBear cub = assertInstanceOf(EntityGrizzlyBear.class, spawned.getValue());
        assertSame(EntityType.GRIZZLY_BEAR, cub.getType());
        assertTrue(cub.isBaby());
        assertEquals(-24000, cub.getAge());
        assertFalse(cub.isTame());
        assertNull(cub.getOwnerReference());
        assertEquals(6000, first.getAge());
        assertEquals(6000, second.getAge());
        assertFalse(first.isInLove());
        assertFalse(second.isInLove());
        verify(level).broadcastEntityEvent(first, (byte)18);
        verify(level, times(1)).addFreshEntity(any(ExperienceOrb.class));
    }

    @Test
    void babyGrizzlyIdentityAndUntamedStateSurviveSaveReload() {
        EntityGrizzlyBear cub = assertInstanceOf(EntityGrizzlyBear.class, parent().getBreedOffspring(level, parent()));
        cub.setBaby(true);
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
        assertTrue(cub.save(output));
        assertEquals("minecraft:grizzly_bear", output.buildResult().getStringOr("id", ""));

        EntityGrizzlyBear restored = assertInstanceOf(EntityGrizzlyBear.class,
            EntityType.create(TagValueInput.create(problems, level.registryAccess(), output.buildResult()),
                level, EntitySpawnReason.LOAD).orElseThrow());
        assertSame(EntityType.GRIZZLY_BEAR, restored.getType());
        assertEquals(cub.getAge(), restored.getAge());
        assertTrue(restored.isBaby());
        assertFalse(restored.isTame());
        assertNull(restored.getOwnerReference());
        assertTrue(problems.isEmpty(), problems::getReport);
    }

    @Test
    void polarBearOffspringRemainPolarBears() {
        PolarBear parent = EntityType.POLAR_BEAR.create(level, EntitySpawnReason.COMMAND);
        assertNotNull(parent);
        AgeableMob offspring = parent.getBreedOffspring(level, parent);
        assertInstanceOf(PolarBear.class, offspring);
        assertSame(EntityType.POLAR_BEAR, offspring.getType());
    }
}
