package net.minecraft.resources;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.json.JsonFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Performance tests for JSON resource loading.
 * Tests the complete pipeline: File I/O → Parsing → Object Construction
 * 
 * This test measures realistic JSON loading scenarios that occur during
 * Minecraft resource pack loading, including language files, sounds,
 * models, and configuration files.
 * 
 * Note: Code is intentionally inlined in timing loops (not extracted to methods)
 * to avoid method call overhead that would skew performance measurements.
 */
@DisplayName("JSON Resource Loading Performance Tests")
class JsonResourceLoadingPerformanceTest {
    
    private static final int WARMUP_ITERATIONS = 10;
    private static final int TEST_ITERATIONS = 100;
    
    private static final String SMALL_JSON = "/json_performance_tests/small.json";
    private static final String MEDIUM_JSON = "/json_performance_tests/medium.json";
    private static final String LARGE_JSON = "/json_performance_tests/large.json";
    
    private Gson gson;
    private JsonFormat nightConfigJsonFormat;
    
    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().create();
        nightConfigJsonFormat = JsonFormat.minimalInstance();
    }
    
    @Test
    @DisplayName("should load small JSON files quickly with Gson")
    void testSmallJsonWithGson() throws IOException {
        System.out.println("\n=== Small JSON Loading Performance (Gson) ===");
        testJsonLoadingWithGson(SMALL_JSON, "Small JSON");
    }
    
    @Test
    @DisplayName("should load medium JSON files quickly with Gson")
    void testMediumJsonWithGson() throws IOException {
        System.out.println("\n=== Medium JSON Loading Performance (Gson) ===");
        testJsonLoadingWithGson(MEDIUM_JSON, "Medium JSON");
    }
    
    @Test
    @DisplayName("should load large JSON files quickly with Gson")
    void testLargeJsonWithGson() throws IOException {
        System.out.println("\n=== Large JSON Loading Performance (Gson) ===");
        testJsonLoadingWithGson(LARGE_JSON, "Large JSON");
    }
    
    @Test
    @DisplayName("should load small JSON files quickly with NightConfig")
    void testSmallJsonWithNightConfig() throws IOException {
        System.out.println("\n=== Small JSON Loading Performance (NightConfig) ===");
        testJsonLoadingWithNightConfig(SMALL_JSON, "Small JSON");
    }
    
    @Test
    @DisplayName("should load medium JSON files quickly with NightConfig")
    void testMediumJsonWithNightConfig() throws IOException {
        System.out.println("\n=== Medium JSON Loading Performance (NightConfig) ===");
        testJsonLoadingWithNightConfig(MEDIUM_JSON, "Medium JSON");
    }
    
    @Test
    @DisplayName("should load large JSON files quickly with NightConfig")
    void testLargeJsonWithNightConfig() throws IOException {
        System.out.println("\n=== Large JSON Loading Performance (NightConfig) ===");
        testJsonLoadingWithNightConfig(LARGE_JSON, "Large JSON");
    }
    
    @Test
    @DisplayName("should measure file I/O separately from parsing")
    void testFileIOVsParsing() throws IOException {
        System.out.println("\n=== File I/O vs Parsing Performance Breakdown ===");
        
        String resourcePath = LARGE_JSON;
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Could not find resource: " + resourcePath);
        }
        
        // Pre-read the content into memory
        String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        inputStream.close();
        
        // Test File I/O (reading from resource stream)
        PerformanceTimer fileIOTimer = new PerformanceTimer();
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            try {
                InputStream is = getClass().getResourceAsStream(resourcePath);
                byte[] bytes = is.readAllBytes();
                is.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            fileIOTimer.time(() -> {
                try {
                    InputStream is = getClass().getResourceAsStream(resourcePath);
                    byte[] bytes = is.readAllBytes();
                    is.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        
        // Test Parsing (from pre-loaded string)
        PerformanceTimer parsingTimer = new PerformanceTimer();
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            gson.fromJson(jsonContent, JsonObject.class);
        });
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            parsingTimer.time(() -> {
                gson.fromJson(jsonContent, JsonObject.class);
            });
        }
        
        fileIOTimer.printSummary("File I/O");
        parsingTimer.printSummary("Parsing");
        
        System.out.printf("%nTotal Pipeline (I/O + Parsing): %s average%n", 
            PerformanceTimer.formatDuration(fileIOTimer.getAverage() + parsingTimer.getAverage()));
    }
    
    @Test
    @DisplayName("should measure complete end-to-end resource loading")
    void testCompleteResourceLoading() throws IOException {
        System.out.println("\n=== Complete End-to-End Resource Loading ===");
        
        PerformanceTimer totalTimer = new PerformanceTimer();
        PerformanceTimer ioTimer = new PerformanceTimer();
        PerformanceTimer parseTimer = new PerformanceTimer();
        
        String resourcePath = LARGE_JSON;
        
        // Warmup
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            try {
                InputStream is = getClass().getResourceAsStream(resourcePath);
                Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                JsonObject obj = gson.fromJson(reader, JsonObject.class);
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        
        // Test with separate timing for each stage
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long totalStart = System.nanoTime();
            
            // Stage 1: File I/O
            long ioStart = System.nanoTime();
            InputStream is = getClass().getResourceAsStream(resourcePath);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();
            long ioDuration = System.nanoTime() - ioStart;
            ioTimer.addMeasurement(ioDuration);
            
            // Stage 2: Parsing
            long parseStart = System.nanoTime();
            JsonObject obj = gson.fromJson(content, JsonObject.class);
            long parseDuration = System.nanoTime() - parseStart;
            parseTimer.addMeasurement(parseDuration);
            
            long totalDuration = System.nanoTime() - totalStart;
            totalTimer.addMeasurement(totalDuration);
        }
        
        System.out.println("\nPerformance Breakdown:");
        ioTimer.printSummary("  Stage 1 - File I/O");
        parseTimer.printSummary("  Stage 2 - Parsing");
        totalTimer.printSummary("  Total (All Stages)");
        
        double ioPercent = (ioTimer.getAverage() / totalTimer.getAverage()) * 100;
        double parsePercent = (parseTimer.getAverage() / totalTimer.getAverage()) * 100;
        
        System.out.printf("%nPercentage Breakdown:%n");
        System.out.printf("  File I/O: %.1f%%%n", ioPercent);
        System.out.printf("  Parsing:  %.1f%%%n", parsePercent);
    }
    
    private void testJsonLoadingWithGson(String resourcePath, String label) throws IOException {
        PerformanceTimer timer = new PerformanceTimer();
        
        // Warmup
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            try {
                InputStream is = getClass().getResourceAsStream(resourcePath);
                if (is == null) throw new IOException("Resource not found: " + resourcePath);
                Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                JsonObject obj = gson.fromJson(reader, JsonObject.class);
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        
        // Actual measurements
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            timer.time(() -> {
                try {
                    InputStream is = getClass().getResourceAsStream(resourcePath);
                    if (is == null) throw new IOException("Resource not found: " + resourcePath);
                    Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    JsonObject obj = gson.fromJson(reader, JsonObject.class);
                    reader.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        
        timer.printSummary(label + " (Gson)");
    }
    
    private void testJsonLoadingWithNightConfig(String resourcePath, String label) throws IOException {
        PerformanceTimer timer = new PerformanceTimer();
        
        // Warmup
        PerformanceTimer.warmup(WARMUP_ITERATIONS, () -> {
            try {
                InputStream is = getClass().getResourceAsStream(resourcePath);
                if (is == null) throw new IOException("Resource not found: " + resourcePath);
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                
                Config config = Config.inMemory();
                nightConfigJsonFormat.createParser().parse(content, config, ParsingMode.REPLACE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        
        // Actual measurements
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            timer.time(() -> {
                try {
                    InputStream is = getClass().getResourceAsStream(resourcePath);
                    if (is == null) throw new IOException("Resource not found: " + resourcePath);
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    is.close();
                    
                    Config config = Config.inMemory();
                    nightConfigJsonFormat.createParser().parse(content, config, ParsingMode.REPLACE);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        
        timer.printSummary(label + " (NightConfig)");
    }
}
