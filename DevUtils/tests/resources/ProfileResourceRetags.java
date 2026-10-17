import java.lang.reflect.InvocationTargetException;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Diagnostic repetition of the same production rebind/sync workload in the
 * shared Frozen observer. Does not change either checkout's game behavior. */
public final class ProfileResourceRetags {
    public static void main(String[] args) throws Exception {
        Path content = Path.of(args[0]);
        Path fixtures = Path.of(args[1]);
        Path record = Path.of(args[2]);
        Path ready = Path.of(args[3]);
        int iterations = Integer.parseInt(args[4]);
        ResourceStackReference.main(new String[]{content.toString(),
            fixtures.resolve("warmup").toString(), record.toString(), "1"});
        var workload = ResourceStackReference.class.getDeclaredMethod(
            "observeSync", Path.class, Path.class, int.class);
        workload.setAccessible(true);
        // The shared observer emits this label after registry setup and the
        // initial sync warm-up, immediately before its retag loop. Observe
        // that existing output; leave its production calls unchanged.
        var output = ResourceStackReference.class.getDeclaredField("record");
        output.setAccessible(true);
        output.set(null, new PrintWriter(Writer.nullWriter()) {
            @Override public void println(String line) {
                if ("-- reload".equals(line)) {
                    try {
                        Files.writeString(ready, Long.toString(ProcessHandle.current().pid()));
                    } catch (java.io.IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                }
                super.println(line);
            }
        });
        try {
            workload.invoke(null, content, fixtures.resolve("profile"), iterations);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
        System.out.println("RESOURCE_RETAG_PROFILE_COMPLETE diagnostic_only=true");
    }
}
