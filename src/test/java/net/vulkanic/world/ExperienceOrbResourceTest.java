package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.NativePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ExperienceOrbResourceTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static PackResources pack(Path root, String name, ResourceLocation identity, byte[] bytes) throws Exception {
        Path file = root.resolve(name).resolve("assets").resolve(identity.getNamespace()).resolve(identity.getPath());
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return NativePackResources.directory(
            new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty()), root.resolve(name), List.of());
    }

    @Test void vanillaAndOverridesBothCopyTheResolvedResourceWithoutRowConversion(@TempDir Path root) throws Exception {
        var identity = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
        byte[] base = {1,2,3,4}, replacement = {5,6,7,8};
        var vanilla = pack(root, "vanilla", identity, base);
        var override = pack(root, "override", identity, replacement);
        for (var packs : List.of(List.of(vanilla),List.of(vanilla,override),List.of(vanilla))) {
            var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs);
            var resource = manager.getResource(identity).orElseThrow();
            var asset = RustGalWorldPrimitiveRenderer.copyExperienceOrbTexture(resource);
            assertArrayEquals(packs.size()==1 ? base : replacement,asset.pngBytes());
            assertEquals(0x4f524233,asset.textureId());
            assertEquals(1,asset.requestedMipLevels());
        }
    }
}
