package net.vulkanic.shaderpack;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.IrisDefines;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.OptionType;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Bounded filesystem extraction of semantic shader-pack source files. */
public final class RustShaderPackSourceCollector {
	public static final int MAX_FILES = 4096;
	public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
	public static final long MAX_TOTAL_BYTES = 64L * 1024L * 1024L;
	public static final int MAX_ASSET_FILES = 4096;
	public static final int MAX_ASSET_FILE_BYTES = 32 * 1024 * 1024;
	public static final long MAX_ASSET_TOTAL_BYTES = 256L * 1024L * 1024L;
	/** Reserved Rust-owned semantic source-config path. */
	public static final String RUNTIME_OPTIONS_PATH = "mattmc/runtime-options.properties";
	/** Reserved Rust-owned typed source-constant configuration path. */
	public static final String RUNTIME_CONSTANTS_PATH = "mattmc/runtime-constants.properties";
	/** Reserved Rust-owned semantic source-environment path. */
	public static final String RUNTIME_ENVIRONMENT_PATH = "mattmc/runtime-environment.properties";
	/** Reserved Rust-owned table of canonical Minecraft block-state identities. */
	public static final String RUNTIME_BLOCK_STATE_IDENTITIES_PATH = "mattmc/runtime-block-states.properties";

	private RustShaderPackSourceCollector() {
	}

	public static SourceGeneration collectConfiguredPack(long generation) throws IOException {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			if (!wholeFrameShaderConfigEnabled()) {
				return disabled(generation);
			}
			throw new IOException("configured Iris shader packs are unavailable until their Rust-owned source configuration collector is complete");
		}
		if (!Iris.getIrisConfig().areShadersEnabled()) {
			return disabled(generation);
		}
		Optional<String> configuredName = Iris.getIrisConfig().getShaderPackName();
		if (configuredName.isEmpty()) {
			return disabled(generation);
		}
		String packName = configuredName.get();
		Path shaderpacks = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
		Path pack = shaderpacks.resolve(packName).normalize();
		if (!pack.startsWith(shaderpacks) || !pack.getFileName().toString().equals(packName)) {
			throw new IOException("configured shader-pack path escapes shaderpacks directory");
		}
		SourceGeneration source;
		if (Files.isDirectory(pack)) {
			source = collectWithAssets(pack.resolve("shaders"), packName, generation);
		} else {
			try (FileSystem archive = FileSystems.newFileSystem(pack)) {
				source = collectWithAssets(archive.getPath("/shaders"), packName, generation);
			}
		}
		ShaderPack resolvedPack = Iris.getCurrentPack().orElseThrow(() ->
			new IOException("configured shader pack disappeared during source collection"));
		return withConfiguredSemanticSnapshots(withResolvedIrisSourceSnapshot(source, resolvedPack), resolvedPack);
	}

	/**
	 * Returns the configured pack name only after Iris has completed its own
	 * pack activation. This exposes configuration readiness only: callers use
	 * it to defer one bounded semantic source collection until startup can
	 * produce a complete immutable snapshot.
	 */
	public static Optional<String> activeConfiguredPackName() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			// Do not touch Iris configuration or resolved-pack objects after Rust
			// owns presentation. A configured pack is deliberately unadmitted until
			// the Rust-owned source configuration path can represent it completely.
			return Optional.empty();
		}
		if (!Iris.getIrisConfig().areShadersEnabled() || Iris.getCurrentPack().isEmpty()) {
			return Optional.empty();
		}
		return Iris.getIrisConfig().getShaderPackName().filter(name -> !name.isBlank());
	}

	/** Reads only the copied on-disk preference used to reject unported packs. */
	private static boolean wholeFrameShaderConfigEnabled() throws IOException {
		Path config = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
			.resolve("config").resolve("iris.properties");
		if (!Files.isRegularFile(config)) {
			return true;
		}
		Properties values = new Properties();
		try (var input = Files.newInputStream(config)) {
			values.load(input);
		}
		return !"false".equals(values.getProperty("enableShaders"));
	}

	public static SourceGeneration disabled(long generation) {
		if (generation <= 0L) {
			throw new IllegalArgumentException("shader-pack source generation must be positive");
		}
		return new SourceGeneration("disabled", generation, List.of(), 0L, List.of(), 0L);
	}

	public static SourceGeneration collect(Path shaderRoot, String packName, long generation) throws IOException {
		Objects.requireNonNull(shaderRoot, "shaderRoot");
		Objects.requireNonNull(packName, "packName");
		if (generation <= 0L) {
			throw new IllegalArgumentException("shader-pack source generation must be positive");
		}
		if (packName.isBlank()) {
			throw new IllegalArgumentException("shader-pack source pack name is empty");
		}
		Path normalizedRoot = shaderRoot.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalizedRoot)) {
			throw new IOException("shader-pack source root is not a directory: " + normalizedRoot);
		}

		List<Path> paths;
		try (Stream<Path> files = Files.walk(normalizedRoot)) {
			paths = files
				.filter(Files::isRegularFile)
				.filter(RustShaderPackSourceCollector::isSourceFile)
				.sorted(Comparator.comparing(path -> normalizedRoot.relativize(path).toString()))
				.toList();
		}
		if (paths.size() > MAX_FILES) {
			throw new IOException("shader-pack source file count exceeds " + MAX_FILES);
		}

		long totalBytes = 0L;
		List<VulkanicGalBridge.ShaderPackSourceFileRecord> files = new java.util.ArrayList<>(paths.size());
		for (Path path : paths) {
			Path relative = normalizedRoot.relativize(path).normalize();
			if (relative.isAbsolute() || relative.startsWith("..")) {
				throw new IOException("shader-pack source path escapes root: " + path);
			}
			long size = Files.size(path);
			if (size > MAX_FILE_BYTES) {
				throw new IOException("shader-pack source file exceeds " + MAX_FILE_BYTES + " bytes: " + relative);
			}
			totalBytes = Math.addExact(totalBytes, size);
			if (totalBytes > MAX_TOTAL_BYTES) {
				throw new IOException("shader-pack source payload exceeds " + MAX_TOTAL_BYTES + " bytes");
			}
			files.add(new VulkanicGalBridge.ShaderPackSourceFileRecord(
				relative.toString().replace('\\', '/'),
				Files.readAllBytes(path)
			));
		}
		return new SourceGeneration(packName, generation, List.copyOf(files), totalBytes, List.of(), 0L);
	}

	/**
	 * Collects source text and binary assets from the same shader-relative root.
	 * Asset paths retain that root-relative identity so source declarations can
	 * refer to them without leaking host paths, archives, or Iris resources.
	 */
	static SourceGeneration collectWithAssets(Path shaderRoot, String packName, long generation) throws IOException {
		SourceGeneration source = collect(shaderRoot, packName, generation);
		Path normalizedRoot = shaderRoot.toAbsolutePath().normalize();
		List<Path> paths;
		try (Stream<Path> files = Files.walk(normalizedRoot)) {
			paths = files
				.filter(Files::isRegularFile)
				.filter(RustShaderPackSourceCollector::isAssetFile)
				.sorted(Comparator.comparing(path -> normalizedRoot.relativize(path).toString()))
				.toList();
		}
		if (paths.size() > MAX_ASSET_FILES) {
			throw new IOException("shader-pack asset file count exceeds " + MAX_ASSET_FILES);
		}

		long totalBytes = 0L;
		List<VulkanicGalBridge.ShaderPackAssetFileRecord> assets = new java.util.ArrayList<>(paths.size());
		for (Path path : paths) {
			Path relative = normalizedRoot.relativize(path).normalize();
			if (relative.isAbsolute() || relative.startsWith("..")) {
				throw new IOException("shader-pack asset path escapes root: " + path);
			}
			long size = Files.size(path);
			if (size > MAX_ASSET_FILE_BYTES) {
				throw new IOException("shader-pack asset exceeds " + MAX_ASSET_FILE_BYTES + " bytes: " + relative);
			}
			totalBytes = Math.addExact(totalBytes, size);
			if (totalBytes > MAX_ASSET_TOTAL_BYTES) {
				throw new IOException("shader-pack asset payload exceeds " + MAX_ASSET_TOTAL_BYTES + " bytes");
			}
			assets.add(new VulkanicGalBridge.ShaderPackAssetFileRecord(
				relative.toString().replace('\\', '/'),
				Files.readAllBytes(path)
			));
		}
		return new SourceGeneration(
			source.packName(),
			source.generation(),
			source.files(),
			source.totalBytes(),
			List.copyOf(assets),
			totalBytes
		);
	}

	private static boolean isSourceFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		return name.endsWith(".vsh")
			|| name.endsWith(".fsh")
			|| name.endsWith(".gsh")
			|| name.endsWith(".csh")
			|| name.endsWith(".glsl")
			|| name.endsWith(".properties");
	}

	private static boolean isAssetFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		return name.endsWith(".png")
			|| name.endsWith(".jpg")
			|| name.endsWith(".jpeg")
			|| name.endsWith(".bmp")
			|| name.endsWith(".tga")
			|| name.endsWith(".dds")
			|| name.endsWith(".ktx")
			|| name.endsWith(".ktx2")
			|| name.endsWith(".exr")
			|| name.endsWith(".raw")
			|| name.endsWith(".bin")
			|| name.endsWith(".dat")
			|| name.endsWith(".mcmeta")
			|| name.endsWith(".json");
	}

	/**
	 * Copies only the resolved scalar option values that affect source
	 * preprocessing. The Iris option objects are never retained or passed over
	 * FFI; Rust receives one immutable, bounded properties payload alongside
	 * the selected pack sources.
	 */
	private static SourceGeneration withConfiguredOptionSnapshot(SourceGeneration source, ShaderPack pack) throws IOException {
		OptionSet optionSet = pack.getShaderPackOptions().getOptionSet();
		OptionValues values = pack.getShaderPackOptions().getOptionValues();
		TreeMap<String, String> options = new TreeMap<>();
		TreeMap<String, String> constants = new TreeMap<>();
		// Shader sources are copied from Iris's resolved include graph. Resolved
		// string options are transported because a stage may use one without
		// transitively including its declaration. Boolean defines are different:
		// Iris discovers source-derived feature gates as BooleanOption entries too.
		// Sending their defaults back as external definitions changes the source
		// conditional graph, so only an explicit user boolean selection is sent.
		// Typed GLSL consts remain separate from macro options.
		optionSet.getBooleanOptions().forEach((name, option) -> {
			if (!isShaderStageSelector(name)) {
				var selected = values.getBooleanValue(name);
				if (option.getOption().getType() == OptionType.CONST) {
					if (selected != net.irisshaders.iris.helpers.OptionalBoolean.DEFAULT) {
						constants.put(name, Boolean.toString(values.getBooleanValueOrDefault(name)));
					}
				} else if (selected != net.irisshaders.iris.helpers.OptionalBoolean.DEFAULT) {
					putEnabledBooleanOption(options, name, values.getBooleanValueOrDefault(name));
				}
			}
		});
		optionSet.getStringOptions().forEach((name, option) ->
			{
				if (!isShaderStageSelector(name)) {
					var selected = values.getStringValue(name);
					String resolved = values.getStringValueOrDefault(name);
					if (option.getOption().getType() == OptionType.CONST) {
						selected.ifPresent(value -> constants.put(name, value));
					} else {
						options.put(name, resolved);
					}
				}
			});
		return withRuntimeConstantSnapshot(withRuntimeOptionSnapshot(source, options), constants);
	}

	static boolean isShaderStageSelector(String name) {
		return switch (Objects.requireNonNull(name, "name")) {
			case "VERTEX_SHADER", "FRAGMENT_SHADER", "GEOMETRY_SHADER", "COMPUTE_SHADER" -> true;
			default -> false;
		};
	}

	static void putEnabledBooleanOption(Map<String, String> options, String name, boolean enabled) {
		Objects.requireNonNull(options, "options");
		Objects.requireNonNull(name, "name");
		if (enabled) {
			options.put(name, "1");
		}
	}

	private static SourceGeneration withConfiguredSemanticSnapshots(SourceGeneration source, ShaderPack pack) throws IOException {
		return withRuntimeBlockStateIdentitySnapshot(withRuntimeEnvironmentSnapshot(
			withConfiguredOptionSnapshot(source, pack),
			configuredEnvironmentDefines(pack)
		));
	}

	/**
	 * Replaces raw shader files with the configured include graph's immutable
	 * source. This is a source/configuration snapshot only: it copies no Iris
	 * program, framebuffer, texture, callback, or native handle.
	 */
	static SourceGeneration withResolvedIrisSourceSnapshot(SourceGeneration source, ShaderPack pack) throws IOException {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(pack, "pack");
		Map<String, byte[]> resolvedSources = new TreeMap<>();
		pack.getShaderPackOptions().getIncludes().getNodes().forEach((path, node) -> {
			String relative = relativeShaderPath(path);
			resolvedSources.put(relative, (String.join("\n", node.getLines()) + "\n").getBytes(StandardCharsets.UTF_8));
		});
		return withResolvedSourceSnapshot(source, resolvedSources);
	}

	static SourceGeneration withResolvedSourceSnapshot(SourceGeneration source, Map<String, byte[]> resolvedSources) throws IOException {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(resolvedSources, "resolvedSources");
		long totalBytes = 0L;
		List<VulkanicGalBridge.ShaderPackSourceFileRecord> files = new java.util.ArrayList<>(source.files().size());
		for (VulkanicGalBridge.ShaderPackSourceFileRecord file : source.files()) {
			byte[] contents = resolvedSources.getOrDefault(file.path(), file.contentsUtf8());
			if (contents.length > MAX_FILE_BYTES) {
				throw new IOException("resolved shader-pack source file exceeds " + MAX_FILE_BYTES + " bytes: " + file.path());
			}
			totalBytes = Math.addExact(totalBytes, contents.length);
			if (totalBytes > MAX_TOTAL_BYTES) {
				throw new IOException("resolved shader-pack source payload exceeds " + MAX_TOTAL_BYTES + " bytes");
			}
			files.add(new VulkanicGalBridge.ShaderPackSourceFileRecord(file.path(), contents));
		}
		return new SourceGeneration(
			source.packName(), source.generation(), List.copyOf(files), totalBytes, source.assets(), source.assetTotalBytes()
		);
	}

	private static String relativeShaderPath(AbsolutePackPath path) {
		String value = path.getPathString().replace('\\', '/');
		return value.startsWith("/") ? value.substring(1) : value;
	}

	/**
	 * Copies immutable game semantics, not Iris's resolved material map. Rust
	 * owns the selected-pack parser and resolves these identities against its
	 * own source-derived block-property contract.
	 */
	static SourceGeneration withRuntimeBlockStateIdentitySnapshot(SourceGeneration source) throws IOException {
		Objects.requireNonNull(source, "source");
		StringBuilder properties = new StringBuilder();
		for (Block block : BuiltInRegistries.BLOCK) {
			String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				int rawStateId = Block.getId(state);
				if (rawStateId < 0) {
					throw new IOException("Minecraft block state has no raw registry identity: " + state);
				}
				properties.append("state.").append(rawStateId).append('=').append(blockId);
				for (Property<?> property : state.getProperties().stream()
					.sorted(java.util.Comparator.comparing(Property::getName)).toList()) {
					properties.append('|').append(property.getName()).append('=')
						.append(propertyValueName(state, property));
				}
				properties.append('\n');
			}
		}
		byte[] contents = properties.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (contents.length > MAX_FILE_BYTES) {
			throw new IOException("shader-pack runtime block-state payload exceeds " + MAX_FILE_BYTES + " bytes");
		}
		return appendReservedProperties(source, RUNTIME_BLOCK_STATE_IDENTITIES_PATH, contents, "block-state identity");
	}

	private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static TreeMap<String, String> configuredEnvironmentDefines(ShaderPack pack) throws IOException {
		TreeMap<String, String> defines = new TreeMap<>();
		for (var define : IrisDefines.createIrisReplacements()) {
			putEnvironmentDefine(defines, define.key(), define.value());
		}
		for (FeatureFlags feature : FeatureFlags.values()) {
			if (feature.isUsable()) {
				putEnvironmentDefine(defines, "IRIS_FEATURE_" + feature.name(), "");
			}
		}
		for (FeatureFlags feature : FeatureFlags.values()) {
			if (pack.hasFeature(feature)) {
				putEnvironmentDefine(defines, "IRIS_FEATURE_" + feature.name(), "");
			}
		}
		// These are pack preprocessor semantics copied from the stable render
		// phase enumeration. They do not expose an active Iris phase, program,
		// or renderer state; Rust owns pass selection after source lowering.
		for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
			putEnvironmentDefine(defines, "MC_RENDER_STAGE_" + phase.name(), Integer.toString(phase.ordinal()));
		}
		return defines;
	}

	private static void putEnvironmentDefine(TreeMap<String, String> defines, String name, String value) throws IOException {
		String normalizedValue = value == null || value.isEmpty() ? "1" : value;
		String previous = defines.putIfAbsent(name, normalizedValue);
		if (previous != null && !previous.equals(normalizedValue)) {
			throw new IOException("conflicting shader-pack environment define: " + name);
		}
	}

	static SourceGeneration withRuntimeOptionSnapshot(SourceGeneration source, java.util.Map<String, String> options) throws IOException {
		return withRuntimeScalarSnapshot(source, options, RUNTIME_OPTIONS_PATH, "option");
	}

	static SourceGeneration withRuntimeConstantSnapshot(SourceGeneration source, java.util.Map<String, String> constants) throws IOException {
		return withRuntimeScalarSnapshot(source, constants, RUNTIME_CONSTANTS_PATH, "constant");
	}

	private static SourceGeneration withRuntimeScalarSnapshot(
		SourceGeneration source,
		java.util.Map<String, String> values,
		String reservedPath,
		String kind
	) throws IOException {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(values, "values");
		StringBuilder properties = new StringBuilder();
		for (var entry : new TreeMap<>(values).entrySet()) {
			validateOptionEntry(entry.getKey(), entry.getValue());
			properties.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		byte[] contents = properties.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (contents.length > MAX_FILE_BYTES) {
			throw new IOException("shader-pack runtime option payload exceeds " + MAX_FILE_BYTES + " bytes");
		}
		return appendReservedProperties(source, reservedPath, contents, kind);
	}

	static SourceGeneration withRuntimeEnvironmentSnapshot(SourceGeneration source, java.util.Map<String, String> defines) throws IOException {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(defines, "defines");
		StringBuilder properties = new StringBuilder();
		for (var entry : new TreeMap<>(defines).entrySet()) {
			String value = entry.getValue() == null || entry.getValue().isEmpty() ? "1" : entry.getValue();
			validateOptionEntry(entry.getKey(), value);
			properties.append(entry.getKey()).append('=').append(value).append('\n');
		}
		byte[] contents = properties.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (contents.length > MAX_FILE_BYTES) {
			throw new IOException("shader-pack runtime environment payload exceeds " + MAX_FILE_BYTES + " bytes");
		}
		return appendReservedProperties(source, RUNTIME_ENVIRONMENT_PATH, contents, "environment");
	}

	private static SourceGeneration appendReservedProperties(
		SourceGeneration source,
		String reservedPath,
		byte[] contents,
		String kind
	) throws IOException {
		List<VulkanicGalBridge.ShaderPackSourceFileRecord> files = new java.util.ArrayList<>(source.files());
		if (files.stream().anyMatch(file -> reservedPath.equals(file.path()))) {
			throw new IOException("shader-pack source reserves " + reservedPath);
		}
		long totalBytes = Math.addExact(source.totalBytes(), contents.length);
		if (totalBytes > MAX_TOTAL_BYTES) {
			throw new IOException("shader-pack source and " + kind + " payload exceed " + MAX_TOTAL_BYTES + " bytes");
		}
		files.add(new VulkanicGalBridge.ShaderPackSourceFileRecord(reservedPath, contents));
		return new SourceGeneration(
			source.packName(),
			source.generation(),
			List.copyOf(files),
			totalBytes,
			source.assets(),
			source.assetTotalBytes()
		);
	}

	private static void validateOptionEntry(String name, String value) throws IOException {
		if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IOException("shader-pack option name is not a preprocessor identifier: " + name);
		}
		if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)
			|| value.indexOf('#') >= 0 || value.indexOf('\\') >= 0 || value.indexOf('=') >= 0) {
			throw new IOException("shader-pack option value is not one preprocessor token: " + name);
		}
	}

	public record SourceGeneration(
		String packName,
		long generation,
		List<VulkanicGalBridge.ShaderPackSourceFileRecord> files,
		long totalBytes,
		List<VulkanicGalBridge.ShaderPackAssetFileRecord> assets,
		long assetTotalBytes
	) {
		public SourceGeneration {
			Objects.requireNonNull(packName, "packName");
			files = List.copyOf(files);
			assets = List.copyOf(assets);
		}
	}
}
