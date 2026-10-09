import com.sun.tools.attach.VirtualMachine;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.ProtectionDomain;

/** JDK 25 test agent: identical, bounded frame timing on Current and Frozen.
 * It does not select a renderer, camera, world, GC, or terrain readiness gate.
 */
public final class GameplayPerformanceObserver {
    private static final int CAPACITY = 1_000_000;
    private static final long[] END_MS = new long[CAPACITY];
    private static final long[] INTERVAL_NS = new long[CAPACITY];
    private static final long[] TICK_NS = new long[CAPACITY];
    private static volatile int count;
    private static volatile boolean active;
    private static volatile boolean overflow;
    private static volatile long dhDrawCalls;
    private static long previousStart, start;
    private static long interval;
    private static volatile Object minimapOptions;
    private static volatile Field minimapHide;
    private static volatile Boolean requestedHidden;

    public static void beginFrame() {
        if (!active) return;
        // Apply the requested normal setting once, on the game's render thread.
        // This compensates for Frozen's legacy capture launcher hiding the map.
        if (requestedHidden != null && minimapOptions != null) {
            try {
                minimapHide.setBoolean(minimapOptions, requestedHidden);
                requestedHidden = null;
            } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        long now = System.nanoTime();
        interval = previousStart == 0 ? 0 : now - previousStart;
        previousStart = start = now;
    }

    public static void endFrame() {
        if (!active || start == 0) return;
        int index = count;
        if (index == CAPACITY) { overflow = true; return; }
        TICK_NS[index] = System.nanoTime() - start;
        END_MS[index] = System.currentTimeMillis();
        INTERVAL_NS[index] = interval;
        count = index + 1;
        start = 0;
    }

    public static void recordDhDraw() { dhDrawCalls++; }

    static byte[] instrument(byte[] original) {
        var owner = ClassDesc.of("GameplayFrameHooks");
        var signature = MethodTypeDesc.ofDescriptor("()V");
        var cf = ClassFile.of();
        return cf.transformClass(cf.parse(original), (builder, element) -> {
            if (element instanceof MethodModel method && method.methodName().equalsString("runTick")
                    && method.methodType().equalsString("(Z)V")) {
                builder.transformMethod(method, (mb, me) -> {
                    if (me instanceof CodeModel code) {
                        mb.transformCode(code, new CodeTransform() {
                            public void atStart(CodeBuilder cb) { cb.invokestatic(owner, "beginFrame", signature); }
                            public void accept(CodeBuilder cb, CodeElement ce) {
                                if (ce instanceof ReturnInstruction) cb.invokestatic(owner, "endFrame", signature);
                                cb.with(ce);
                            }
                        });
                    } else mb.with(me);
                });
            } else if (element instanceof MethodModel method && method.methodName().equalsString("renderLodBuffers")) {
                builder.transformMethod(method, (mb, me) -> {
                    if (me instanceof CodeModel code) mb.transformCode(code, (cb, ce) -> {
                        cb.with(ce);
                        if (ce instanceof InvokeInstruction call && call.name().equalsString("drawElements")) {
                            cb.invokestatic(owner, "recordDhDraw", signature);
                        }
                    });
                    else mb.with(me);
                });
            } else builder.with(element);
        });
    }

    private static Object read(Object object, String name) throws ReflectiveOperationException {
        Class<?> type = object instanceof Class<?> c ? c : object.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object instanceof Class<?> ? null : object);
    }

    private static Class<?> loaded(Instrumentation inst, String name) {
        for (Class<?> type : inst.getAllLoadedClasses()) if (type.getName().equals(name)) return type;
        return null;
    }

    private static String quote(Object value) {
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
    }

    public static void agentmain(String argument, Instrumentation inst) throws Exception {
        Path output = Path.of(argument);
        inst.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(output.getParent().resolve("observer-hooks.jar").toFile()));
        GameplayFrameHooks.begin = GameplayPerformanceObserver::beginFrame;
        GameplayFrameHooks.end = GameplayPerformanceObserver::endFrame;
        GameplayFrameHooks.draw = GameplayPerformanceObserver::recordDhDraw;
        String mode = Files.readString(output.resolve("minimap-mode.txt")).trim();
        Class<?> mc = loaded(inst, "net.minecraft.client.Minecraft");
        if (mc == null || !inst.isModifiableClass(mc)) throw new IllegalStateException("game class is unavailable for timing");
        ClassFileTransformer transform = new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraft/client/Minecraft".equals(name)
                        && !"com/seibel/distanthorizons/core/render/renderer/LodRenderer".equals(name)) return null;
                return instrument(bytes);
            }
        };
        inst.addTransformer(transform, true);
        inst.retransformClasses(mc);
        Class<?> lod = loaded(inst, "com.seibel.distanthorizons.core.render.renderer.LodRenderer");
        if (lod != null && inst.isModifiableClass(lod)) inst.retransformClasses(lod);
        active = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> writeFrames(output), "gameplay-frame-save"));
        Thread observer = new Thread(() -> observe(output, inst, mc, mode), "gameplay-performance-observer");
        observer.setDaemon(true);
        observer.start();
    }

    private static synchronized void writeFrames(Path output) {
        try {
            int size = count;
            Path temporary = output.resolve("frames.bin.tmp");
            try (var stream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                stream.writeInt(size);
                stream.writeBoolean(overflow);
                for (int i = 0; i < size; i++) {
                    stream.writeLong(END_MS[i]); stream.writeLong(INTERVAL_NS[i]); stream.writeLong(TICK_NS[i]);
                }
            }
            Files.move(temporary, output.resolve("frames.bin"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            try { Files.writeString(output.resolve("frames.error"), error.toString()); } catch (Exception ignored) { }
        }
    }

    private static void observe(Path output, Instrumentation inst, Class<?> mc, String mode) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output.resolve("runtime.jsonl")))) {
            boolean minimapConfigured = false;
            // At most 15 minutes, including load/settling. The driver owns the shorter process budget.
            for (int second = 0; second < 900; second++) {
                if (Files.exists(output.resolve("stop"))) {
                    active = false;
                    writeFrames(output);
                    return;
                }
                Object game = mc.getMethod("getInstance").invoke(null);
                // GLFW can expose the window before Minecraft finishes constructing
                // its input tracker and debug overlay. Keep all frame hooks active.
                if (game == null) { Thread.sleep(1000); continue; }
                Object tracker = mc.getMethod("getFramerateLimitTracker").invoke(game);
                Object debug = mc.getMethod("getDebugOverlay").invoke(game);
                if (tracker == null || debug == null) { Thread.sleep(1000); continue; }
                Object screen = read(game, "screen"), player = read(game, "player"), level = read(game, "level");
                Class<?> voxel = loaded(inst, "net.voxelmap.VoxelMap");
                Object options = voxel == null ? null : read(voxel, "mapOptions");
                if (!minimapConfigured && options != null && screen == null && player != null && level != null) {
                    Field hide = options.getClass().getField("hide");
                    minimapHide = hide; minimapOptions = options;
                    requestedHidden = switch (mode) {
                        case "visible" -> false;
                        case "hidden" -> true;
                        default -> throw new IllegalArgumentException("unknown minimap mode " + mode);
                    };
                    minimapConfigured = true;
                }
                Runtime rt = Runtime.getRuntime();
                StringBuilder row = new StringBuilder("{\"time_ms\":").append(System.currentTimeMillis())
                    .append(",\"frames\":").append(count).append(",\"overflow\":").append(overflow)
                    .append(",\"dh_draw_calls\":").append(dhDrawCalls)
                    .append(",\"fps\":").append(mc.getMethod("getFps").invoke(game))
                    .append(",\"world\":").append(player != null && level != null)
                    .append(",\"screen\":").append(quote(screen == null ? "none" : screen.getClass().getName()))
                    .append(",\"debug_visible\":").append(debug.getClass().getMethod("showDebugScreen").invoke(debug))
                    .append(",\"throttle\":").append(quote(tracker.getClass().getMethod("getThrottleReason").invoke(tracker)))
                    .append(",\"heap_used\":").append(rt.totalMemory() - rt.freeMemory())
                    .append(",\"heap_committed\":").append(rt.totalMemory());
                if (options != null) {
                    row.append(",\"minimap_hidden\":").append(read(options, "hide"));
                    row.append(",\"minimap_allowed\":").append(read(options, "minimapAllowed"));
                }
                Class<?> coordinator = loaded(inst, "net.vulkanic.gui.RustGalFrameCoordinator");
                if (coordinator != null) {
                    row.append(",\"presented_frames\":").append(coordinator.getMethod("presentedFrameCount").invoke(null));
                    Object metrics = read(coordinator, "METRICS");
                    for (String field : new String[]{"worldLodFramesExecuted", "worldLodInstancesSubmitted",
                            "worldLodAssetUpdateCalls", "rawImageUpdateCalls", "rawImageUpdatePayloadBytes"}) {
                        try {
                            Object value = read(metrics, field);
                            row.append(',').append(quote(field)).append(':').append(value);
                        }
                        catch (NoSuchFieldException ignored) { }
                    }
                }
                if (player != null) {
                    row.append(",\"x\":").append(player.getClass().getMethod("getX").invoke(player))
                        .append(",\"z\":").append(player.getClass().getMethod("getZ").invoke(player));
                }
                writer.println(row.append('}')); writer.flush();
                if (Files.exists(output.resolve("stop"))) {
                    active = false;
                    writeFrames(output);
                    return;
                }
                Thread.sleep(1000);
            }
        } catch (Throwable error) {
            try { Files.writeString(output.resolve("observer.error"), error.toString()); } catch (Exception ignored) { }
        }
    }
}
