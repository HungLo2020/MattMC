package net.minecraft.world.entity;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every registered entity type, built from the Rust catalog, against Frozen's
 * recorded definitions (DevUtils/tests/content/EntityTypeReference.java, the
 * same dump format). VerifyEntityTypes.py repeats this against a live Frozen.
 */
class NativeEntityTypesTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    static String exact(float value) { return Float.toString(value) + "/" + Integer.toHexString(Float.floatToRawIntBits(value)); }
    static String exact(double value) { return Double.toString(value) + "/" + Long.toHexString(Double.doubleToRawLongBits(value)); }

    @SuppressWarnings("unchecked")
    static List<String> dump() throws ReflectiveOperationException {
        var rows = new ArrayList<String>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            var row = new StringBuilder();
            row.append(BuiltInRegistries.ENTITY_TYPE.getId(type)).append(' ').append(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            row.append(" category=").append(type.getCategory().getSerializedName());
            row.append(" serialize=").append(field(type, "serialize"));
            row.append(" summon=").append(field(type, "summon"));
            row.append(" fireImmune=").append(field(type, "fireImmune"));
            row.append(" spawnFar=").append(field(type, "canSpawnFarFromPlayer"));
            row.append(" peaceful=").append(field(type, "allowedInPeaceful"));
            var immune = new ArrayList<String>();
            for (var block : (Iterable<net.minecraft.world.level.block.Block>) field(type, "immuneTo"))
                immune.add(BuiltInRegistries.BLOCK.getKey(block).toString());
            row.append(" immuneTo=").append(immune);
            EntityDimensions dimensions = (EntityDimensions) field(type, "dimensions");
            row.append(" width=").append(exact(dimensions.width()));
            row.append(" height=").append(exact(dimensions.height()));
            row.append(" eye=").append(exact(dimensions.eyeHeight()));
            row.append(" fixed=").append(dimensions.fixed());
            Map<EntityAttachment, List<Vec3>> attachments = (Map<EntityAttachment, List<Vec3>>) field(dimensions.attachments(), "attachments");
            for (EntityAttachment attachment : EntityAttachment.values()) {
                row.append(' ').append(attachment.name().toLowerCase()).append('=');
                var points = new ArrayList<String>();
                for (Vec3 point : attachments.getOrDefault(attachment, List.of()))
                    points.add(exact(point.x) + "," + exact(point.y) + "," + exact(point.z));
                row.append(points);
            }
            row.append(" spawnScale=").append(exact((float) field(type, "spawnDimensionsScale")));
            row.append(" tracking=").append(type.clientTrackingRange());
            row.append(" interval=").append(type.updateInterval());
            row.append(" description=").append(type.getDescriptionId());
            row.append(" loot=").append(type.getDefaultLootTable().map(key -> key.location().toString()).orElse("none"));
            var features = new ArrayList<String>();
            for (var feature : FeatureFlags.REGISTRY.toNames(type.requiredFeatures())) features.add(feature.toString());
            java.util.Collections.sort(features);
            row.append(" features=").append(features);
            rows.add(row.toString());
        }
        return rows;
    }


    @Test void everyEntityTypeMatchesFrozen() throws Exception {
        String expected;
        try (InputStream in = new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/content/frozen-entity-types.txt.gz")))) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var expectedRows = expected.split("\n");
        var actualRows = dump();
        assertEquals(expectedRows.length, actualRows.size(), "entity type count");
        for (int i = 0; i < expectedRows.length; i++) assertEquals(expectedRows[i], actualRows.get(i), "entity type " + i);
        assertEquals(NativeEntityTypes.count(), BuiltInRegistries.ENTITY_TYPE.size(), "every Rust definition is registered");
    }

    @Test void registrationOutOfCatalogOrderIsRejected() {
        assertThrows(IllegalStateException.class, () -> NativeEntityTypes.definition(0, "zombie"));
        assertThrows(IllegalStateException.class, () -> NativeEntityTypes.definition(NativeEntityTypes.count(), "zombie"));
        assertEquals("acacia_boat", NativeEntityTypes.definition(0, "acacia_boat").name());
    }
}
