package net.vulkanic.shaderpack;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shaderpack.IrisDefines;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.OptionType;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.vulkanic.shaderpack.RustShaderPackSourceCollector.SourceGeneration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Iris-only source preparation for the borrowed OpenGL compatibility route.
 *
 * <p>The Rust whole-frame collector deliberately has no Iris class dependency;
 * this adapter is reachable only after that route has been ruled out.</p>
 */
final class IrisShaderPackCompatibilityCollector {
    private static final int MAX_COMPATIBILITY_ENTRIES = 4_096;
    private IrisShaderPackCompatibilityCollector() {
    }

    static SourceGeneration collectConfiguredPack(long generation) throws IOException {
        ensureJavaIrisCompatibilityAvailable();
        if (!Iris.getIrisConfig().areShadersEnabled()) {
            return RustShaderPackSourceCollector.disabled(generation);
        }
        Optional<String> configuredName = Iris.getIrisConfig().getShaderPackName();
        if (configuredName.isEmpty()) {
            return RustShaderPackSourceCollector.disabled(generation);
        }
        String packName = configuredName.get();
        Path shaderpacks = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
        Path pack = shaderpacks.resolve(packName).normalize();
        if (!pack.startsWith(shaderpacks) || !pack.getFileName().toString().equals(packName)) {
            throw new IOException("configured shader-pack path escapes shaderpacks directory");
        }
        SourceGeneration source;
        if (Files.isDirectory(pack)) {
            source = RustShaderPackSourceCollector.collectWithAssets(pack.resolve("shaders"), packName, generation);
        } else {
            try (FileSystem archive = FileSystems.newFileSystem(pack)) {
                source = RustShaderPackSourceCollector.collectWithAssets(archive.getPath("/shaders"), packName, generation);
            }
        }
        ShaderPack resolvedPack = Iris.getCurrentPack().orElseThrow(() ->
            new IOException("configured shader pack disappeared during source collection"));
        SourceGeneration merged = RustShaderPackSourceCollector.withActiveVanillaPostEffectResources(source);
        return withConfiguredSemanticSnapshots(withResolvedIrisSourceSnapshot(merged, resolvedPack), resolvedPack);
    }

    static Optional<String> activeConfiguredPackName() {
        ensureJavaIrisCompatibilityAvailable();
        if (!Iris.getIrisConfig().areShadersEnabled() || Iris.getCurrentPack().isEmpty()) {
            return Optional.empty();
        }
        return Iris.getIrisConfig().getShaderPackName()
            .filter(name -> !name.isBlank())
            .map(name -> RustShaderPackSourceCollector.sourceGenerationKey(
                name, activeVanillaPostEffectId()
            ));
    }

    private static void ensureJavaIrisCompatibilityAvailable() {
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException("Java Iris shader-pack compatibility collection is unavailable on the Rust Vulkan route");
        }
    }

    private static Optional<String> activeVanillaPostEffectId() {
        try {
            var effect = net.minecraft.client.Minecraft.getInstance().gameRenderer.currentPostEffect();
            if (effect == null) return Optional.empty();
            String identity = effect.toString();
            return switch (identity) {
                case "minecraft:invert", "minecraft:creeper", "minecraft:spider" -> Optional.empty();
                default -> Optional.of(identity);
            };
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static SourceGeneration withConfiguredSemanticSnapshots(SourceGeneration source, ShaderPack pack) throws IOException {
        return RustShaderPackSourceCollector.withRuntimeBlockStateIdentitySnapshot(
            RustShaderPackSourceCollector.withRuntimeEnvironmentSnapshot(
                withConfiguredOptionSnapshot(source, pack), configuredEnvironmentDefines(pack)
            )
        );
    }

    private static SourceGeneration withConfiguredOptionSnapshot(SourceGeneration source, ShaderPack pack) throws IOException {
        OptionSet optionSet = pack.getShaderPackOptions().getOptionSet();
        OptionValues values = pack.getShaderPackOptions().getOptionValues();
        TreeMap<String, String> options = new TreeMap<>();
        TreeMap<String, String> constants = new TreeMap<>();
        optionSet.getBooleanOptions().forEach((name, option) -> {
            if (!RustShaderPackSourceCollector.isShaderStageSelector(name)) {
                var selected = values.getBooleanValue(name);
                if (option.getOption().getType() == OptionType.CONST) {
                    if (selected != net.irisshaders.iris.helpers.OptionalBoolean.DEFAULT) {
						putBounded(constants, name, Boolean.toString(values.getBooleanValueOrDefault(name)));
                    }
                } else if (selected != net.irisshaders.iris.helpers.OptionalBoolean.DEFAULT) {
					if (options.size() >= MAX_COMPATIBILITY_ENTRIES && !options.containsKey(name)) {
						throw new IllegalStateException("shader-pack compatibility option count exceeds " + MAX_COMPATIBILITY_ENTRIES);
					}
					RustShaderPackSourceCollector.putEnabledBooleanOption(options, name, values.getBooleanValueOrDefault(name));
                }
            }
        });
        optionSet.getStringOptions().forEach((name, option) -> {
            if (!RustShaderPackSourceCollector.isShaderStageSelector(name)) {
                var selected = values.getStringValue(name);
                String resolved = values.getStringValueOrDefault(name);
				if (option.getOption().getType() == OptionType.CONST) selected.ifPresent(value -> putBounded(constants, name, value));
				else putBounded(options, name, resolved);
            }
        });
        return RustShaderPackSourceCollector.withRuntimeConstantSnapshot(
            RustShaderPackSourceCollector.withRuntimeOptionSnapshot(source, options), constants
        );
    }

    private static SourceGeneration withResolvedIrisSourceSnapshot(SourceGeneration source, ShaderPack pack) throws IOException {
        Map<String, byte[]> resolvedSources = new TreeMap<>();
        pack.getShaderPackOptions().getIncludes().getNodes().forEach((path, node) -> {
            if (resolvedSources.size() >= RustShaderPackSourceCollector.MAX_FILES) {
                throw new IllegalStateException("shader-pack resolved include count exceeds "
                    + RustShaderPackSourceCollector.MAX_FILES);
            }
            String relative = relativeShaderPath(path);
            resolvedSources.put(relative, (String.join("\n", node.getLines()) + "\n").getBytes(StandardCharsets.UTF_8));
        });
        return RustShaderPackSourceCollector.withResolvedSourceSnapshot(source, resolvedSources);
    }

    private static String relativeShaderPath(AbsolutePackPath path) {
        String value = path.getPathString().replace('\\', '/');
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static TreeMap<String, String> configuredEnvironmentDefines(ShaderPack pack) throws IOException {
        TreeMap<String, String> defines = new TreeMap<>();
        for (var define : IrisDefines.createIrisReplacements()) putEnvironmentDefine(defines, define.key(), define.value());
        for (FeatureFlags feature : FeatureFlags.values()) {
            if (feature.isUsable() || pack.hasFeature(feature)) putEnvironmentDefine(defines, "IRIS_FEATURE_" + feature.name(), "");
        }
        for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
            putEnvironmentDefine(defines, "MC_RENDER_STAGE_" + phase.name(), Integer.toString(phase.ordinal()));
        }
        return defines;
    }

    private static void putEnvironmentDefine(TreeMap<String, String> defines, String name, String value) throws IOException {
        String normalizedValue = value == null || value.isEmpty() ? "1" : value;
        String previous = defines.putIfAbsent(name, normalizedValue);
        if (previous != null && !previous.equals(normalizedValue)) throw new IOException("conflicting shader-pack environment define: " + name);
    }

    private static void putBounded(TreeMap<String, String> values, String name, String value) {
        if (!values.containsKey(name) && values.size() >= MAX_COMPATIBILITY_ENTRIES) {
            throw new IllegalStateException("shader-pack compatibility entry count exceeds " + MAX_COMPATIBILITY_ENTRIES);
        }
        values.put(name, value);
    }
}
