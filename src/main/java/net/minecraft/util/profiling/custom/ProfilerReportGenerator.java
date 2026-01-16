package net.minecraft.util.profiling.custom;

import com.mojang.logging.LogUtils;
import net.minecraft.SystemReport;
import net.minecraft.Util;
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
import java.util.stream.Collectors;

/**
 * Generates formatted profiling reports.
 */
public class ProfilerReportGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILENAME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").withZone(ZoneId.systemDefault());
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MS = 1_000_000L;

    public Path generate(ProfilingSession session) throws IOException {
        // Ensure directory exists
        Files.createDirectories(MetricsPersister.PROFILING_RESULTS_DIR);

        // Generate filename
        String timestamp = FILENAME_FORMATTER.format(Instant.ofEpochMilli(
            System.currentTimeMillis()));
        String filename = "profile-" + timestamp + ".txt";
        Path reportPath = MetricsPersister.PROFILING_RESULTS_DIR.resolve(filename);

        // Generate report content
        StringBuilder report = new StringBuilder();
        generateHeader(report, session);
        generateThreadSummary(report, session);
        generatePrimaryThreadAnalysis(report, session);
        generateAllThreadsDetail(report, session);
        generatePerformanceNotes(report, session);
        generateFooter(report, reportPath);

        // Write to file
        Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8));

        return reportPath;
    }

    private void generateHeader(StringBuilder sb, ProfilingSession session) {
        sb.append("=".repeat(80)).append("\n");
        sb.append(centerText("MattMC Performance Profile Report", 80)).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        sb.append("Session Information:\n");
        sb.append("  Session ID:     ").append(session.getSessionId()).append("\n");
        
        String initiatorName = "Unknown";
        try {
            if (session.getInitiator() != null && session.getInitiator().getTextName() != null) {
                initiatorName = session.getInitiator().getTextName();
            }
        } catch (Exception e) {
            // Ignore
        }
        sb.append("  Started by:     ").append(initiatorName).append("\n");
        
        sb.append("  Start Time:     ").append(TIMESTAMP_FORMATTER.format(
            Instant.ofEpochMilli(System.currentTimeMillis() - 
                (System.nanoTime() - session.getStartTime()) / NANOS_PER_MS))).append("\n");
        sb.append("  End Time:       ").append(TIMESTAMP_FORMATTER.format(
            Instant.ofEpochMilli(System.currentTimeMillis() - 
                (System.nanoTime() - session.getEndTime()) / NANOS_PER_MS))).append("\n");
        
        long durationSeconds = session.getDuration() / NANOS_PER_SECOND;
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        double preciseSeconds = session.getDuration() / (double) NANOS_PER_SECOND;
        
        sb.append("  Duration:       ").append(minutes).append(" minutes, ")
            .append(seconds).append(" seconds (")
            .append(String.format("%.2f", preciseSeconds)).append(" seconds)\n");
        sb.append("  \n");

        // System information
        SystemReport sysReport = new SystemReport();
        sb.append("System Information:\n");
        sb.append("  Minecraft:      1.21.10 (MattMC)\n");
        sb.append("  Java Version:   ").append(System.getProperty("java.version")).append("\n");
        sb.append("  OS:             ").append(System.getProperty("os.name")).append(" ")
            .append(System.getProperty("os.version")).append("\n");
        sb.append("  CPU Cores:      ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        sb.append("  Max Memory:     ").append(maxMemory).append(" MB\n");
        sb.append("  Used Memory:    ").append(usedMemory).append(" MB\n");
        sb.append("\n");
    }

    private void generateThreadSummary(StringBuilder sb, ProfilingSession session) {
        sb.append("=".repeat(80)).append("\n");
        sb.append(centerText("THREAD SUMMARY", 80)).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        Map<Long, ThreadRecord> threads = session.getThreads();
        long activeCount = threads.values().stream().filter(t -> !t.isTerminated()).count();
        long terminatedCount = threads.values().stream().filter(ThreadRecord::isTerminated).count();

        sb.append("Total Threads Tracked: ").append(threads.size()).append("\n");
        sb.append("  - Active at end:     ").append(activeCount).append("\n");
        sb.append("  - Terminated:        ").append(terminatedCount).append("\n\n");

        // Categorize threads
        Map<String, List<ThreadRecord>> categories = categorizeThreads(threads);
        
        sb.append("Thread Categories:\n");
        for (Map.Entry<String, List<ThreadRecord>> entry : categories.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(":").append(" ".repeat(Math.max(1, 20 - entry.getKey().length())))
                .append(entry.getValue().size()).append("\n");
        }
        sb.append("\n");
    }

    private Map<String, List<ThreadRecord>> categorizeThreads(Map<Long, ThreadRecord> threads) {
        Map<String, List<ThreadRecord>> categories = new LinkedHashMap<>();
        
        for (ThreadRecord thread : threads.values()) {
            String category;
            String name = thread.getName();
            
            if (name.contains("Server thread") || name.contains("Render thread")) {
                category = "Main Threads";
            } else if (name.contains("Netty")) {
                category = "Network I/O";
            } else if (name.contains("Worker-Main")) {
                category = "Worker Pools";
            } else if (name.contains("IO-Worker") || name.contains("Download")) {
                category = "File I/O";
            } else {
                category = "Other";
            }
            
            categories.computeIfAbsent(category, k -> new ArrayList<>()).add(thread);
        }
        
        return categories;
    }

    private void generatePrimaryThreadAnalysis(StringBuilder sb, ProfilingSession session) {
        sb.append("=".repeat(80)).append("\n");
        sb.append(centerText("PRIMARY THREAD ANALYSIS", 80)).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        // Main thread analysis
        ThreadRecord mainThread = findThreadByName(session.getThreads(), "Server thread");
        if (mainThread != null) {
            generateMainThreadAnalysis(sb, session, mainThread);
        }

        // Render thread analysis
        ThreadRecord renderThread = findThreadByName(session.getThreads(), "Render thread");
        if (renderThread != null) {
            generateRenderThreadAnalysis(sb, session, renderThread);
        }
    }

    private void generateMainThreadAnalysis(StringBuilder sb, ProfilingSession session, ThreadRecord mainThread) {
        sb.append("─".repeat(80)).append("\n");
        sb.append("Server Thread (Main Thread)\n");
        sb.append("─".repeat(80)).append("\n\n");

        double activeSeconds = mainThread.getTotalCpuTime() / (double) NANOS_PER_SECOND;
        double totalSeconds = session.getDuration() / (double) NANOS_PER_SECOND;
        double activePercent = (activeSeconds / totalSeconds) * 100;

        sb.append(String.format("Total Active Time:    %.2f seconds (%.2f%% of session)\n", 
            activeSeconds, activePercent));
        sb.append("Total Ticks:          ").append(String.format("%,d", session.getTotalTicks())).append("\n");
        
        if (session.getTotalTicks() > 0) {
            double avgTickMs = session.getAvgTickTime() / NANOS_PER_MS;
            sb.append(String.format("Average Tick Time:    %.1f ms\n", avgTickMs));
        }
        
        sb.append("\n");

        // Show hierarchical breakdown if available
        Map<String, OperationRecord> hierarchicalOps = session.getMainThreadHierarchicalOperations();
        if (hierarchicalOps != null && !hierarchicalOps.isEmpty()) {
            sb.append("Detailed Hierarchical Breakdown:\n\n");
            generateHierarchicalBreakdown(sb, hierarchicalOps);
            sb.append("\n");
        }

        // Show top-level operations
        Map<String, OperationRecord> ops = session.getMainThreadOperations();
        if (!ops.isEmpty()) {
            sb.append("Top-Level Operations:\n\n");
            generateOperationBreakdown(sb, ops, activeSeconds);
        }
        
        sb.append("\n");
    }

    private void generateRenderThreadAnalysis(StringBuilder sb, ProfilingSession session, ThreadRecord renderThread) {
        sb.append("─".repeat(80)).append("\n");
        sb.append("Render Thread (Client-side only)\n");
        sb.append("─".repeat(80)).append("\n\n");

        double activeSeconds = renderThread.getTotalCpuTime() / (double) NANOS_PER_SECOND;
        double totalSeconds = session.getDuration() / (double) NANOS_PER_SECOND;
        double activePercent = (activeSeconds / totalSeconds) * 100;

        sb.append(String.format("Total Active Time:    %.2f seconds (%.2f%% of session)\n", 
            activeSeconds, activePercent));
        sb.append("Total Frames:         ").append(String.format("%,d", session.getTotalFrames())).append("\n");
        
        if (session.getTotalFrames() > 0 && totalSeconds > 0) {
            double avgFps = session.getTotalFrames() / totalSeconds;
            double avgFrameMs = session.getAvgFrameTime() / NANOS_PER_MS;
            sb.append(String.format("Average FPS:          %.2f\n", avgFps));
            sb.append(String.format("Frame Time Average:   %.2f ms\n", avgFrameMs));
        }
        
        sb.append("\n");

        // Show hierarchical breakdown if available
        Map<String, OperationRecord> hierarchicalOps = session.getRenderThreadHierarchicalOperations();
        if (hierarchicalOps != null && !hierarchicalOps.isEmpty()) {
            sb.append("Detailed Hierarchical Breakdown:\n\n");
            generateHierarchicalBreakdown(sb, hierarchicalOps);
            sb.append("\n");
        }

        // Show top-level operations
        Map<String, OperationRecord> ops = session.getRenderThreadOperations();
        if (!ops.isEmpty()) {
            sb.append("Top-Level Operations:\n\n");
            generateOperationBreakdown(sb, ops, activeSeconds);
        }
        
        sb.append("\n");
    }

    private void generateOperationBreakdown(StringBuilder sb, Map<String, OperationRecord> operations, double totalSeconds) {
        sb.append("Time Distribution by Operation:\n\n");

        // Sort by total time descending
        List<Map.Entry<String, OperationRecord>> sorted = operations.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().getTotalTime(), a.getValue().getTotalTime()))
            .limit(20)  // Top 20 operations
            .collect(Collectors.toList());

        long totalNanos = operations.values().stream()
            .mapToLong(OperationRecord::getTotalTime)
            .sum();

        int index = 1;
        for (Map.Entry<String, OperationRecord> entry : sorted) {
            OperationRecord op = entry.getValue();
            double seconds = op.getTotalTime() / (double) NANOS_PER_SECOND;
            double percent = (op.getTotalTime() / (double) totalNanos) * 100;
            
            sb.append(String.format("%d. %-40s %8.2fs  %6.2f%%\n", 
                index++, truncate(entry.getKey(), 40), seconds, percent));
        }
    }

    private void generateAllThreadsDetail(StringBuilder sb, ProfilingSession session) {
        sb.append("=".repeat(80)).append("\n");
        sb.append(centerText("ALL THREADS DETAIL", 80)).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        Map<Long, ThreadRecord> threads = session.getThreads();
        
        // Group threads
        Map<String, List<ThreadRecord>> categories = categorizeThreads(threads);
        
        for (Map.Entry<String, List<ThreadRecord>> entry : categories.entrySet()) {
            sb.append("[").append(entry.getKey()).append("]\n");
            
            for (ThreadRecord thread : entry.getValue()) {
                generateThreadDetail(sb, thread, session.getEndTime());
            }
            
            sb.append("\n");
        }
    }

    private void generateThreadDetail(StringBuilder sb, ThreadRecord thread, long sessionEnd) {
        sb.append("  ID:           ").append(thread.getThreadId()).append("\n");
        sb.append("  Name:         ").append(thread.getName()).append("\n");
        
        if (thread.getPurpose() != null && !thread.getPurpose().isEmpty()) {
            sb.append("  Purpose:      ").append(thread.getPurpose()).append("\n");
        }
        
        double lifetimeSeconds = thread.getLifetime(sessionEnd) / (double) NANOS_PER_SECOND;
        sb.append(String.format("  Lifetime:     %.2f seconds\n", lifetimeSeconds));
        
        if (thread.getTotalCpuTime() > 0) {
            double cpuSeconds = thread.getTotalCpuTime() / (double) NANOS_PER_SECOND;
            double cpuPercent = (cpuSeconds / lifetimeSeconds) * 100;
            sb.append(String.format("  CPU Time:     %.2f seconds (%.2f%% active)\n", 
                cpuSeconds, cpuPercent));
        }
        
        sb.append("  Terminated:   ").append(thread.isTerminated() ? "Yes" : "[Still Running]").append("\n");
        sb.append("\n");
    }

    private void generatePerformanceNotes(StringBuilder sb, ProfilingSession session) {
        sb.append("=".repeat(80)).append("\n");
        sb.append(centerText("PERFORMANCE NOTES", 80)).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        sb.append("Profiling completed successfully.\n");
        sb.append("Review the operation breakdowns above to identify performance bottlenecks.\n\n");
    }
    
    /**
     * Generate hierarchical breakdown of operations in a tree structure.
     */
    private void generateHierarchicalBreakdown(StringBuilder sb, Map<String, OperationRecord> operations) {
        // Build a tree structure from the flat map
        HierarchicalNode root = buildHierarchy(operations);
        
        // Calculate total time for percentage calculations
        long totalNanos = operations.values().stream()
            .mapToLong(OperationRecord::getTotalTime)
            .sum();
        
        // Render the tree, limiting to top entries
        renderHierarchicalTree(sb, root, totalNanos, 0, 100);
    }
    
    /**
     * Build a hierarchical tree from flat operation paths.
     */
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
    
    /**
     * Render hierarchical tree with indentation.
     */
    private void renderHierarchicalTree(StringBuilder sb, HierarchicalNode node, long totalNanos, int depth, int maxEntries) {
        if (depth > 10 || maxEntries <= 0) { // Limit depth to prevent huge reports
            return;
        }
        
        // Sort children by total time (descending)
        List<HierarchicalNode> sortedChildren = new ArrayList<>(node.children.values());
        sortedChildren.sort((a, b) -> {
            long timeA = a.record != null ? a.record.getTotalTime() : a.getTotalTimeRecursive();
            long timeB = b.record != null ? b.record.getTotalTime() : b.getTotalTimeRecursive();
            return Long.compare(timeB, timeA);
        });
        
        // Limit to top entries at each level
        int entriesToShow = Math.min(maxEntries, sortedChildren.size());
        
        for (int i = 0; i < entriesToShow; i++) {
            HierarchicalNode child = sortedChildren.get(i);
            long childTime = child.record != null ? child.record.getTotalTime() : child.getTotalTimeRecursive();
            
            if (childTime == 0) continue;
            
            double seconds = childTime / (double) NANOS_PER_SECOND;
            double percent = (childTime / (double) totalNanos) * 100;
            
            // Indentation
            String indent = "  ".repeat(depth);
            String prefix = depth == 0 ? "" : "├─ ";
            
            // Format: indent + prefix + name + time + percentage
            sb.append(String.format("%s%s%-40s %8.2fs  %6.2f%%\n", 
                indent, prefix, truncate(child.name, 40), seconds, percent));
            
            // Render children (reduce maxEntries for deeper levels)
            if (!child.children.isEmpty()) {
                renderHierarchicalTree(sb, child, totalNanos, depth + 1, Math.max(10, maxEntries - 10));
            }
        }
        
        // Show "other" if there are more entries
        if (sortedChildren.size() > entriesToShow) {
            long otherTime = 0;
            for (int i = entriesToShow; i < sortedChildren.size(); i++) {
                HierarchicalNode child = sortedChildren.get(i);
                otherTime += child.record != null ? child.record.getTotalTime() : child.getTotalTimeRecursive();
            }
            
            if (otherTime > 0) {
                double seconds = otherTime / (double) NANOS_PER_SECOND;
                double percent = (otherTime / (double) totalNanos) * 100;
                String indent = "  ".repeat(depth);
                String prefix = depth == 0 ? "" : "└─ ";
                
                sb.append(String.format("%s%s%-40s %8.2fs  %6.2f%%\n", 
                    indent, prefix, "... and " + (sortedChildren.size() - entriesToShow) + " more", seconds, percent));
            }
        }
    }
    
    /**
     * Tree node for hierarchical operations.
     */
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

    private void generateFooter(StringBuilder sb, Path reportPath) {
        sb.append("=".repeat(80)).append("\n");
        sb.append(centerText("END OF REPORT", 80)).append("\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Generated: ").append(TIMESTAMP_FORMATTER.format(Instant.now())).append("\n");
        sb.append("Report File: ").append(reportPath).append("\n");
    }

    private ThreadRecord findThreadByName(Map<Long, ThreadRecord> threads, String nameContains) {
        return threads.values().stream()
            .filter(t -> t.getName().contains(nameContains))
            .findFirst()
            .orElse(null);
    }

    private String centerText(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
