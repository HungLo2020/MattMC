package net.minecraft.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Performance test for loading ALL JSON resources in the game.
 * Measures the complete pipeline: File I/O → Parsing → Object Construction
 * 
 * This test loads all ~13,000 JSON files from the actual game resources
 * including language files, models, sounds, advancements, recipes, etc.
 */
@DisplayName("JSON Resource Loading Performance Test")
class JsonResourceLoadingPerformanceTest {
    
    private static final int TEST_ITERATIONS = 3;
    
    private Gson gson;
    private List<String> allJsonPaths;
    
    @BeforeEach
    void setUp() throws Exception {
        gson = new GsonBuilder().create();
        allJsonPaths = findAllJsonResources();
        System.out.printf("Found %,d JSON files to load%n", allJsonPaths.size());
    }
    
    /**
     * Finds all JSON files in the resources directory.
     */
    private List<String> findAllJsonResources() throws Exception {
        List<String> jsonPaths = new ArrayList<>();
        
        // Get the main resources path from the build output
        Path resourcesPath = Paths.get("build/resources/main");
        
        if (!Files.exists(resourcesPath)) {
            // If build output doesn't exist, try source directory
            resourcesPath = Paths.get("src/main/resources");
        }
        
        if (!Files.exists(resourcesPath)) {
            throw new IOException("Could not find resources directory");
        }
        
        final Path finalResourcesPath = resourcesPath;
        
        // Find all .json files
        try (Stream<Path> paths = Files.walk(resourcesPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".json"))
                 .forEach(p -> {
                     // Convert to resource path format (starting with /)
                     String relative = finalResourcesPath.relativize(p).toString().replace('\\', '/');
                     jsonPaths.add("/" + relative);
                 });
        }
        
        return jsonPaths;
    }
    
    @Test
    @DisplayName("should load all JSON resources from the game")
    void testLoadAllGameJsonResources() throws Exception {
        System.out.println("\n=== Loading All Game JSON Resources ===");
        System.out.printf("Total JSON files: %,d%n", allJsonPaths.size());
        System.out.printf("Test iterations: %d%n%n", TEST_ITERATIONS);
        
        PerformanceTimer totalTimer = new PerformanceTimer();
        PerformanceTimer ioTimer = new PerformanceTimer();
        PerformanceTimer parseTimer = new PerformanceTimer();
        
        // Get the base path for resources
        Path basePath = Files.exists(Paths.get("build/resources/main")) 
            ? Paths.get("build/resources/main") 
            : Paths.get("src/main/resources");
        
        for (int iteration = 0; iteration < TEST_ITERATIONS; iteration++) {
            System.out.printf("Iteration %d/%d...%n", iteration + 1, TEST_ITERATIONS);
            
            long iterationStart = System.nanoTime();
            long totalIO = 0;
            long totalParse = 0;
            int successCount = 0;
            int failCount = 0;
            
            for (String resourcePath : allJsonPaths) {
                try {
                    // Remove leading slash and construct full path
                    Path jsonPath = basePath.resolve(resourcePath.substring(1));
                    
                    // Stage 1: File I/O
                    long ioStart = System.nanoTime();
                    String content = Files.readString(jsonPath, StandardCharsets.UTF_8);
                    long ioTime = System.nanoTime() - ioStart;
                    totalIO += ioTime;
                    
                    // Stage 2: Parsing
                    long parseStart = System.nanoTime();
                    JsonElement element = gson.fromJson(content, JsonElement.class);
                    long parseTime = System.nanoTime() - parseStart;
                    totalParse += parseTime;
                    
                    successCount++;
                } catch (Exception e) {
                    // Some JSON files may be malformed or have special formats
                    failCount++;
                }
            }
            
            long iterationTime = System.nanoTime() - iterationStart;
            
            // Record measurements for this iteration
            totalTimer.addMeasurement(iterationTime);
            ioTimer.addMeasurement(totalIO);
            parseTimer.addMeasurement(totalParse);
            
            System.out.printf("  Loaded: %,d files (%.1f%%)%n", successCount, 
                (successCount * 100.0) / allJsonPaths.size());
            System.out.printf("  Failed: %,d files%n", failCount);
            System.out.printf("  Time: %s%n%n", PerformanceTimer.formatDuration(iterationTime));
        }
        
        // Print summary
        System.out.println("=== Performance Summary ===");
        totalTimer.printSummary("Total Time (All Files)");
        ioTimer.printSummary("Total File I/O");
        parseTimer.printSummary("Total Parsing");
        
        double ioPercent = (ioTimer.getAverage() / totalTimer.getAverage()) * 100;
        double parsePercent = (parseTimer.getAverage() / totalTimer.getAverage()) * 100;
        
        System.out.printf("%nPercentage Breakdown:%n");
        System.out.printf("  File I/O: %.1f%%%n", ioPercent);
        System.out.printf("  Parsing:  %.1f%%%n", parsePercent);
        
        // Calculate per-file average
        double avgPerFile = totalTimer.getAverage() / allJsonPaths.size();
        System.out.printf("%nAverage per file: %s%n", PerformanceTimer.formatDuration(avgPerFile));
    }
}
