package fallen;

import java.lang.instrument.Instrumentation;

/** Selects this observer through the pinned, probe-free server boot adapter. */
public final class RegionSequenceAgent {
    public static void premain(String options,Instrumentation instrumentation){
        FallenResourceAgent.premain(options,instrumentation);
    }
}
