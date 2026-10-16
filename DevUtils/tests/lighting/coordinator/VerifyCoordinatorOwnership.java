package net.minecraft.server.level;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/** Unchanged public producer/consumer witness for deferred identity ownership.
 * No expected coordinator or native representation is embedded here. */
public final class VerifyCoordinatorOwnership {
    private static final SectionPos FIRST = SectionPos.of(0, 1, 0);
    private static final SectionPos SECOND = SectionPos.of(0, 2, 0);

    private static final class CustomLayer extends DataLayer {
        CustomLayer(byte[] bytes) { super(bytes); }
        @Override public DataLayer copy() {
            VerifyThreadedLighting.event("custom-copy");
            return super.copy();
        }
    }

    private static DataLayer read(VerifyThreadedLighting.Fixture fixture, LightLayer channel, SectionPos section) {
        return fixture.engine.getLayerListener(channel).getDataLayerData(section);
    }

    private static void aliases(VerifyThreadedLighting.Fixture fixture, DataLayer source, String phase) {
        boolean first = read(fixture, LightLayer.BLOCK, FIRST) == source;
        boolean second = read(fixture, LightLayer.BLOCK, SECOND) == source;
        boolean sky = read(fixture, LightLayer.SKY, FIRST) == source;
        VerifyThreadedLighting.event(phase + " aliases " + first + " " + second + " " + sky);
        if (!first || !second || !sky) throw new AssertionError("Public queued/published identity changed at " + phase);
    }

    private static void values(DataLayer source, DataLayer independent, String phase) throws Exception {
        VerifyThreadedLighting.event(phase + " values " + source.get(0, 0, 0) + " " + source.get(1, 1, 1)
            + " " + source.get(2, 2, 2) + " independent=" + independent.get(0, 0, 0)
            + " content=" + VerifyThreadedLighting.layer(source));
        if (independent.get(0, 0, 0) != 2) throw new AssertionError("Independent source copy changed");
    }

    private static void scenario(String mode, PrintWriter trace) throws Exception {
        VerifyThreadedLighting.trace = null;
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            fixture.initialize();
            fixture.assertReady();
            VerifyThreadedLighting.trace = trace;
            VerifyThreadedLighting.event("case " + mode);
            byte[] originalArray = new byte[DataLayer.SIZE];
            Arrays.fill(originalArray, (byte)0x22);
            DataLayer source = mode.equals("custom") ? new CustomLayer(originalArray)
                : mode.equals("array") ? new DataLayer(originalArray) : new DataLayer(2);
            DataLayer independent = source.copy();
            fixture.engine.queueSectionData(LightLayer.BLOCK, FIRST, source);
            fixture.engine.queueSectionData(LightLayer.BLOCK, SECOND, source);
            fixture.engine.queueSectionData(LightLayer.SKY, FIRST, source);
            if (read(fixture, LightLayer.BLOCK, FIRST) == source) throw new AssertionError("Import ran before dispatch");

            if (mode.startsWith("escape")) {
                byte[] escaped = source.getData();
                escaped[0] = (byte)0x67;
                if (mode.equals("escape-fill")) {
                    source.fill(4);
                    escaped[0] = (byte)0xab;
                }
            } else if (mode.equals("array") || mode.equals("custom")) {
                originalArray[0] = (byte)0x67;
            } else {
                source.fill(4);
            }
            source.set(1, 1, 1, 7);
            values(source, independent, "before-dispatch");

            Error marker = new AssertionError("ownership PRE marker");
            AtomicBoolean once = new AtomicBoolean(mode.equals("throwing-pre"));
            fixture.add(false, () -> {
                aliases(fixture, source, "PRE");
                source.fill(6);
                originalArray[0] = (byte)0xdd;
                source.set(2, 2, 2, 11);
                byte[] escaped = source.getData();
                escaped[0] = (byte)0xcd;
                VerifyThreadedLighting.event("PRE mutation " + source.get(0, 0, 0) + " " + source.get(2, 2, 2));
                if (once.getAndSet(false)) throw marker;
            });
            fixture.transport();
            VerifyThreadedLighting.event("before-PRE pending " + fixture.pending());
            if (read(fixture, LightLayer.BLOCK, FIRST) == source) throw new AssertionError("Import ran during transport");
            try {
                fixture.update();
                VerifyThreadedLighting.event("first-update success");
                if (mode.equals("throwing-pre")) throw new AssertionError("PRE error lost");
            } catch (Error failure) {
                if (failure != marker) throw failure;
                VerifyThreadedLighting.event("first-update marker-identical " + (failure == marker) + " pending " + fixture.pending());
                aliases(fixture, source, "failed-PRE");
                values(source, independent, "failed-PRE");
                fixture.update();
                VerifyThreadedLighting.event("retry success");
            }
            aliases(fixture, source, "published");
            values(source, independent, "published");
            source.fill(3);
            originalArray[0] = (byte)0xee;
            aliases(fixture, source, "after-fill");
            values(source, independent, "after-fill");
            VerifyThreadedLighting.event("done pending " + fixture.pending() + " work " + fixture.engine.hasLightWork());
        } finally {
            VerifyThreadedLighting.trace = null;
        }
    }

    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (PrintWriter trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            for (String mode : new String[]{"native", "array", "escape", "escape-fill", "custom", "throwing-pre"})
                scenario(mode, trace);
        }
        System.out.println("OWNERSHIP_ROWS " + VerifyThreadedLighting.events);
    }
}
