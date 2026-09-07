package net.minecraft.client.dev;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditMenuResourcesTest {
    private static final ResourceLocation ID = ResourceLocation.withDefaultNamespace("textures/gui/menu_background.png");

    @Test
    void fingerprintIncludesBytesPackIdentityAndStackOrder() {
        Resource a = resource("base", new byte[] {1, 2});
        Resource b = resource("override", new byte[] {3});
        var original = GraphicsAuditMenuResources.capture(manager(List.of(a, b)), "en_us");
        assertEquals("complete", original.get("status").getAsString());
        assertEquals(2, original.get("resourceCount").getAsInt());
        assertEquals(3, original.get("bytes").getAsInt());
        assertFalse(original.get("dependencyClosureProven").getAsBoolean());
        for (List<Resource> changed : List.of(List.of(b, a),
                List.of(a, resource("other-pack", new byte[] {3})),
                List.of(a, resource("override", new byte[] {4})))) {
            assertNotEquals(original.get("sha256"),
                GraphicsAuditMenuResources.capture(manager(changed), "en_us").get("sha256"));
        }
        assertEquals(original, GraphicsAuditMenuResources.capture(manager(List.of(a, b)), "en_us"));
    }

    @Test
    void failedReadIsClosedAndCannotProduceSuccessfulPrefix() {
        AtomicBoolean closed = new AtomicBoolean();
        Resource bad = new Resource(pack("broken"), () -> new java.io.InputStream() {
            @Override public int read() throws IOException { throw new IOException("fixture read failure"); }
            @Override public void close() { closed.set(true); }
        });
        var result = GraphicsAuditMenuResources.capture(manager(List.of(resource("good", new byte[] {1}), bad)), "en_us");
        assertEquals("failed", result.get("status").getAsString());
        assertFalse(result.has("sha256"));
        assertFalse(result.has("entries"));
        assertTrue(closed.get());
        assertEquals("failed", GraphicsAuditMenuResources.capture(manager(List.of()), "en_us").get("status").getAsString());
    }

    @Test
    void excessiveStackCountCannotProduceACompleteReceipt() {
        var result = GraphicsAuditMenuResources.capture(manager(java.util.Collections.nCopies(
            16385, resource("base", new byte[0]))), "en_us");
        assertEquals("failed", result.get("status").getAsString());
        assertFalse(result.has("sha256"));
        assertFalse(result.has("entries"));
    }

    @Test
    void metadataOnlyOverrideChangesFingerprintWithoutChangingImageBytes() {
        var image = List.of(resource("base", new byte[] {1, 2}));
        var plain = GraphicsAuditMenuResources.capture(manager(image), "en_us");
        var nearest = GraphicsAuditMenuResources.capture(manager(image,
            List.of(resource("metadata-only-pack", "{\"texture\":{\"blur\":false}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))), "en_us");
        var linear = GraphicsAuditMenuResources.capture(manager(image,
            List.of(resource("metadata-only-pack", "{\"texture\":{\"blur\":true}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))), "en_us");
        assertEquals("complete", linear.get("status").getAsString());
        assertEquals(1, linear.get("metadataQueries").getAsInt());
        assertEquals(2, linear.get("resourceCount").getAsInt());
        assertNotEquals(plain.get("sha256"), nearest.get("sha256"));
        assertNotEquals(nearest.get("sha256"), linear.get("sha256"));
        var entries = linear.getAsJsonArray("entries");
        assertEquals(ID + ".mcmeta", entries.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("metadata-only-pack", entries.get(1).getAsJsonObject().get("pack").getAsString());
    }

    private static Resource resource(String pack, byte[] bytes) {
        return new Resource(pack(pack), () -> new ByteArrayInputStream(bytes));
    }
    private static PackResources pack(String name) {
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
            new Class<?>[] {PackResources.class}, (proxy, method, args) -> {
                if (method.getName().equals("packId")) return name;
                throw new UnsupportedOperationException(method.getName());
            });
    }
    private static ResourceManager manager(List<Resource> stack) {
        return manager(stack, List.of());
    }
    @SuppressWarnings("unchecked")
    private static ResourceManager manager(List<Resource> stack, List<Resource> metadata) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
            new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
                if (method.getName().equals("getResourceStack")) {
                    assertEquals(ID.withPath(ID.getPath() + ".mcmeta"), args[0]);
                    return metadata;
                }
                if (method.getName().equals("listResourceStacks")) {
                    String root = (String) args[0];
                    Predicate<ResourceLocation> filter = (Predicate<ResourceLocation>) args[1];
                    return ID.getPath().startsWith(root + "/") && filter.test(ID) ? Map.of(ID, stack) : Map.of();
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
