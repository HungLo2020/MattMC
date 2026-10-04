package net.vulkanic.world;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandardFoilTextureCacheTest {
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
    private static final ResourceLocation ITEM = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void repeatedConsumersReuseOneCompleteImmutableCopy() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var resources = ResourceProvider.fromMap(Map.of(ITEM, resource(PNG, opens, closes, false, false)));
        var cache = new StandardFoilTextureCache(1024);
        var first = cache.get(resources, ITEM, 123);
        for (int i = 0; i < 100; i++) assertSame(first, cache.get(resources, ITEM, 123));
        assertEquals(1, opens.get());
        assertEquals(1, closes.get());
        var returned = first.pngBytes();
        returned[0] = 0;
        assertArrayEquals(PNG, cache.get(resources, ITEM, 123).pngBytes());
        assertEquals(1, first.samplingFilter());
        assertEquals(1, first.samplingAddress());
        assertEquals(1, first.requestedMipLevels());
    }

    @Test void reloadReadsNewSelectedPixelsAndMetadataAndDoesNotHideMissingResource() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var selected = new HashMap<ResourceLocation, Resource>();
        selected.put(ITEM, resource(PNG, opens, closes, false, false));
        var resources = ResourceProvider.fromMap(selected);
        var cache = new StandardFoilTextureCache(1024);
        var first = cache.get(resources, ITEM, 123);
        var replacementImage = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        replacementImage.setRGB(0, 0, 0xff00ff00);
        var encoded = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(replacementImage, "png", encoded));
        byte[] replacement = encoded.toByteArray();
        selected.put(ITEM, resource(replacement, opens, closes, true, true));
        cache.clear();
        var reloaded = cache.get(resources, ITEM, 123);
        assertNotSame(first, reloaded);
        assertArrayEquals(replacement, reloaded.pngBytes());
        assertEquals(2, reloaded.samplingFilter());
        assertEquals(2, reloaded.samplingAddress());
        assertEquals(1, reloaded.requestedMipLevels());
        assertArrayEquals(PNG, first.pngBytes(), "already selected immutable assets remain valid");
        cache.clear();
        selected.clear();
        assertThrows(java.io.FileNotFoundException.class, () -> cache.get(resources, ITEM, 123));
        assertEquals(2, opens.get());
        assertEquals(2, closes.get());
    }

    @Test void twoResourceIdentitiesDoNotMixTheirSamplingContracts() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var resources = ResourceProvider.fromMap(Map.of(
            ITEM, resource(PNG, opens, closes, false, false),
            ARMOR, resource(PNG, opens, closes, true, true)));
        var cache = new StandardFoilTextureCache(1024);
        var item = cache.get(resources, ITEM, 123);
        var armor = cache.get(resources, ARMOR, 456);
        assertEquals(123, item.textureId());
        assertEquals(456, armor.textureId());
        assertEquals(1, item.samplingFilter());
        assertEquals(2, armor.samplingFilter());
        assertSame(item, cache.get(resources, ITEM, 123));
        assertSame(armor, cache.get(resources, ARMOR, 456));
        assertEquals(2, opens.get());
    }

    @Test void actualRendererReloadInvalidatesTheFoilCopy() throws Exception {
        var field = RustGalWorldPrimitiveRenderer.class.getDeclaredField("STANDARD_FOIL_TEXTURES");
        field.setAccessible(true);
        var cache = (StandardFoilTextureCache) field.get(null);
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var resources = ResourceProvider.fromMap(Map.of(ITEM, resource(PNG, opens, closes, false, false)));
        cache.clear();
        try {
            cache.get(resources, ITEM, 123);
            RustGalWorldPrimitiveRenderer.reloadWorldAssets(null);
            assertThrows(java.io.FileNotFoundException.class, () -> cache.get(ResourceProvider.EMPTY, ITEM, 123),
                "reload must consult the newly selected resources even if an older foil was copied");
            cache.get(resources, ITEM, 123);
            assertEquals(2, opens.get());
            assertEquals(2, closes.get());
        } finally {
            cache.clear();
        }
    }

    @Test void unreadableMetadataClosesPixelsAndRemainsRetryable() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var readable = resource(PNG, opens, closes, false, false);
        var broken = new Resource(pack(), readable::open, () -> { throw new IOException("broken selected metadata"); });
        var selected = new HashMap<ResourceLocation, Resource>();
        selected.put(ITEM, broken);
        var resources = ResourceProvider.fromMap(selected);
        var cache = new StandardFoilTextureCache(1024);
        assertThrows(IOException.class, () -> cache.get(resources, ITEM, 123));
        assertThrows(IOException.class, () -> cache.get(resources, ITEM, 123));
        assertEquals(2, closes.get());
        selected.put(ITEM, readable);
        var recovered = cache.get(resources, ITEM, 123);
        assertSame(recovered, cache.get(resources, ITEM, 123));
        assertEquals(3, opens.get());
    }

    @Test void encodedSizeBoundRejectsOversizeWithoutCachingAndClosesStream() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var selected = new HashMap<ResourceLocation, Resource>();
        selected.put(ITEM, resource(new byte[PNG.length + 1], opens, closes, false, false));
        var resources = ResourceProvider.fromMap(selected);
        var cache = new StandardFoilTextureCache(PNG.length);
        assertThrows(IOException.class, () -> cache.get(resources, ITEM, 123));
        assertEquals(1, closes.get());
        selected.put(ITEM, resource(PNG, opens, closes, false, false));
        assertArrayEquals(PNG, cache.get(resources, ITEM, 123).pngBytes());
        assertEquals(2, opens.get());
    }

    @Test void thirdIdentityEvictsLeastRecentlyUsedWhileTextureIdChangesRequireNewCopy() throws Exception {
        var opens = new AtomicInteger();
        var closes = new AtomicInteger();
        var third = ResourceLocation.withDefaultNamespace("textures/misc/other_foil.png");
        var resource = resource(PNG, opens, closes, false, false);
        var resources = ResourceProvider.fromMap(Map.of(ITEM, resource, ARMOR, resource, third, resource));
        var cache = new StandardFoilTextureCache(1024);
        var item = cache.get(resources, ITEM, 123);
        var armor = cache.get(resources, ARMOR, 456);
        assertSame(item, cache.get(resources, ITEM, 123));
        cache.get(resources, third, 789);
        assertSame(item, cache.get(resources, ITEM, 123));
        assertNotSame(armor, cache.get(resources, ARMOR, 456));
        assertEquals(4, opens.get());
        var remapped = cache.get(resources, ITEM, 321);
        assertEquals(321, remapped.textureId());
        assertNotSame(item, remapped);
        assertEquals(5, opens.get());
        assertEquals(opens.get(), closes.get());
    }

    private static Resource resource(byte[] bytes, AtomicInteger opens, AtomicInteger closes, boolean blur, boolean clamp) {
        return new Resource(pack(), () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(bytes) {
                @Override public void close() { closes.incrementAndGet(); }
            };
        }, () -> ResourceMetadata.fromJsonStream(new ByteArrayInputStream(
            ("{\"texture\":{\"blur\":" + blur + ",\"clamp\":" + clamp + "}}").getBytes(StandardCharsets.UTF_8))));
    }

    private static PackResources pack() {
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
            (proxy, method, args) -> {
                if (method.getName().equals("packId")) return "selected-pack";
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
