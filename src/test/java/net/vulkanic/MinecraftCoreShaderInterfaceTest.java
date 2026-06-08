package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MinecraftCoreShaderInterfaceTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path CORE_SHADER_DIR = PROJECT_ROOT.resolve("src/main/resources/assets/minecraft/shaders/core");
    private static final Pattern STAGE_VARIABLE_PATTERN = Pattern.compile(
        "^\\s*(?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*"
            + "(?:layout\\s*\\([^)]*\\)\\s*)?"
            + "%s\\s+[A-Za-z_][A-Za-z0-9_]*\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^]]+\\])?\\s*;",
        Pattern.MULTILINE
    );

    @Test
    public void testTextBackgroundShaderDoesNotDeclareUnusedTextureInput() throws IOException {
        String fragmentSource = Files.readString(CORE_SHADER_DIR.resolve("rendertype_text_background.fsh"));

        assertFalse(fragmentSource.contains("uniform sampler2D Sampler0;"),
            "Text background pipeline does not bind Sampler0 and the shader does not sample it");
        assertFalse(fragmentSource.contains("in vec2 texCoord0;"),
            "Text background vertex shader does not output texCoord0");
        assertTrue(fragmentSource.contains("vec4 color = vertexColor * ColorModulator;"),
            "Text background color should remain driven by the vertex color and global color modulator");
    }

    @Test
    public void testMinecraftCoreFragmentInputsHaveMatchingVertexOutputs() throws IOException {
        Set<String> mismatches = new TreeSet<>();

        try (Stream<Path> paths = Files.list(CORE_SHADER_DIR)) {
            for (Path fragmentPath : paths.filter(path -> path.getFileName().toString().endsWith(".fsh")).toList()) {
                Path vertexPath = CORE_SHADER_DIR.resolve(fragmentPath.getFileName().toString().replaceFirst("\\.fsh$", ".vsh"));
                if (!Files.exists(vertexPath)) {
                    continue;
                }

                Set<String> fragmentInputs = collectStageVariables(Files.readString(fragmentPath), "in");
                Set<String> vertexOutputs = collectStageVariables(Files.readString(vertexPath), "out");
                for (String input : fragmentInputs) {
                    if (!vertexOutputs.contains(input)) {
                        mismatches.add(fragmentPath.getFileName() + " input '" + input
                            + "' is not produced by " + vertexPath.getFileName());
                    }
                }
            }
        }

        assertTrue(mismatches.isEmpty(), "Fragment/vertex interface mismatches: " + mismatches);
    }

    private static Set<String> collectStageVariables(String shaderSource, String direction) {
        Pattern pattern = Pattern.compile(String.format(STAGE_VARIABLE_PATTERN.pattern(), direction), Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(shaderSource);
        Set<String> variables = new TreeSet<>();
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }
}
