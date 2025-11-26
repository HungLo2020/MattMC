package mattmc.performance;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates a dedicated performance report file with detailed metrics.
 * This report is generated alongside the standard JUnit test reports and
 * provides detailed performance data that is more useful than pass/fail status.
 * 
 * Usage in tests:
 * <pre>
 * PerformanceReportGenerator.recordResult("TestName", "MetricName", value, "unit");
 * </pre>
 * 
 * After all tests complete, call generateReport() or use the JUnit extension.
 */
public class PerformanceReportGenerator {
    
    private static final ConcurrentHashMap<String, List<MetricEntry>> results = new ConcurrentHashMap<>();
    private static final String REPORT_DIR = "build/reports/performance";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * A single metric entry.
     */
    public static class MetricEntry {
        public final String metricName;
        public final double value;
        public final String unit;
        public final String timestamp;
        
        public MetricEntry(String metricName, double value, String unit) {
            this.metricName = metricName;
            this.value = value;
            this.unit = unit;
            this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        }
    }
    
    /**
     * Record a performance metric result.
     * 
     * @param testName The name of the test
     * @param metricName The name of the metric (e.g., "Execution Time", "Memory Used")
     * @param value The measured value
     * @param unit The unit of measurement (e.g., "ms", "MB", "ops/sec")
     */
    public static void recordResult(String testName, String metricName, double value, String unit) {
        results.computeIfAbsent(testName, k -> new ArrayList<>())
               .add(new MetricEntry(metricName, value, unit));
    }
    
    /**
     * Record a PerformanceResult from PerformanceTestBase.
     */
    public static void recordResult(PerformanceTestBase.PerformanceResult result) {
        String testName = result.testName;
        recordResult(testName, "Total Execution Time", result.getExecutionTimeMs(), "ms");
        recordResult(testName, "Avg Time per Iteration", result.getAvgTimePerIterationMs(), "ms");
        recordResult(testName, "Iterations", result.iterations, "count");
        recordResult(testName, "Heap Memory Used", result.getHeapMemoryMB(), "MB");
        recordResult(testName, "Heap Memory Delta", result.getHeapMemoryDeltaMB(), "MB");
        recordResult(testName, "Thread Count", result.threadCount, "count");
        recordResult(testName, "CPU Time", result.getCpuTimeMs(), "ms");
    }
    
    /**
     * Record FrameTimeMetrics from PerformanceTestBase.
     */
    public static void recordResult(String testName, PerformanceTestBase.FrameTimeMetrics metrics) {
        recordResult(testName, "Average Frame Time", metrics.avgFrameTimeMs, "ms");
        recordResult(testName, "Min Frame Time", metrics.minFrameTimeMs, "ms");
        recordResult(testName, "Max Frame Time", metrics.maxFrameTimeMs, "ms");
        recordResult(testName, "95th Percentile", metrics.percentile95Ms, "ms");
        recordResult(testName, "99th Percentile", metrics.percentile99Ms, "ms");
        recordResult(testName, "FPS", metrics.fps, "fps");
        recordResult(testName, "Frame Count", metrics.frameCount, "count");
    }
    
    /**
     * Generate the performance report files (text and HTML).
     */
    public static void generateReport() {
        if (results.isEmpty()) {
            return;
        }
        
        try {
            Path reportDir = Paths.get(REPORT_DIR);
            Files.createDirectories(reportDir);
            
            generateTextReport(reportDir.resolve("performance-report.txt"));
            generateHtmlReport(reportDir.resolve("performance-report.html"));
            generateCsvReport(reportDir.resolve("performance-report.csv"));
            
        } catch (IOException e) {
            System.err.println("Failed to generate performance report: " + e.getMessage());
        }
    }
    
    private static void generateTextReport(Path path) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            
            writer.println("=" .repeat(80));
            writer.println("MattMC PERFORMANCE TEST REPORT");
            writer.println("Generated: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            writer.println("=".repeat(80));
            writer.println();
            
            for (var entry : results.entrySet()) {
                String testName = entry.getKey();
                List<MetricEntry> metrics = entry.getValue();
                
                writer.println("TEST: " + testName);
                writer.println("-".repeat(60));
                
                for (MetricEntry metric : metrics) {
                    writer.printf("  %-30s: %12.3f %s%n", 
                                metric.metricName, metric.value, metric.unit);
                }
                writer.println();
            }
            
            writer.println("=".repeat(80));
            writer.println("END OF REPORT");
        }
    }
    
    private static void generateHtmlReport(Path path) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("    <meta charset=\"UTF-8\">");
            writer.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            writer.println("    <title>MattMC Performance Report</title>");
            writer.println("    <style>");
            writer.println("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; background: #f5f5f5; }");
            writer.println("        h1 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }");
            writer.println("        h2 { color: #34495e; margin-top: 30px; }");
            writer.println("        .timestamp { color: #7f8c8d; font-size: 14px; margin-bottom: 20px; }");
            writer.println("        .test-card { background: white; border-radius: 8px; padding: 20px; margin: 15px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
            writer.println("        .test-name { font-size: 18px; font-weight: 600; color: #2980b9; margin-bottom: 15px; }");
            writer.println("        table { border-collapse: collapse; width: 100%; }");
            writer.println("        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ecf0f1; }");
            writer.println("        th { background: #3498db; color: white; font-weight: 500; }");
            writer.println("        tr:hover { background: #f8f9fa; }");
            writer.println("        .value { font-family: 'Consolas', monospace; font-weight: 600; color: #27ae60; }");
            writer.println("        .unit { color: #7f8c8d; font-size: 12px; }");
            writer.println("        .summary { background: #ecf0f1; padding: 15px; border-radius: 5px; margin: 20px 0; }");
            writer.println("        .metric-good { color: #27ae60; }");
            writer.println("        .metric-warn { color: #f39c12; }");
            writer.println("        .metric-bad { color: #e74c3c; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("    <h1>🚀 MattMC Performance Test Report</h1>");
            writer.printf("    <div class=\"timestamp\">Generated: %s</div>%n", 
                         LocalDateTime.now().format(TIMESTAMP_FORMAT));
            
            writer.printf("    <div class=\"summary\"><strong>Total Tests:</strong> %d</div>%n", results.size());
            
            for (var entry : results.entrySet()) {
                String testName = entry.getKey();
                List<MetricEntry> metrics = entry.getValue();
                
                writer.println("    <div class=\"test-card\">");
                writer.printf("        <div class=\"test-name\">📊 %s</div>%n", escapeHtml(testName));
                writer.println("        <table>");
                writer.println("            <thead><tr><th>Metric</th><th>Value</th><th>Unit</th></tr></thead>");
                writer.println("            <tbody>");
                
                for (MetricEntry metric : metrics) {
                    writer.printf("            <tr><td>%s</td><td class=\"value\">%.3f</td><td class=\"unit\">%s</td></tr>%n",
                                escapeHtml(metric.metricName), metric.value, escapeHtml(metric.unit));
                }
                
                writer.println("            </tbody>");
                writer.println("        </table>");
                writer.println("    </div>");
            }
            
            writer.println("</body>");
            writer.println("</html>");
        }
    }
    
    private static void generateCsvReport(Path path) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            
            writer.println("Test Name,Metric Name,Value,Unit,Timestamp");
            
            for (var entry : results.entrySet()) {
                String testName = entry.getKey();
                for (MetricEntry metric : entry.getValue()) {
                    writer.printf("\"%s\",\"%s\",%.6f,\"%s\",\"%s\"%n",
                                escapeCsv(testName), escapeCsv(metric.metricName),
                                metric.value, escapeCsv(metric.unit), metric.timestamp);
                }
            }
        }
    }
    
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
    
    private static String escapeCsv(String s) {
        return s.replace("\"", "\"\"");
    }
    
    /**
     * Clear all recorded results (useful between test runs).
     */
    public static void clear() {
        results.clear();
    }
}
