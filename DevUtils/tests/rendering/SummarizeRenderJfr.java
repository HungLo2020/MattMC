import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordingFile;

/** Stream selected render-thread method sample weights without expanding JFR JSON. */
class SummarizeRenderJfr {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 33) {
            throw new IllegalArgumentException("usage: java SummarizeRenderJfr.java recording.jfr class.method [class.method ...]");
        }
        Map<String, long[]> methods = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].matches("[\\w.$<>]+")) throw new IllegalArgumentException("invalid method name");
            methods.put(args[i], new long[4]);
        }
        long allocations = 0, allocationWeight = 0, executions = 0, natives = 0;
        Instant start = null, end = null;
        try (var recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                String type = event.getEventType().getName();
                int kind = switch (type) {
                    case "jdk.ObjectAllocationSample" -> 0;
                    case "jdk.ExecutionSample" -> 2;
                    case "jdk.NativeMethodSample" -> 3;
                    default -> -1;
                };
                if (kind < 0) continue;
                var thread = event.getThread(kind == 0 ? "eventThread" : "sampledThread");
                if (thread == null || !"Render thread".equals(thread.getJavaName())) continue;
                Instant time = event.getStartTime();
                if (start == null || time.isBefore(start)) start = time;
                if (end == null || time.isAfter(end)) end = time;
                long weight = kind == 0 ? event.getLong("weight") : 1;
                if (kind == 0) { allocations++; allocationWeight += weight; }
                else if (kind == 2) executions++;
                else natives++;
                var stack = event.getStackTrace();
                if (stack == null) continue;
                var counted = new HashSet<String>();
                for (var frame : stack.getFrames()) {
                    var method = frame.getMethod();
                    String name = method.getType().getName() + "." + method.getName();
                    var counts = methods.get(name);
                    if (counts != null && counted.add(name)) {
                        counts[kind]++;
                        if (kind == 0) counts[1] += weight;
                    }
                }
            }
        }
        System.out.println("{\"schema\":\"render-jfr-method-weights-v1\",\"thread\":\"Render thread\",");
        System.out.println("\"first_sample\":\"" + start + "\",\"last_sample\":\"" + end + "\",");
        System.out.printf("\"total_samples\":{\"allocation\":%d,\"allocation_weight\":%d,\"execution\":%d,\"native\":%d},%n",
            allocations, allocationWeight, executions, natives);
        System.out.println("\"methods\":{");
        boolean first = true;
        for (var entry : methods.entrySet()) {
            if (!first) System.out.println(",");
            first = false;
            var counts = entry.getValue();
            System.out.printf("\"%s\":{\"allocation_samples\":%d,\"allocation_weight\":%d,\"execution_samples\":%d,\"native_samples\":%d}",
                entry.getKey(), counts[0], counts[1], counts[2], counts[3]);
        }
        System.out.println("},\"limitations\":\"Sampled stack weights, not exact allocated bytes or throughput; compare equivalent recording settings and workloads.\"}");
    }
}
