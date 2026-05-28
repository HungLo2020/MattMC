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
    private static final java.util.regex.Pattern GLSL_VERSION_PATTERN = java.util.regex.Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");
    private static final java.util.regex.Pattern STANDALONE_UNIFORM_DECLARATION_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:lowp\\s+|mediump\\s+|highp\\s+)?uniform\\s+([^;{}]+?)\\s*;\\s*(?://.*)?$"
    );
    private static final java.util.regex.Pattern EXPLICIT_BINDING_PATTERN = java.util.regex.Pattern.compile(
        "layout\\s*\\(([^)]*\\bbinding\\s*=\\s*(\\d+)[^)]*)\\)"
    );
    static final String GENERATED_UNIFORM_BLOCK_NAME = "VulkanicStandaloneUniforms";

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
        if (shaderSource.isEmpty()) {
            return shaderSource;
        }

        String normalized = promoteVersionForVulkan(shaderSource);
        normalized = rewriteStandaloneUniformsForVulkan(normalized);

        if (stage != VulkanicShaderStage.VERTEX) {
            return normalized;
        }

        normalized = LEGACY_VERTEX_ID_PATTERN.matcher(normalized).replaceAll("gl_VertexIndex");
        return LEGACY_INSTANCE_ID_PATTERN.matcher(normalized).replaceAll("gl_InstanceIndex");
    }

    private static String promoteVersionForVulkan(String shaderSource) {
        java.util.regex.Matcher versionMatcher = GLSL_VERSION_PATTERN.matcher(shaderSource);
        if (!versionMatcher.find()) {
            return "#version 450\n" + shaderSource;
        }

        int declaredVersion = Integer.parseInt(versionMatcher.group(1));
        if (declaredVersion >= 450) {
            return shaderSource;
        }

        return shaderSource.substring(0, versionMatcher.start(1))
            + "450"
            + shaderSource.substring(versionMatcher.end(1));
    }

    private static String rewriteStandaloneUniformsForVulkan(String shaderSource) {
        java.util.regex.Matcher matcher = STANDALONE_UNIFORM_DECLARATION_PATTERN.matcher(shaderSource);
        StringBuffer strippedSource = new StringBuffer();
        List<String> blockMembers = new ArrayList<>();

        while (matcher.find()) {
            String declaration = matcher.group(1).trim();
            if (declaration.isEmpty()) {
                matcher.appendReplacement(strippedSource, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            String typeToken = declaration.split("\\s+", 2)[0];
            if (isOpaqueUniformType(typeToken)) {
                matcher.appendReplacement(strippedSource, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            blockMembers.add(declaration + ";");
            matcher.appendReplacement(strippedSource, "");
        }

        matcher.appendTail(strippedSource);

        if (blockMembers.isEmpty()) {
            return shaderSource;
        }

        int insertOffset = findUniformBlockInsertionOffset(strippedSource.toString());
        StringBuilder block = new StringBuilder();
        // Use the HIGHER of: (max explicit binding + 1) OR (count of non-explicitly-bound opaque
        // uniforms). The latter ensures the UBO is placed after all auto-mapped samplers, which
        // glslang assigns starting from binding 0 independently per resource type. Without this,
        // VulkanicStandaloneUniforms would get explicit binding 0 and collide with the first
        // auto-mapped sampler (e.g. colortex0 from Iris shaders that have no explicit bindings).
        int maxExplicit = findNextExplicitBindingIndex(strippedSource.toString());
        int nonExplicitOpaqueCount = countNonExplicitOpaqueUniforms(strippedSource.toString());
        int standaloneBindingIndex = Math.max(maxExplicit, nonExplicitOpaqueCount);
        block.append("layout(std140, set = 0, binding = ")
            .append(standaloneBindingIndex)
            .append(") uniform ")
            .append(GENERATED_UNIFORM_BLOCK_NAME)
            .append(" {\n");

        for (String member : blockMembers) {
            block.append("    ").append(member).append("\n");
        }

        block.append("};\n\n");

        return strippedSource.substring(0, insertOffset)
            + block
            + strippedSource.substring(insertOffset);
    }

    private static boolean isOpaqueUniformType(String typeToken) {
        return typeToken.contains("sampler")
            || typeToken.contains("image")
            || typeToken.equals("atomic_uint");
    }

    private static int findUniformBlockInsertionOffset(String shaderSource) {
        java.util.regex.Matcher versionMatcher = GLSL_VERSION_PATTERN.matcher(shaderSource);
        if (!versionMatcher.find()) {
            return 0;
        }

        int lineEnd = shaderSource.indexOf('\n', versionMatcher.end());
        if (lineEnd < 0) {
            return shaderSource.length();
        }

        int offset = lineEnd + 1;
        while (offset < shaderSource.length()) {
            int nextLineEnd = shaderSource.indexOf('\n', offset);
            if (nextLineEnd < 0) {
                nextLineEnd = shaderSource.length();
            }

            String trimmed = shaderSource.substring(offset, nextLineEnd).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#extension")) {
                offset = nextLineEnd < shaderSource.length() ? nextLineEnd + 1 : nextLineEnd;
                continue;
            }

            break;
        }

        return offset;
    }

    static int countNonExplicitOpaqueUniforms(String shaderSource) {
        java.util.regex.Matcher matcher = STANDALONE_UNIFORM_DECLARATION_PATTERN.matcher(shaderSource);
        int count = 0;
        while (matcher.find()) {
            String declaration = matcher.group(1).trim();
            if (declaration.isEmpty()) continue;
            String typeToken = declaration.split("\\s+", 2)[0];
            if (!isOpaqueUniformType(typeToken)) continue;
            // Only count if no explicit "binding" qualifier appears in the full match
            String fullMatch = matcher.group();
            if (!fullMatch.contains("binding")) {
                count++;
            }
        }
        return count;
    }

    static boolean hasStandaloneUniformBlockMembers(String shaderSource) {
        java.util.regex.Matcher matcher = STANDALONE_UNIFORM_DECLARATION_PATTERN.matcher(shaderSource);
        while (matcher.find()) {
            String declaration = matcher.group(1).trim();
            if (declaration.isEmpty()) {
                continue;
            }

            String typeToken = declaration.split("\\s+", 2)[0];
            if (!isOpaqueUniformType(typeToken)) {
                return true;
            }
        }
        return false;
    }

    private static int findNextExplicitBindingIndex(String shaderSource) {
        java.util.regex.Matcher matcher = EXPLICIT_BINDING_PATTERN.matcher(shaderSource);
        int maxBinding = -1;
        while (matcher.find()) {
            try {
                int binding = Integer.parseInt(matcher.group(2));
                if (binding > maxBinding) {
                    maxBinding = binding;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxBinding + 1;
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