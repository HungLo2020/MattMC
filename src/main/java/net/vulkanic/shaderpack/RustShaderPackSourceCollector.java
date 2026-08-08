package net.vulkanic.shaderpack;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.IrisDefines;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.vulkanic.bridge.VulkanicGalBridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
	/** Reserved Rust-owned semantic source-environment path. */
	public static final String RUNTIME_ENVIRONMENT_PATH = "mattmc/runtime-environment.properties";

	private RustShaderPackSourceCollector() {
	}

	public static SourceGeneration collectConfiguredPack(long generation) throws IOException {
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
		return withConfiguredSemanticSnapshots(source);
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
	private static SourceGeneration withConfiguredOptionSnapshot(SourceGeneration source) throws IOException {
		ShaderPack pack = Iris.getCurrentPack().orElseThrow(() ->
			new IOException("configured shader pack has no resolved option state"));
		OptionSet optionSet = pack.getShaderPackOptions().getOptionSet();
		OptionValues values = pack.getShaderPackOptions().getOptionValues();
		TreeMap<String, String> options = new TreeMap<>();
		optionSet.getBooleanOptions().forEach((name, option) ->
			options.put(name, values.getBooleanValueOrDefault(name) ? "1" : "0"));
		optionSet.getStringOptions().forEach((name, option) ->
			options.put(name, values.getStringValueOrDefault(name)));
		return withRuntimeOptionSnapshot(source, options);
	}

	private static SourceGeneration withConfiguredSemanticSnapshots(SourceGeneration source) throws IOException {
		ShaderPack pack = Iris.getCurrentPack().orElseThrow(() ->
			new IOException("configured shader pack has no resolved environment state"));
		return withRuntimeEnvironmentSnapshot(
			withConfiguredOptionSnapshot(source),
			configuredEnvironmentDefines(pack)
		);
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
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(options, "options");
		StringBuilder properties = new StringBuilder();
		for (var entry : new TreeMap<>(options).entrySet()) {
			validateOptionEntry(entry.getKey(), entry.getValue());
			properties.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		byte[] contents = properties.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (contents.length > MAX_FILE_BYTES) {
			throw new IOException("shader-pack runtime option payload exceeds " + MAX_FILE_BYTES + " bytes");
		}
		return appendReservedProperties(source, RUNTIME_OPTIONS_PATH, contents, "option");
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
