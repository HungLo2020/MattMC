package fallen;

import java.lang.instrument.Instrumentation;

/** Reuse the pinned public-world probes, with an independent region driver. */
public final class RegionAuditAgent {
    public static void premain(String options,Instrumentation instrumentation){
        FallenAudit.STARTED.set(true);
        FallenAgent.premain(options,instrumentation);
        FallenResourceAgent.premain(options,instrumentation);
    }
}
