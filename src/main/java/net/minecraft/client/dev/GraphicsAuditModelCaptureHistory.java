package net.minecraft.client.dev;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.function.ToLongFunction;

/** Bounded copies of producer observations near selected screenshot frames. */
final class GraphicsAuditModelCaptureHistory<T> {
    private final Map<Long, List<T>> frames = new TreeMap<>();
    private final HashSet<Long> captures = new HashSet<>();
    private final ToLongFunction<T> frameIndex;

    GraphicsAuditModelCaptureHistory(ToLongFunction<T> frameIndex) {
        this.frameIndex = frameIndex;
    }

    void retain(long capture, List<T> observations) {
        if (capture <= 0 || observations.size() > 512
                || (!captures.contains(capture) && captures.size() >= 64)) {
            throw new IllegalStateException("model capture history bounds exceeded");
        }
        var selected = observations.stream()
            .filter(value -> frameIndex.applyAsLong(value) >= capture - 8 && frameIndex.applyAsLong(value) <= capture)
            .collect(Collectors.groupingBy(value -> frameIndex.applyAsLong(value)));
        selected.forEach((frame, values) -> {
            var previous = frames.get(frame);
            if (previous != null && !previous.equals(values)) {
                throw new IllegalStateException("model observations changed after capture");
            }
        });
        captures.add(capture);
        selected.forEach((frame, values) -> frames.putIfAbsent(frame, List.copyOf(values)));
    }

    List<T> merge(List<T> live) {
        var result = new ArrayList<T>();
        frames.values().forEach(result::addAll);
        // Preserve duplicate producers within a frame. Only replace a live
        // frame with its previously copied complete observation list.
        live.stream().filter(value -> !frames.containsKey(frameIndex.applyAsLong(value))).forEach(result::add);
        return List.copyOf(result);
    }
}
