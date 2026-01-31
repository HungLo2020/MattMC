package net.minecraft.util.profiling.custom;

import net.logging.LogUtils;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates interactive HTML profiling reports with expandable/collapsible sections.
 */
public class HtmlProfilerReportGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").withZone(ZoneId.systemDefault());
    
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MS = 1_000_000L;
    
    public Path generate(ProfilingSession session) throws IOException {
        // Create report directory
        Path reportDir = MetricsPersister.PROFILING_RESULTS_DIR.resolve("html");
        Files.createDirectories(reportDir);
        
        // Generate filename with timestamp
        String filename = "profile-" + TIMESTAMP_FORMATTER.format(Instant.ofEpochSecond(session.getStartTime() / NANOS_PER_SECOND)) + ".html";
        Path reportPath = reportDir.resolve(filename);
        
        // Generate HTML content
        String htmlContent = generateHtmlReport(session);
        
        // Write to file
        Files.writeString(reportPath, htmlContent, StandardCharsets.UTF_8);
        
        LOGGER.info("Generated HTML profiling report: {}", reportPath);
        return reportPath;
    }
    
    private String generateHtmlReport(ProfilingSession session) {
        StringBuilder html = new StringBuilder();
        
        // HTML header with styles and JavaScript
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Profiling Report - ").append(session.getSessionId()).append("</title>\n");
        html.append("    <style>\n");
        html.append(getCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // Header
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>Minecraft Profiling Report</h1>\n");
        html.append("        <div class=\"session-info\">\n");
        html.append("            <span>Session ID: ").append(session.getSessionId()).append("</span>\n");
        html.append("            <span>Duration: ").append(String.format("%.2f", session.getDuration() / (double) NANOS_PER_SECOND)).append(" seconds</span>\n");
        html.append("            <span>Generated: ").append(TIMESTAMP_FORMATTER.format(Instant.now())).append("</span>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        
        html.append("    <div class=\"container\">\n");
        
        // Server main thread section
        ThreadRecord mainThread = findThreadByName(session.getThreads(), "Server thread");
        if (mainThread != null) {
            html.append("        <div class=\"thread-section\">\n");
            generateThreadSection(html, "Server Thread (Main Thread)", mainThread, 
                session.getMainThreadHierarchicalOperations(), session.getTotalTicks(), 
                session.getAvgTickTime(), session.getDuration(), true);
            html.append("        </div>\n");
        }
        
        // Client render thread section
        ThreadRecord renderThread = findThreadByName(session.getThreads(), "Render thread");
        if (renderThread != null) {
            html.append("        <div class=\"thread-section\">\n");
            generateThreadSection(html, "Render Thread (Client)", renderThread,
                session.getRenderThreadHierarchicalOperations(), session.getTotalFrames(),
                session.getAvgFrameTime(), session.getDuration(), false);
            html.append("        </div>\n");
        }
        
        html.append("    </div>\n");
        
        // JavaScript for expand/collapse functionality
        html.append("    <script>\n");
        html.append(getJavaScript());
        html.append("    </script>\n");
        
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private void generateThreadSection(StringBuilder html, String threadName, ThreadRecord thread,
                                       Map<String, OperationRecord> hierarchicalOps, int ticksOrFrames,
                                       double avgTime, long sessionDuration, boolean isMainThread) {
        double activeSeconds = thread.getTotalCpuTime() / (double) NANOS_PER_SECOND;
        double totalSeconds = sessionDuration / (double) NANOS_PER_SECOND;
        
        // Calculate utilization - cap at 100% to avoid confusion
        // (CPU time can exceed wall time due to concurrent operations, but we display capped percentage)
        double activePercent = Math.min((activeSeconds / totalSeconds) * 100, 100.0);
        
        html.append("            <h2>").append(threadName).append("</h2>\n");
        html.append("            <div class=\"thread-stats\">\n");
        html.append("                <div class=\"stat\"><span class=\"label\">Active Time:</span> ").append(String.format("%.2f", activeSeconds)).append(" seconds (").append(String.format("%.2f", activePercent)).append("% utilized)</div>\n");
        
        if (isMainThread) {
            html.append("                <div class=\"stat\"><span class=\"label\">Total Ticks:</span> ").append(String.format("%,d", ticksOrFrames)).append("</div>\n");
            if (ticksOrFrames > 0) {
                double avgTickMs = avgTime / NANOS_PER_MS;
                html.append("                <div class=\"stat\"><span class=\"label\">Average Tick Time:</span> ").append(String.format("%.1f", avgTickMs)).append(" ms</div>\n");
            }
        } else {
            html.append("                <div class=\"stat\"><span class=\"label\">Total Frames:</span> ").append(String.format("%,d", ticksOrFrames)).append("</div>\n");
            if (ticksOrFrames > 0 && totalSeconds > 0) {
                double avgFps = ticksOrFrames / totalSeconds;
                double avgFrameMs = avgTime / NANOS_PER_MS;
                html.append("                <div class=\"stat\"><span class=\"label\">Average FPS:</span> ").append(String.format("%.2f", avgFps)).append("</div>\n");
                html.append("                <div class=\"stat\"><span class=\"label\">Average Frame Time:</span> ").append(String.format("%.2f", avgFrameMs)).append(" ms</div>\n");
            }
        }
        
        html.append("            </div>\n");
        
        if (hierarchicalOps != null && !hierarchicalOps.isEmpty()) {
            html.append("            <div class=\"operations\">\n");
            HierarchicalNode root = buildHierarchy(hierarchicalOps);
            generateHierarchicalHtml(html, root, hierarchicalOps, 0);
            html.append("            </div>\n");
        } else {
            html.append("            <div class=\"operations\">\n");
            html.append("                <div class=\"operation-item\">\n");
            html.append("                    <span class=\"toggle-placeholder\"></span>\n");
            html.append("                    <span class=\"operation-name\" style=\"color: #999; font-style: italic;\">No hierarchical profiling data available for this thread</span>\n");
            html.append("                    <span class=\"operation-time\"></span>\n");
            html.append("                    <span class=\"operation-percent\"></span>\n");
            html.append("                    <span class=\"operation-calls\"></span>\n");
            html.append("                </div>\n");
            html.append("            </div>\n");
        }
    }
    
    private void generateHierarchicalHtml(StringBuilder html, HierarchicalNode node,
                                          Map<String, OperationRecord> allOps, int depth) {
        if (depth > 10) return; // Prevent infinite recursion
        
        // Calculate total time for percentage calculations
        long totalNanos = allOps.values().stream()
            .mapToLong(OperationRecord::getTotalTime)
            .sum();
        
        // Sort children by total time (descending)
        List<HierarchicalNode> sortedChildren = new ArrayList<>(node.children.values());
        sortedChildren.sort((a, b) -> {
            long timeA = a.record != null ? a.record.getTotalTime() : a.getTotalTimeRecursive();
            long timeB = b.record != null ? b.record.getTotalTime() : b.getTotalTimeRecursive();
            return Long.compare(timeB, timeA);
        });
        
        // Show top 20 items at top level, top 10 at deeper levels
        int maxEntries = depth == 0 ? 20 : 10;
        int entriesToShow = Math.min(maxEntries, sortedChildren.size());
        
        for (int i = 0; i < entriesToShow; i++) {
            HierarchicalNode child = sortedChildren.get(i);
            long childTime = child.record != null ? child.record.getTotalTime() : child.getTotalTimeRecursive();
            
            if (childTime == 0) continue;
            
            double seconds = childTime / (double) NANOS_PER_SECOND;
            double percent = (childTime / (double) totalNanos) * 100;
            
            boolean hasChildren = !child.children.isEmpty();
            String nodeId = "node-" + UUID.randomUUID().toString();
            
            html.append("                <div class=\"operation-item\" style=\"margin-left: ").append(depth * 20).append("px;\">\n");
            
            if (hasChildren) {
                html.append("                    <span class=\"toggle\" onclick=\"toggleNode('").append(nodeId).append("')\">▶</span>\n");
            } else {
                html.append("                    <span class=\"toggle-placeholder\"></span>\n");
            }
            
            html.append("                    <span class=\"operation-name\">").append(escapeHtml(child.name)).append("</span>\n");
            html.append("                    <span class=\"operation-time\">").append(String.format("%.3f", seconds)).append("s</span>\n");
            html.append("                    <span class=\"operation-percent\">").append(String.format("%.2f", percent)).append("%</span>\n");
            
            if (child.record != null) {
                html.append("                    <span class=\"operation-calls\">").append(String.format("%,d", child.record.getCallCount())).append(" calls</span>\n");
            }
            
            html.append("                </div>\n");
            
            if (hasChildren) {
                html.append("                <div id=\"").append(nodeId).append("\" class=\"operation-children\" style=\"display: none;\">\n");
                generateHierarchicalHtml(html, child, allOps, depth + 1);
                html.append("                </div>\n");
            }
        }
        
        // Show "and X more" if there are more items
        if (sortedChildren.size() > entriesToShow) {
            long otherTime = 0;
            for (int i = entriesToShow; i < sortedChildren.size(); i++) {
                HierarchicalNode child = sortedChildren.get(i);
                otherTime += child.record != null ? child.record.getTotalTime() : child.getTotalTimeRecursive();
            }
            
            if (otherTime > 0) {
                double seconds = otherTime / (double) NANOS_PER_SECOND;
                double percent = (otherTime / (double) totalNanos) * 100;
                
                html.append("                <div class=\"operation-item\" style=\"margin-left: ").append(depth * 20).append("px; opacity: 0.6;\">\n");
                html.append("                    <span class=\"toggle-placeholder\"></span>\n");
                html.append("                    <span class=\"operation-name\">... and ").append(sortedChildren.size() - entriesToShow).append(" more</span>\n");
                html.append("                    <span class=\"operation-time\">").append(String.format("%.3f", seconds)).append("s</span>\n");
                html.append("                    <span class=\"operation-percent\">").append(String.format("%.2f", percent)).append("%</span>\n");
                html.append("                    <span class=\"operation-calls\"></span>\n");
                html.append("                </div>\n");
            }
        }
    }
    
    private HierarchicalNode buildHierarchy(Map<String, OperationRecord> operations) {
        HierarchicalNode root = new HierarchicalNode("root");
        
        for (Map.Entry<String, OperationRecord> entry : operations.entrySet()) {
            String path = entry.getKey();
            OperationRecord record = entry.getValue();
            
            // Split path into components
            String[] parts = path.split("\\.");
            
            // Navigate/create tree nodes
            HierarchicalNode current = root;
            for (String part : parts) {
                current = current.getOrCreateChild(part);
            }
            
            // Set the operation record
            current.record = record;
        }
        
        return root;
    }
    
    private ThreadRecord findThreadByName(Map<Long, ThreadRecord> threads, String name) {
        return threads.values().stream()
            .filter(t -> t.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
    
    private String getCSS() {
        return """
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                background: #f5f5f5;
                padding: 20px;
            }
            
            .header {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 30px;
                border-radius: 10px;
                margin-bottom: 20px;
                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            }
            
            .header h1 {
                font-size: 32px;
                margin-bottom: 10px;
            }
            
            .session-info {
                display: flex;
                gap: 30px;
                font-size: 14px;
                opacity: 0.9;
            }
            
            .container {
                max-width: 1400px;
                margin: 0 auto;
            }
            
            .thread-section {
                background: white;
                border-radius: 10px;
                padding: 25px;
                margin-bottom: 20px;
                box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            }
            
            .thread-section h2 {
                color: #333;
                font-size: 24px;
                margin-bottom: 15px;
                padding-bottom: 10px;
                border-bottom: 2px solid #667eea;
            }
            
            .thread-stats {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                gap: 15px;
                margin-bottom: 20px;
                padding: 15px;
                background: #f8f9fa;
                border-radius: 8px;
            }
            
            .stat {
                font-size: 14px;
                color: #555;
            }
            
            .stat .label {
                font-weight: 600;
                color: #333;
            }
            
            .operations {
                margin-top: 20px;
            }
            
            .operation-item {
                display: flex;
                align-items: center;
                padding: 8px 10px;
                margin: 2px 0;
                background: #fafafa;
                border-left: 3px solid #667eea;
                border-radius: 4px;
                font-family: 'Courier New', monospace;
                font-size: 13px;
                transition: background 0.2s;
            }
            
            .operation-item:hover {
                background: #f0f0f0;
            }
            
            .toggle {
                cursor: pointer;
                user-select: none;
                color: #667eea;
                font-weight: bold;
                width: 20px;
                text-align: center;
                transition: transform 0.2s;
            }
            
            .toggle.expanded {
                transform: rotate(90deg);
            }
            
            .toggle-placeholder {
                width: 20px;
                display: inline-block;
            }
            
            .operation-name {
                flex: 1;
                color: #333;
                font-weight: 500;
                margin-left: 5px;
            }
            
            .operation-time {
                width: 100px;
                text-align: right;
                color: #2196F3;
                font-weight: 600;
            }
            
            .operation-percent {
                width: 80px;
                text-align: right;
                color: #4CAF50;
                font-weight: 600;
            }
            
            .operation-calls {
                width: 120px;
                text-align: right;
                color: #FF9800;
                font-size: 11px;
            }
            
            .operation-children {
                overflow: hidden;
                transition: max-height 0.3s ease-out;
            }
        """;
    }
    
    private String getJavaScript() {
        return """
            function toggleNode(nodeId) {
                const node = document.getElementById(nodeId);
                const toggle = node.previousElementSibling.querySelector('.toggle');
                
                if (node.style.display === 'none') {
                    node.style.display = 'block';
                    toggle.textContent = '▼';
                    toggle.classList.add('expanded');
                } else {
                    node.style.display = 'none';
                    toggle.textContent = '▶';
                    toggle.classList.remove('expanded');
                }
            }
            
            // Auto-expand top-level items on load
            window.addEventListener('DOMContentLoaded', () => {
                console.log('Profiling report loaded');
            });
        """;
    }
    
    private static class HierarchicalNode {
        String name;
        OperationRecord record;
        Map<String, HierarchicalNode> children = new LinkedHashMap<>();
        
        HierarchicalNode(String name) {
            this.name = name;
        }
        
        HierarchicalNode getOrCreateChild(String childName) {
            return children.computeIfAbsent(childName, HierarchicalNode::new);
        }
        
        long getTotalTimeRecursive() {
            long total = record != null ? record.getTotalTime() : 0;
            for (HierarchicalNode child : children.values()) {
                total += child.getTotalTimeRecursive();
            }
            return total;
        }
    }
}
