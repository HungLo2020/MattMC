import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.phys.Vec3;

/**
 * Dumps every registered entity type's built definition, in registry order.
 * Compiled unchanged against Frozen and Current; reads the EntityType fields
 * reflectively so both builds report the same facts. Optional argument: a file
 * to write the full dump to.
 */
public final class EntityTypeReference {
    private EntityTypeReference() {}

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

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        long began = System.nanoTime();
        net.minecraft.server.Bootstrap.bootStrap();
        long bootstrap = System.nanoTime() - began;
        var rows = dump();
        var text = String.join("\n", rows) + "\n";
        if (args.length > 0) Files.writeString(Path.of(args[0]), text);
        var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        System.out.println("ENTITY_TYPE_REFERENCE types=" + rows.size() + " sha256=" + digest + " bootstrap_ns=" + bootstrap);
    }
}
