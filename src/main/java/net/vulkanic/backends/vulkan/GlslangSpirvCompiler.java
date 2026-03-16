package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GlslangSpirvCompiler implements SpirvCompiler {

    private static final java.util.regex.Pattern LEGACY_VERTEX_ID_PATTERN = java.util.regex.Pattern.compile("\\bgl_VertexID\\b");
    private static final java.util.regex.Pattern LEGACY_INSTANCE_ID_PATTERN = java.util.regex.Pattern.compile("\\bgl_InstanceID\\b");

    @Override
    public VulkanicSpirvModule compile(VulkanicShaderStage stage, CharSequence source, String sourceName, String entryPoint) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(entryPoint, "entryPoint must not be null");

        String compilerExecutable = resolveCompilerExecutable();
        String shaderSource = source.toString();
        if (shaderSource.isBlank()) {
            throw new IllegalStateException("Cannot compile blank shader source to SPIR-V: " + sourceName);
        }

        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("vulkanic-spirv-");
            String stageSuffix = stageToken(stage);

            Path sourcePath = tempDirectory.resolve("shader." + stageSuffix + ".glsl");
            Path outputPath = tempDirectory.resolve("shader." + stageSuffix + ".spv");

            Files.writeString(sourcePath, shaderSource, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(compilerExecutable);
            command.add("-V");
            command.add("-S");
            command.add(stageSuffix);
            command.add("--auto-map-bindings");
            command.add("--auto-map-locations");
            command.add("-e");
            command.add(entryPoint);
            command.add("-o");
            command.add(outputPath.toAbsolutePath().toString());
            command.add(sourcePath.toAbsolutePath().toString());

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            String outputText = new String(output, StandardCharsets.UTF_8).trim();

            if (exitCode != 0) {
                throw new IllegalStateException(
                    "SPIR-V compilation failed for '" + sourceName + "' (stage=" + stage + ", entryPoint=" + entryPoint + ")"
                        + " using compiler '" + compilerExecutable + "' with exit code " + exitCode
                        + (outputText.isEmpty() ? "" : ": " + outputText)
                );
            }

            byte[] spirvBytes = Files.readAllBytes(outputPath);
            if (spirvBytes.length == 0) {
                throw new IllegalStateException(
                    "SPIR-V compiler produced empty output for '" + sourceName + "' using compiler '"
                        + compilerExecutable + "'."
                );
            }

            return new VulkanicSpirvModule(stage, entryPoint, spirvBytes, sourceName, compilerExecutable);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to invoke SPIR-V compiler for '" + sourceName + "'. Ensure glslangValidator is installed"
                    + " or set -Dvulkanic.spirv.compiler=/path/to/glslangValidator. Cause: " + exception.getMessage(),
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while compiling shader to SPIR-V: " + sourceName, exception);
        } finally {
            if (tempDirectory != null) {
                deleteTempDirectoryQuietly(tempDirectory);
            }
        }
    }

    private static String resolveCompilerExecutable() {
        String propertyValue = System.getProperty("vulkanic.spirv.compiler");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        String environmentValue = System.getenv("VULKANIC_SPIRV_COMPILER");
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }

        return "glslangValidator";
    }

    private static String stageToken(VulkanicShaderStage stage) {
        return switch (stage) {
            case VERTEX -> "vert";
            case FRAGMENT -> "frag";
            case GEOMETRY -> "geom";
            case COMPUTE -> "comp";
            case TESSELLATION_CONTROL -> "tesc";
            case TESSELLATION_EVALUATION -> "tese";
        };
    }

    static String normalizeForVulkan(VulkanicShaderStage stage, String shaderSource) {
        if (stage != VulkanicShaderStage.VERTEX || shaderSource.isEmpty()) {
            return shaderSource;
        }

        String normalized = LEGACY_VERTEX_ID_PATTERN.matcher(shaderSource).replaceAll("gl_VertexIndex");
        return LEGACY_INSTANCE_ID_PATTERN.matcher(normalized).replaceAll("gl_InstanceIndex");
    }

    private static void deleteTempDirectoryQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }
}