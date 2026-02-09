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
 * 
 * This ensures that game code uses the Vulkanic API abstraction layer rather than
 * directly depending on specific graphics APIs (OpenGL or Vulkan).
 * 
 * See VULKAN-COMPAT.md for details on the architectural principles.
 */
public class ArchitecturalBoundaryTest {
    
    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    private static final Path SRC_MAIN_JAVA = Paths.get(PROJECT_ROOT, "src", "main", "java");
    
    // Allowed paths for OpenGL imports
    private static final Path OPENGL_BACKEND_PATH = Paths.get(PROJECT_ROOT, "src", "main", "java", "net", "vulkanic", "backends", "opengl");
    
    // Allowed paths for Vulkan imports
    private static final Path VULKAN_BACKEND_PATH = Paths.get(PROJECT_ROOT, "src", "main", "java", "net", "vulkanic", "backends", "vulkan");
    
    // Patterns to detect forbidden imports
    private static final Pattern OPENGL_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+org\\.lwjgl\\.opengl\\.[^;]+;", Pattern.MULTILINE);
    private static final Pattern VULKAN_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+org\\.lwjgl\\.vulkan\\.[^;]+;", Pattern.MULTILINE);
    
    @Test
    public void testOpenGLImportsOnlyInBackend() throws IOException {
        List<String> violations = new ArrayList<>();
        
        // Scan all Java files in src/main/java
        try (Stream<Path> paths = Files.walk(SRC_MAIN_JAVA)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                // Skip files in the allowed OpenGL backend directory
                if (file.startsWith(OPENGL_BACKEND_PATH)) {
                    continue;
                }
                
                // Read file content
                String content = Files.readString(file);
                
                // Check for OpenGL imports
                Matcher matcher = OPENGL_IMPORT_PATTERN.matcher(content);
                if (matcher.find()) {
                    String relativePath = SRC_MAIN_JAVA.relativize(file).toString();
                    List<String> illegalImports = new ArrayList<>();
                    
                    matcher.reset();
                    while (matcher.find()) {
                        illegalImports.add(matcher.group().trim());
                    }
                    
                    violations.add(String.format(
                        "File: %s\n  Illegal OpenGL imports:\n    %s",
                        relativePath,
                        String.join("\n    ", illegalImports)
                    ));
                }
            }
        }
        
        if (!violations.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("\n");
            errorMessage.append("================================================================================\n");
            errorMessage.append("ARCHITECTURAL BOUNDARY VIOLATION: Illegal OpenGL Imports Detected\n");
            errorMessage.append("================================================================================\n");
            errorMessage.append("\n");
            errorMessage.append("The Vulkanic abstraction layer prohibits direct OpenGL imports outside\n");
            errorMessage.append("of the designated backend directory.\n");
            errorMessage.append("\n");
            errorMessage.append("RULE: Only code in 'src/main/java/net/vulkanic/backends/opengl/'\n");
            errorMessage.append("      may import org.lwjgl.opengl.* classes.\n");
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
            errorMessage.append("TO FIX: Remove direct OpenGL imports and use the VulkanicAPI instead.\n");
            errorMessage.append("        See VULKAN-COMPAT.md for migration guidance.\n");
            errorMessage.append("================================================================================\n");
            
            fail(errorMessage.toString());
        }
    }
    
    @Test
    public void testVulkanImportsOnlyInBackend() throws IOException {
        List<String> violations = new ArrayList<>();
        
        // Scan all Java files in src/main/java
        try (Stream<Path> paths = Files.walk(SRC_MAIN_JAVA)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                // Skip files in the allowed Vulkan backend directory
                if (file.startsWith(VULKAN_BACKEND_PATH)) {
                    continue;
                }
                
                // Read file content
                String content = Files.readString(file);
                
                // Check for Vulkan imports
                Matcher matcher = VULKAN_IMPORT_PATTERN.matcher(content);
                if (matcher.find()) {
                    String relativePath = SRC_MAIN_JAVA.relativize(file).toString();
                    List<String> illegalImports = new ArrayList<>();
                    
                    matcher.reset();
                    while (matcher.find()) {
                        illegalImports.add(matcher.group().trim());
                    }
                    
                    violations.add(String.format(
                        "File: %s\n  Illegal Vulkan imports:\n    %s",
                        relativePath,
                        String.join("\n    ", illegalImports)
                    ));
                }
            }
        }
        
        if (!violations.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("\n");
            errorMessage.append("================================================================================\n");
            errorMessage.append("ARCHITECTURAL BOUNDARY VIOLATION: Illegal Vulkan Imports Detected\n");
            errorMessage.append("================================================================================\n");
            errorMessage.append("\n");
            errorMessage.append("The Vulkanic abstraction layer prohibits direct Vulkan imports outside\n");
            errorMessage.append("of the designated backend directory.\n");
            errorMessage.append("\n");
            errorMessage.append("RULE: Only code in 'src/main/java/net/vulkanic/backends/vulkan/'\n");
            errorMessage.append("      may import org.lwjgl.vulkan.* classes.\n");
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
            errorMessage.append("TO FIX: Remove direct Vulkan imports and use the VulkanicAPI instead.\n");
            errorMessage.append("        See VULKAN-COMPAT.md for migration guidance.\n");
            errorMessage.append("================================================================================\n");
            
            fail(errorMessage.toString());
        }
    }
    
    @Test
    public void testBackendDirectoriesExist() {
        assertTrue(Files.exists(OPENGL_BACKEND_PATH), 
            "OpenGL backend directory should exist: " + OPENGL_BACKEND_PATH);
        assertTrue(Files.exists(VULKAN_BACKEND_PATH), 
            "Vulkan backend directory should exist: " + VULKAN_BACKEND_PATH);
    }
}
