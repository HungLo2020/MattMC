package fallen;
import java.lang.instrument.Instrumentation;
/** Boots the bounded profile through the retained observer's probe-free hook. */
public final class RegionProfileAgent {
    public static void premain(String options,Instrumentation instrumentation){FallenResourceAgent.premain(options,instrumentation);}
}
