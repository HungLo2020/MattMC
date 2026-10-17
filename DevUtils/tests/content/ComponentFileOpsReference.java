import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapLike;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.DelegatingOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.decoration.PaintingVariant;

/** Unchanged three-way observer for custom component ops in definition files.
 * Frozen has no native file entry; its original codec parses the file's JSON.
 * Arguments: output file. Each route starts fresh counters and input.
 */
public final class ComponentFileOpsReference {
    private ComponentFileOpsReference() {}

    static final class ComponentOps extends DelegatingOps<JsonElement> {
        final boolean mapError, listError;
        int maps, lists;
        ComponentOps(boolean mapError, boolean listError) {
            super(JsonOps.INSTANCE);
            this.mapError = mapError;
            this.listError = listError;
        }
        @Override public DataResult<MapLike<JsonElement>> getMap(JsonElement input) {
            var result = super.getMap(input);
            if (!input.isJsonObject() || !input.getAsJsonObject().has("nutrition")) return result;
            maps++;
            return mapError ? result.flatMap(map -> DataResult.error(() -> "custom food map error", map, Lifecycle.deprecated(1))) : result;
        }
        @Override public DataResult<Consumer<Consumer<JsonElement>>> getList(JsonElement input) {
            var result = super.getList(input);
            if (!input.isJsonArray()) return result;
            lists++;
            return listError ? result.flatMap(list -> DataResult.error(() -> "custom bee list error", list, Lifecycle.deprecated(2))) : result;
        }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Codec<PaintingVariant> codec = PaintingVariant.DIRECT_CODEC;
        var rows = new ArrayList<String>();
        for (boolean mapError : new boolean[]{false, true}) for (boolean listError : new boolean[]{false, true}) {
            for (String[] component : new String[][]{{"food", "{\"nutrition\":4,\"saturation\":1}"}, {"bees", "[]"}}) {
                String source = "{\"width\":2,\"height\":3,\"asset_id\":\"minecraft:a\",\"title\":{\"text\":\"a\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:stick\",\"components\":{\"minecraft:"
                    + component[0] + "\":" + component[1] + "}}}}";
                for (String route : new String[]{"tree", "file"}) {
                    var custom = new ComponentOps(mapError, listError);
                    var ops = RegistryOps.create(custom, registries);
                    DataResult<?> decoded;
                    try {
                        decoded = route.equals("file") ? (DataResult<?>)codec.getClass()
                            .getMethod("decodeFile", RegistryOps.class, byte[].class).invoke(codec, ops, source.getBytes(StandardCharsets.UTF_8))
                            : codec.parse(ops, JsonParser.parseString(source));
                    } catch (NoSuchMethodException missingNativeEntry) {
                        decoded = codec.parse(ops, JsonParser.parseString(source));
                    }
                    rows.add(component[0] + " map_error=" + mapError + " list_error=" + listError + " route=" + route
                        + " error=" + decoded.error().map(DataResult.Error::message) + " partial=" + decoded.hasResultOrPartial()
                        + " lifecycle=" + decoded.lifecycle() + " maps=" + custom.maps + " lists=" + custom.lists);
                }
            }
        }
        Files.write(Path.of(args[0]), rows);
        System.out.println("COMPONENT_FILE_OPS_REFERENCE rows=" + rows.size());
    }
}
