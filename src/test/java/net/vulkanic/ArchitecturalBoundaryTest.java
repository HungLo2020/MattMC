package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Architectural Boundary Enforcement Test
 * 
 * This test enforces the Vulkanic abstraction layer principle:
 * - Only code in src/main/java/net/vulkanic/backends/opengl/ can import org.lwjgl.opengl.*
 * - Only code in src/main/java/net/vulkanic/backends/vulkan/ can import org.lwjgl.vulkan.*
 * - Only code in src/main/java/net/vulkanic/ can import net.vulkanic.backends.* classes
 * 
 * This ensures that game code uses the Vulkanic API abstraction layer rather than
 * directly depending on specific graphics APIs (OpenGL or Vulkan) or backend implementations.
 * 
 * See src/main/java/net/vulkanic/README.md for details on the architectural principles.
 */
public class ArchitecturalBoundaryTest {
    
    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final Path SRC_MAIN_JAVA = Paths.get(PROJECT_ROOT, "src", "main", "java");
    
    // Vulkanic package root - only code inside this directory can access backends
    private static final Path VULKANIC_PATH = Paths.get(PROJECT_ROOT, "src", "main", "java", "net", "vulkanic");
    
    // Allowed paths for OpenGL imports
    private static final Path OPENGL_BACKEND_PATH = Paths.get(PROJECT_ROOT, "src", "main", "java", "net", "vulkanic", "backends", "opengl");
    
    // Allowed paths for Vulkan imports
    private static final Path VULKAN_BACKEND_PATH = Paths.get(PROJECT_ROOT, "src", "main", "java", "net", "vulkanic", "backends", "vulkan");
    
    // Patterns to detect forbidden imports
    private static final Pattern OPENGL_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+org\\.lwjgl\\.opengl\\.[^;]+;", Pattern.MULTILINE);
    private static final Pattern VULKAN_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+org\\.lwjgl\\.vulkan\\.[^;]+;", Pattern.MULTILINE);
    private static final Pattern BACKEND_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+net\\.vulkanic\\.backends\\.[^;]+;", Pattern.MULTILINE);
    private static final Pattern OPENGL_BACKEND_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+net\\.vulkanic\\.backends\\.opengl\\.[^;]+;", Pattern.MULTILINE);
    private static final Pattern VULKAN_BACKEND_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+net\\.vulkanic\\.backends\\.vulkan\\.[^;]+;", Pattern.MULTILINE);
    private static final Pattern OPENGL_BACKEND_REFERENCE_PATTERN = Pattern.compile("\\bnet\\.vulkanic\\.backends\\.opengl\\.");
    private static final Pattern VULKAN_BACKEND_REFERENCE_PATTERN = Pattern.compile("\\bnet\\.vulkanic\\.backends\\.vulkan\\.");
    
    @Test
    public void testOpenGLImportsOnlyInBackend() throws IOException {
        List<String> violations = checkImportViolations(
            OPENGL_IMPORT_PATTERN,
            OPENGL_BACKEND_PATH,
            "OpenGL"
        );
        
        if (!violations.isEmpty()) {
            fail(buildViolationMessage(
                "OpenGL",
                "src/main/java/net/vulkanic/backends/opengl/",
                "org.lwjgl.opengl.*",
                violations
            ));
        }
    }
    
    @Test
    public void testVulkanImportsOnlyInBackend() throws IOException {
        List<String> violations = checkImportViolations(
            VULKAN_IMPORT_PATTERN,
            VULKAN_BACKEND_PATH,
            "Vulkan"
        );
        
        if (!violations.isEmpty()) {
            fail(buildViolationMessage(
                "Vulkan",
                "src/main/java/net/vulkanic/backends/vulkan/",
                "org.lwjgl.vulkan.*",
                violations
            ));
        }
    }
    
    @Test
    public void testBackendImportsOnlyFromVulkanicPackage() throws IOException {
        List<String> violations = checkImportViolations(
            BACKEND_IMPORT_PATTERN,
            VULKANIC_PATH,
            "Backend"
        );
        
        if (!violations.isEmpty()) {
            fail(buildBackendViolationMessage(violations));
        }
    }

    @Test
    public void testOpenGLBackendDoesNotReferenceVulkanBackendImplementation() throws IOException {
        List<String> violations = checkForbiddenBackendReferences(
            OPENGL_BACKEND_PATH,
            VULKAN_BACKEND_IMPORT_PATTERN,
            VULKAN_BACKEND_REFERENCE_PATTERN,
            "OpenGL",
            "Vulkan"
        );

        if (!violations.isEmpty()) {
            fail(buildCrossBackendViolationMessage("OpenGL", "Vulkan", violations));
        }
    }

    @Test
    public void testVulkanBackendDoesNotReferenceOpenGLBackendImplementation() throws IOException {
        List<String> violations = checkForbiddenBackendReferences(
            VULKAN_BACKEND_PATH,
            OPENGL_BACKEND_IMPORT_PATTERN,
            OPENGL_BACKEND_REFERENCE_PATTERN,
            "Vulkan",
            "OpenGL"
        );

        if (!violations.isEmpty()) {
            fail(buildCrossBackendViolationMessage("Vulkan", "OpenGL", violations));
        }
    }
    
    /**
     * Scans Java source files for import violations.
     * 
     * @param importPattern Regex pattern to match forbidden imports
     * @param allowedPath Directory path where imports are allowed
     * @param backendName Name of the backend (for error messages)
     * @return List of violation messages
     */
    private List<String> checkImportViolations(Pattern importPattern, Path allowedPath, String backendName) throws IOException {
        List<String> violations = new ArrayList<>();
        
        // Scan all Java files in src/main/java
        try (Stream<Path> paths = Files.walk(SRC_MAIN_JAVA)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                // Skip files in the allowed backend directory
                if (file.startsWith(allowedPath)) {
                    continue;
                }
                
                // Read file content and check for violations
                String content = Files.readString(file);
                List<String> illegalImports = new ArrayList<>();
                
                Matcher matcher = importPattern.matcher(content);
                while (matcher.find()) {
                    illegalImports.add(matcher.group().trim());
                }
                
                if (!illegalImports.isEmpty()) {
                    String relativePath = SRC_MAIN_JAVA.relativize(file).toString();
                    violations.add(String.format(
                        "File: %s\n  Illegal %s imports:\n    %s",
                        relativePath,
                        backendName,
                        String.join("\n    ", illegalImports)
                    ));
                }
            }
        }
        
        return violations;
    }

    private List<String> checkForbiddenBackendReferences(
        Path backendPath,
        Pattern forbiddenImportPattern,
        Pattern forbiddenReferencePattern,
        String ownerBackend,
        String forbiddenBackend
    ) throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(backendPath)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                List<String> illegalEntries = new ArrayList<>();

                Matcher importMatcher = forbiddenImportPattern.matcher(content);
                while (importMatcher.find()) {
                    illegalEntries.add(importMatcher.group().trim());
                }

                String[] lines = content.split("\\R", -1);
                for (int index = 0; index < lines.length; index++) {
                    String line = lines[index];
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import ")) {
                        continue;
                    }

                    if (forbiddenReferencePattern.matcher(line).find()) {
                        illegalEntries.add("line " + (index + 1) + ": " + trimmed);
                    }
                }

                if (!illegalEntries.isEmpty()) {
                    String relativePath = SRC_MAIN_JAVA.relativize(file).toString();
                    violations.add(String.format(
                        "File: %s\n  %s backend illegally references %s backend symbols:\n    %s",
                        relativePath,
                        ownerBackend,
                        forbiddenBackend,
                        String.join("\n    ", illegalEntries)
                    ));
                }
            }
        }

        return violations;
    }
    
    /**
     * Builds a detailed error message for import violations.
     */
    private String buildViolationMessage(String backendName, String allowedPath, String importPackage, List<String> violations) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("\n");
        errorMessage.append("================================================================================\n");
        errorMessage.append("ARCHITECTURAL BOUNDARY VIOLATION: Illegal ").append(backendName).append(" Imports Detected\n");
        errorMessage.append("================================================================================\n");
        errorMessage.append("\n");
        errorMessage.append("The Vulkanic abstraction layer prohibits direct ").append(backendName).append(" imports outside\n");
        errorMessage.append("of the designated backend directory.\n");
        errorMessage.append("\n");
        errorMessage.append("RULE: Only code in '").append(allowedPath).append("'\n");
        errorMessage.append("      may import ").append(importPackage).append(" classes.\n");
        errorMessage.append("\n");
        errorMessage.append("REASON: Game code must use the Vulkanic API abstraction layer to support\n");
        errorMessage.append("        multiple graphics backends (OpenGL and Vulkan).\n");
        errorMessage.append("\n");
        errorMessage.append("VIOLATIONS FOUND:\n");
        errorMessage.append("--------------------------------------------------------------------------------\n");
        for (String violation : violations) {
            errorMessage.append(violation).append("\n\n");
        }
        errorMessage.append("================================================================================\n");
        errorMessage.append("TO FIX: Remove direct ").append(backendName).append(" imports and use the VulkanicAPI instead.\n");
        errorMessage.append("        See src/main/java/net/vulkanic/README.md for architectural guidance.\n");
        errorMessage.append("================================================================================\n");
        
        return errorMessage.toString();
    }
    
    /**
     * Builds a detailed error message for backend import violations.
     */
    private String buildBackendViolationMessage(List<String> violations) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("\n");
        errorMessage.append("================================================================================\n");
        errorMessage.append("ARCHITECTURAL BOUNDARY VIOLATION: Illegal Backend Imports Detected\n");
        errorMessage.append("================================================================================\n");
        errorMessage.append("\n");
        errorMessage.append("The Vulkanic abstraction layer prohibits code outside of net.vulkanic package\n");
        errorMessage.append("from directly importing backend implementation classes.\n");
        errorMessage.append("\n");
        errorMessage.append("RULE: Only code in 'src/main/java/net/vulkanic/'\n");
        errorMessage.append("      may import net.vulkanic.backends.* classes.\n");
        errorMessage.append("\n");
        errorMessage.append("REASON: Game and mod code must use the Vulkanic frontend API (VulkanicAPI,\n");
        errorMessage.append("        GraphicsBackend, CommandContext) instead of directly accessing backend\n");
        errorMessage.append("        implementations. This ensures the abstraction layer works properly and\n");
        errorMessage.append("        allows switching between OpenGL and Vulkan backends.\n");
        errorMessage.append("\n");
        errorMessage.append("VIOLATIONS FOUND:\n");
        errorMessage.append("--------------------------------------------------------------------------------\n");
        for (String violation : violations) {
            errorMessage.append(violation).append("\n\n");
        }
        errorMessage.append("================================================================================\n");
        errorMessage.append("TO FIX: Remove direct backend imports and use the VulkanicAPI frontend instead.\n");
        errorMessage.append("        See src/main/java/net/vulkanic/README.md for architectural guidance.\n");
        errorMessage.append("================================================================================\n");
        
        return errorMessage.toString();
    }

    private String buildCrossBackendViolationMessage(String sourceBackend, String forbiddenBackend, List<String> violations) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("\n");
        errorMessage.append("================================================================================\n");
        errorMessage.append("ARCHITECTURAL BOUNDARY VIOLATION: Cross-Backend Dependency Detected\n");
        errorMessage.append("================================================================================\n");
        errorMessage.append("\n");
        errorMessage.append(sourceBackend).append(" backend code must not import or reference ")
            .append(forbiddenBackend).append(" backend implementation types.\n");
        errorMessage.append("\n");
        errorMessage.append("REASON: Backends must remain fully isolated so backend selection never\n");
        errorMessage.append("        relies on implementation code from another backend.\n");
        errorMessage.append("\n");
        errorMessage.append("VIOLATIONS FOUND:\n");
        errorMessage.append("--------------------------------------------------------------------------------\n");
        for (String violation : violations) {
            errorMessage.append(violation).append("\n\n");
        }
        errorMessage.append("================================================================================\n");
        errorMessage.append("TO FIX: Remove cross-backend imports/references and route interactions through\n");
        errorMessage.append("        net.vulkanic frontend abstractions only.\n");
        errorMessage.append("================================================================================\n");

        return errorMessage.toString();
    }
    
    @Test
    public void testBackendDirectoriesExist() {
        assertTrue(Files.exists(OPENGL_BACKEND_PATH), 
            "OpenGL backend directory should exist: " + OPENGL_BACKEND_PATH);
        assertTrue(Files.exists(VULKAN_BACKEND_PATH), 
            "Vulkan backend directory should exist: " + VULKAN_BACKEND_PATH);
    }
}
