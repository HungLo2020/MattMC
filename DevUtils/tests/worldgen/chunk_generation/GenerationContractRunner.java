import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Runs the same compiled observable contracts against each sealed main runtime. */
public final class GenerationContractRunner {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request();
        for (String name : args) request.selectors(DiscoverySelectors.selectClass(name));
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request.build(), listener);
        var result = listener.getSummary();
        result.printTo(new PrintWriter(System.out, true));
        result.printFailuresTo(new PrintWriter(System.out, true));
        if (result.getTestsFoundCount() == 0 || result.getTestsFailedCount() != 0 || result.getContainersFailedCount() != 0)
            System.exit(1);
    }
}
