package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Post-presentation diagnostics only. Never supplies resources to a renderer. */
public final class GraphicsAuditMenuResources {
    private static final int MAX_RESOURCES = 16384;
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    private GraphicsAuditMenuResources() {}

    public static JsonObject capture(ResourceManager manager, String language) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "mattmc-menu-resource-scope-v2");
        result.addProperty("scope", "gui-font-language-atlas-stacks-with-metadata");
        result.addProperty("dependencyClosureProven", false);
        result.addProperty("language", language);
        try {
            Map<ResourceLocation, List<Resource>> resources = new TreeMap<>();
            for (String root : List.of("textures/gui", "textures/font", "font")) {
                resources.putAll(manager.listResourceStacks(root, id -> true));
            }
            resources.putAll(manager.listResourceStacks("lang",
                id -> id.getPath().equals("lang/en_us.json") || id.getPath().equals("lang/" + language + ".json")));
            resources.putAll(manager.listResourceStacks("atlases", id -> id.getPath().equals("atlases/gui.json")));
            if (resources.isEmpty() || resources.size() > MAX_RESOURCES) {
                throw new IOException("missing or excessive menu resource scope");
            }
            // ResourceManager's directory listing omits metadata sidecars.
            // Resolve their stacks explicitly, including metadata supplied by
            // a higher-priority pack without a replacement image. Do not call
            // Resource.metadata(), which would populate its rendering cache.
            int metadataQueries = 0;
            for (ResourceLocation id : List.copyOf(resources.keySet())) {
                if (id.getPath().endsWith(".mcmeta")) continue;
                ResourceLocation metadataId = id.withPath(id.getPath() + ".mcmeta");
                List<Resource> metadata = manager.getResourceStack(metadataId);
                metadataQueries++;
                if (!metadata.isEmpty()) resources.put(metadataId, metadata);
                if (resources.size() > MAX_RESOURCES) throw new IOException("menu metadata count exceeds budget");
            }
            JsonArray entries = new JsonArray();
            byte[] buffer = new byte[16384];
            long total = 0;
            int count = 0;
            for (var entry : resources.entrySet()) {
                int layer = 0;
                for (Resource resource : entry.getValue()) {
                    if (++count > MAX_RESOURCES) throw new IOException("menu resource stack count exceeds budget");
                    MessageDigest digest = sha256();
                    long bytes = 0;
                    try (var input = resource.open()) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (read == 0) continue;
                            total += read;
                            bytes += read;
                            if (total > MAX_BYTES) throw new IOException("menu resource bytes exceed budget");
                            digest.update(buffer, 0, read);
                        }
                    }
                    JsonObject item = new JsonObject();
                    item.addProperty("id", entry.getKey().toString());
                    item.addProperty("layer", layer++);
                    item.addProperty("pack", resource.sourcePackId());
                    item.addProperty("bytes", bytes);
                    item.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
                    entries.add(item);
                }
            }
            if (count == 0) throw new IOException("empty menu resource stacks");
            result.addProperty("status", "complete");
            result.addProperty("metadataQueries", metadataQueries);
            result.addProperty("resourceCount", count);
            result.addProperty("bytes", total);
            result.addProperty("sha256", HexFormat.of().formatHex(
                sha256().digest(entries.toString().getBytes(StandardCharsets.UTF_8))));
            result.add("entries", entries);
        } catch (IOException | RuntimeException error) {
            result.addProperty("status", "failed");
            result.addProperty("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        return result;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
